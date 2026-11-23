package app.pocketdex.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketdex.data.ArtStore
import app.pocketdex.data.Dex
import app.pocketdex.data.Species
import kotlin.math.PI
import kotlin.math.sin

private val Mono = FontFamily.Monospace

/** Where the big lens goes when it is centred on the front camera hole (page coordinates). */
data class LensPlacement(val center: DpOffset, val diameter: Dp)

/**
 * Top strip ("lid") of both pages. [lens] is set when the big lens sits on the front camera
 * hole; otherwise it goes in its usual top-left spot. The LEDs sit next to it.
 */
data class LidSpec(val height: Dp, val lens: LensPlacement?, val leds: DpOffset)

@Composable
fun LeftPanel(
    state: DexUiState,
    vm: DexViewModel,
    camera: CameraHandle,
    onScan: () -> Unit,
    onPickPhoto: () -> Unit,
    lensActive: Boolean,
    lid: LidSpec,
    modifier: Modifier = Modifier,
) {
    val busy = state.display is Display.Analyzing || state.display == Display.Booting
    Column(modifier.dexShell(left = true, seamHigh = lid.height, seamLow = lid.height + 14.u, corner = 22.u)) {
        // Lid: lens + status LEDs
        Box(Modifier.fillMaxWidth().height(lid.height)) {
            val spot = lid.lens
            if (spot != null) {
                Lens(
                    spot.diameter, active = lensActive, onClick = vm::toggleSpeak,
                    modifier = Modifier.offset(spot.center.x - spot.diameter / 2, spot.center.y - spot.diameter / 2),
                )
            } else {
                Lens(62.u, active = lensActive, onClick = vm::toggleSpeak, modifier = Modifier.offset(18.u, 13.u))
            }
            Leds(busy = busy, recording = state.display == Display.Camera, size = 12.u, modifier = Modifier.offset(lid.leds.x, lid.leds.y))
        }
        // Compact, nearly square screen centred in the free red space (like the reference device).
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.u), contentAlignment = BiasAlignment(0f, -0.35f)) {
            // Largest 0.9:1 screen that fits both the width (76 %) and the height left over.
            val w = minOf(maxWidth * 0.76f, maxHeight * 0.9f)
            ScreenBezel(Modifier.size(w, w / 0.9f)) {
                DisplayContent(state, vm, camera)
            }
        }
        Controls(state, vm, onScan, onPickPhoto)
        Spacer(Modifier.height(22.u))
    }
}

/** Light grey frame around the black display, with the cut lower-left corner. */
@Composable
private fun ScreenBezel(modifier: Modifier, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val c = with(density) { 30.u.toPx() }
    val r = with(density) { 12.dp.toPx() }
    val bezelShape = GenericShape { size, _ ->
        moveTo(r, 0f); lineTo(size.width - r, 0f)
        quadraticTo(size.width, 0f, size.width, r)
        lineTo(size.width, size.height - r)
        quadraticTo(size.width, size.height, size.width - r, size.height)
        lineTo(c, size.height); lineTo(0f, size.height - c); lineTo(0f, r)
        quadraticTo(0f, 0f, r, 0f); close()
    }
    Box(
        modifier
            .clip(bezelShape)
            .background(Brush.verticalGradient(listOf(DexColors.Bezel, DexColors.BezelShade)))
            .border(2.dp, Color(0xFF8E8880), bezelShape),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.u)) {
            Row(Modifier.fillMaxWidth().height(22.u), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.u).clip(CircleShape).background(DexColors.LedRed.darken(0.15f)))
                Spacer(Modifier.width(14.u))
                Box(Modifier.size(7.u).clip(CircleShape).background(DexColors.LedRed.darken(0.15f)))
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.u))
                    .background(DexColors.Screen)
                    .border(2.dp, Color(0xFF55504A), RoundedCornerShape(6.u)),
            ) {
                content()
            }
            Row(Modifier.fillMaxWidth().height(36.u), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(18.u))
                Box(Modifier.size(15.u).clip(CircleShape).background(DexColors.LedRed.darken(0.1f)).border(1.dp, DexColors.Edge, CircleShape))
                Spacer(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(3.u)) {
                    repeat(4) { Box(Modifier.width(30.u).height(2.5.dp).background(Color(0xFF4A4540))) }
                }
            }
        }
    }
}

