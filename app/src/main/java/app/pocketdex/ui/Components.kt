package app.pocketdex.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

fun Color.darken(f: Float) = lerp(this, Color.Black, f)
fun Color.lighten(f: Float) = lerp(this, Color.White, f)

fun View.tap() = performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

/** A chunky plastic button that sinks and darkens while pressed. */
@Composable
fun DexButton(
    onClick: () -> Unit,
    color: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    Box(
        modifier
            .graphicsLayer {
                translationY = if (pressed) 2.dp.toPx() else 0f
                scaleX = if (pressed) 0.97f else 1f
                scaleY = if (pressed) 0.97f else 1f
            }
            .shadow(if (pressed) 1.dp else 5.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(color.lighten(0.18f), color, color.darken(0.12f))))
            .border(1.5.dp, color.darken(0.4f), shape)
            .drawWithContent {
                drawContent()
                if (pressed) drawRect(Color.Black.copy(alpha = 0.18f))
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = onLongClick?.let { lc -> { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); lc() } },
                onClick = { view.tap(); onClick() },
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Small printed label under a control, like silkscreen on plastic. */
@Composable
fun Silk(text: String, size: TextUnit, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = DexColors.Silk.copy(alpha = 0.8f), fontSize = size, fontWeight = FontWeight.Bold, maxLines = 1)
}

/** The big blue lens. Pulses while [active]. */
@Composable
fun Lens(size: Dp, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "lens").animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse",
    )
    val glow = if (active) pulse else 0.15f
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    Canvas(
        modifier
            .size(size)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = { view.tap(); onClick() }),
    ) {
        val r = this.size.minDimension / 2
        if (active) {
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF8FDBFF).copy(alpha = 0.7f * glow), Color.Transparent), center, r * 1.35f),
                r * 1.35f,
            )
        }
        drawCircle(Color(0xFF5A0610), r, center + Offset(0f, r * 0.06f))
        drawCircle(Brush.linearGradient(listOf(Color.White, Color(0xFFCFCFCF)), Offset.Zero, Offset(0f, r * 2)), r * 0.97f)
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF9BE0FF), Color(0xFF2196F3), Color(0xFF0B3E8C)),
                center + Offset(-r * 0.25f, -r * 0.25f), r * 1.05f,
            ),
            r * 0.78f,
        )
        drawCircle(Color(0xFFBDEBFF).copy(alpha = 0.55f * glow), r * 0.78f)
        drawCircle(Color.White.copy(alpha = 0.9f), r * 0.17f, center + Offset(-r * 0.3f, -r * 0.32f))
        drawCircle(Color.White.copy(alpha = 0.5f), r * 0.07f, center + Offset(-r * 0.05f, -r * 0.45f))
    }
}

@Composable
fun Leds(busy: Boolean, recording: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "leds").animateFloat(
        0f, 3f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "t",
    )
    val phase = t.toInt().coerceIn(0, 2)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(size * 0.55f)) {
        Led(DexColors.LedRed, if (busy) phase == 0 else recording && t < 1.5f, size)
        Led(DexColors.LedYellow, busy && phase == 1, size)
        Led(DexColors.LedGreen, if (busy) phase == 2 else !recording, size)
    }
}

@Composable
fun Led(color: Color, on: Boolean, size: Dp) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        if (on) drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent), center, r * 1.9f), r * 1.9f)
        drawCircle(Color(0xFF4A040C), r)
        drawCircle(if (on) color else color.darken(0.6f), r * 0.82f)
        drawCircle(Color.White.copy(alpha = if (on) 0.75f else 0.25f), r * 0.24f, center + Offset(-r * 0.28f, -r * 0.28f))
    }
}

