package com.jarvis.app

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes JARVIS conversation turns straight into an Obsidian vault as
 * markdown — fully offline, plain java.io.File, no network call, no
 * Turso. One note per day (YYYY-MM-DD.md) inside a "JARVIS" subfolder
 * of the vault, appended to as the day goes on, matching how Obsidian
 * daily notes normally work so it shows up cleanly in your vault.
 *
 * Vault path is hardcoded to what you confirmed in the app:
 * /storage/emulated/0/JarvisSync/vault
 *
 * REQUIRES "All files access" (MANAGE_EXTERNAL_STORAGE) on Android 11+,
 * since /storage/emulated/0/JarvisSync is outside this app's private
 * sandbox. If that permission isn't granted, writes fail silently
 * (caught, logged, swallowed) rather than crashing the app — see
 * logTurn()'s try/catch. Grant it: Settings → Apps → JARVIS → Permissions
 * → All files access.
 */
object ObsidianSync {
    private const val VAULT_ROOT = "/storage/emulated/0/JarvisSync/vault"
    private const val SUBFOLDER = "JARVIS"

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun logTurn(fromJarvis: Boolean, text: String) {
        if (text.isBlank()) return
        try {
            val folder = File(VAULT_ROOT, SUBFOLDER)
            if (!folder.exists()) folder.mkdirs()

            val now = Date()
            val noteFile = File(folder, "${dayFormat.format(now)}.md")
            if (!noteFile.exists()) {
                noteFile.writeText("# JARVIS conversation — ${dayFormat.format(now)}\n\n")
            }

            val speaker = if (fromJarvis) "**JARVIS**" else "**You**"
            val entry = "- ${timeFormat.format(now)} ${speaker}: ${text.replace("\n", " ")}\n"
            noteFile.appendText(entry)
        } catch (e: Exception) {
            // Swallowed on purpose — a failed Obsidian write should never
            // break the conversation itself. Most common cause: "All
            // files access" not granted.
            android.util.Log.w("ObsidianSync", "logTurn failed: ${e.message}")
        }
    }

    /**
     * Human Core Memory Interface sink (§16): writes a *sidecar* context note
     * carrying relationship tags for an exchange, so the main conversation
     * file stays clean and no turn is double-logged. The sidecar is what
     * lets a later memory query answer "was this shared with JARVIS while the
     * user was stressed?" without JARVIS having to guess.
     *
     * Same silent-failure contract as [logTurn] — a failed annotation must
     * never affect the conversation.
     */
    fun logAnnotated(userText: String, jarvisText: String, ts: Long, tags: List<String>) {
        if (userText.isBlank() && jarvisText.isBlank()) return
        if (tags.isEmpty()) return
        try {
            val folder = File(File(VAULT_ROOT, SUBFOLDER), "context")
            if (!folder.exists()) folder.mkdirs()

            val day = dayFormat.format(Date(ts))
            val time = timeFormat.format(Date(ts))
            val noteFile = File(folder, "$day.md")
            if (!noteFile.exists()) {
                noteFile.writeText("# JARVIS relationship context — $day\n\n")
            }

            val tagStr = " [${tags.joinToString(", ")}]"
            val sb = StringBuilder()
            if (userText.isNotBlank()) sb.append("- $time **You**: ${userText.replace("\n", " ")}").append('\n')
            if (jarvisText.isNotBlank()) sb.append("- $time **JARVIS**: ${jarvisText.replace("\n", " ")}").append(tagStr).append('\n')
            noteFile.appendText(sb.toString())
        } catch (e: Exception) {
            android.util.Log.w("ObsidianSync", "logAnnotated failed: ${e.message}")
        }
    }
}