@Composable
private fun DisplayContent(state: DexUiState, vm: DexViewModel, camera: CameraHandle) {
    val scanlines = Modifier.drawWithContent {
        drawContent()
        val step = 3.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(Color.Black.copy(alpha = 0.05f), Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
    }
    val artProgress by vm.art.progress.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().then(scanlines)) {
        val dex = vm.dex
        when (val d = state.display) {
            Display.Booting -> BootScreen()
            Display.Idle -> IdleScreen(state, artProgress)
            Display.Camera -> CameraScreen(state, camera)
            is Display.Analyzing -> AnalyzingScreen(d)
            Display.Entry -> if (dex != null && state.current != null) EntryScreen(state, dex, state.current, vm) else IdleScreen(state, artProgress)
            is Display.Candidates -> if (dex != null) CandidatesScreen(d, dex, state, vm)
            is Display.Message -> MessageScreen(d)
        }
    }
}

// ---- screens ----------------------------------------------------------------------------

@Composable
private fun BootScreen() {
    val t by rememberInfiniteTransition(label = "boot").animateFloat(0f, 4f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "d")
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tr("口袋图鉴", "POCKET DEX"), color = DexColors.Accent, fontSize = 20.su, fontFamily = Mono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.u))
        Text(tr("系统启动中", "SYSTEM BOOT") + ".".repeat(t.toInt()), color = DexColors.ScreenDim, fontSize = 11.su, fontFamily = Mono)
    }
}

@Composable
private fun IdleScreen(state: DexUiState, art: ArtStore.Progress) {
    val blink by rememberInfiniteTransition(label = "idle").animateFloat(0f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "b")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.u)) {
        Text(tr("口袋图鉴", "PocketDex"), color = DexColors.ScreenText, fontSize = 24.su, fontWeight = FontWeight.Black)
        Text(tr("全国图鉴 · 1025 种", "NATIONAL DEX · 1025"), color = DexColors.Accent, fontSize = 11.su, fontFamily = Mono)
        Spacer(Modifier.height(6.u))
        val mode = if (state.settings.claudeAvailable) tr("离线识别 + Claude 复核", "On-device + Claude check") else tr("离线识别", "On-device recognition")
        Text("▶ $mode", color = DexColors.LedGreen.copy(alpha = 0.5f + 0.5f * blink), fontSize = 11.su, fontFamily = Mono)
        if (!art.complete) {
            val count = "${art.done}/${art.total}"
            val line = when {
                art.running -> tr("立绘下载中 $count", "Downloading artwork $count")
                art.waitingForWifi -> tr("连接 Wi-Fi 后下载立绘（$count）", "Artwork downloads on Wi-Fi ($count)")
                art.failed -> tr("部分立绘下载失败，下次打开会重试（$count）", "Some artwork failed, will retry next time ($count)")
                else -> tr("立绘 $count", "Artwork $count")
            }
            Text("▶ $line", color = DexColors.Accent.copy(alpha = 0.85f), fontSize = 11.su, fontFamily = Mono)
        }
        Spacer(Modifier.height(12.u))
        val lines = listOf(
            tr("扫描", "SCAN") to tr("打开相机，对准宝可梦再按一次", "Open the camera, aim, press again"),
            tr("相册", "PHOTO") to tr("从照片里识别", "Identify from a photo"),
            tr("数字键", "Keypad") to tr("输入编号后按「查找」", "Type a number, press SEARCH"),
            tr("十字键", "D-pad") to tr("← → 翻页 · ↑ ↓ 切换页面", "← → browse · ↑ ↓ switch page"),
            tr("蓝色镜头", "Blue lens") to tr("朗读图鉴", "Read the entry aloud"),
            tr("设置", "SETUP") to tr("语言、Claude 复核、朗读", "Language, Claude check, voice"),
        )
        lines.forEach { (k, v) ->
            Row(Modifier.padding(vertical = 3.u)) {
                Text(k, color = DexColors.Accent, fontSize = 12.su, fontWeight = FontWeight.Bold, modifier = Modifier.width(68.u))
                Text(v, color = DexColors.ScreenDim, fontSize = 12.su)
            }
        }
    }
}

