package com.jarvis.app.turn

/**
 * Open session evidence attached to a Friday turn.
 *
 * This is descriptive context, not a cognitive authority or a Human Core
 * protocol. Missing observations remain null instead of being fabricated.
 * Channel values are open strings so a new transport does not require an
 * enum migration.
 */
data class TurnContext(
    val channel: String = Channel.ANDROID_APP,
    val localHour: Int? = null,
    val secondsSinceLastContact: Long? = null,
    val wasAway: Boolean? = null,
    val prosody: TurnProsody? = null
) {
    object Channel {
        const val ANDROID_APP = "android_app"
        /** Compatibility label for older app fixtures; the stored value remains an open string. */
        const val MOBILE_APP = ANDROID_APP
        const val TELEGRAM = "telegram"
    }
}

/** Optional observed speech features; values are hints rather than identity. */
data class TurnProsody(
    val paceFast: Boolean? = null,
    val loud: Boolean? = null,
    val marker: String? = null
)
