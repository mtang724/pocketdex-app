package app.pocketdex.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Official artwork, downloaded from PokeAPI's sprite repository the first time it's needed and
 * kept on the phone as small webp files. No Pokémon images ship inside the app or the repo.
 *
 * On first launch every image is fetched in the background (only on an unmetered network,
 * since it's about 130 MB); whatever is on screen is fetched right away on any network.
 */
class ArtStore(context: Context, private val total: Int = 1025) {

    data class Progress(
        val done: Int,
        val total: Int,
        val running: Boolean = false,
        val waitingForWifi: Boolean = false,
        val failed: Boolean = false,
    ) {
        val complete: Boolean get() = done >= total
    }

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "art").apply { mkdirs() }
    private val memory = LruCache<Int, ImageBitmap>(48)
    private val inFlight = ConcurrentHashMap<Int, Deferred<Boolean>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val doneCount = AtomicInteger(dir.list()?.count { it.endsWith(".webp") } ?: 0)
    private var bulk: Job? = null

    private val _progress = MutableStateFlow(Progress(doneCount.get(), total))
    val progress: StateFlow<Progress> = _progress

    fun cached(id: Int): ImageBitmap? = memory.get(id)

    /** Artwork for [id], downloading it first if needed. Null when it isn't cached and can't be fetched. */
    suspend fun load(id: Int): ImageBitmap? {
        memory.get(id)?.let { return it }
        if (!ensure(id)) return null
        return withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file(id).path)?.asImageBitmap() }
            ?.also { memory.put(id, it) }
    }

    /** Starts (or resumes) the one-time download of every image. Safe to call repeatedly. */
    fun startBulkDownload() {
        if (bulk?.isActive == true || _progress.value.complete) return
        val unmetered = runCatching {
            val cm = appContext.getSystemService(ConnectivityManager::class.java)
            cm.activeNetwork != null && !cm.isActiveNetworkMetered
        }.getOrDefault(false)
        if (!unmetered) {
            _progress.update { it.copy(waitingForWifi = true) }
            return
        }
        bulk = scope.launch {
            _progress.update { it.copy(running = true, waitingForWifi = false, failed = false) }
            val permits = Semaphore(6)
            val failures = AtomicInteger()
            (1..total).filterNot { file(it).exists() }
                .map { id -> launch { permits.withPermit { if (!ensure(id)) failures.incrementAndGet() } } }
                .joinAll()
            _progress.update { it.copy(running = false, failed = failures.get() > 0) }
        }
    }

    private fun file(id: Int) = File(dir, "$id.webp")

    /** Makes sure the file for [id] exists, sharing one download between concurrent callers. */
    private suspend fun ensure(id: Int): Boolean {
        if (file(id).exists()) return true
        val mine = scope.async(start = CoroutineStart.LAZY) { download(id) }
        val job = inFlight.putIfAbsent(id, mine) ?: mine.also { it.start() }
        return try {
            job.await()
        } finally {
            inFlight.remove(id, job)
        }
    }

    private fun download(id: Int): Boolean {
        if (file(id).exists()) return true
        val ok = runCatching {
            val conn = URL(URL_FORMAT.format(id)).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching false
                val src = conn.inputStream.use { BitmapFactory.decodeStream(it) } ?: return@runCatching false
                val scale = SIZE.toFloat() / max(src.width, src.height)
                val small = if (scale < 1f) {
                    Bitmap.createScaledBitmap(src, (src.width * scale).roundToInt(), (src.height * scale).roundToInt(), true)
                } else src
                val tmp = File(dir, "$id.tmp")
                tmp.outputStream().use { small.compress(webp, 82, it) }
                if (small !== src) small.recycle()
                src.recycle()
                tmp.renameTo(file(id))
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
        if (ok) _progress.update { it.copy(done = doneCount.incrementAndGet()) }
        return ok
    }

    @Suppress("DEPRECATION")
    private val webp: Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    companion object {
        private const val SIZE = 256
        private const val URL_FORMAT =
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/%d.png"
    }
}