@Composable
private fun CameraScreen(state: DexUiState, camera: CameraHandle) {
    Box(Modifier.fillMaxSize()) {
        CameraViewfinder(camera, state.torch, state.zoom, Modifier.fillMaxSize())
        Reticle(Modifier.fillMaxSize())
        val zoom = if (state.zoom > 0f) "  ·  " + tr("缩放", "Zoom") + " ${(state.zoom * 100).toInt()}%" else ""
        Text(
            tr("对准宝可梦，按「扫描」", "Aim at a Pokémon, press SCAN") + zoom,
            color = Color.White, fontSize = 11.su,
            modifier = Modifier.align(Alignment.BottomCenter).padding(6.u)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.u)).padding(horizontal = 8.u, vertical = 3.u),
        )
    }
}

@Composable
private fun Reticle(modifier: Modifier) {
    Canvas(modifier) {
        val side = size.minDimension * 0.75f
        val l = (size.width - side) / 2
        val t = (size.height - side) / 2
        val len = side * 0.14f
        val c = Color.White.copy(alpha = 0.85f)
        val sw = 3.dp.toPx()
        listOf(Offset(l, t) to Offset(1f, 1f), Offset(l + side, t) to Offset(-1f, 1f), Offset(l, t + side) to Offset(1f, -1f), Offset(l + side, t + side) to Offset(-1f, -1f))
            .forEach { (p, d) ->
                drawLine(c, p, p + Offset(len * d.x, 0f), sw)
                drawLine(c, p, p + Offset(0f, len * d.y), sw)
            }
    }
}

@Composable
private fun AnalyzingScreen(d: Display.Analyzing) {
    val t by rememberInfiniteTransition(label = "scan").animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "y")
    val dots by rememberInfiniteTransition(label = "dots").animateFloat(0f, 4f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "d")
    Box(Modifier.fillMaxSize()) {
        Image(d.photo.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.55f)
        Reticle(Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height * t
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF6BE3FF).copy(alpha = 0.5f), Color.Transparent), y - 40f, y + 40f),
                Offset(0f, y - 40f), Size(size.width, 80f),
            )
            drawLine(Color(0xFF9DEEFF), Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
        }
        Text(
            d.phase.label() + ".".repeat(dots.toInt()),
            color = Color.White, fontSize = 15.su, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.u)).padding(horizontal = 12.u, vertical = 6.u),
        )
    }
}

@Composable
private fun MessageScreen(d: Display.Message) {
    Column(Modifier.fillMaxSize().padding(16.u), verticalArrangement = Arrangement.Center) {
        Text(d.msg.title(), color = DexColors.Accent, fontSize = 18.su, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.u))
        Text(d.msg.body(), color = DexColors.ScreenText, fontSize = 13.su, lineHeight = 19.su)
        Spacer(Modifier.height(14.u))
        Text(tr("按「取消」返回", "Press CANCEL to go back"), color = DexColors.ScreenDim, fontSize = 11.su)
    }
}

