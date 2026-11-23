package app.pocketdex.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pocketdex.data.ArtStore
import app.pocketdex.data.Dex
import app.pocketdex.data.DexSettings
import app.pocketdex.data.Lang
import app.pocketdex.data.SettingsStore
import app.pocketdex.data.Species
import app.pocketdex.ml.ClaudeRecognizer
import app.pocketdex.ml.Guess
import app.pocketdex.ml.PokemonClassifier
import app.pocketdex.util.Beeper
import app.pocketdex.util.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab(val zh: String, val en: String) {
    ENTRY("图鉴", "Dex"), ABILITY("特性", "Abil."), MATCHUP("相克", "Types"),
    EVOLUTION("进化", "Evo"), MOVES("招式", "Moves"), INFO("资料", "Info"),
}

enum class Dir { UP, DOWN, LEFT, RIGHT }

/** What the analysing screen is waiting for. */
enum class Phase { LOCAL, CLAUDE }

/** Where the entry on screen came from. */
enum class Source { LOCAL, CLAUDE, NUMBER, RANDOM }

data class Recognition(val source: Source, val confidence: Float? = null)

/** Why the candidate list is shown. */
sealed interface Note {
    data object Unsure : Note
    data object Blurry : Note
    data class ClaudeFailed(val detail: String) : Note
    data object ClaudeNoMatch : Note
}

sealed interface Msg {
    data class ClassifyError(val detail: String) : Msg
    data object NoApiKey : Msg
    data class BadNumber(val max: Int) : Msg
}

/** One-shot notices for the UI to show as a toast. */
enum class UiEvent { TTS_MISSING }

sealed interface Display {
    data object Booting : Display
    data object Idle : Display
    data object Camera : Display
    data class Analyzing(val photo: Bitmap, val phase: Phase) : Display
    data object Entry : Display
    data class Candidates(val photo: Bitmap, val guesses: List<Guess>, val selected: Int, val note: Note) : Display
    data class Message(val msg: Msg) : Display
}

data class DexUiState(
    val display: Display = Display.Booting,
    val current: Species? = null,
    val tab: Tab = Tab.ENTRY,
    val digits: String = "",
    val recognition: Recognition? = null,
    val lastPhoto: Bitmap? = null,
    val lastGuesses: List<Guess> = emptyList(),
    val torch: Boolean = false,
    val zoom: Float = 0f,
    val settings: DexSettings = DexSettings(),
    val showSettings: Boolean = false,
    val event: UiEvent? = null,
) {
    val lang: Lang get() = settings.language
}

class DexViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val classifier = PokemonClassifier(app)
    val speaker = Speaker(app)
    private val beeper = Beeper()
    val art = ArtStore(app)

    private val _state = MutableStateFlow(DexUiState(settings = store.load()))
    val state: StateFlow<DexUiState> = _state

    var dex: Dex? = null
        private set

    private var job: Job? = null
    private var claude: Pair<String, ClaudeRecognizer>? = null

    init {
        viewModelScope.launch {
            val minBoot = async { delay(900) }
            dex = withContext(Dispatchers.IO) { Dex.load(getApplication()) }
            minBoot.await()
            _state.update { it.copy(display = Display.Idle) }
            // First launch: fetch all artwork from PokeAPI in the background (Wi-Fi only).
            art.startBulkDownload()
            // Copy + load the model in the background so the first scan is quick.
            withContext(Dispatchers.Default) { runCatching { classifier.warmUp() }.onFailure { Log.e(TAG, "warm up", it) } }
        }
    }

    // ---- camera / scanning -------------------------------------------------------------

    fun openCamera() {
        speaker.stop()
        _state.update { it.copy(display = Display.Camera, zoom = 0f) }
    }

    fun closeCamera() {
        _state.update { it.copy(display = restingDisplay(it), torch = false) }
    }

    fun toggleTorch() {
        if (_state.value.display == Display.Camera) _state.update { it.copy(torch = !it.torch) }
    }

    fun analyze(photo: Bitmap) {
        job?.cancel()
        speaker.stop()
        _state.update { it.copy(display = Display.Analyzing(photo, Phase.LOCAL), torch = false, lastPhoto = photo, digits = "") }
        job = viewModelScope.launch {
            val minShow = async { delay(700) }
            val guesses = try {
                withContext(Dispatchers.Default) { classifier.classify(photo) }
            } catch (e: Exception) {
                Log.e(TAG, "classify", e)
                minShow.await()
                showMessage(Msg.ClassifyError(e.message ?: e.javaClass.simpleName))
                return@launch
            }
            minShow.await()
            Log.i(TAG, "guesses: " + guesses.joinToString { "#${it.dexId} ${"%.3f".format(it.prob)}" })
            _state.update { it.copy(lastGuesses = guesses) }
            val top = guesses.first()
            val s = _state.value.settings
            when {
                top.prob >= ACCEPT -> show(top.dexId, Recognition(Source.LOCAL, top.prob), fromScan = true)
                s.claudeAvailable && s.claudeOnLowConfidence -> runClaude(photo, guesses)
                else -> showCandidates(photo, guesses, if (top.prob < 0.15f) Note.Blurry else Note.Unsure)
            }
        }
    }

    /** Manually ask Claude about the last photo (from the entry screen or the candidate list). */
    fun recheckWithClaude() {
        val st = _state.value
        val photo = st.lastPhoto ?: return
        if (!st.settings.claudeAvailable) {
            showMessage(Msg.NoApiKey)
            return
        }
        job?.cancel()
        job = viewModelScope.launch { runClaude(photo, st.lastGuesses) }
    }

    private suspend fun runClaude(photo: Bitmap, guesses: List<Guess>) {
        val d = dex ?: return
        _state.update { it.copy(display = Display.Analyzing(photo, Phase.CLAUDE)) }
        val hints = guesses.take(3).mapNotNull { g -> d[g.dexId]?.let { "${it.name.en} #${it.id} ${(g.prob * 100).toInt()}%" } }
        val result = try {
            withContext(Dispatchers.IO) { recognizer().identify(photo, hints) }
        } catch (e: Exception) {
            Log.e(TAG, "claude", e)
            showCandidates(photo, guesses, Note.ClaudeFailed(e.message?.take(60) ?: e.javaClass.simpleName))
            return
        }
        val species = when {
            result.found && result.dexNumber in 1..d.maxId -> d[result.dexNumber]
            result.found -> d.findByEnglishName(result.nameEn)
            else -> null
        }
        if (species != null) {
            show(species.id, Recognition(Source.CLAUDE), fromScan = true)
        } else {
            showCandidates(photo, guesses, Note.ClaudeNoMatch)
        }
    }

    private fun recognizer(): ClaudeRecognizer {
        val s = _state.value.settings
        val key = s.apiKey + "|" + s.claudeModel
        claude?.let { if (it.first == key) return it.second }
        return ClaudeRecognizer(s.apiKey, s.claudeModel).also { claude = key to it }
    }

    private fun showCandidates(photo: Bitmap, guesses: List<Guess>, note: Note) {
        if (_state.value.settings.sound) beeper.fail()
        _state.update { it.copy(display = Display.Candidates(photo, guesses.take(3), 0, note)) }
    }

    fun pickCandidate(index: Int) {
        val c = _state.value.display as? Display.Candidates ?: return
        val g = c.guesses.getOrNull(index) ?: return
        show(g.dexId, Recognition(Source.LOCAL, g.prob), fromScan = true)
    }

    // ---- browsing ---------------------------------------------------------------------

    fun show(id: Int, recognition: Recognition? = null, fromScan: Boolean = false) {
        val d = dex ?: return
        val s = d[id] ?: return
        _state.update {
            it.copy(
                display = Display.Entry,
                current = s,
                tab = if (fromScan || it.display != Display.Entry) Tab.ENTRY else it.tab,
                recognition = recognition,
                digits = "",
            )
        }
        if (fromScan) {
            val st = _state.value.settings
            if (st.sound) beeper.success()
            if (st.autoSpeak) speak(s)
        } else {
            speaker.stop()
        }
    }

    fun setTab(tab: Tab) {
        if (_state.value.current != null) _state.update { it.copy(tab = tab, display = Display.Entry) }
    }

    fun dpad(dir: Dir) {
        val st = _state.value
        val d = dex ?: return
        when (val disp = st.display) {
            Display.Camera -> {
                val z = (st.zoom + if (dir == Dir.UP) 0.1f else if (dir == Dir.DOWN) -0.1f else 0f).coerceIn(0f, 1f)
                _state.update { it.copy(zoom = z) }
            }
            is Display.Candidates -> when (dir) {
                Dir.UP -> _state.update { it.copy(display = disp.copy(selected = (disp.selected - 1).mod(disp.guesses.size))) }
                Dir.DOWN -> _state.update { it.copy(display = disp.copy(selected = (disp.selected + 1).mod(disp.guesses.size))) }
                else -> Unit
            }
            is Display.Analyzing, Display.Booting -> Unit
            else -> when (dir) {
                Dir.LEFT, Dir.RIGHT -> {
                    val cur = st.current?.id ?: 0
                    val next = if (dir == Dir.RIGHT) cur % d.maxId + 1 else if (cur <= 1) d.maxId else cur - 1
                    show(next)
                }
                Dir.UP, Dir.DOWN -> if (st.current != null) {
                    val tabs = Tab.entries
                    val i = (st.tab.ordinal + if (dir == Dir.DOWN) 1 else -1).mod(tabs.size)
                    setTab(tabs[i])
                }
            }
        }
    }

    fun digit(n: Int) {
        if (_state.value.digits.length < 4) _state.update { it.copy(digits = (it.digits + n).trimStart('0')) }
    }

    fun search() {
        val st = _state.value
        val d = dex ?: return
        if (st.digits.isNotEmpty()) {
            val id = st.digits.toInt()
            if (id in 1..d.maxId) show(id, Recognition(Source.NUMBER)) else {
                _state.update { it.copy(digits = "") }
                showMessage(Msg.BadNumber(d.maxId))
            }
            return
        }
        val disp = st.display
        if (disp is Display.Candidates) pickCandidate(disp.selected)
    }

    fun cancel() {
        val st = _state.value
        if (st.digits.isNotEmpty()) {
            _state.update { it.copy(digits = "") }
            return
        }
        job?.cancel()
        speaker.stop()
        when (st.display) {
            Display.Camera -> closeCamera()
            Display.Entry -> _state.update { it.copy(display = Display.Idle) }
            Display.Idle, Display.Booting -> Unit
            else -> _state.update { it.copy(display = restingDisplay(it)) }
        }
    }

    fun random() {
        val d = dex ?: return
        show((1..d.maxId).random(), Recognition(Source.RANDOM))
    }

    // ---- voice ------------------------------------------------------------------------

    fun toggleSpeak() {
        if (speaker.speaking.value) speaker.stop() else _state.value.current?.let { speak(it) }
    }

    private fun speak(s: Species) {
        val d = dex ?: return
        val lang = _state.value.lang
        val types = s.types.joinToString(if (lang == Lang.ZH) "、" else " and ") { d.typeName(it, lang) }
        val text = when (lang) {
            Lang.ZH -> "${s.name.zh}，${s.genus.zh}，${types}属性。${s.flavor.zh}"
            Lang.EN -> "${s.name.en}, the ${s.genus.en}. $types type. ${s.flavor.en}"
        }
        if (!speaker.speak(text, lang.locale)) _state.update { it.copy(event = UiEvent.TTS_MISSING) }
    }

    fun consumeEvent() = _state.update { it.copy(event = null) }

    // ---- settings ---------------------------------------------------------------------

    fun openSettings(open: Boolean) = _state.update { it.copy(showSettings = open) }

    fun saveSettings(s: DexSettings) {
        if (s.language != _state.value.settings.language) speaker.stop()
        store.save(s)
        _state.update { it.copy(settings = store.load(), showSettings = false) }
    }

    private fun showMessage(msg: Msg) {
        _state.update { it.copy(display = Display.Message(msg)) }
    }

    private fun restingDisplay(st: DexUiState): Display = if (st.current != null) Display.Entry else Display.Idle

    override fun onCleared() {
        speaker.shutdown()
        beeper.release()
        classifier.close()
    }

    companion object {
        private const val TAG = "PocketDex"
        /** On-device top-1 probability above which we trust the local model. */
        const val ACCEPT = 0.5f
    }
}
