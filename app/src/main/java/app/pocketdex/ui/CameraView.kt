package app.pocketdex.ui

import android.graphics.Bitmap
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Lets the scan button grab the current viewfinder frame. */
class CameraHandle {
    internal var view: PreviewView? = null

    /** The frame exactly as shown in the viewfinder, or null if the camera isn't streaming yet. */
    fun snapshot(): Bitmap? = view?.bitmap?.copy(Bitmap.Config.ARGB_8888, false)
}

@Composable
fun CameraViewfinder(handle: CameraHandle, torch: Boolean, zoom: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            isTapToFocusEnabled = true
            isPinchToZoomEnabled = true
        }
    }
    DisposableEffect(owner) {
        controller.bindToLifecycle(owner)
        onDispose {
            controller.unbind()
            handle.view = null
        }
    }
    LaunchedEffect(torch) { runCatching { controller.enableTorch(torch) } }
    LaunchedEffect(zoom) { runCatching { controller.setLinearZoom(zoom) } }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                this.controller = controller
                handle.view = this
            }
        },
    )
}