@Composable
private fun CandidatesScreen(d: Display.Candidates, dex: Dex, state: DexUiState, vm: DexViewModel) {
    Column(Modifier.fillMaxSize().padding(10.u)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(d.photo.asImageBitmap(), null, Modifier.size(56.u).clip(RoundedCornerShape(4.u)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(10.u))
            Text(d.note.text(), color = DexColors.ScreenText, fontSize = 13.su, lineHeight = 18.su)
        }
        Spacer(Modifier.height(10.u))
        d.guesses.forEachIndexed { i, g ->
            val s = dex[g.dexId] ?: return@forEachIndexed
            val sel = i == d.selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.u)
                    .clip(RoundedCornerShape(6.u))
                    .background(if (sel) Color(0xFF2C3440) else Color.Transparent)
                    .border(1.dp, if (sel) DexColors.Accent else Color(0xFF2A2F36), RoundedCornerShape(6.u))
                    .clickable { vm.pickCandidate(i) }
                    .padding(6.u),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Art(s.id, Modifier.size(40.u))
                Spacer(Modifier.width(8.u))
                Column(Modifier.weight(1f)) {
                    Text(s.name.t, color = DexColors.ScreenText, fontSize = 14.su, fontWeight = FontWeight.Bold)
                    Text("No.${s.number}", color = DexColors.ScreenDim, fontSize = 10.su, fontFamily = Mono)
                }
                Text("${(g.prob * 100).toInt()}%", color = if (sel) DexColors.Accent else DexColors.ScreenDim, fontSize = 13.su, fontFamily = Mono)
            }
        }
        Spacer(Modifier.weight(1f))
        if (state.settings.claudeAvailable) {
            Chip(tr("用 Claude 复核", "Ask Claude"), onClick = vm::recheckWithClaude)
            Spacer(Modifier.height(6.u))
        }
        Text(tr("↑↓ 选择 · 查找 确认 · 取消 返回", "↑↓ choose · SEARCH confirm · CANCEL back"), color = DexColors.ScreenDim, fontSize = 10.su)
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Text(
        text, color = Color(0xFF15181C), fontSize = 11.su, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(DexColors.Accent).clickable(onClick = onClick).padding(horizontal = 10.u, vertical = 3.u),
    )
}

@Composable
fun Art(id: Int, modifier: Modifier = Modifier) {
    val art by rememberArtwork(id)
    Box(modifier, contentAlignment = Alignment.Center) {
        when (val a = art) {
            is ArtState.Ready -> Image(a.image, null, Modifier.fillMaxSize())
            // Not downloaded yet and offline: a quiet placeholder instead of an empty box.
            ArtState.Missing -> Text("?", color = DexColors.ScreenDim.copy(alpha = 0.5f), fontSize = 28.su, fontWeight = FontWeight.Black)
            ArtState.Loading -> Unit
        }
    }
}

// ---- entry with tabs --------------------------------------------------------------------

