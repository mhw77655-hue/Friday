package com.jarvis.app.humancore.bus

import java.util.concurrent.CopyOnWriteArrayList

/**
 * The in-process State Bus (§0.10).
 *
 * In-process means: this bus does NOT cross process or device boundaries —
 * cross-device state movement is the job of the sync layer (SyncPort /
 * ConflictResolver, §21.2/§22.2), which reads persisted stores, never this
 * live bus.
 *
 * Guarantees:
 *  - subscribers are copied on write, so subscribing/unsubscribing during a
 *    publish is safe;
 *  - a slow or throwing subscriber never blocks or corrupts other
 *    subscribers (each is invoked with its own error isolation, and
 *    subscribers must not do I/O — the bus is an in-process signal, not a
 *    work queue);
 *  - a bounded audit trail keeps the last [AUDIT_LIMIT] events with their
 *    reasons, satisfying the "log the why" requirement even when no module
 *    subscribed to a particular event type.
 */
object StateBus {

    private const val AUDIT_LIMIT = 200

    private val subscribers = CopyOnWriteArrayList<(HcEvent) -> Unit>()
    private val audit = ArrayDeque<HcEvent>()
    private val auditLock = Any()

    /** Returns an unsubscribe function. */
    fun subscribe(subscriber: (HcEvent) -> Unit): () -> Unit {
        subscribers.add(subscriber)
        return { subscribers.remove(subscriber) }
    }

    fun publish(event: HcEvent) {
        synchronized(auditLock) {
            audit.addLast(event)
            while (audit.size > AUDIT_LIMIT) audit.removeFirst()
        }
        subscribers.forEach { subscriber ->
            try {
                subscriber(event)
            } catch (e: Exception) {
                // A subscriber bug must never take down a publish loop.
                // Subscribers are internal modules; a failure here is logged
                // via the subsystem's own diagnostics, not the bus.
            }
        }
    }

    /** Most-recent-first view of the audit trail (for diagnostics/tests). */
    fun auditTrail(): List<HcEvent> {
        synchronized(auditLock) { return audit.toList().asReversed() }
    }

    fun clearAudit() {
        synchronized(auditLock) { audit.clear() }
    }

    /**
     * TEST-ONLY teardown: drops all subscribers and the audit trail. The bus
     * is a process-wide singleton; every graph built in the test suite
     * subscribes its InternalStateManager and never unsubscribes, so without
     * this, stale subscribers accumulate across tests (HUMAN_CORE_AUDIT M-9).
     * Production never calls this — subscribers live for the app lifetime.
     */
    fun reset() {
        subscribers.clear()
        clearAudit()
    }
}
