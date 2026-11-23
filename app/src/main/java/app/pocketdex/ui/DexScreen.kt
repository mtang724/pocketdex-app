package app.pocketdex.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketdex.data.DexSettings
import app.pocketdex.data.Lang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun DexScreen(vm: DexViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val speaking by vm.speaker.speaking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val camera = remember { CameraHandle() }
    val en = state.lang == Lang.EN
    fun toast(zh: String, english: String) = Toast.makeText(context, if (en) english else zh, Toast.LENGTH_SHORT).show()

    LaunchedEffect(state.event) {
        when (state.event) {
            UiEvent.TTS_MISSING -> toast("手机没有可用的中文语音，请在系统设置里安装 TTS 语音包", "No English voice on this phone. Install one in system TTS settings.")
            null -> Unit
        }
        if (state.event != null) vm.consumeEvent()
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.openCamera() else toast("需要相机权限才能扫描", "Camera permission is needed to scan")
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { decodeScaled(context, uri) }.getOrNull() }
            if (bmp != null) vm.analyze(bmp) else toast("无法读取这张图片", "Couldn't read that image")
        }
    }

    val onScan: () -> Unit = {
        when (state.display) {
            Display.Camera -> camera.snapshot()?.let(vm::analyze)
                ?: toast("相机还没准备好，也可以用「相册」识别", "Camera isn't ready. You can also use PHOTO.")
            Display.Booting, is Display.Analyzing -> Unit
            else -> if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                vm.openCamera()
            } else {
                permission.launch(Manifest.permission.CAMERA)
            }
        }
    }
    val onPickPhoto: () -> Unit = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    BackHandler(enabled = state.digits.isNotEmpty() || state.display !in listOf(Display.Idle, Display.Booting)) { vm.cancel() }

    val view = LocalView.current
    var cutout by remember { mutableStateOf<android.graphics.Rect?>(null) }
    val lensActive = speaking || state.display is Display.Analyzing || state.display == Display.Booting

    CompositionLocalProvider(LocalLang provides state.lang, LocalArtStore provides vm.art) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(DexColors.Backdrop)
                .onGloballyPositioned {
                    val r = topCutout(view)
                    if (r != cutout) cutout = r
                },
        ) {
            val density = LocalDensity.current
            // Unfolded foldables and tablets show the pages side by side; phones stack them.
            val twoPane = maxWidth >= 560.dp
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            val hingeW = 18.dp
            val hingePx = with(density) { hingeW.toPx() }

            // A round camera hole at the top of the left page gets the big lens centred on it.
            val spot = cutout?.let { r ->
                val cx = r.exactCenterX()
                val cy = r.exactCenterY()
                val leftPageEnd = if (twoPane) (wPx - hingePx) / 2f else wPx
                val radius = minOf(cx, cy, with(density) { 40.dp.toPx() }) - with(density) { 3.dp.toPx() }
                val roundish = r.width() <= r.height() * 1.6f
                if (roundish && cy < hPx * 0.12f && cx < leftPageEnd && radius >= with(density) { 16.dp.toPx() }) {
                    LensSpot(Offset(cx, cy), radius)
                } else null
            }
            // Small holes can sit over the red lid; only a wide notch needs the content moved down.
            val smallHole = cutout?.let { it.width() < wPx * 0.25f } ?: true
            val topInset = if (spot != null || smallHole) 0.dp else WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
            val usableH = maxHeight - topInset
            val scale = if (twoPane) {
                min(((maxWidth - hingeW) / 2) / 340.dp, usableH / 700.dp)
            } else {
                min(maxWidth / 340.dp, usableH / 1000.dp)
            }.coerceIn(0.5f, 1.7f)

            CompositionLocalProvider(LocalScale provides scale, LocalStacked provides !twoPane) {
                val lens = spot?.let {
                    with(density) { LensPlacement(DpOffset(it.center.x.toDp(), it.center.y.toDp()), (it.radius * 2).toDp()) }
                }
                val lid = lens?.let { maxOf(it.center.y + it.diameter / 2 + 14.dp, 54.u) } ?: 88.u
                val lidSpec = LidSpec(
                    height = lid,
                    lens = lens,
                    leds = lens?.let { DpOffset(it.center.x + it.diameter / 2 + 12.dp, it.center.y - 6.u) } ?: DpOffset(96.u, 18.u),
                )
                val content = Modifier.fillMaxSize().padding(top = topInset)
                if (twoPane) {
                    Row(content, verticalAlignment = Alignment.CenterVertically) {
                        LeftPanel(state, vm, camera, onScan, onPickPhoto, lensActive, lidSpec, Modifier.weight(1f).fillMaxHeight())
                        Hinge(vertical = true, modifier = Modifier.width(hingeW).fillMaxHeight(0.94f))
                        RightPanel(state, vm, lid, Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    // Phones and the folded outer screen: the pages are stacked, top and bottom.
                    Column(content, horizontalAlignment = Alignment.CenterHorizontally) {
                        LeftPanel(state, vm, camera, onScan, onPickPhoto, lensActive, lidSpec, Modifier.weight(1.1f).fillMaxWidth())
                        Hinge(vertical = false, modifier = Modifier.height(14.u).fillMaxWidth(0.94f))
                        RightPanel(state, vm, 40.u, Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }

        if (state.showSettings) {
            SettingsDialog(state.settings, onDismiss = { vm.openSettings(false) }, onSave = vm::saveSettings)
        }
    }
}

/** The big lens centred on the front camera hole, in window pixels. */
private data class LensSpot(val center: Offset, val radius: Float)

/** Bounding box of a camera cutout touching the top edge, in window pixels (Android 9+ only). */
private fun topCutout(view: android.view.View): android.graphics.Rect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    return view.rootWindowInsets?.displayCutout?.boundingRects?.firstOrNull { !it.isEmpty && it.top <= 0 }
}

@Composable
private fun SettingsDialog(initial: DexSettings, onDismiss: () -> Unit, onSave: (DexSettings) -> Unit) {
    var key by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.claudeModel) }
    var lowConf by remember { mutableStateOf(initial.claudeOnLowConfidence) }
    var speak by remember { mutableStateOf(initial.autoSpeak) }
    var sound by remember { mutableStateOf(initial.sound) }
    var lang by remember { mutableStateOf(initial.language) }
    MaterialTheme(colorScheme = darkColorScheme()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(tr("设置", "Settings")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("语言", "Language"), Modifier.weight(1f), fontSize = 14.sp)
                        Lang.entries.forEach { l ->
                            FilterChip(
                                selected = lang == l, onClick = { lang = l },
                                label = { Text(if (l == Lang.ZH) "中文" else "English") },
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    Text(tr("本地识别：ViT 模型 · 1025 种 · 完全离线", "On-device: ViT model · 1025 species · fully offline"), fontSize = 13.sp)
                    OutlinedTextField(
                        value = key, onValueChange = { key = it.trim() }, singleLine = true,
                        label = { Text(tr("Claude API Key（可选）", "Claude API key (optional)")) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = model, onValueChange = { model = it.trim() }, singleLine = true,
                        label = { Text(tr("Claude 模型", "Claude model")) }, modifier = Modifier.fillMaxWidth(),
                    )
                    ToggleRow(tr("本地不确定时用 Claude 复核", "Ask Claude when unsure"), lowConf) { lowConf = it }
                    ToggleRow(tr("识别后自动朗读", "Read entry after a scan"), speak) { speak = it }
                    ToggleRow(tr("提示音", "Beeps"), sound) { sound = it }
                    Text(
                        tr(
                            "API Key 只保存在本机，只有本地模型拿不准、或你手动点「Claude 复核」时才会把照片发给 Claude。",
                            "The API key stays on this phone. A photo is only sent to Claude when the on-device model is unsure, or when you tap \"Ask Claude\".",
                        ),
                        fontSize = 11.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSave(DexSettings(key, model.ifBlank { DexSettings.DEFAULT_MODEL }, lowConf, speak, sound, lang))
                }) { Text(tr("保存", "Save")) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消", "Cancel")) } },
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun decodeScaled(context: Context, uri: Uri, maxSide: Int = 1280): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val s = maxSide.toFloat() / max(info.size.width, info.size.height)
            if (s < 1f) decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}
