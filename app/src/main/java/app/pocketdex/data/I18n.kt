package app.pocketdex.data

import java.util.Locale

enum class Lang {
    ZH, EN;

    val locale: Locale get() = if (this == ZH) Locale.SIMPLIFIED_CHINESE else Locale.US

    companion object {
        fun system(): Lang = if (Locale.getDefault().language == "zh") ZH else EN
    }
}

/** A piece of text in both languages. Each side falls back to the other if missing. */
data class L(val zh: String, val en: String) {
    operator fun get(lang: Lang): String = if (lang == Lang.EN) en.ifEmpty { zh } else zh.ifEmpty { en }
}
