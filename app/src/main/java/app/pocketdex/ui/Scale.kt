package app.pocketdex.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** True on phones (and folded foldables), where the two pages are stacked top and bottom. */
val LocalStacked = compositionLocalOf { false }

/** Layout scale of the current half: 1.0 when a half is 340 dp wide. */
val LocalScale = compositionLocalOf { 1f }

/** Scaled dp. */
val Int.u: Dp
    @Composable @ReadOnlyComposable get() = (this * LocalScale.current).dp

val Float.u: Dp
    @Composable @ReadOnlyComposable get() = (this * LocalScale.current).dp

/** Scaled sp. */
val Int.su: TextUnit
    @Composable @ReadOnlyComposable get() = (this * LocalScale.current).sp

val Float.su: TextUnit
    @Composable @ReadOnlyComposable get() = (this * LocalScale.current).sp