@Composable
private fun EntryScreen(state: DexUiState, dex: Dex, s: Species, vm: DexViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF0C0E11)).padding(horizontal = 4.u, vertical = 4.u), horizontalArrangement = Arrangement.SpaceBetween) {
            Tab.entries.forEach { tab ->
                val sel = tab == state.tab
                Text(
                    tab.label(),
                    color = if (sel) Color(0xFF15181C) else DexColors.ScreenDim,
                    fontSize = 10.5f.su, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(4.u)).background(if (sel) DexColors.Accent else Color.Transparent)
                        .clickable { vm.setTab(tab) }.padding(horizontal = 4.u, vertical = 2.u),
                )
            }
        }
        key(s.id, state.tab) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(10.u)) {
                when (state.tab) {
                    Tab.ENTRY -> EntryTab(state, dex, s, vm)
                    Tab.ABILITY -> AbilityTab(dex, s)
                    Tab.MATCHUP -> MatchupTab(dex, s)
                    Tab.EVOLUTION -> EvolutionTab(dex, s, vm)
                    Tab.MOVES -> MovesTab(dex, s)
                    Tab.INFO -> InfoTab(dex, s)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryTab(state: DexUiState, dex: Dex, s: Species, vm: DexViewModel) {
    val lang = LocalLang.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth > 300.dp
        val artSize: Dp = if (wide) maxWidth * 0.42f else maxWidth * 0.62f
        val header: @Composable () -> Unit = {
            Column {
                Text("No.${s.number}", color = DexColors.ScreenDim, fontSize = 11.su, fontFamily = Mono)
                Text(s.name.t, color = DexColors.ScreenText, fontSize = 22.su, fontWeight = FontWeight.Black)
                Text(s.genus.t, color = DexColors.ScreenDim, fontSize = 11.su, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.u))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.u)) {
                    s.types.forEach { TypeBadge(dex.typeName(it, lang), it, 11.su) }
                }
            }
        }
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtPlate(s.id, artSize)
                Spacer(Modifier.width(10.u))
                header()
            }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ArtPlate(s.id, artSize)
                Spacer(Modifier.height(6.u))
                Box(Modifier.fillMaxWidth()) { header() }
            }
        }
    }
    Spacer(Modifier.height(8.u))
    Divider()
    Row(Modifier.fillMaxWidth().padding(vertical = 6.u)) {
        Field(tr("身高", "Height"), s.heightM, Modifier.weight(1f))
        Field(tr("体重", "Weight"), s.weightKg, Modifier.weight(1f))
    }
    val normal = s.abilities.filter { !it.second }.mapNotNull { dex.abilities[it.first]?.name?.get(lang) }
    val hidden = s.abilities.filter { it.second }.mapNotNull { dex.abilities[it.first]?.name?.get(lang) }
    Row(Modifier.fillMaxWidth().padding(bottom = 6.u)) {
        Field(tr("特性", "Abilities"), normal.joinToString(" / ").ifEmpty { "—" }, Modifier.weight(1f))
        Field(tr("隐藏特性", "Hidden ability"), hidden.joinToString(" / ").ifEmpty { "—" }, Modifier.weight(1f))
    }
    Divider()
    Spacer(Modifier.height(8.u))
    Text(s.flavor.t, color = DexColors.ScreenText, fontSize = 13.su, lineHeight = 20.su)
    state.recognition?.let { r ->
        Spacer(Modifier.height(10.u))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.u).clip(CircleShape).background(if (r.source == Source.CLAUDE) Color(0xFFD9A066) else DexColors.LedGreen))
            Spacer(Modifier.width(5.u))
            Text(r.label(), color = DexColors.ScreenDim, fontSize = 10.su, fontFamily = Mono)
            if (r.source == Source.LOCAL && state.lastPhoto != null && state.settings.claudeAvailable) {
                Spacer(Modifier.width(8.u))
                Chip(tr("Claude 复核", "Ask Claude"), onClick = vm::recheckWithClaude)
            }
        }
    }
}

@Composable
private fun ArtPlate(id: Int, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.u))
            .background(Brush.radialGradient(listOf(Color(0xFF2E3A48), Color(0xFF15181C)))),
        contentAlignment = Alignment.Center,
    ) {
        Art(id, Modifier.fillMaxSize().padding(6.u))
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = DexColors.ScreenDim, fontSize = 10.su)
        Text(value, color = DexColors.ScreenText, fontSize = 13.su, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Divider() {
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        var x = 0f
        while (x < size.width) {
            drawLine(Color(0xFF3A414A), Offset(x, 0f), Offset(x + 4f, 0f), 2f); x += 8f
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = DexColors.Accent, fontSize = 12.su, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.u, bottom = 4.u))
}

@Composable
private fun AbilityTab(dex: Dex, s: Species) {
    s.abilities.forEach { (id, hidden) ->
        val a = dex.abilities[id] ?: return@forEach
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.name.t, color = DexColors.ScreenText, fontSize = 15.su, fontWeight = FontWeight.Bold)
            if (hidden) {
                Spacer(Modifier.width(6.u))
                Text(
                    tr("隐藏特性", "Hidden"), color = Color(0xFF15181C), fontSize = 9.su,
                    modifier = Modifier.background(Color(0xFFB39DDB), RoundedCornerShape(3.u)).padding(horizontal = 4.u),
                )
            }
        }
        Text(a.desc.t, color = DexColors.ScreenDim, fontSize = 12.su, lineHeight = 18.su, modifier = Modifier.padding(top = 2.u, bottom = 10.u))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchupTab(dex: Dex, s: Species) {
    val lang = LocalLang.current
    val m = dex.defenseMultipliers(s.types)
    val groups = listOf(
        tr("4 倍弱点", "4× weak") to 4f,
        tr("2 倍弱点", "2× weak") to 2f,
        tr("½ 抵抗", "½ resist") to 0.5f,
        tr("¼ 抵抗", "¼ resist") to 0.25f,
        tr("无效", "Immune") to 0f,
    )
    Text(tr("受到各属性招式的伤害", "Damage taken from each move type"), color = DexColors.ScreenDim, fontSize = 11.su)
    groups.forEach { (label, factor) ->
        val list = m.filterValues { it == factor }.keys.sorted()
        if (list.isEmpty()) return@forEach
        SectionTitle(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.u), verticalArrangement = Arrangement.spacedBy(5.u)) {
            list.forEach { TypeBadge(dex.typeName(it, lang), it, 11.su) }
        }
    }
}

