package com.jarvis.app.threads

/**
 * THREAD-OBJECTS (Gate 3a, priority 2): unfinished/tangent thoughts become
 * tracked thread objects instead of discarded text.
 *
 * When a user message contains more than one distinct thought (a task plus an
 * aside, an unfinished sentence, a jump to an unrelated topic), each becomes an
 * [OpenThread]: id, content, completeness (finished / trailing-off), a decay
 * clock (creation turn + wall-clock creation time, over which the thread ages
 * into and out of its resurface window), and a link back to the turn it came
 * from ([OpenThread.createdAtTurn]). The main task gets [OpenThread.mainAnchorId]
 * — the first segment of a message is the main thread and every tangent of that
 * message points its main-anchor at it, so the anchor survives any number of
 * tangents.
 *
 * All splitting is rule-based and script-agnostic (works on English, Egyptian
 * Arabic in Arabic script, and Franco-Arabic — punctuation is the boundary, not
 * words), so mixed-language input is handled by the same rules.
 *
 * The whole subsystem is pure in-memory, local, and deterministic; no I/O, no
 * network (the verifyCommand greps the package for zero HTTP/socket markers),
 * no model calls — the ~acknowledgment~ language is produced by boring rules
 * and consumed by the real generation payload of the DIRECT_REPLY turn.
 *
 * The negative control ([ThreadTracker.enabled] / [setEnabled]) is the AC7
 * lever: with THREAD-OBJECTS disabled, [ThreadTracker] creates no threads, no
 * acknowledgment clauses, and no resurfacings — so the three_thoughts_test
 * acceptance assertions fail, proving they are not vacuously satisfied.
 */
object ThreadObjects {

    /** Completeness of a single thought segment. */
    enum class Completeness {
        /** A thought that was expressed to completion in the message. */
        FINISHED,

        /**
         * A thought that trails off mid-sentence (an ellipsis, a hanging
         * conjunction, or an unfinished clause). AC3: never auto-completed,
         * never invented — stored exactly as the user left it, only held open.
         */
        TRAILING_OFF
    }

    /**
     * One tracked thought. [mainAnchorId] is the id of the message's main
     * segment (the first thought); for the main segment itself it equals own
     * id, for every tangent it points at the main thread — surviving any
     * number of tangents across any number of turns.
     */
    data class OpenThread(
        val id: String,
        val content: String,
        val completeness: Completeness,
        val createdAtTurn: Long,
        val createdAtMs: Long,
        val mainAnchorId: String,
        /** Resolved by the user or by [ThreadTracker.close] — no longer open. */
        val closed: Boolean = false,
        val resolvedAtTurn: Long? = null
    )

