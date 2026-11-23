package app.pocketdex

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.pocketdex.ui.DexScreen
import app.pocketdex.ui.DexViewModel
import app.pocketdex.ui.Display
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: DexViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        setContent { DexScreen(vm) }
        handleTestImage(intent)
    }

    override fun onStart() {
        super.onStart()
        // Resume the one-time artwork download, e.g. after the phone joins Wi-Fi.
        vm.art.startBulkDownload()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTestImage(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Full-screen so the device skin fills the display; swipe from an edge to show the bars. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /**
     * Debug builds only: `adb shell am start -n app.pocketdex/.MainActivity --es test_image test.jpg`
     * runs recognition on files/test.jpg in the app's private storage (copy it there with run-as).
     */
    private fun handleTestImage(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        val name = intent?.getStringExtra("test_image")?.substringAfterLast('/') ?: return
        lifecycleScope.launch {
            vm.state.first { it.display != Display.Booting }
            val bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(File(filesDir, name).path) } ?: return@launch
            vm.analyze(bmp)
        }
    }
}
