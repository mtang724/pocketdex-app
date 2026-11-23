package app.pocketdex.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.min
import kotlin.math.roundToInt

data class Guess(val dexId: Int, val prob: Float)

/**
 * On-device Pokémon classifier: a ViT-base model fine-tuned on all 1025 species
 * (Hugging Face skshmjn/Pokemon-classifier-gen9-1025, Apache-2.0), exported to ONNX and
 * int8-quantized. Output index i corresponds to National Pokédex number i + 1.
 */
class PokemonClassifier(private val context: Context) : Closeable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    private var sessionOrNull: OrtSession? = null

    private val session: OrtSession
        @Synchronized get() = sessionOrNull ?: run {
            val opts = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setIntraOpNumThreads(4)
            }
            env.createSession(ensureModelFile().absolutePath, opts).also { sessionOrNull = it }
        }

    /** Loads the model ahead of the first scan so the first result is fast. */
    fun warmUp() {
        session
    }

    /**
     * Runs the model on a few centred square crops (full, 75 %, 55 %) and keeps the crop the
     * model is most sure about. This handles a Pokémon that only fills part of the frame.
     */
    fun classify(bitmap: Bitmap, topK: Int = 5): List<Guess> {
        var best: List<Guess> = emptyList()
        for (scale in floatArrayOf(1f, 0.75f, 0.55f)) {
            val crop = centerSquare(bitmap, scale)
            val guesses = runModel(crop, topK)
            if (crop !== bitmap) crop.recycle()
            if (best.isEmpty() || guesses.first().prob > best.first().prob) best = guesses
            if (best.first().prob > 0.9f) break
        }
        return best
    }

    private fun runModel(src: Bitmap, topK: Int): List<Guess> {
        val scaled = Bitmap.createScaledBitmap(src, SIZE, SIZE, true)
        val pixels = IntArray(SIZE * SIZE)
        scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        if (scaled !== src) scaled.recycle()

        // CHW float tensor normalised with mean = std = 0.5
        val buf = FloatBuffer.allocate(3 * SIZE * SIZE)
        val plane = SIZE * SIZE
        for (i in 0 until plane) {
            val p = pixels[i]
            buf.put(i, ((p shr 16) and 0xFF) / 127.5f - 1f)
            buf.put(plane + i, ((p shr 8) and 0xFF) / 127.5f - 1f)
            buf.put(2 * plane + i, (p and 0xFF) / 127.5f - 1f)
        }
        OnnxTensor.createTensor(env, buf, longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { input ->
            session.run(mapOf("pixel_values" to input)).use { out ->
                @Suppress("UNCHECKED_CAST")
                val probs = (out[0].value as Array<FloatArray>)[0]
                return probs.indices
                    .sortedByDescending { probs[it] }
                    .take(topK)
                    .map { Guess(it + 1, probs[it]) }
            }
        }
    }

    private fun centerSquare(b: Bitmap, scale: Float): Bitmap {
        val side = (min(b.width, b.height) * scale).roundToInt().coerceAtLeast(1)
        if (side == b.width && side == b.height) return b
        return Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
    }

    private fun ensureModelFile(): File {
        val out = File(context.filesDir, MODEL_ASSET.substringAfterLast('/'))
        val expected = context.assets.openFd(MODEL_ASSET).use { it.length }
        if (!out.exists() || out.length() != expected) {
            val tmp = File(out.path + ".tmp")
            context.assets.open(MODEL_ASSET).use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 20) } }
            tmp.renameTo(out)
        }
        return out
    }

    @Synchronized
    override fun close() {
        runCatching { sessionOrNull?.close() }
        sessionOrNull = null
    }

    companion object {
        private const val SIZE = 224
        private const val MODEL_ASSET = "model/pokemon_vit_int8.onnx"
    }
}
