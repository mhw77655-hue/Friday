package com.jarvis.app.android

/**
 * Media-transport commands the [MediaSessionControlBackend] can dispatch to an
 * active Android media session.
 */
enum class MediaTransportCommand {
    PLAY,
    PAUSE,
    NEXT_TRACK,
    PREV_TRACK
}

/**
 * Injectable platform seam for media-session device control — the
 * "media-session" rung of the device-control fallback chain.
 *
 * The backend never talks to the Android runtime directly; it drives this port,
 * so the same [MediaSessionControlBackend] is unit-testable on the JVM through a
 * deterministic fake while the production [PlatformMediaSessionControlPort] owns
 * the real `android.media.session.*` interaction.
 */
interface MediaSessionControlPort {
    /** Active transport sessions exposed by the platform, most relevant first. */
    fun activeSessions(): List<Session>

    /**
     * Dispatch [command] to the session identified by [sessionKey]; returns true
     * only when the platform accepted the dispatch.
     */
    fun dispatch(sessionKey: String, command: MediaTransportCommand): Boolean

    /**
     * A read-only snapshot of one active media session.
     *
     * [state] mirrors `PlaybackState.getState()` int values (0 NONE, 1 STOPPED,
     * 2 PAUSED, 3 PLAYING, ...); [positionMs] mirrors `PlaybackState.getPosition()`.
     */
    data class Session(
        val key: String,
        val packageName: String,
        val state: Int,
        val positionMs: Long
    )
}

/**
 * Production [MediaSessionControlPort] over the real Android media-session API
 * (`android.media.session.MediaSessionManager`). Compile-time-only reference
 * (android.jar stub) — constructed only on-device, never invoked on the JVM,
 * matching the existing `PlatformBiometricPromptResultMapper` pattern.
 *
 * Platform availability is truth: sessions are only exposed to apps that the
 * platform has granted media-session access to. When that is not the case the
 * port reports no active sessions and the router chain moves to the next rung.
 */
class PlatformMediaSessionControlPort(context: android.content.Context) : MediaSessionControlPort {

    private val manager: android.media.session.MediaSessionManager =
        context.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE)
            as android.media.session.MediaSessionManager

    override fun activeSessions(): List<MediaSessionControlPort.Session> =
        runCatching {
            manager.getActiveSessions(null).map { controller -> Session(controller) }
        }.getOrDefault(emptyList())

    override fun dispatch(sessionKey: String, command: MediaTransportCommand): Boolean {
        val controller = manager.getActiveSessions(null)
            .firstOrNull { c -> Session(c).key == sessionKey } ?: return false
        val controls = controller.transportControls
        return runCatching {
            when (command) {
                MediaTransportCommand.PLAY -> controls.play()
                MediaTransportCommand.PAUSE -> controls.pause()
                MediaTransportCommand.NEXT_TRACK -> controls.skipToNext()
                MediaTransportCommand.PREV_TRACK -> controls.skipToPrevious()
            }
        }.isSuccess
    }

    private fun Session(controller: android.media.session.MediaController): MediaSessionControlPort.Session {
        val playbackState = controller.playbackState
        return MediaSessionControlPort.Session(
            key = controller.packageName + ":" + controller.sessionToken,
            packageName = controller.packageName,
            state = playbackState?.state ?: android.media.session.PlaybackState.STATE_NONE,
            positionMs = playbackState?.position ?: 0L
        )
    }
}

/**
 * Real [ControlBackend] for the "media-session" rung of the device-control
 * fallback chain. Dispatches media-transport commands (play/pause/next/prev) to
 * the active platform media session through a [MediaSessionControlPort].
 *
 * Never fire-and-assume: [execute] reports [VerificationStage.VERIFIED] only
 * when the session's re-read playback state observably confirms the dispatched
 * command took effect; otherwise the attempt is [VerificationStage.NOT_ATTEMPTED].
 *
 * Risk-gating is not the backend's job — like [ScreenBridgeControlBackend], this
 * backend sits behind the same [RiskGate] through the hosted device-control path.
 */
class MediaSessionControlBackend(
    private val port: MediaSessionControlPort
) : ControlBackend {

    override val id: String = "media-session"

    override fun available(): Boolean = port.activeSessions().isNotEmpty()

    override fun execute(action: ControlAction): VerificationEvidence {
        val command = commandFor(action.actionType)
            ?: return VerificationEvidence(
                VerificationStage.NOT_ATTEMPTED, id, "unsupported action ${action.actionType}"
            )
        val session = port.activeSessions().firstOrNull()
            ?: return VerificationEvidence(VerificationStage.NOT_ATTEMPTED, id, "no active media session")
        val before = Snapshot(session.state, session.positionMs)
        val dispatched = port.dispatch(session.key, command)
        if (!dispatched) {
            return VerificationEvidence(VerificationStage.NOT_ATTEMPTED, id, "platform declined transport dispatch")
        }
        val after = port.activeSessions()
            .firstOrNull { it.key == session.key }
            ?.let { Snapshot(it.state, it.positionMs) }
        val confirmed = after != null && confirms(command, before, after)
        return if (confirmed) {
            VerificationEvidence(
                VerificationStage.VERIFIED,
                id,
                "transport ${command.name} confirmed on ${packageLabel(session)}"
            )
        } else {
            VerificationEvidence(
                VerificationStage.NOT_ATTEMPTED,
                id,
                "no observable playback-state change after ${command.name}"
            )
        }
    }

    private fun commandFor(type: ActionType): MediaTransportCommand? = when (type) {
        ActionType.PLAY -> MediaTransportCommand.PLAY
        ActionType.PAUSE -> MediaTransportCommand.PAUSE
        ActionType.NEXT_TRACK -> MediaTransportCommand.NEXT_TRACK
        ActionType.PREV_TRACK -> MediaTransportCommand.PREV_TRACK
        else -> null
    }

    private fun confirms(
        command: MediaTransportCommand,
        before: Snapshot,
        after: Snapshot
    ): Boolean = when (command) {
        MediaTransportCommand.PLAY -> after.state == PlaybackStateValues.PLAYING
        MediaTransportCommand.PAUSE -> after.state == PlaybackStateValues.PAUSED
        MediaTransportCommand.NEXT_TRACK,
        MediaTransportCommand.PREV_TRACK ->
            after.state != before.state || after.positionMs < before.positionMs
    }

    private fun packageLabel(session: MediaSessionControlPort.Session): String =
        session.packageName.ifBlank { session.key }

    private data class Snapshot(val state: Int, val positionMs: Long)

    /** `PlaybackState` int constants mirrored so backend logic stays JVM-testable. */
    private object PlaybackStateValues {
        const val PAUSED: Int = 2
        const val PLAYING: Int = 3
    }
}