package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.store.MoodState
import com.jarvis.app.humancore.store.MoodStore

/**
 * The Mood System (§6): the fast affective layer that shifts within minutes
 * to hours. This module is the read-side façade over the Mood store — it
 * exposes JARVIS's current (decay-adjusted) mood as a clamped style modifier
 * bundle, so expression consumers read a single `MoodModifiers` value instead
 * of reaching into decay math.
 *
 * The write side is exclusively the Emotional Regulation module (§6) — mood
 * is never written by conversation content directly.
 *
 * Two moods must not be confused:
 *  - this module: JARVIS's OWN mood (internal weather);
 *  - the Emotional Intelligence module (§7): JARVIS's READ of the USER's
 *    mood (empathy input). They influence each other through Emotional
 *    Regulation (coupling), never by conflation.
 *
 * Constraint honored here: mood may influence *style* but never *values
 * compliance*. The Consistency Guard ignores mood entirely (§6 Constraints).
 */
class MoodSystem(
    private val mood: MoodStore,
    private val clock: () -> Long
) {

    /** Effective mood right now (stored values decayed toward baseline, §6). */
    fun current(now: Long = clock()): MoodState = mood.effective(now)

    /**
     * Clamped style-facing mood. `isSignificant` is false when the mood is
     * effectively neutral — style code uses it to avoid micro-adjusting
     * tone on noise (§0.11 runaway-state guard).
     */
    fun styleModifiers(now: Long = clock()): MoodModifiers {
        val m = mood.effective(now)
        return MoodModifiers(
            valence = com.jarvis.app.humancore.algo.Clamp.unitSymmetric(m.valence),
            arousal = com.jarvis.app.humancore.algo.Clamp.unitSymmetric(m.arousal),
            isSignificant = Math.abs(m.valence) > 0.15 || Math.abs(m.arousal) > 0.15
        )
    }
}

/** Clamped style-facing mood bundle (§6). */
data class MoodModifiers(
    val valence: Double,
    val arousal: Double,
    val isSignificant: Boolean
)
