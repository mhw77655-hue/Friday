package com.jarvis.app.voice

/**
 * Small deterministic action vocabulary for phone navigation. Complex requests
 * remain with Friday's cognitive pipeline; this parser only claims commands
 * whose intent is unambiguous.
 */
object PhoneCommandInterpreter {
    fun parse(utterance: String): PhoneControlCommand? {
        val text = utterance.trim().lowercase()
        if (text.isBlank()) return null
        return when (text) {
            "go back", "back", "previous screen" -> PhoneControlCommand.Back
            "go home", "home screen", "open home" -> PhoneControlCommand.Home
            "show recent apps", "open recents", "recent apps" -> PhoneControlCommand.Recents
            "show notifications", "open notifications", "notifications" -> PhoneControlCommand.Notifications
            "open quick settings", "show quick settings", "quick settings" -> PhoneControlCommand.QuickSettings
            "scroll down", "move down" -> PhoneControlCommand.ScrollForward
            "scroll up", "move up" -> PhoneControlCommand.ScrollBackward
            else -> when {
                text.startsWith("open ") && text.length > 5 ->
                    PhoneControlCommand.OpenApp(utterance.trim().substringAfter(' ', "").trim())
                text.startsWith("launch ") && text.length > 7 ->
                    PhoneControlCommand.OpenApp(utterance.trim().substringAfter(' ', "").trim())
                text.startsWith("tap ") && text.length > 4 ->
                    PhoneControlCommand.TapText(utterance.trim().substringAfter(' ', "").trim())
                text.startsWith("click ") && text.length > 6 ->
                    PhoneControlCommand.TapText(utterance.trim().substringAfter(' ', "").trim())
                text.startsWith("type ") && text.length > 5 ->
                    PhoneControlCommand.TypeText(utterance.trim().substringAfter(' ', "").trim())
                else -> null
            }
        }
    }

    fun dispatch(utterance: String): PhoneControlResult? {
        val command = parse(utterance) ?: return null
        return FridayAccessibilityService.execute(command)
    }
}
