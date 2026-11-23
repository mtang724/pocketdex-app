package app.pocketdex.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pocketdex.data.Dex
import app.pocketdex.data.Lang
import app.pocketdex.data.Species
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RightPanel(state: DexUiState, vm: DexViewModel, lid: Dp, modifier: Modifier = Modifier) {
    Column(modifier.dexShell(left = false, seamHigh = lid, seamLow = lid + 14.u, corner = 22.u)) {
        Box(Modifier.fillMaxWidth().height(lid)) {
            Column(Modifier.align(Alignment.TopEnd).padding(top = 12.u, end = 20.u), horizontalAlignment = Alignment.End) {
                if (state.lang == Lang.EN) {
                    Text("PocketDex", color = DexColors.Edge, fontSize = 19.su, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                } else {
                    Text("口袋图鉴", color = DexColors.Edge, fontSize = 18.su, fontWeight = FontWeight.Black)
                }
            }
            Box(Modifier.offset(22.u, 18.u)) {
                Led(Color(0xFFFF7AA2), on = state.display is Display.Analyzing, size = 13.u)
            }
        }
        val shown = (state.display as? Display.Candidates)?.let { c -> vm.dex?.get(c.guesses[c.selected].dexId) } ?: state.current
        // A short stats strip centred in the free space; the keypad and buttons get the room.
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.u, vertical = 10.u), contentAlignment = Alignment.Center) {
            StatsDisplay(shown, vm.dex, Modifier.fillMaxWidth().aspectRatio(1.45f))
        }
        Keypad(vm::digit, Modifier.padding(horizontal = 20.u))
        Spacer(Modifier.height(16.u))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.u), verticalAlignment = Alignment.CenterVertically) {
            InputWindow(state.digits)
            Spacer(Modifier.width(8.u))
            NumberWindow(state.current?.id)
            Spacer(Modifier.width(12.u))
            Column(verticalArrangement = Arrangement.spacedBy(6.u)) {
                repeat(2) { Box(Modifier.width(34.u).height(6.u).clip(RoundedCornerShape(50)).background(Color(0xFF4A0710))) }
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DexButton(onClick = vm::random, color = DexColors.Yellow, shape = CircleShape, modifier = Modifier.size(46.u))
                Spacer(Modifier.height(3.u))
                Silk(tr("随机", "RANDOM"), 10.su)
            }
        }
        Spacer(Modifier.height(16.u))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.u), horizontalArrangement = Arrangement.spacedBy(14.u)) {
            BigGreen(tr("查找", "SEARCH"), vm::search, Modifier.weight(1f))
            BigGreen(tr("取消", "CANCEL"), vm::cancel, Modifier.weight(1f))
        }
        Spacer(Modifier.height(22.u))
    }
}

