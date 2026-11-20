package com.jarvis.app.voice

import android.content.Context
import android.content.SharedPreferences
import com.jarvis.app.voice.provider.VoiceQuality
import org.json.JSONArray
import org.json.JSONObject

/**
 * R2 — persists [VoiceProfile]s in the app process (SharedPreferences).
 *
 * The profile is a capability-level identity, so it lives app-side; the
 * habitat only ever receives `(language, voiceId, options)` per request. JSON
 * keeps the store trivial and testable without any Android DI.
 */
/**
 * Migrate the built-in Friday voice away from legacy Android system TTS.
 * Old installs may have persisted a null/"system" provider before the neural
 * habitat became the production voice path. Preserve explicit neural choices
 * and reference-voice selections; never silently revive Google/system speech.
 */
internal fun migrateLegacyFridayVoiceProfile(profile: VoiceProfile): VoiceProfile {
    if (profile.id != DefaultJarvisVoiceProfile.id) return profile
    val hasReference = !profile.referenceAudioPath.isNullOrBlank()
    val provider = profile.preferredProvider?.lowercase()
    // The former built-in Piper/Amy default was a speed-first placeholder.
    // Move that uncustomized default to the higher-quality Kokoro voice while
    // preserving explicit Piper voice choices and owner reference recordings.
    val needsMigration = provider.isNullOrBlank() || provider == "system" ||
        (provider == "piper" && profile.preferredVoice.isNullOrBlank())
    val migratedProvider = when {
        needsMigration && hasReference -> "pocket"
        needsMigration -> "sherpa"
        else -> provider
    }
    val migratedVoice = when {
        needsMigration && hasReference -> "reference"
        needsMigration -> "speaker_2"
        else -> profile.preferredVoice
    }
    val neuralFallbacks = profile.fallbackProviders.filterNot { it.equals("system", ignoreCase = true) }
        .ifEmpty {
            if (migratedProvider == "sherpa") listOf("piper", "sherpa-arabic") else listOf("sherpa")
        }
    return profile.copy(
        preferredProvider = migratedProvider,
        preferredVoice = migratedVoice,
        fallbackProviders = neuralFallbacks
    )
}

class VoiceProfileStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("voice_profiles", Context.MODE_PRIVATE)

    fun all(): List<VoiceProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return listOf(DefaultJarvisVoiceProfile)
        val arr = JSONArray(raw)
        val profiles = (0 until arr.length()).mapNotNull { i ->
            decode(arr.getJSONObject(i))
        }.map { profile ->
            val renamed = if (profile.id == DefaultJarvisVoiceProfile.id &&
                profile.name.equals("Jarvis", ignoreCase = true)
            ) profile.copy(name = DefaultJarvisVoiceProfile.name) else profile
            migrateLegacyFridayVoiceProfile(renamed)
        }
        return profiles.ifEmpty { listOf(DefaultJarvisVoiceProfile) }
    }

    fun get(id: String): VoiceProfile? = all().firstOrNull { it.id == id }

    fun save(profile: VoiceProfile) {
        val updated = (all().filterNot { it.id == profile.id } + profile)
        prefs.edit().putString(KEY_PROFILES, JSONArray(updated.map { encode(it) }).toString()).apply()
    }

    fun delete(id: String) {
        val updated = all().filterNot { it.id == id }
        if (updated.isEmpty()) {
            prefs.edit().remove(KEY_PROFILES).apply()
        } else {
            prefs.edit().putString(KEY_PROFILES, JSONArray(updated.map { encode(it) }).toString()).apply()
        }
    }

    private fun encode(p: VoiceProfile): JSONObject = JSONObject()
        .put("id", p.id)
        .put("name", p.name)
        .put("language", p.language)
        .put("preferredProvider", p.preferredProvider ?: JSONObject.NULL)
        .put("preferredVoice", p.preferredVoice ?: JSONObject.NULL)
        .put("speechRate", p.speechRate.toDouble())
        .put("pitch", p.pitch.toDouble())
        .put("pronunciation", JSONObject(p.pronunciation))
        .put("fallbackProviders", JSONArray(p.fallbackProviders))
        .put("quality", p.qualityPreference.name)
        .put("resource", p.resourcePreference.name)
        .put("referenceAudioPath", p.referenceAudioPath ?: JSONObject.NULL)
        .put("referenceTranscript", p.referenceTranscript ?: JSONObject.NULL)

    private fun decode(o: JSONObject): VoiceProfile? = runCatching {
        val pron = mutableMapOf<String, String>()
        val pronObj = o.optJSONObject("pronunciation")
        if (pronObj != null) {
            val keys = pronObj.keys()
            while (keys.hasNext()) {
                val k = keys.next(); pron[k] = pronObj.optString(k)
            }
        }
        val fallbacks = mutableListOf<String>()
        val fb = o.optJSONArray("fallbackProviders")
        if (fb != null) for (i in 0 until fb.length()) fallbacks.add(fb.optString(i))
        VoiceProfile(
            id = o.getString("id"),
            name = o.optString("name", o.getString("id")),
            language = o.optString("language", "en"),
            preferredProvider = o.optString("preferredProvider").takeIf { it.isNotBlank() && it != "null" },
            preferredVoice = o.optString("preferredVoice").takeIf { it.isNotBlank() && it != "null" },
            speechRate = o.optDouble("speechRate", 1.0).toFloat().takeIf { it > 0 } ?: 1.0f,
            pitch = o.optDouble("pitch", 1.0).toFloat().takeIf { it > 0 } ?: 1.0f,
            pronunciation = pron,
            fallbackProviders = fallbacks,
            qualityPreference = runCatching { VoiceQuality.valueOf(o.optString("quality")) }
                .getOrDefault(VoiceQuality.HIGH),
            resourcePreference = runCatching { ResourcePreference.valueOf(o.optString("resource")) }
                .getOrDefault(ResourcePreference.BALANCED),
            referenceAudioPath = o.optString("referenceAudioPath").takeIf { it.isNotBlank() && it != "null" },
            referenceTranscript = o.optString("referenceTranscript").takeIf { it.isNotBlank() && it != "null" }
        )
    }.getOrNull()

    companion object {
        private const val KEY_PROFILES = "profiles_json"
    }
}
