package com.jarvis.app.voice

/**
 * An extensible registry of bounded phone-action skills.
 *
 * Skills describe what the Android body can already do; they do not grant
 * permissions or execute actions. The winning command still passes through
 * FridayPhoneDecisionTool and PhoneCommandOrchestrator before Android acts.
 */
data class PhoneActionSkill(
    val id: String,
    val aliases: List<String>,
    val command: PhoneControlCommand,
    val label: String = id.substringAfterLast('.').replace('_', ' ')
)

class LocalPhoneActionSkillRegistry(
    skills: List<PhoneActionSkill>
) {
    sealed interface Resolution {
        data class Action(
            val skillId: String,
            val command: PhoneControlCommand,
            val confidence: Double
        ) : Resolution

        data class Clarification(val question: String) : Resolution
        data object NotMatched : Resolution
    }

    private data class ScoredSkill(
        val skill: PhoneActionSkill,
        val score: Double
    )

    private val skills = skills.mapNotNull { skill ->
        val aliases = skill.aliases.map(::tokens).filter { it.size >= 2 }.distinct()
        if (skill.id.isBlank() || aliases.isEmpty()) null else skill to aliases
    }

    fun resolve(utterance: String): Resolution {
        val input = tokens(utterance)
        if (input.size < 2) return Resolution.NotMatched

        val scored = skills.flatMap { (skill, aliases) ->
            aliases.mapNotNull { alias ->
                val overlap = input.intersect(alias).size
                if (overlap == 0) null
                else ScoredSkill(skill, overlap.toDouble() / maxOf(input.size, alias.size))
            }
        }.groupBy { it.skill.id }
            .map { (_, candidates) -> candidates.maxBy { it.score } }
            .sortedByDescending { it.score }

        val best = scored.firstOrNull() ?: return Resolution.NotMatched
        val second = scored.getOrNull(1)
        val margin = best.score - (second?.score ?: 0.0)

        if (best.score >= MATCH_THRESHOLD && margin >= AMBIGUITY_MARGIN) {
            return Resolution.Action(best.skill.id, best.skill.command, best.score)
        }
        if (best.score >= CLARIFICATION_THRESHOLD && (second == null || margin < AMBIGUITY_MARGIN)) {
            val choices = scored.take(2).map { it.skill.label }
            val question = if (second == null) {
                "I think you mean " + choices.first() + ". Is that right?"
            } else {
                "Should I " + choices[0] + " or " + choices[1] + "? I won't change anything until you confirm."
            }
            return Resolution.Clarification(question)
        }
        return Resolution.NotMatched
    }

    fun mayMatch(utterance: String): Boolean =
        resolve(utterance) !is Resolution.NotMatched

    private fun tokens(raw: String): Set<String> {
        val normalized = raw.lowercase()
            .replace(Regex("^\\s*(hey\\s+)?friday[, :]?\\s*"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
        return normalized.split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in STOP_WORDS }
            .map { TOKEN_ALIASES[it] ?: it }
            .toSet()
    }

    companion object {
        private const val MATCH_THRESHOLD = 0.78
        private const val CLARIFICATION_THRESHOLD = 0.45
        private const val AMBIGUITY_MARGIN = 0.12

        private val STOP_WORDS = setOf(
            "please", "can", "could", "would", "will", "you", "your", "my",
            "the", "a", "an", "to", "for", "on", "of", "hey", "kindly",
            "i", "want", "need", "me", "just", "now"
        )
        private val TOKEN_ALIASES = mapOf(
            "louder" to "loud",
            "loudest" to "loud",
            "quieter" to "quiet",
            "quietly" to "quiet",
            "lower" to "down",
            "raise" to "up",
            "raised" to "up",
            "previously" to "previous",
            "notifications" to "notification",
            "apps" to "app",
            "songs" to "song",
            "tracks" to "track",
            "wifi" to "wifi",
            "wi" to "wifi",
            "fi" to "wifi"
        )

        private val defaultSkills = listOf(
            PhoneActionSkill("navigation.back", listOf(
                "go back", "take me back", "go back one screen", "return to previous screen",
                "navigate to previous screen"
            ), PhoneControlCommand.Back, "go back"),
            PhoneActionSkill("navigation.home", listOf(
                "go to home screen", "take me to home screen", "return to home screen",
                "show home screen"
            ), PhoneControlCommand.Home, "go to the home screen"),
            PhoneActionSkill("navigation.recents", listOf(
                "show recent apps", "switch between apps", "show my recent apps",
                "bring up recent applications"
            ), PhoneControlCommand.Recents, "show recent apps"),
            PhoneActionSkill("system.notifications", listOf(
                "pull down notifications", "open notification shade", "show notification shade",
                "bring down notifications"
            ), PhoneControlCommand.Notifications, "open notifications"),
            PhoneActionSkill("system.quick_settings", listOf(
                "open quick settings", "show quick settings", "bring up quick panel"
            ), PhoneControlCommand.QuickSettings, "open quick settings"),
            PhoneActionSkill("system.settings", listOf(
                "open system settings", "take me to settings", "open phone settings"
            ), PhoneControlCommand.OpenSettings, "open system settings"),
            PhoneActionSkill("media.play", listOf(
                "play my music", "resume my music", "start music playback"
            ), PhoneControlCommand.PlayMedia, "play music"),
            PhoneActionSkill("media.pause", listOf(
                "pause my music", "pause the playback", "stop music playback"
            ), PhoneControlCommand.PauseMedia, "pause music"),
            PhoneActionSkill("media.next", listOf(
                "play next song", "skip to next song", "move to next track"
            ), PhoneControlCommand.NextTrack, "skip to the next track"),
            PhoneActionSkill("media.previous", listOf(
                "play previous song", "go to previous track", "return to last track"
            ), PhoneControlCommand.PreviousTrack, "return to the previous track"),
            PhoneActionSkill("volume.up", listOf(
                "make it louder", "make it a bit louder", "turn volume up",
                "increase the volume", "raise the sound", "make the sound loud"
            ), PhoneControlCommand.VolumeUp, "increase the volume"),
            PhoneActionSkill("volume.down", listOf(
                "make it quieter", "turn volume down", "decrease the volume",
                "lower the sound", "make the sound quiet"
            ), PhoneControlCommand.VolumeDown, "decrease the volume"),
            PhoneActionSkill("network.internet_panel", listOf(
                "open wifi settings", "show wifi settings", "open internet settings",
                "open connectivity settings", "show wifi controls"
            ), PhoneControlCommand.OpenInternetPanel, "open the internet settings panel"),
            PhoneActionSkill("screen.scroll_down", listOf(
                "scroll down", "move the screen down", "scroll further down"
            ), PhoneControlCommand.ScrollForward, "scroll down"),
            PhoneActionSkill("screen.scroll_up", listOf(
                "scroll up", "move the screen up", "scroll further up"
            ), PhoneControlCommand.ScrollBackward, "scroll up")
        )

        private val DEFAULT = LocalPhoneActionSkillRegistry(defaultSkills)

        fun default(): LocalPhoneActionSkillRegistry = DEFAULT
    }
}
