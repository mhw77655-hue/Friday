package com.jarvis.app.android

import com.jarvis.app.ScreenBridge

/**
 * Production [ControlBackend] for the screen-scape summary action, backed by
 * the REAL [ScreenBridge] (Shizuku shell service). This is the first real
 * member of the android capability adapter stack.
 *
 * [available] reports true only when the Shizuku shell service is actually
 * bound, so the [DeviceControlRouter] chain skips it on devices without
 * Shizuku rather than failing.
 *
 * The backend id is the exact chain-rung key [DeviceControlRouter.ORDER]
 * sorts by ("shizuku"), because this backend IS the Shizuku rung of the
 * fallback chain. A backend id that is not a member of [DeviceControlRouter.ORDER]
 * sorts to index -1 and would be tried BEFORE every ordered rung, silently
 * destroying the official-api -> intent -> media-session -> accessibility ->
 * shizuku -> adb order.
 *
 * [execute] dispatches a real screen capture through [ScreenBridge] and
 * reports the last captured screen text as [VerificationEvidence]: a non-empty
 * capture is [VerificationStage.VERIFIED]; if no text has landed yet the
 * attempt is [VerificationStage.NOT_ATTEMPTED] (never fire-and-assume).
 */
class ScreenBridgeControlBackend : ControlBackend {

    override val id: String = "shizuku"

    override fun available(): Boolean =
        ScreenBridge.status.value == "bound"

    override fun execute(action: ControlAction): VerificationEvidence {
        if (action.actionType != ActionType.SCREENSCAPE_SUMMARY) {
            return VerificationEvidence(VerificationStage.NOT_ATTEMPTED, id, "unsupported action ${action.actionType}")
        }
        ScreenBridge.captureScreenSummary()
        val text = ScreenBridge.lastScreenText.value
        return if (text.isNotBlank() && text != "not bound") {
            VerificationEvidence(VerificationStage.VERIFIED, id, text.take(200))
        } else {
            VerificationEvidence(VerificationStage.NOT_ATTEMPTED, id, "no screen text captured yet")
        }
    }
}