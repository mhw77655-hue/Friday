package com.jarvis.app.tts

import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared process-scoped TTS holder ensuring at most one TTS owner per process.
 *
 * In the main app process, [JarvisTts] registers as the primary engine provider,
 * and other components ([StreamingTts], [AckSpeech]) coordinate with or reuse it.
 * In the `:voice` process, [VoiceService]'s [SystemTtsVoiceProvider] registers
 * as the primary engine provider.
 */
object ProcessTtsHolder {

    val gate = TtsReadinessGate()

    val readinessState get() = gate.state
    val currentReadiness get() = gate.current

    @Volatile private var primaryOwnerId: String? = null
    @Volatile private var rawEngine: Any? = null
    private val ownerRegistrationCount = AtomicInteger(0)

    /**
     * Checks if an owner is already registered or being initialized in this process.
     */
    @Synchronized
    fun hasRegisteredOwner(): Boolean = primaryOwnerId != null

    /**
     * Registers a candidate TTS owner for the current process.
     * Returns true if registered as the primary owner, or false if another owner is already registered.
     */
    @Synchronized
    fun registerOwner(ownerId: String, engine: Any?): Boolean {
        if (primaryOwnerId == null || primaryOwnerId == ownerId) {
            primaryOwnerId = ownerId
            if (engine != null) {
                rawEngine = engine
            }
            ownerRegistrationCount.incrementAndGet()
            return true
        }
        return false
    }

    /**
     * Sets or updates the raw engine instance for the registered owner.
     */
    @Synchronized
    fun setEngine(ownerId: String, engine: Any?): Boolean {
        if (primaryOwnerId == null || primaryOwnerId == ownerId) {
            primaryOwnerId = ownerId
            rawEngine = engine
            return true
        }
        return false
    }

    /**
     * Gets the current primary owner ID.
     */
    fun getPrimaryOwnerId(): String? = primaryOwnerId

    /**
     * Gets the registered engine instance regardless of Ready status (for binding reuse within the process).
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getRawEngine(): T? = rawEngine as? T

    /**
     * Gets the active engine instance only if Ready via the gate.
     */
    fun <T : Any> getEngineSlot(): TtsSlot<T> = gate.slot {
        @Suppress("UNCHECKED_CAST")
        rawEngine as? T
    }

    /**
     * Resets state for testing purposes.
     */
    @Synchronized
    fun resetForTesting() {
        primaryOwnerId = null
        rawEngine = null
        ownerRegistrationCount.set(0)
        gate.resetForTesting()
    }
}