@Composable
private fun EvolutionTab(dex: Dex, s: Species, vm: DexViewModel) {
    val chain = dex.chain(s).sortedWith(compareBy({ dex.stage(it) }, { it.id }))
    if (chain.size <= 1) {
        Text(tr("${s.name.zh} 不会进化。", "${s.name.en} does not evolve."), color = DexColors.ScreenText, fontSize = 13.su)
        return
    }
    chain.forEach { e ->
        val stage = dex.stage(e)
        val cur = e.id == s.id
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = (stage * 14).u, top = 3.u, bottom = 3.u)
                .clip(RoundedCornerShape(6.u))
                .background(if (cur) Color(0xFF2C3440) else Color.Transparent)
                .clickable { vm.show(e.id) }
                .padding(4.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (stage > 0) Text("↳ ", color = DexColors.ScreenDim, fontSize = 13.su)
            Art(e.id, Modifier.size(40.u))
            Spacer(Modifier.width(6.u))
            Column(Modifier.weight(1f)) {
                Text(e.name.t, color = if (cur) DexColors.Accent else DexColors.ScreenText, fontSize = 14.su, fontWeight = FontWeight.Bold)
                val cond = e.evoCondition.t
                Text("No.${e.number}" + if (cond.isNotEmpty()) " · $cond" else "", color = DexColors.ScreenDim, fontSize = 10.su)
            }
        }
    }
}

@Composable
private fun MovesTab(dex: Dex, s: Species) {
    val lang = LocalLang.current
    Text(tr("升级可学会的招式", "Moves learned by level-up"), color = DexColors.ScreenDim, fontSize = 11.su, modifier = Modifier.padding(bottom = 4.u))
    Row(Modifier.fillMaxWidth().padding(vertical = 2.u)) {
        listOf("Lv" to 0.12f, tr("招式", "Move") to 0.36f, tr("属性", "Type") to 0.26f, tr("威力", "Pow") to 0.13f, tr("命中", "Acc") to 0.13f).forEach { (h, w) ->
            Text(h, color = DexColors.Accent, fontSize = 10.su, modifier = Modifier.weight(w))
        }
    }
    s.moves.forEach { (id, lv) ->
        val mv = dex.moves[id] ?: return@forEach
        Row(Modifier.fillMaxWidth().padding(vertical = 3.u), verticalAlignment = Alignment.CenterVertically) {
            Text(if (lv == 0) tr("进化", "Evo") else lv.toString(), color = DexColors.ScreenDim, fontSize = 11.su, fontFamily = Mono, modifier = Modifier.weight(0.12f))
            Text(mv.name.t, color = DexColors.ScreenText, fontSize = 12.su, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.36f))
            Box(Modifier.weight(0.26f).padding(end = 4.u)) { TypeBadge(dex.typeName(mv.type, lang), mv.type, 8.5f.su, compact = true) }
            Text(if (mv.power > 0) mv.power.toString() else "—", color = DexColors.ScreenText, fontSize = 11.su, fontFamily = Mono, modifier = Modifier.weight(0.13f))
            Text(if (mv.accuracy > 0) mv.accuracy.toString() else "—", color = DexColors.ScreenText, fontSize = 11.su, fontFamily = Mono, modifier = Modifier.weight(0.13f))
        }
    }
}

