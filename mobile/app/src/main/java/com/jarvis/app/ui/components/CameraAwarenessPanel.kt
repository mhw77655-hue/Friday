package com.jarvis.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.jarvis.app.companioncore.contract.UserPresenceEvent
import com.jarvis.app.companioncore.presence.CompanionCoreHolder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Opt-in local visual awareness. Frames are never recorded or uploaded. */
@Composable
fun CameraAwarenessPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        enabled = granted
    }
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = com.jarvis.app.ui.theme.JarvisColors.SurfacePanel)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text("VISUAL AWARENESS", style = com.jarvis.app.ui.theme.JarvisType.Label)
            Text(
                "Optional front-camera presence sensing. Friday detects whether a person is in view; she does not identify them. Frames stay on-device and are not saved.",
                style = com.jarvis.app.ui.theme.JarvisType.Technical.copy(fontSize = 10.sp, color = com.jarvis.app.ui.theme.JarvisColors.TextSecondary)
            )
            Button(onClick = {
                if (enabled) enabled = false
                else if (hasPermission) enabled = true
                else permissionLauncher.launch(Manifest.permission.CAMERA)
            }) { Text(if (enabled) "Turn visual awareness off" else "Enable visual awareness") }
            if (denied) Text("Camera permission was not granted.", color = com.jarvis.app.ui.theme.JarvisColors.CoreWarning)
            if (enabled) {
                val previewView = remember { PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                } }
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().height(180.dp))
                DisposableEffect(previewView, lifecycleOwner) {
                    val executor = Executors.newSingleThreadExecutor()
                    val future = ProcessCameraProvider.getInstance(context)
                    var provider: ProcessCameraProvider? = null
                    val detector = FaceDetection.getClient(
                        FaceDetectorOptions.Builder()
                            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                            .build()
                    )
                    val positives = AtomicInteger(0)
                    val negatives = AtomicInteger(0)
                    val lastPresence = AtomicReference<Boolean?>(null)
                    future.addListener({
                        runCatching {
                            provider = future.get()
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                            analysis.setAnalyzer(executor) { proxy ->
                                val mediaImage = proxy.image
                                if (mediaImage == null) proxy.close()
                                else {
                                    detector.process(InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees))
                                        .addOnSuccessListener { faces ->
                                            if (faces.isNotEmpty()) {
                                                negatives.set(0)
                                                if (positives.incrementAndGet() >= 2 && lastPresence.getAndSet(true) != true) {
                                                    CompanionCoreHolder.instance()?.integration?.sendUserPresenceEvent(
                                                        UserPresenceEvent(UserPresenceEvent.EventType.USER_PRESENT, System.currentTimeMillis(), 0.85)
                                                    )
                                                }
                                            } else {
                                                positives.set(0)
                                                if (negatives.incrementAndGet() >= 8 && lastPresence.getAndSet(false) == true) {
                                                    CompanionCoreHolder.instance()?.integration?.sendUserPresenceEvent(
                                                        UserPresenceEvent(UserPresenceEvent.EventType.USER_ABSENT, System.currentTimeMillis(), 0.75)
                                                    )
                                                }
                                            }
                                        }
                                        .addOnCompleteListener { proxy.close() }
                                }
                            }
                            provider?.unbindAll()
                            provider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                        }
                    }, ContextCompat.getMainExecutor(context))
                    onDispose {
                        runCatching { provider?.unbindAll() }
                        runCatching { detector.close() }
                        executor.shutdown()
                    }
                }
            }
        }
    }
}
