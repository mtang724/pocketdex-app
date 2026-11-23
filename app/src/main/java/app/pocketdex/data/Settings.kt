package app.pocketdex.data

import android.content.Context
import app.pocketdex.BuildConfig

data class DexSettings(
    val apiKey: String = "",
    val claudeModel: String = DEFAULT_MODEL,
    /** Ask Claude when the on-device model is unsure. */
    val claudeOnLowConfidence: Boolean = true,
    val autoSpeak: Boolean = true,
    val sound: Boolean = true,
    val language: Lang = Lang.system(),
) {
    val claudeAvailable: Boolean get() = apiKey.isNotBlank()

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("pocketdex", Context.MODE_PRIVATE)

    fun load(): DexSettings = DexSettings(
        apiKey = prefs.getString("api_key", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.ANTHROPIC_API_KEY,
        claudeModel = prefs.getString("claude_model", null)?.takeIf { it.isNotBlank() } ?: DexSettings.DEFAULT_MODEL,
        claudeOnLowConfidence = prefs.getBoolean("claude_low_conf", true),
        autoSpeak = prefs.getBoolean("auto_speak", true),
        sound = prefs.getBoolean("sound", true),
        language = prefs.getString("language", null)?.let { runCatching { Lang.valueOf(it) }.getOrNull() } ?: Lang.system(),
    )

    fun save(s: DexSettings) {
        prefs.edit()
            .putString("api_key", s.apiKey.trim())
            .putString("claude_model", s.claudeModel.trim())
            .putBoolean("claude_low_conf", s.claudeOnLowConfidence)
            .putBoolean("auto_speak", s.autoSpeak)
            .putBoolean("sound", s.sound)
            .putString("language", s.language.name)
            .apply()
    }
}
