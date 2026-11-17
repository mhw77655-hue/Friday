package com.jarvis.app.body

/**
 * Pure Android I/O transport transition table. [next] maps microphone,
 * recognizer and playback session events to transport lifecycle changes.
 * It is not a model of Friday's identity, cognition, personality or inner life.
 *
 * Meaning-making and development live in Friday's individual cognitive
 * substrate; this adapter only coordinates platform I/O and interruption.
 */
object BodyStateMachine {

    fun next(current: BodyState, event: BodyEvent): BodyState = when (event) {
        is BodyEvent.WakeDetected -> when (current) {
            BodyState.IDLE, BodyState.USER_ABSENT, BodyState.USER_PRESENT -> BodyState.WAKE
            // Hearing "jarvis" mid-turn is a barge-in: cut the speech and
            // take the new command.
            BodyState.SPEAKING, BodyState.RESPONDING, BodyState.THINKING,
            BodyState.RETRIEVING -> BodyState.INTERRUPTED
            else -> current
        }

        is BodyEvent.SpeechStart -> when (current) {
            BodyState.WAKE, BodyState.IDLE, BodyState.LISTENING -> BodyState.LISTENING
            BodyState.SPEAKING -> BodyState.INTERRUPTED
            else -> current
        }

        is BodyEvent.SpeechEnd -> when (current) {
            BodyState.LISTENING, BodyState.WAKE -> BodyState.HEARING
            BodyState.INTERRUPTED -> BodyState.HEARING
            else -> current
        }

        is BodyEvent.SttResult -> when (current) {
            // HEARING = platform-STT session closure from a captured utterance;
            // WAKE/LISTENING = the platform recognizer's live result;
            // INTERRUPTED = the follow-up command after a barge-in.
            BodyState.HEARING, BodyState.WAKE, BodyState.LISTENING, BodyState.INTERRUPTED ->
                if (event.text.isNotBlank()) BodyState.THINKING else BodyState.IDLE
            else -> current
        }

        is BodyEvent.UserInput -> when (current) {
            BodyState.HEARING, BodyState.WAKE, BodyState.IDLE -> BodyState.THINKING
            BodyState.SPEAKING -> BodyState.INTERRUPTED   // barge-in during speech
            BodyState.INTERRUPTED -> BodyState.THINKING
            else -> current
        }

        is BodyEvent.BargeIn -> when (current) {
            BodyState.SPEAKING, BodyState.RESPONDING, BodyState.THINKING -> BodyState.INTERRUPTED
            else -> current
        }

        is BodyEvent.MemoryRetrieved -> when (current) {
            BodyState.RETRIEVING -> BodyState.THINKING
            else -> current
        }

        is BodyEvent.ResponseGenerated -> when (current) {
            BodyState.THINKING -> BodyState.RESPONDING
            else -> current
        }

        is BodyEvent.ResponseChunk -> when (current) {
            BodyState.RESPONDING, BodyState.THINKING ->
                if (event.isFinal) BodyState.SPEAKING else BodyState.RESPONDING
            else -> current
        }

        is BodyEvent.TtsStart -> when (current) {
            BodyState.RESPONDING -> BodyState.SPEAKING
            else -> current
        }

        is BodyEvent.TtsEnd -> when (current) {
            BodyState.SPEAKING -> BodyState.IDLE
            else -> current
        }

        is BodyEvent.TeachWord -> when (current) {
            BodyState.IDLE, BodyState.WAKE, BodyState.LISTENING -> BodyState.LEARNING
            else -> current
        }

        is BodyEvent.WordLearned -> when (current) {
            BodyState.LEARNING -> BodyState.IDLE
            else -> current
        }

        is BodyEvent.UserPresenceChanged -> if (event.present) BodyState.USER_PRESENT else BodyState.USER_ABSENT

        is BodyEvent.ErrorOccurred -> BodyState.ERROR

        is BodyEvent.Reset -> BodyState.IDLE

        else -> current
    }
}
