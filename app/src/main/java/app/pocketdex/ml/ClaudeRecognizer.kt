package app.pocketdex.ml

import android.graphics.Bitmap
import android.util.Base64
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.TextBlockParam
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Cloud re-check used only when the on-device model is unsure (needs an API key). */
class ClaudeRecognizer(apiKey: String, private val model: String) {

    data class Result(val found: Boolean, val dexNumber: Int, val nameEn: String, val confidence: String)

    class RefusedException(message: String) : Exception(message)

    private val client: AnthropicClient = AnthropicOkHttpClient.builder().apiKey(apiKey).build()

    /** Blocking network call; run it off the main thread. */
    fun identify(photo: Bitmap, localHints: List<String>): Result {
        val b64 = Base64.encodeToString(toJpeg(photo), Base64.NO_WRAP)

        val schema = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty(
                "properties",
                JsonValue.from(
                    mapOf(
                        "found" to mapOf("type" to "boolean"),
                        "dex_number" to mapOf(
                            "type" to "integer",
                            "description" to "National Pokédex number of the most prominent Pokémon, 0 if none",
                        ),
                        "name_en" to mapOf("type" to "string", "description" to "English species name"),
                        "confidence" to mapOf("type" to "string", "enum" to listOf("high", "medium", "low")),
                    ),
                ),
            )
            .putAdditionalProperty("required", JsonValue.from(listOf("found", "dex_number", "name_en", "confidence")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()

        val hint = if (localHints.isEmpty()) "" else
            "\nAn on-device model guessed (may be wrong): ${localHints.joinToString("; ")}."

        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(4096L)
            .system(SYSTEM_PROMPT)
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.LOW)
                    .format(JsonOutputFormat.builder().schema(schema).build())
                    .build(),
            )
            .addUserMessageOfContentBlockParams(
                listOf(
                    ContentBlockParam.ofImage(
                        ImageBlockParam.builder()
                            .source(
                                Base64ImageSource.builder()
                                    .data(b64)
                                    .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                                    .build(),
                            )
                            .build(),
                    ),
                    ContentBlockParam.ofText(
                        TextBlockParam.builder().text("Which Pokémon is this?$hint").build(),
                    ),
                ),
            )
            // Server-side fallback: if the primary model declines, the API retries on a fallback model.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .build()

        val message = client.messages().create(params)
        val stop = message.stopReason().map { it.toString() }.orElse("")
        if (stop.contains("refusal", ignoreCase = true)) throw RefusedException("Claude 拒绝了这次请求")

        val text = message.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("")
        val json = JSONObject(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1))
        return Result(
            found = json.optBoolean("found"),
            dexNumber = json.optInt("dex_number"),
            nameEn = json.optString("name_en"),
            confidence = json.optString("confidence"),
        )
    }

    private fun toJpeg(src: Bitmap): ByteArray {
        val scale = 1024f / max(src.width, src.height)
        val b = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).roundToInt(), (src.height * scale).roundToInt(), true) else src
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 85, out)
        if (b !== src) b.recycle()
        return out.toByteArray()
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "You identify Pokémon for a Pokédex app. Photos are often taken of computer screens, TVs, " +
                "toys, cards, plush or drawings, so expect glare, blur and odd angles. Identify the most " +
                "prominent Pokémon and give its National Pokédex number (1-1025). If no Pokémon is visible, " +
                "set found=false and dex_number=0."
    }
}
