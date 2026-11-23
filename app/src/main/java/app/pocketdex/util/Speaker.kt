package app.pocketdex.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Reads Pokédex entries aloud with the system TTS voice (Chinese or English). */
class Speaker(context: Context) {
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    private var ready = false
    private var currentLocale: Locale? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            tts.setSpeechRate(1.05f)
            tts.setPitch(0.9f)
        }
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _speaking.value = true }
            override fun onDone(utteranceId: String?) { _speaking.value = false }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { _speaking.value = false }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { _speaking.value = false }
        })
    }

    /** Returns false when the TTS engine has no voice for [locale]. */
    fun speak(text: String, locale: Locale): Boolean {
        if (!ready) return false
        if (locale != currentLocale) {
            val r = tts.setLanguage(locale)
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) return false
            currentLocale = locale
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dex")
        return true
    }

    fun stop() {
        tts.stop()
        _speaking.value = false
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}

/** Short device beeps for scan results. */
class Beeper {
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }.getOrNull()

    fun success() { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 180) }
    fun fail() { tone?.startTone(ToneGenerator.TONE_PROP_NACK, 220) }
    fun blip() { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 60) }
    fun release() { tone?.release() }
}
