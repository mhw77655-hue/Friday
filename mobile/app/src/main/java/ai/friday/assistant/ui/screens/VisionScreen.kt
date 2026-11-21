package ai.friday.assistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.viewinterop.AndroidView
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType
import ai.friday.assistant.vision.CameraPresenceController

@Composable
fun VisionScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context) { CameraPresenceController(context) }
    val snapshot by controller.snapshot.collectAsStateWithLifecycle()
    var enabled by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    val preview = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        enabled = granted
        permissionDenied = !granted
    }

    LaunchedEffect(enabled, lifecycleOwner) {
        if (enabled) controller.bind(lifecycleOwner, preview) else controller.stop()
    }
    DisposableEffect(controller) {
        onDispose { controller.close() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("FRIDAY · VISUAL PRESENCE", style = FridayType.Display)
        Text(
            "Camera perception runs on this phone. Frames are not saved and Friday does not identify people. You control when the camera is active.",
            style = FridayType.Body
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FridayColors.SurfacePanel)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Camera awareness", style = FridayType.Body)
                Switch(
                    checked = enabled,
                    onCheckedChange = { requested ->
                        if (!requested) {
                            enabled = false
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) enabled = true
                            else permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                )
                if (permissionDenied) {
                    Text("Camera permission was not granted. You can enable it later in Android settings.", style = FridayType.Technical)
                }
            }
        }

        if (enabled) {
            AndroidView(
                factory = { preview },
                modifier = Modifier.fillMaxWidth().height(260.dp)
            )
            Text(
                when {
                    snapshot.error != null -> "Perception issue: ${snapshot.error}"
                    snapshot.faceCount == 0 -> "Friday is observing the environment. No face currently detected."
                    snapshot.faceCount == 1 -> "One person detected. Friday can orient her attention."
                    else -> "${snapshot.faceCount} people detected. No identities are assigned."
                },
                style = FridayType.Body
            )
            snapshot.headYawDegrees?.let {
                Text("Head orientation: ${it.toInt()}°", style = FridayType.Technical)
            }
        } else {
            Text("Visual perception is off. Friday's visual presence remains available without the camera.", style = FridayType.Technical)
        }
    }
}
