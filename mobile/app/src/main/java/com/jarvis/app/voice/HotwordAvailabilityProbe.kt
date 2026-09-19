package com.jarvis.app.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Result of probing whether this device can host an always-on 'jarvis' wake
 * word through the platform's DSP-backed SoundTrigger.
 *
 * This is OEM/DSP-dependent and NOT guaranteed on a given handset (e.g. a
 * Realme 9 Pro 5G), so the probe never assumes either way — it logs what the
 * platform reports and the caller falls back to manual tap-to-trigger STT
 * unless [SUPPORTED] is proven.
 */
enum class HotwordAvailability {
    /** Platform STT exists and SoundTrigger custom-keyphrase enrollment is available. */
    SUPPORTED,

    /** The device or platform cannot host a DSP wake word — manual tap-to-trigger only. */
    UNSUPPORTED,

    /** Could not determine from available signals — treat as unsupported (manual trigger). */
    UNKNOWN
}

/**
 * The wake strategy the body uses for a given [HotwordAvailability] probe
 * result (AC5). Only a proven [HotwordAvailability.SUPPORTED] enables the
 * always-on 'jarvis' continuous wake; [UNSUPPORTED] / [UNKNOWN] route to the
 * manual tap-to-trigger STT path and continuous wake remains a tracked gap.
 */
enum class WakeStrategy {
    CONTINUOUS,
    MANUAL_TAP_TO_TRIGGER
}

fun wakeStrategyFor(availability: HotwordAvailability): WakeStrategy = when (availability) {
    HotwordAvailability.SUPPORTED -> WakeStrategy.CONTINUOUS
    HotwordAvailability.UNSUPPORTED, HotwordAvailability.UNKNOWN -> WakeStrategy.MANUAL_TAP_TO_TRIGGER
}

/**
 * Injectable platform seam for the always-on-hotword / SoundTrigger probe.
 * The probe never talks to the Android runtime directly; it reads this port so
 * the classification is unit-testable on the JVM, while the production
 * [PlatformHotwordSupportPort] owns the real `android.service.voice.*`
 * interaction (compile-time-only android.jar reference, device-only).
 */
interface HotwordSupportPort {
    /** Whether a platform speech-recognition service is installed (the STT prerequisite). */
    fun isPlatformSttAvailable(): Boolean

    /**
     * The platform's SoundTrigger availability status for custom keyphrase
     * enrollment, or null when it cannot be determined on this device/API level.
     * Mirrors AlwaysOnHotwordDetector's `STATE_*` int status values.
     */
    fun soundTriggerAvailabilityStatus(): Int?

    /** Record the probe outcome wherever diagnostics live. */
    fun log(message: String)
}

/**
 * Production [HotwordSupportPort] over the real Android platform APIs.
 * Compile-time-only reference (android.jar stub) — device-only, never invoked
 * on the JVM. Honest by construction: a missing/unknown SoundTrigger status is
 * reported as null (→ [HotwordAvailability.UNKNOWN], manual tap-to-trigger),
 * never assumed to be supported.
 */
class PlatformHotwordSupportPort(private val context: android.content.Context) : HotwordSupportPort {

    override fun isPlatformSttAvailable(): Boolean =
        android.speech.SpeechRecognizer.isRecognitionAvailable(context)

    override fun soundTriggerAvailabilityStatus(): Int? {
        // AlwaysOnHotwordDetector's availability is surfaced through the
        // SoundTrigger HAL; it cannot be instantiated without a live
        // VoiceInteractionService binding. On API levels / devices where that
        // path is unavailable we report null (honest UNKNOWN) rather than
        // fabricating support.
        return try {
            val cls = Class.forName("android.service.voice.AlwaysOnHotwordDetector")
            methodOf(cls) ?: return null
        } catch (_: ClassNotFoundException) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun methodOf(cls: Class<*>): Int? {
        return try {
            cls.getMethod("getAvailabilityStatus", Int::class.javaPrimitiveType, android.content.Intent::class.java)
                .invoke(null, 0, android.content.Intent())
                .let { it as? Int }
        } catch (_: Throwable) {
            null
        }
    }

    override fun log(message: String) {
        android.util.Log.i("HotwordAvailabilityProbe", message)
    }
}

/**
 * Real runtime probe of always-on-hotword / SoundTrigger availability (AC1).
 *
 * The probe does not assume support is present — it reads the platform's actual
 * signals ([HotwordSupportPort]) and classifies the result. The outcome is
 * logged and drives the caller's decision: only a proven [HotwordAvailability.SUPPORTED]
 * enables continuous 'jarvis' wake; [UNSUPPORTED] / [UNKNOWN] route to manual
 * tap-to-trigger STT (a tracked gap when not wired as the permanent design).
 */
object HotwordAvailabilityProbe {

    // Mirrors AlwaysOnHotwordDetector constant int values so classification
    // stays JVM-testable without touching the platform at call time.
    const val STATE_SERVICE_UNAVAILABLE: Int = 0
    const val STATE_KEYPHRASE_ENROLLED: Int = 1
    const val STATE_KEYPHRASE_UNENROLLED: Int = 2
    const val STATE_HARDWARE_UNSUPPORTED: Int = 3

    /** The probe's current outcome; [UNKNOWN] until [queryAndLog] runs. */
    private val _availability = MutableStateFlow(HotwordAvailability.UNKNOWN)
    val availability: StateFlow<HotwordAvailability> = _availability.asStateFlow()

    /**
     * Pure classification: map the platform signals to an honest availability.
     * A null SoundTrigger status (undeterminable) must never be assumed to be
     * supported — it collapses to [HotwordAvailability.UNKNOWN] even when platform
     * STT exists. A missing platform STT makes hotword moot ([HotwordAvailability.UNSUPPORTED]).
     */
    fun classify(
        platformSttAvailable: Boolean,
        soundTriggerStatus: Int?
    ): HotwordAvailability = when {
        !platformSttAvailable -> HotwordAvailability.UNSUPPORTED
        soundTriggerStatus == null -> HotwordAvailability.UNKNOWN
        soundTriggerStatus == STATE_HARDWARE_UNSUPPORTED ||
            soundTriggerStatus == STATE_SERVICE_UNAVAILABLE -> HotwordAvailability.UNSUPPORTED
        else -> HotwordAvailability.SUPPORTED // enrolled or unenrolled: HAL can host/trigger a keyphrase
    }

    /**
     * Run the probe against [port], log the outcome and publish [availability].
     * Returns the classification so callers can branch on it directly.
     */
    fun queryAndLog(port: HotwordSupportPort): HotwordAvailability {
        val platformStt = port.isPlatformSttAvailable()
        val status = port.soundTriggerAvailabilityStatus()
        val result = classify(platformStt, status)
        val statusLabel = status?.let { stateLabel(it) } ?: "unknown"
        port.log("platformStt=$platformStt soundTrigger=$statusLabel -> $result")
        _availability.value = result
        return result
    }

    private fun stateLabel(status: Int): String = when (status) {
        STATE_SERVICE_UNAVAILABLE -> "service_unavailable"
        STATE_KEYPHRASE_ENROLLED -> "keyphrase_enrolled"
        STATE_KEYPHRASE_UNENROLLED -> "keyphrase_unenrolled"
        STATE_HARDWARE_UNSUPPORTED -> "hardware_unsupported"
        else -> "unknown($status)"
    }
}
