package com.jarvis.app.voice

import android.content.Context
import android.content.Intent

/**
 * R1 substrate — the environment recipe for the voice capability habitat.
 *
 * Per the Voice Organism spec (§4, §14): JARVIS stores the recipe for its
 * organism's habitat, not the habitat itself. This is that recipe: everything
 * the [VoiceOrganismHost] needs to (re)create the `:voice` process
 * deterministically. When the process dies, Android materializes it again on
 * the next bind — a rebuild is just re-applying this recipe.
 */
data class VoiceEnvironment(
    /** Android process suffix — the isolated address space voice runs in. */
    val processName: String = ":voice",
    /** The service implementing the voice contract inside the `:voice` process. */
    val serviceClass: Class<*> = VoiceService::class.java,
    /** Bind flags — BIND_AUTO_CREATE materializes the process on bind. */
    val bindFlags: Int = Context.BIND_AUTO_CREATE,
    /** Backoff before a supervised rebind after process death. */
    val restartBackoffMs: Long = 500L,
    /** Consecutive deaths before the host marks the substrate DISABLED (no infinite loop). */
    val maxConsecutiveRestarts: Int = 5,
    /** Ping round-trip budget — exceeding it marks the substrate DEGRADED. */
    val pingTimeoutMs: Long = 3000L
) {
    /** Explicit bind intent for the service component (same APK, other process). */
    fun bindIntent(context: Context): Intent = Intent(context, serviceClass)

    /** "com.jarvis.app:voice" — the human-readable habitat name. */
    fun habitatName(context: Context): String = context.packageName + processName
}
