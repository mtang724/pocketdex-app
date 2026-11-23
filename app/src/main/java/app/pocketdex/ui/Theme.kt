package app.pocketdex.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import app.pocketdex.data.ArtStore

object DexColors {
    val Body = Color(0xFFD7192E)
    val BodyLight = Color(0xFFF0384B)
    val BodyDark = Color(0xFF9E0D1E)
    val Edge = Color(0xFF6E0612)
    val Backdrop = Color(0xFF1A0508)
    val Bezel = Color(0xFFE9E6E1)
    val BezelShade = Color(0xFFC9C4BD)
    val Screen = Color(0xFF15181C)
    val ScreenText = Color(0xFFF2F2F2)
    val ScreenDim = Color(0xFF9AA3AD)
    val Accent = Color(0xFFFFD54A)
    val Key = Color(0xFF5AA9F5)
    val KeyDark = Color(0xFF2C6FB5)
    val Green = Color(0xFF3FAE4E)
    val GreenDark = Color(0xFF23742F)
    val Dpad = Color(0xFF1F3B2C)
    val Silk = Color(0xFFFFD6DC)
    val LedRed = Color(0xFFFF4A4A)
    val LedYellow = Color(0xFFFFD21F)
    val LedGreen = Color(0xFF45DB43)
    val Yellow = Color(0xFFFFCC1A)
}

private val typeColors = mapOf(
    1 to 0xFFA8A77A, 2 to 0xFFC22E28, 3 to 0xFFA98FF3, 4 to 0xFFA33EA1, 5 to 0xFFE2BF65,
    6 to 0xFFB6A136, 7 to 0xFFA6B91A, 8 to 0xFF735797, 9 to 0xFFB7B7CE, 10 to 0xFFEE8130,
    11 to 0xFF6390F0, 12 to 0xFF7AC74C, 13 to 0xFFF7D02C, 14 to 0xFFF95587, 15 to 0xFF96D9D6,
    16 to 0xFF6F35FC, 17 to 0xFF705746, 18 to 0xFFD685AD,
)

fun typeColor(id: Int): Color = Color(typeColors[id] ?: 0xFF888888)

fun statColor(v: Int): Color = when {
    v < 50 -> Color(0xFFF34444)
    v < 80 -> Color(0xFFFF8A1F)
    v < 100 -> Color(0xFFFFDD57)
    v < 120 -> Color(0xFFA0E515)
    v < 150 -> Color(0xFF23CD5E)
    else -> Color(0xFF00C2B8)
}


val LocalArtStore = staticCompositionLocalOf<ArtStore> { error("ArtStore not provided") }

sealed interface ArtState {
    data object Loading : ArtState
    data class Ready(val image: ImageBitmap) : ArtState
    /** Not downloaded yet and no network right now. */
    data object Missing : ArtState
}

/** Official artwork, downloaded from PokeAPI on first use and cached on the phone. */
@Composable
fun rememberArtwork(id: Int): State<ArtState> {
    val store = LocalArtStore.current
    return produceState<ArtState>(initialValue = ArtState.Loading, id) {
        // produceState keeps the previous value when the key changes, so reset it for the new id.
        val cached = store.cached(id)
        value = cached?.let { ArtState.Ready(it) } ?: ArtState.Loading
        if (cached == null) value = store.load(id)?.let { ArtState.Ready(it) } ?: ArtState.Missing
    }
}
