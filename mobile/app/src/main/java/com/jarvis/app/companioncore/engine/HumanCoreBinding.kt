package com.jarvis.app.companioncore.engine

/**
 * The dual-mode binding interface behind §2.30 (spec §2.30, audit R-M5/R-I2).
 *
 * Companion Core code is written once against this interface; a build uses
 * either the local in-process binding or the remote HTTP/WebSocket binding
 * depending on the active brain topology. Both must produce identical
 * [RawCoreState] shapes from equivalent underlying state (local-vs-remote
 * binding parity test, audit M-8) and both are **synchronous pull** readers —
 * the Presence Engine tick loop drives the poll, never the reverse.
 *
 * Implementations MUST NOT block the render thread; network reads happen on
 * the Engine Tick Lane (plan §1.6). [read] returns the last-known-good state
 * on any stall (spec §2.30 failure handling).
 */
interface HumanCoreBinding {
    /** Stable identity of the binding, for diagnostics. */
    val label: String

    /** Schema version this binding speaks (audit R-M5/R-I2 handshake). */
    val schemaVersion: Int

    /**
     * Synchronously read the raw core state. Must return fast (no render-thread
     * blocking); on a stall returns the last-known-good or [RawCoreState.empty].
     */
    fun read(): RawCoreState

    /**
     * Whether the binding currently has a live connection (remote) / an
     * initialized HC (local). Drives staleness semantics, not the read itself.
     */
    fun isHealthy(): Boolean
}