@Composable
private fun BigGreen(text: String, onClick: () -> Unit, modifier: Modifier) {
    DexButton(onClick = onClick, color = DexColors.Green, shape = RoundedCornerShape(10.u), modifier = modifier.height(64.u)) {
        Text(text, color = Color(0xFF0E2E12), fontSize = 19.su, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun Keypad(onDigit: (Int) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.u)) {
        listOf(listOf(1, 2, 3, 4, 5), listOf(6, 7, 8, 9, 0)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.u)) {
                row.forEach { n ->
                    DexButton(onClick = { onDigit(n) }, color = DexColors.Key, shape = RoundedCornerShape(8.u), modifier = Modifier.weight(1f).height(58.u)) {
                        Text(n.toString(), color = Color.White, fontSize = 24.su, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun InputWindow(digits: String) {
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(0f, 1f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "c")
    WhiteWindow {
        Text(
            if (digits.isEmpty()) "_" else digits + if (blink > 0.5f) "_" else " ",
            color = if (digits.isEmpty()) Color(0xFF9A9A9A) else Color(0xFF1A1A1A),
            fontSize = 19.su, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun NumberWindow(id: Int?) {
    WhiteWindow {
        Text(id?.toString()?.padStart(4, '0') ?: "----", color = Color(0xFF1A1A1A), fontSize = 17.su, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun WhiteWindow(content: @Composable () -> Unit) {
    Box(
        Modifier.width(76.u).height(46.u).clip(RoundedCornerShape(5.u)).background(Color(0xFFF4F1EC))
            .border(2.dp, Color(0xFF7A0A15), RoundedCornerShape(4.u)),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun StatsDisplay(s: Species?, dex: Dex?, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(8.u)).background(DexColors.Screen)
            .border(3.dp, Color(0xFF3A0309), RoundedCornerShape(8.u)).padding(12.u),
    ) {
        if (s == null || dex == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("无数据", "NO DATA"), color = DexColors.ScreenDim, fontSize = 16.su, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text(tr("扫描或输入编号", "Scan or type a number"), color = DexColors.ScreenDim, fontSize = 11.su)
            }
            return@Box
        }
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(s.name.t, color = DexColors.ScreenText, fontSize = 16.su, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.width(6.u))
                Text("No.${s.number}", color = DexColors.ScreenDim, fontSize = 10.su, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                Text(tr("总和 ", "Total "), color = DexColors.ScreenDim, fontSize = 10.su)
                Text(s.total.toString(), color = DexColors.Accent, fontSize = 16.su, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.u))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val roomForRadar = maxHeight > 300.dp
                Column(Modifier.fillMaxSize()) {
                    StatRows(s, if (roomForRadar) Modifier else Modifier.weight(1f))
                    if (roomForRadar) Radar(s.stats, Modifier.weight(1f).fillMaxWidth().padding(top = 8.u))
                }
            }
        }
    }
}

/** The six stat bars; spread evenly over the screen height when there's no radar chart. */
@Composable
private fun StatRows(s: Species, rowsModifier: Modifier) {
    Column(rowsModifier.fillMaxWidth(), verticalArrangement = Arrangement.SpaceEvenly) {
        s.stats.forEachIndexed { i, v ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.5f.u), verticalAlignment = Alignment.CenterVertically) {
                Text(statLabels[i], color = DexColors.ScreenDim, fontSize = 11.su, modifier = Modifier.width(34.u))
                Text(v.toString(), color = DexColors.ScreenText, fontSize = 12.su, fontFamily = FontFamily.Monospace, modifier = Modifier.width(30.u))
                Box(Modifier.weight(1f).height(7.u).clip(RoundedCornerShape(50)).background(Color(0xFF2A2F36))) {
                    Box(Modifier.fillMaxWidth((v / 200f).coerceIn(0.03f, 1f)).height(7.u).clip(RoundedCornerShape(50)).background(statColor(v)))
                }
            }
        }
    }
}

@Composable
private fun Radar(stats: List<Int>, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val labels = statLabels
    val labelStyle = TextStyle(color = DexColors.ScreenDim, fontSize = 10.su)
    Canvas(modifier) {
        val r = size.minDimension / 2 * 0.8f
        val c = center
        // order around the hexagon: HP, 攻击, 防御, 速度, 特防, 特攻 (like the games)
        val order = listOf(0, 1, 2, 5, 4, 3)
        fun pt(i: Int, f: Float): Offset {
            val a = (-90f + 60f * i) * (Math.PI / 180).toFloat()
            return c + Offset(cos(a) * r * f, sin(a) * r * f)
        }
        for (level in 1..3) {
            val p = Path()
            for (i in 0..5) { val q = pt(i, level / 3f); if (i == 0) p.moveTo(q.x, q.y) else p.lineTo(q.x, q.y) }
            p.close()
            drawPath(p, Color(0xFF39414B), style = Stroke(1.dp.toPx()))
        }
        for (i in 0..5) drawLine(Color(0xFF2E353D), c, pt(i, 1f), 1.dp.toPx())
        val poly = Path()
        order.forEachIndexed { i, si ->
            val q = pt(i, (stats[si] / 150f).coerceIn(0.05f, 1.15f))
            if (i == 0) poly.moveTo(q.x, q.y) else poly.lineTo(q.x, q.y)
        }
        poly.close()
        drawPath(poly, DexColors.Accent.copy(alpha = 0.32f))
        drawPath(poly, DexColors.Accent, style = Stroke(2.dp.toPx()))
        order.forEachIndexed { i, si ->
            val layout = measurer.measure(labels[si], labelStyle)
            val q = pt(i, 1.2f)
            drawText(layout, topLeft = q - Offset(layout.size.width / 2f, layout.size.height / 2f))
        }
    }
}
