package com.jarvis.app.voice

import com.jarvis.app.model.FinishReason
import com.jarvis.app.model.GenerateRequest
import com.jarvis.app.model.GenerateResult
import com.jarvis.app.model.Message
import com.jarvis.app.model.MessageRole
import com.jarvis.app.model.ModelManager
import org.json.JSONObject

/**
 * Lets Friday's existing intelligence translate natural speech + the current
 * accessible screen into one bounded Android body action.
 *
 * This is not a second model. Production uses the same ModelManager that
 * answers ordinary conversation. The planner emits a typed intent only; Jev
 * evaluates the proposed action and Android's own service executes it.
 */
class PhoneActionPlanner(
    private val localModelReady: suspend () -> Boolean = { true },
    private val localSkills: LocalPhoneActionSkillRegistry = LocalPhoneActionSkillRegistry.default(),
    private val complete: suspend (GenerateRequest) -> GenerateResult
) {
    constructor(modelManager: ModelManager) : this(
        localModelReady = {
            modelManager.activeProvider.value.providerType ==
                com.jarvis.app.env.ModelProviderType.LLAMA_CPP
        },
        complete = { request -> modelManager.generate(request) }
    )

    sealed interface Result {
        data class Action(val command: PhoneControlCommand) : Result
        data class Clarification(val question: String) : Result
        data object NotPhoneAction : Result
        data class Unavailable(val reason: String) : Result
    }

    /** Used by the body router so injected skills can claim their own language. */
    fun shouldHandle(utterance: String): Boolean =
        DEVICE_INTENT.containsMatchIn(utterance) || localSkills.mayMatch(utterance)

    suspend fun plan(utterance: String, screenSnapshot: String): Result {
        if (utterance.isBlank()) return Result.NotPhoneAction

        // First resolve supported body skills locally. This path does not load,
        // wake or call a model; execution still passes through Friday's policy
        // organ and the Android accessibility bridge.
        when (val local = localSkills.resolve(utterance)) {
            is LocalPhoneActionSkillRegistry.Resolution.Action -> return Result.Action(local.command)
            is LocalPhoneActionSkillRegistry.Resolution.Clarification ->
                return Result.Clarification(local.question)
            LocalPhoneActionSkillRegistry.Resolution.NotMatched -> Unit
        }

        if (!isLikelyDeviceRequest(utterance)) return Result.NotPhoneAction
        if (!localModelReady()) {
            return Result.Unavailable(
                "No installed model-free phone skill safely matches this request yet. Nothing was changed. No screen context was sent to a cloud model."
            )
        }

        val prompt = buildString {
            appendLine("SPOKEN REQUEST:")
            appendLine(utterance.take(1200))
            appendLine()
            appendLine("CURRENT ACCESSIBLE ANDROID SCREEN (local-only context):")
            appendLine(screenSnapshot.take(7000).ifBlank { "No accessible screen information is available." })
            appendLine()
            appendLine("Return exactly one JSON object, with no Markdown or prose.")
            appendLine("For a phone action: {\"kind\":\"action\",\"type\":\"...\",\"target\":\"...\"}")
            appendLine("For text entry: {\"kind\":\"action\",\"type\":\"type_text\",\"text\":\"exact requested text\"}")
            appendLine("For ambiguity or a missing target: {\"kind\":\"clarify\",\"question\":\"short question\"}")
            appendLine("If this is conversation rather than a phone action: {\"kind\":\"conversation\"}")
            appendLine()
            appendLine("Allowed types: back, home, recents, notifications, quick_settings, open_settings,")
            appendLine("open_app, tap_text, type_text, scroll_forward, scroll_backward, play, pause,")
            appendLine("next_track, previous_track, volume_up, volume_down, open_internet_panel.")
            appendLine("Never invent a visible screen target. tap_text must match a visible accessible label.")
            appendLine("type_text is allowed only when the current screen reports a focused editable field.")
            appendLine("Do not propose sending, deleting, purchasing, changing security, or other consequential")
            appendLine("actions as a single hidden step. Use one visible, explicit action at a time.")
        }

        val response = try {
            complete(
                GenerateRequest(
                    messages = listOf(
                        Message(MessageRole.SYSTEM, SYSTEM_PROMPT),
                        Message(MessageRole.USER, prompt)
                    ),
                    maxTokens = 256,
                    temperature = 0.1f
                )
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return Result.Unavailable("Friday could not prepare a phone action: ${failure.message.orEmpty()}")
        }

        if (response.finishReason == FinishReason.ERROR || response.content.isBlank()) {
            return Result.Unavailable("Friday's phone-action planner is unavailable.")
        }

        val json = extractObject(response.content)
            ?: return Result.Unavailable("Friday could not read a safe phone-action plan.")
        return runCatching {
            when (json.optString("kind")) {
                "conversation" -> Result.NotPhoneAction
                "clarify" -> Result.Clarification(
                    json.optString("question").trim().takeIf { it.isNotBlank() }
                        ?: "Which phone action do you mean?"
                )
                "action" -> parseAction(json, screenSnapshot)
                else -> Result.Unavailable("The phone-action plan had an unknown intent.")
            }
        }.getOrElse {
            Result.Unavailable("The phone-action plan was invalid: ${it.message.orEmpty()}")
        }
    }

    private fun parseAction(json: JSONObject, screen: String): Result {
        val type = json.optString("type").lowercase()
        val target = json.optString("target").trim()
        val command = when (type) {
            "back" -> PhoneControlCommand.Back
            "home" -> PhoneControlCommand.Home
            "recents" -> PhoneControlCommand.Recents
            "notifications" -> PhoneControlCommand.Notifications
            "quick_settings" -> PhoneControlCommand.QuickSettings
            "open_settings" -> PhoneControlCommand.OpenSettings
            "open_app" -> target.takeIf { it.isNotBlank() }?.let(PhoneControlCommand::OpenApp)
            "tap_text" -> {
                require(target.isNotBlank()) { "tap target is blank" }
                require(screenHasExactTarget(screen, target)) {
                    "the requested control is not an exact visible match in the current accessible screen"
                }
                PhoneControlCommand.TapText(target)
            }
            "type_text" -> {
                val text = json.optString("text")
                require(text.isNotBlank()) { "requested text is blank" }
                require(text.length <= 1000) { "text entry exceeds the 1000-character safety limit" }
                require(Regex("editable=true;focused=true").containsMatchIn(screen)) {
                    "there is no verified focused editable field"
                }
                PhoneControlCommand.TypeText(text)
            }
            "scroll_forward" -> PhoneControlCommand.ScrollForward
            "scroll_backward" -> PhoneControlCommand.ScrollBackward
            "play" -> PhoneControlCommand.PlayMedia
            "pause" -> PhoneControlCommand.PauseMedia
            "next_track" -> PhoneControlCommand.NextTrack
            "previous_track" -> PhoneControlCommand.PreviousTrack
            "volume_up" -> PhoneControlCommand.VolumeUp
            "volume_down" -> PhoneControlCommand.VolumeDown
            "open_internet_panel" -> PhoneControlCommand.OpenInternetPanel
            else -> null
        } ?: return Result.Unavailable("Friday cannot perform the proposed phone action yet.")
        return Result.Action(command)
    }

    private fun screenHasExactTarget(screen: String, target: String): Boolean {
        val requested = target.trim()
        if (requested.isBlank()) return false
        return screen.lineSequence()
            .filter { it.startsWith("node=") }
            .map { it.substringAfter("node=").substringBefore(";clickable=") }
            .flatMap { it.split(" | ").asSequence() }
            .map(String::trim)
            .any { value ->
                value.equals(requested, ignoreCase = true) ||
                    value.endsWith("/$requested", ignoreCase = true)
            }
    }

    private fun extractObject(text: String): JSONObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }

    companion object {
        private val DEVICE_INTENT = Regex(
            "\\b(open|launch|tap|click|press|select|choose|scroll|swipe|go to|navigate|switch|turn on|turn off|enable|disable|increase|decrease|lower|raise|volume|brightness|wifi|wi-fi|bluetooth|settings|notification|quick settings|play|pause|skip|next track|previous track|type|enter text|write|send|reply|call|search in|find in)\\b",
            RegexOption.IGNORE_CASE
        )

        fun isLikelyDeviceRequest(utterance: String): Boolean =
            DEVICE_INTENT.containsMatchIn(utterance) ||
                LocalPhoneActionSkillRegistry.default().mayMatch(utterance)
        
        private const val SYSTEM_PROMPT = """
            You are Friday's phone-action planning faculty. Friday is the intelligence
            and owner of the device-control body. Translate one spoken request into
            one explicit, bounded Android action using only the supplied accessible
            screen evidence. You do not execute actions and you do not grant Android
            permissions. Never invent targets or claim an action succeeded. If the
            request is ambiguous, ask one concise clarification. If it is ordinary
            conversation, return kind=conversation. Output valid JSON only.
        """
    }
}
