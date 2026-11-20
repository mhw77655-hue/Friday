package com.jarvis.app.voice

/**
 * Small deterministic action vocabulary for phone navigation. Complex requests
 * remain with Friday's cognitive pipeline; this parser only claims commands
 * whose intent is unambiguous.
 */
object PhoneCommandInterpreter {
    fun parse(utterance: String): PhoneControlCommand? {
        val cleaned = normalizeSpokenCommand(utterance)
        val text = cleaned.lowercase()
        if (text.isBlank()) return null
        when (text) {
            "ارجع", "ارجع للخلف", "رجعني", "ارجع خطوة" -> return PhoneControlCommand.Back
            "الرئيسية", "روح للرئيسية", "افتح الشاشة الرئيسية" -> return PhoneControlCommand.Home
            "افتح الإعدادات", "افتح الاعدادات", "إعدادات الهاتف", "اعدادات الهاتف" -> return PhoneControlCommand.OpenSettings
            "افتح الإشعارات", "افتح الاشعارات", "وريني الإشعارات", "وريني الاشعارات" -> return PhoneControlCommand.Notifications
            "افتح التطبيقات الأخيرة", "افتح التطبيقات الاخيرة" -> return PhoneControlCommand.Recents
            "افتح الإعدادات السريعة", "افتح الاعدادات السريعة" -> return PhoneControlCommand.QuickSettings
            "علي الصوت", "علي صوت الميديا", "ارفع الصوت", "زود الصوت" -> return PhoneControlCommand.VolumeUp
            "وطي الصوت", "وطي صوت الميديا", "قلل الصوت", "اخفض الصوت" -> return PhoneControlCommand.VolumeDown
            "شغل الموسيقى", "شغل المزيكا", "كمل الموسيقى", "كمل المزيكا" -> return PhoneControlCommand.PlayMedia
            "وقف الموسيقى", "وقف المزيكا", "اقفل الموسيقى", "اقفل المزيكا" -> return PhoneControlCommand.PauseMedia
            "افتح إعدادات الواي فاي", "افتح اعدادات الواي فاي", "افتح الواي فاي" -> return PhoneControlCommand.OpenInternetPanel
        }
        Regex("^(?:افتح|شغل|شغّل)\\s+(.+)$").matchEntire(text)?.groupValues?.get(1)
            ?.trim()?.takeIf { it.isNotBlank() && it.length <= 60 && !it.contains(" و ") }
            ?.let { return PhoneControlCommand.OpenApp(it) }
        Regex("^خلي (?:صوت الميديا|الصوت) (?:على )?(\\d{1,3})%?$").matchEntire(text)
            ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..100 }
            ?.let { return PhoneControlCommand.SetMediaVolume(it) }
        Regex("^(?:خلي السطوع|اضبط السطوع على|ظبط السطوع على) (\\d{1,3})%?$").matchEntire(text)
            ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..100 }
            ?.let { return PhoneControlCommand.SetBrightness(it) }
        Regex("^set (?:the )?(?:media )?volume to (\\d{1,3})(?:\\s*(?:%|percent|per\\s+cent))?$").matchEntire(text)
            ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..100 }
            ?.let { return PhoneControlCommand.SetMediaVolume(it) }
        Regex("^set (?:the )?brightness to (\\d{1,3})(?:\\s*(?:%|percent|per\\s+cent))?$").matchEntire(text)
            ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..100 }
            ?.let { return PhoneControlCommand.SetBrightness(it) }
        return when (text) {
            "go back", "back", "previous screen", "navigate back", "take me back", "return to previous screen" -> PhoneControlCommand.Back
            "go home", "home screen", "open home", "take me home", "return home", "return to home screen" -> PhoneControlCommand.Home
            "show recent apps", "open recents", "recent apps", "show my recent apps" -> PhoneControlCommand.Recents
            "show notifications", "open notifications", "notifications" -> PhoneControlCommand.Notifications
            "open quick settings", "show quick settings", "quick settings" -> PhoneControlCommand.QuickSettings
            "open settings", "phone settings", "system settings" -> PhoneControlCommand.OpenSettings
            "play music", "resume music", "play" -> PhoneControlCommand.PlayMedia
            "pause music", "pause playback", "pause" -> PhoneControlCommand.PauseMedia
            "next track", "skip track", "next song" -> PhoneControlCommand.NextTrack
            "previous track", "last track", "previous song" -> PhoneControlCommand.PreviousTrack
            "turn up the volume", "increase volume", "volume up", "louder" -> PhoneControlCommand.VolumeUp
            "turn down the volume", "decrease volume", "volume down", "quieter" -> PhoneControlCommand.VolumeDown
            "open internet settings", "open wifi settings", "connectivity settings",
            "turn on wifi", "turn off wifi", "enable wifi", "disable wifi",
            "connect to wifi", "disconnect wifi" -> PhoneControlCommand.OpenInternetPanel
            "scroll down", "move down" -> PhoneControlCommand.ScrollForward
            "scroll up", "move up" -> PhoneControlCommand.ScrollBackward
            else -> when {
                text.startsWith("open ") && isSingleTarget(cleaned.substringAfter(' ', "").trim()) ->
                    PhoneControlCommand.OpenApp(cleaned.substringAfter(' ', "").trim())
                text.startsWith("launch ") && isSingleTarget(cleaned.substringAfter(' ', "").trim()) ->
                    PhoneControlCommand.OpenApp(cleaned.substringAfter(' ', "").trim())
                text.startsWith("tap ") && text.length > 4 ->
                    PhoneControlCommand.TapText(cleaned.substringAfter(' ', "").trim())
                text.startsWith("click ") && text.length > 6 ->
                    PhoneControlCommand.TapText(cleaned.substringAfter(' ', "").trim())
                text.startsWith("type ") && text.length > 5 ->
                    PhoneControlCommand.TypeText(cleaned.substringAfter(' ', "").trim())
                else -> null
            }
        }
    }

    private fun isSingleTarget(value: String): Boolean =
        value.isNotBlank() && value.length <= 60 &&
            !Regex("\\b(and then|then|and|after that|before)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(value)

    private fun normalizeSpokenCommand(raw: String): String {
        var value = raw.trim().trimEnd('.', '!', '?', ',').trim()
        val prefixes = listOf(
            Regex("^يا\\s+(?:فرايداي|فرايدي)[, :]?\\s*", RegexOption.IGNORE_CASE),
            Regex("^(?:فرايداي|فرايدي)[, :]?\\s*", RegexOption.IGNORE_CASE),
            Regex("^hey\\s+friday[, :]?\\s*", RegexOption.IGNORE_CASE),
            Regex("^friday[, :]?\\s*", RegexOption.IGNORE_CASE),
            Regex("^would\\s+you\\s+please\\s+", RegexOption.IGNORE_CASE),
            Regex("^(please|can\\s+you|could\\s+you|would\\s+you)\\s+", RegexOption.IGNORE_CASE)
        )
        var changed = true
        while (changed) {
            changed = false
            for (prefix in prefixes) {
                val next = value.replaceFirst(prefix, "")
                if (next != value) {
                    value = next.trim()
                    changed = true
                    break
                }
            }
        }
        return value.replace(Regex("\\s+please$", RegexOption.IGNORE_CASE), "")
            .trim().trimEnd('.', '!', '?', ',').trim()
    }

    fun describe(command: PhoneControlCommand): String = when (command) {
        PhoneControlCommand.Back -> "Navigate back one screen"
        PhoneControlCommand.Home -> "Return to the Android home screen"
        PhoneControlCommand.Recents -> "Show recent applications"
        PhoneControlCommand.Notifications -> "Open the notification shade"
        PhoneControlCommand.QuickSettings -> "Open Android quick settings"
        PhoneControlCommand.OpenSettings -> "Open Android system settings"
        PhoneControlCommand.PlayMedia -> "Start playback in the active media session"
        PhoneControlCommand.PauseMedia -> "Pause playback in the active media session"
        PhoneControlCommand.NextTrack -> "Skip to the next media track"
        PhoneControlCommand.PreviousTrack -> "Skip to the previous media track"
        PhoneControlCommand.VolumeUp -> "Increase media volume by one step"
        PhoneControlCommand.VolumeDown -> "Decrease media volume by one step"
        is PhoneControlCommand.SetMediaVolume -> "Set media volume to ${command.percent} percent"
        is PhoneControlCommand.SetBrightness -> "Set screen brightness to ${command.percent} percent"
        PhoneControlCommand.OpenInternetPanel -> "Open Android internet connectivity panel"
        PhoneControlCommand.ScrollForward -> "Scroll the active screen down"
        PhoneControlCommand.ScrollBackward -> "Scroll the active screen up"
        is PhoneControlCommand.OpenApp -> "Launch installed application named ${command.label}"
        is PhoneControlCommand.TapText -> "Tap an accessible UI control labelled ${command.text}"
        is PhoneControlCommand.TypeText -> "Enter user-provided text into the currently focused field"
    }

    private val consequentialTarget = Regex(
        "\\b(delete|remove|erase|send|submit|pay|purchase|buy|transfer|call|post|publish|confirm|allow|grant|install|uninstall|reset|sign out|log out|share|forward|block|report|wipe|factory reset)\\b",
        RegexOption.IGNORE_CASE
    )
    private val sensitiveText = Regex(
        "\\b(password|passcode|pin|cvv|private key|recovery phrase|one.time code|otp|card number|bank account)\\b",
        RegexOption.IGNORE_CASE
    )

    fun risk(command: PhoneControlCommand): String = when (command) {
        is PhoneControlCommand.TapText ->
            if (consequentialTarget.containsMatchIn(command.text)) "high" else "medium"
        is PhoneControlCommand.TypeText ->
            if (sensitiveText.containsMatchIn(command.text)) "high" else "medium"
        else -> "low"
    }

    fun requiresExplicitConfirmation(command: PhoneControlCommand): Boolean =
        risk(command) == "high"

    fun dispatch(utterance: String): PhoneControlResult? {
        val command = parse(utterance) ?: return null
        return dispatch(command)
    }

    fun dispatch(command: PhoneControlCommand): PhoneControlResult =
        FridayAccessibilityService.execute(command)
}
