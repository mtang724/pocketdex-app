package app.pocketdex.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import app.pocketdex.data.L
import app.pocketdex.data.Lang

val LocalLang = compositionLocalOf { Lang.ZH }

/** Picks the UI string for the current language. */
@Composable
@ReadOnlyComposable
fun tr(zh: String, en: String): String = if (LocalLang.current == Lang.EN) en else zh

/** Data text (names, descriptions) in the current language. */
val L.t: String
    @Composable @ReadOnlyComposable get() = this[LocalLang.current]

val STAT_LABELS_ZH = listOf("HP", "攻击", "防御", "特攻", "特防", "速度")
val STAT_LABELS_EN = listOf("HP", "Atk", "Def", "SpA", "SpD", "Spe")

val statLabels: List<String>
    @Composable @ReadOnlyComposable get() = if (LocalLang.current == Lang.EN) STAT_LABELS_EN else STAT_LABELS_ZH

@Composable
@ReadOnlyComposable
fun Tab.label(): String = tr(zh, en)

@Composable
@ReadOnlyComposable
fun Recognition.label(): String {
    val src = when (source) {
        Source.LOCAL -> tr("本地识别", "On-device")
        Source.CLAUDE -> tr("Claude 复核", "Claude check")
        Source.NUMBER -> tr("编号查找", "By number")
        Source.RANDOM -> tr("随机", "Random")
    }
    return if (confidence != null) "$src ${(confidence * 100).toInt()}%" else src
}

@Composable
@ReadOnlyComposable
fun Phase.label(): String = when (this) {
    Phase.LOCAL -> tr("正在分析", "Analyzing")
    Phase.CLAUDE -> tr("Claude 复核中", "Asking Claude")
}

@Composable
@ReadOnlyComposable
fun Note.text(): String = when (this) {
    Note.Unsure -> tr("不太确定，可能是：", "Not sure. It might be:")
    Note.Blurry -> tr("没看清，靠近一点再试试？也可能是：", "Couldn't see it clearly. Try moving closer. Or maybe:")
    is Note.ClaudeFailed -> tr("Claude 复核失败（$detail），可能是：", "Claude check failed ($detail). It might be:")
    Note.ClaudeNoMatch -> tr("Claude 也没认出宝可梦，可能是：", "Claude didn't spot a Pokémon either. Maybe:")
}

@Composable
@ReadOnlyComposable
fun Msg.title(): String = when (this) {
    is Msg.ClassifyError -> tr("识别出错", "Recognition error")
    Msg.NoApiKey -> tr("未设置 API Key", "No API key")
    is Msg.BadNumber -> tr("没有这个编号", "No such number")
}

@Composable
@ReadOnlyComposable
fun Msg.body(): String = when (this) {
    is Msg.ClassifyError -> detail
    Msg.NoApiKey -> tr(
        "在设置（左下角黑色按钮）里填入 Claude API Key 后可使用云端复核。",
        "Add a Claude API key in Settings (the black button, bottom left) to use the cloud check.",
    )
    is Msg.BadNumber -> tr("全国图鉴编号范围是 1 – $max", "National Dex numbers go from 1 to $max")
}