@Composable
private fun InfoTab(dex: Dex, s: Species) {
    val lang = LocalLang.current
    val gender = if (s.genderRate < 0) tr("无性别", "Genderless") else {
        val f = s.genderRate * 12.5f
        "♂ %.1f%%  ♀ %.1f%%".format(100 - f, f)
    }
    val rarity = when {
        s.mythical -> tr("幻之宝可梦", "Mythical")
        s.legendary -> tr("传说的宝可梦", "Legendary")
        s.baby -> tr("幼年宝可梦", "Baby")
        else -> tr("普通", "Regular")
    }
    listOf(
        tr("分类", "Category") to s.genus.t,
        tr("世代", "Generation") to s.generation.t,
        tr("属性", "Type") to s.types.joinToString(" / ") { dex.typeName(it, lang) },
        tr("种族值总和", "Base stat total") to s.total.toString(),
        tr("捕获率", "Catch rate") to s.captureRate.toString(),
        tr("性别比例", "Gender ratio") to gender,
        tr("蛋群", "Egg groups") to s.eggGroups.joinToString(" / ") { it[lang] }.ifEmpty { "—" },
        tr("颜色", "Color") to s.color.t,
        tr("稀有度", "Rarity") to rarity,
    ).forEach { (k, v) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 5.u)) {
            Text(k, color = DexColors.ScreenDim, fontSize = 12.su, modifier = Modifier.width(96.u))
            Text(v, color = DexColors.ScreenText, fontSize = 12.su, fontWeight = FontWeight.Bold)
        }
        Divider()
    }
}

// ---- physical controls ------------------------------------------------------------------

@Composable
private fun Controls(state: DexUiState, vm: DexViewModel, onScan: () -> Unit, onPickPhoto: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.u), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                LabeledButton(tr("设置", "SETUP")) {
                    DexButton(onClick = { vm.openSettings(true) }, color = Color(0xFF2B2B2E), shape = CircleShape, modifier = Modifier.size(44.u))
                }
                Spacer(Modifier.weight(1f))
                LabeledButton(tr("相册", "PHOTO")) {
                    DexButton(onClick = onPickPhoto, color = Color(0xFFE53950), shape = RoundedCornerShape(50), modifier = Modifier.width(54.u).height(22.u))
                }
                Spacer(Modifier.width(10.u))
                LabeledButton(tr("补光", "LIGHT")) {
                    DexButton(onClick = vm::toggleTorch, color = Color(0xFF3D7BE0), shape = RoundedCornerShape(50), modifier = Modifier.width(54.u).height(22.u))
                }
            }
            Spacer(Modifier.height(10.u))
            DexButton(onClick = onScan, color = Color(0xFF8BE36A), shape = RoundedCornerShape(8.u), modifier = Modifier.fillMaxWidth().height(58.u)) {
                Waveform(active = state.display == Display.Camera, modifier = Modifier.fillMaxSize().padding(horizontal = 14.u, vertical = 10.u))
            }
            Spacer(Modifier.height(4.u))
            Silk(
                if (state.display == Display.Camera) tr("拍摄识别", "SHOOT") else tr("扫描", "SCAN"), 10.su,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        Spacer(Modifier.width(16.u))
        DPad(132.u, onDir = vm::dpad)
    }
}

@Composable
private fun LabeledButton(label: String, button: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        button()
        Spacer(Modifier.height(3.u))
        Silk(label, 10.su)
    }
}

@Composable
private fun Waveform(active: Boolean, modifier: Modifier) {
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "p")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val mid = h / 2
        val p = Path()
        val shift = if (active) phase else 0f
        var x = 0f
        while (x <= w) {
            val u = (x / w + shift) % 1f
            // a heartbeat-like blip in the middle of every cycle
            val y = when {
                u in 0.42f..0.46f -> mid - h * 0.42f * sin(((u - 0.42f) / 0.04f) * PI.toFloat())
                u in 0.46f..0.52f -> mid + h * 0.32f * sin(((u - 0.46f) / 0.06f) * PI.toFloat())
                else -> mid + h * 0.04f * sin(u * 24f)
            }
            if (x == 0f) p.moveTo(x, y) else p.lineTo(x, y)
            x += 2f
        }
        drawPath(p, Color(0xFF1E5A22), style = Stroke(2.2.dp.toPx()))
    }
}