/** Cross-shaped directional pad. Holding a direction repeats it. */
@Composable
fun DPad(size: Dp, onDir: (Dir) -> Unit, modifier: Modifier = Modifier) {
    var pressed by remember { mutableStateOf<Dir?>(null) }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onDir)
    Canvas(
        modifier
            .size(size)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val c = Offset(this.size.width / 2f, this.size.height / 2f)
                    val d = down.position - c
                    if (hypot(d.x, d.y) < this.size.width * 0.08f) return@awaitEachGesture
                    val dir = if (abs(d.x) > abs(d.y)) (if (d.x > 0) Dir.RIGHT else Dir.LEFT) else (if (d.y > 0) Dir.DOWN else Dir.UP)
                    pressed = dir
                    view.tap()
                    callback(dir)
                    val repeat = scope.launch {
                        delay(420)
                        while (true) { callback(dir); view.tap(); delay(110) }
                    }
                    waitForUpOrCancellation()
                    repeat.cancel()
                    pressed = null
                }
            },
    ) {
        val w = this.size.width
        val arm = w * 0.34f
        val lo = (w - arm) / 2
        val cr = CornerRadius(arm * 0.18f)
        fun cross(offset: Offset, color: Color) {
            drawRoundRect(color, offset + Offset(0f, lo), Size(w, arm), cr)
            drawRoundRect(color, offset + Offset(lo, 0f), Size(arm, w), cr)
        }
        cross(Offset(0f, w * 0.03f), Color(0xFF0B1A12))
        cross(Offset.Zero, DexColors.Dpad)
        val hl = Color.White.copy(alpha = 0.18f)
        when (pressed) {
            Dir.UP -> drawRoundRect(hl, Offset(lo, 0f), Size(arm, lo), cr)
            Dir.DOWN -> drawRoundRect(hl, Offset(lo, lo + arm), Size(arm, lo), cr)
            Dir.LEFT -> drawRoundRect(hl, Offset(0f, lo), Size(lo, arm), cr)
            Dir.RIGHT -> drawRoundRect(hl, Offset(lo + arm, lo), Size(lo, arm), cr)
            null -> Unit
        }
        drawCircle(Color.Black.copy(alpha = 0.25f), arm * 0.28f, center)
        val a = arm * 0.16f
        val tri = Color.White.copy(alpha = 0.22f)
        fun triangle(p1: Offset, p2: Offset, p3: Offset) = drawPath(Path().apply { moveTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); close() }, tri)
        triangle(Offset(w / 2, lo * 0.35f), Offset(w / 2 - a, lo * 0.35f + a), Offset(w / 2 + a, lo * 0.35f + a))
        triangle(Offset(w / 2, w - lo * 0.35f), Offset(w / 2 - a, w - lo * 0.35f - a), Offset(w / 2 + a, w - lo * 0.35f - a))
        triangle(Offset(lo * 0.35f, w / 2), Offset(lo * 0.35f + a, w / 2 - a), Offset(lo * 0.35f + a, w / 2 + a))
        triangle(Offset(w - lo * 0.35f, w / 2), Offset(w - lo * 0.35f - a, w / 2 - a), Offset(w - lo * 0.35f - a, w / 2 + a))
    }
}

@Composable
fun TypeBadge(name: String, typeId: Int, fontSize: TextUnit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(typeColor(typeId))
            .padding(horizontal = 7.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(name, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** The hinge tube between the two halves. */
@Composable
fun Hinge(vertical: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val thick = if (vertical) w else h
        val brush = if (vertical) {
            Brush.horizontalGradient(listOf(DexColors.Edge, DexColors.BodyDark, DexColors.BodyLight, DexColors.Body, DexColors.Edge))
        } else {
            Brush.verticalGradient(listOf(DexColors.Edge, DexColors.BodyDark, DexColors.BodyLight, DexColors.Body, DexColors.Edge))
        }
        drawRoundRect(brush, cornerRadius = CornerRadius(thick / 2))
        val cap = thick * 0.9f
        val capColor = DexColors.Edge.copy(alpha = 0.55f)
        if (vertical) {
            drawLine(capColor, Offset(0f, cap), Offset(w, cap), thick * 0.06f)
            drawLine(capColor, Offset(0f, h - cap), Offset(w, h - cap), thick * 0.06f)
        } else {
            drawLine(capColor, Offset(cap, 0f), Offset(cap, h), thick * 0.06f)
            drawLine(capColor, Offset(w - cap, 0f), Offset(w - cap, h), thick * 0.06f)
        }
    }
}

/**
 * Red shell of one half with the stepped seam line. [seamHigh] and [seamLow] are the two
 * heights of the seam; the left half steps down towards the hinge and the right half steps
 * back up, so the line looks continuous across the fold.
 */
fun Modifier.dexShell(left: Boolean, seamHigh: Dp, seamLow: Dp, corner: Dp): Modifier = this
    .shadow(10.dp, RoundedCornerShape(corner))
    .clip(RoundedCornerShape(corner))
    .drawBehind {
        val w = size.width
        val hi = seamHigh.toPx()
        val lo = seamLow.toPx()
        drawRect(Brush.verticalGradient(listOf(DexColors.Body, DexColors.BodyDark.copy(alpha = 0.95f)), 0f, size.height))
        val seam = Path().apply {
            if (left) {
                moveTo(0f, hi); lineTo(w * 0.42f, hi); lineTo(w * 0.52f, lo); lineTo(w, lo)
            } else {
                moveTo(0f, lo); lineTo(w * 0.30f, lo); lineTo(w * 0.40f, hi); lineTo(w, hi)
            }
        }
        val lid = Path().apply {
            addPath(seam)
            lineTo(w, 0f); lineTo(0f, 0f); close()
        }
        drawPath(lid, Brush.verticalGradient(listOf(DexColors.BodyLight, DexColors.Body), 0f, lo))
        drawPath(seam, DexColors.Edge, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(seam, Color.White.copy(alpha = 0.18f), style = Stroke(1.dp.toPx(), join = StrokeJoin.Round))
        drawRoundRect(
            DexColors.Edge, cornerRadius = CornerRadius(corner.toPx()),
            style = Stroke(3.dp.toPx()),
        )
    }
