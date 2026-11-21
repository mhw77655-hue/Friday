package ai.friday.assistant.vision

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import ai.friday.assistant.companioncore.presence.CompanionCoreHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

data class VisualPerceptionSnapshot(
    val enabled: Boolean = false,
    val faceCount: Int = 0,
    val primaryCenterX: Float? = null,
    val primaryCenterY: Float? = null,
    val headYawDegrees: Float? = null,
    val observedAtMs: Long = 0L,
    val error: String? = null
)

/**
 * Consent-gated, on-device visual perception.
 *
 * Camera frames are consumed in memory by ML Kit and immediately closed.
 * No frames, face templates, identity labels, or biometric records are saved.
 * The only exported state is a transient count/pose summary for the presence
 * engine. Camera use is bound to the visible screen's LifecycleOwner.
 */
class CameraPresenceController(context: Context) {
    private val appContext = context.applicationContext
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val lastAnalysisMs = AtomicLong(0L)
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .enableTracking()
            .build()
    )

    private val mutableSnapshot = MutableStateFlow(VisualPerceptionSnapshot())
    val snapshot = mutableSnapshot.asStateFlow()

    @Volatile private var provider: ProcessCameraProvider? = null
    @Volatile private var previewUseCase: Preview? = null
    @Volatile private var analysisUseCase: ImageAnalysis? = null

    fun bind(owner: LifecycleOwner, previewView: PreviewView) {
        mutableSnapshot.value = VisualPerceptionSnapshot(enabled = true)
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            runCatching {
                val cameraProvider = future.get()
                provider = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, ::analyze) }

                previewUseCase = preview
                analysisUseCase = analysis
                cameraProvider.unbind(preview, analysis)
                cameraProvider.bindToLifecycle(
                    owner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )
            }.onFailure {
                mutableSnapshot.value = VisualPerceptionSnapshot(
                    enabled = false,
                    error = it.message ?: "Camera could not be started"
                )
                reportPresence(false)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    fun stop() {
        val currentProvider = provider
        val preview = previewUseCase
        val analysis = analysisUseCase
        if (currentProvider != null) {
            runCatching {
                if (preview != null && analysis != null) currentProvider.unbind(preview, analysis)
            }
        }
        previewUseCase = null
        analysisUseCase = null
        reportPresence(false)
        mutableSnapshot.value = VisualPerceptionSnapshot(enabled = false)
    }

    fun close() {
        stop()
        detector.close()
        executor.shutdown()
    }

    private fun analyze(proxy: androidx.camera.core.ImageProxy) {
        val now = android.os.SystemClock.elapsedRealtime()
        // Two observations per second are enough for presence/attention and
        // avoid running a vision model at camera frame rate on a phone.
        if (now - lastAnalysisMs.get() < 500L) {
            proxy.close()
            return
        }
        lastAnalysisMs.set(now)
        val mediaImage = proxy.image
        if (mediaImage == null) {
            proxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { faces ->
                val primary = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                val width = proxy.width.coerceAtLeast(1)
                val height = proxy.height.coerceAtLeast(1)
                val bounds = primary?.boundingBox
                val count = faces.size
                mutableSnapshot.value = VisualPerceptionSnapshot(
                    enabled = true,
                    faceCount = count,
                    primaryCenterX = bounds?.centerX()?.div(width.toFloat()),
                    primaryCenterY = bounds?.centerY()?.div(height.toFloat()),
                    headYawDegrees = primary?.headEulerAngleY,
                    observedAtMs = System.currentTimeMillis()
                )
                reportPresence(count > 0)
            }
            .addOnFailureListener { error ->
                mutableSnapshot.value = mutableSnapshot.value.copy(error = error.message)
            }
            .addOnCompleteListener {
                proxy.close()
            }
    }

    private fun reportPresence(present: Boolean) {
        CompanionCoreHolder.instance()?.presenceEngine?.reportUserPresence(present)
    }
}