    /**
     * AC1: split one message into its separate thoughts. Boundaries are
     * sentence terminators — ASCII/CDI dot, exclamation, question, Arabic
     * question mark, Arabic semi-colon — plus ellipsis (".", "…", or any
     * dotted variant), which both splits a trailing thought off AND marks it
     * incomplete via [completeness]. Whitespace is collapsed and segments are
     * trimmed; empty segments are dropped. Script-independent.
     */
    @JvmStatic
    fun segment_message(raw: String): List<String> {
        val text = raw.replace(Regex("\\.\\s*\\.\\s*\\.|\\u2026"), "\u2026")
        val parts = mutableListOf<String>()
        val out = StringBuilder()
        for (ch in text) {
            out.append(ch)
            if (ch == '.' || ch == '!' || ch == '?' || ch == '\u061F' || ch == '\u061B' || ch == '\u2026') {
                if (out.isNotEmpty()) parts.add(out.toString())
                out.setLength(0)
            }
        }
        if (out.isNotEmpty()) parts.add(out.toString())
        return parts
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Completion signal for one segment. A thought is TRAILING_OFF when it ends
     * on an ellipsis marker or a dangling conjunct (English "and", Egyptian-Frank "w",
     * Arabic "و"), i.e. the user visibly stopped before finishing. Everything else is
     * FINISHED. AC3: never auto-completes — an incomplete thought stays TRAILING_OFF
     * until the user resolves it (see [ThreadTracker.close_detect]).
     */
    @JvmStatic
    fun completenessOf(segment: String): Completeness {
        val trimmed = segment.trim()
        val trailing = trimmed.substringAfterLast(' ')
        return when {
            trimmed.endsWith("\u2026") ||
                trimmed.endsWith("...") ||
                trailing.isBlank() ||
                trailing.equals("and", ignoreCase = true) ||
                trailing.equals("w", ignoreCase = true) ||
                trailing == "\u0648" ||
                trailing == "\u0643\u0645\u0627\u0646" -> Completeness.TRAILING_OFF
            else -> Completeness.FINISHED
        }
    }

    /**
     * Significant tokens of a phrase: lowercased, punctuation-stripped,
     * stopword-free words of length &gt; 2. Works across scripts: Arabic tokens
     * are split on whitespace exactly like Latin ones and compared by string
     * identity, and digit-letter Franco-Arabic tokens (e.g. "3ayez", "5alas")
     * are kept whole. Used by the topic-relation ([isRelatedTopic]) and
     * idle-moment ([isIdleMoment]) gates.
     */
    @JvmStatic
    fun significantTokens(phrase: String): Set<String> {
        val stopwords = setOf(
            "the", "and", "are", "for", "with", "you", "was", "were",
            "bas", "fel", "el", "al", "ana", "enta", "kda"
        )
        return phrase.lowercase()
            .split(Regex("\\s+"))
            .map { it.trim().trim(' ', ',', '.', '!', '?', '\u061F', '\u061B', '\u2026', '"', '\'') }
            .filter { it.length > 2 && it !in stopwords }
            .toSet()
    }

    /** Related-topic gate: two phrases share at least one significant token. */
    @JvmStatic
    fun isRelatedTopic(a: String, b: String): Boolean =
        significantTokens(a).intersect(significantTokens(b)).isNotEmpty()

    /** Idle-moment gate: a turn with essentially no content (bare acknowledgment). */
    @JvmStatic
    fun isIdleMoment(userText: String): Boolean =
        significantTokens(userText).size <= 1
}
/**
 * The cross-turn registry of open [OpenThread]s — the THREAD-OBJECTS engine
 * seam (nullable on [com.jarvis.app.cognitive.CognitiveEngine]; a null or
 * [enabled]==false tracker makes the whole subsystem a no-op, which is exactly
 * the AC7 negative-control lever).
 *
 * One instance lives per conversation (constructed once in
 * [com.jarvis.app.JarvisEngine.init] / the Termux composition root, handed to
 * the engine), so a thread created on turn 1 is still there on turn 2 — which
 * is what lets an unfinished thought [resurface_policy] and a user-resolved
 * thought [close_detect] later.
 *
 * Deterministic rules, injected clock — no wall-time, threading, or model
 * dependency inside the decision logic; tests inject [now] directly.
 *
 * Order of a turn (all four story-named operations, called by the engine):
 *  1. [close_detect] — a turn that resolves an earlier open thread closes it
 *     FIRST, so a resolving turn never re-acknowledges what it just settled.
 *  2. [segment_message] — the turn's thoughts are split and become threads
 *     (first = main, rest = tangents anchored to it).
 *  3. [thread_acknowledge] — exactly one clause per thread born this turn, so
 *     "all three get answered" without the reply becoming a checklist.
 *  4. [resurface_policy] — an OLDER open thread may re-enter the reply, gated
 *     by its decay clock AND an idle-moment/related-topic turn (never a fixed
 *     timer alone).
 */
class ThreadTracker(
    /** AC7: when false the tracker is inert — no threads, no acks, no resurfacing. */
    private val _enabled: Boolean = true,
    /** A thread cannot resurface before this many turns have passed (bake time). */
    private val minimumHoldTurns: Long = 1,
    /** A thread's resurface window expires after this many turns (decay). */
    private val maximumOpenTurns: Long = 10,
    /** Wall-clock bake time before a fresh thread may resurface. */
    private val minimumHoldMs: Long = 60_000L,
    /** Wall-clock staleness after which a thread stops resurfacing (decay). */
    private val maximumOpenMs: Long = 7L * 24 * 60 * 60 * 1000,
    /** Injectable clock — tests drive decay without sleeping. */
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    @Volatile
    private var enabledFlag: Boolean = _enabled

    val enabled: Boolean get() = enabledFlag

    /** AC7 negative-control lever: turn the whole subsystem on/off. */
    fun setEnabled(enabled: Boolean) {
        enabledFlag = enabled
    }

    private val open = linkedMapOf<String, OpenThread>()

    /**
     * AC5: close any open thread this turn resolves. A thread closes when the
     * new turn shares a topic token with it AND carries a resolution marker
     * ("done", "sorted", Franco "5alas", Egyptian "zabatna", …) — i.e. the
     * user settles the open thought themselves, without being asked. Closed
     * threads are no longer open, are never acknowledged, and never resurface.
     * Returns the ids closed by this turn.
     */
    fun close_detect(turnIndex: Long, userText: String): List<String> {
        if (!enabledFlag) return emptyList()
        val lowered = userText.lowercase()
        if (RESOLUTION_MARKERS.none { lowered.contains(it) }) return emptyList()
        val closed = mutableListOf<String>()
        synchronized(lock) {
            for (id in open.keys.toList()) {
                val t = open.getValue(id)
                if (t.closed) continue
                if (ThreadObjects.isRelatedTopic(t.content, userText)) {
                    open[id] = t.copy(closed = true, resolvedAtTurn = turnIndex)
                    closed.add(id)
                }
            }
        }
        return closed
    }

    /**
     * AC1: turn one raw message into threads. Runs [close_detect] first (a
     * resolving turn never re-opens what it just settled), splits the message
     * with [ThreadObjects.segment_message], and creates one [OpenThread] per
     * segment: the first is the message's main thread (its own anchor), every
     * tangent after it carries [OpenThread.mainAnchorId] pointing at that main
     * thread — the anchor survives any number of tangents. Each thread records
     * the turn it came from ([OpenThread.createdAtTurn]) and the wall-clock
     * creation time that starts its decay clock. A no-op when disabled. Returns
     * the threads born from this turn (empty when disabled or when the message
     * has no segments).
     */
    fun ingestTurn(turnIndex: Long, userText: String): List<OpenThread> {
        if (!enabledFlag) return emptyList()
        close_detect(turnIndex, userText)
        val segments = ThreadObjects.segment_message(userText)
        if (segments.isEmpty()) return emptyList()
        val createdAt = now()
        val ids = segments.indices.map { "thread-$turnIndex-$it" }
        val anchorId = ids.first()
        val created = segments.mapIndexed { i, seg ->
            OpenThread(
                id = ids[i],
                content = seg,
                completeness = ThreadObjects.completenessOf(seg),
                createdAtTurn = turnIndex,
                createdAtMs = createdAt,
                mainAnchorId = anchorId
            )
        }
        synchronized(lock) {
            created.forEach { open[it.id] = it }
        }
        return created
    }

    /**
     * AC2: one acknowledgment clause per thread born on [turnIndex] — in creation
     * order, one clause each, never a checklist. A FINISHED thought is
     * acknowledged as noted; a TRAILING_OFF thought is acknowledged as OPEN
     * (held, not completed — AC3). Empty when disabled or when no thread was
     * born on this turn.
     */
    fun thread_acknowledge(turnIndex: Long): List<String> {
        if (!enabledFlag) return emptyList()
        val createdThisTurn = synchronized(lock) {
            open.values.filter { it.createdAtTurn == turnIndex && !it.closed }
        }
        return createdThisTurn.map { t ->
            if (t.completeness == ThreadObjects.Completeness.FINISHED) {
                "noted: ${t.content}"
            } else {
                "noted open: ${t.content} (held open, not completed)"
            }
        }
    }

    /**
     * AC4: which open threads to bring back on THIS turn. Gated by BOTH:
     *  - the decay clock: the thread is older than [minimumHoldTurns] /
     *    [minimumHoldMs] (baked) but younger than [maximumOpenTurns] /
     *    [maximumOpenMs] (not yet stale); AND
     *  - the turn: an idle moment (bare acknowledgment) or a related topic
     *    (shares a significant token with the thread).
     *
     * Only unfinished (TRAILING_OFF) threads ever resurface, and never when
     * disabled. The clock alone never fires a resurface — the idle/related gate
     * is mandatory — which is what makes this "not a fixed timer alone".
     */
    fun resurface_policy(turnIndex: Long, userText: String): List<OpenThread> {
        if (!enabledFlag) return emptyList()
        val idle = ThreadObjects.isIdleMoment(userText)
        val currentTime = now()
        return synchronized(lock) {
            open.values
                .filter { !it.closed }
                .filter { it.completeness == ThreadObjects.Completeness.TRAILING_OFF }
                .filter { turnIndex - it.createdAtTurn >= minimumHoldTurns }
                .filter { turnIndex - it.createdAtTurn <= maximumOpenTurns }
                .filter { currentTime - it.createdAtMs >= minimumHoldMs }
                .filter { currentTime - it.createdAtMs <= maximumOpenMs }
                .filter { idle || ThreadObjects.isRelatedTopic(it.content, userText) }
                .toList()
        }
    }

    /** Every currently-open (unclosed) thread, oldest first. */
    fun openThreads(): List<OpenThread> = synchronized(lock) {
        open.values.filter { !it.closed }.toList()
    }

    /** Every thread ever created (including closed), oldest first. */
    fun allThreads(): List<OpenThread> = synchronized(lock) {
        open.values.toList()
    }

    /** Thread lookup by id, or null. */
    fun thread(id: String): OpenThread? = synchronized(lock) { open[id] }

    /**
     * The id of [turnIndex]'s main thread (the anchor every tangent of that
     * turn points at), or null if no thread was born on that turn.
     */
    fun mainAnchorId(turnIndex: Long): String? = synchronized(lock) {
        open.values
            .filter { it.createdAtTurn == turnIndex }
            .minByOrNull { it.id }?.id
    }

    private val lock = Any()

    private companion object {
        /** AC5 resolution markers — English + Franco-Arabic + Egyptian Arabic. */
        val RESOLUTION_MARKERS = listOf(
            "done", "sorted", "resolved", "forgot", "never mind", "nevermind",
            "cancelled", "canceled",
            "5alas", "khalas", "khallas", "bas khallas",
            "zabat", "zabatna", "3amalt", "3aleet", "shelna"
        )
    }
}
