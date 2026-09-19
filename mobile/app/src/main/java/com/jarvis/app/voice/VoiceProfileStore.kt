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
class VoiceProfileStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("voice_profiles", Context.MODE_PRIVATE)

    fun all(): List<VoiceProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return listOf(DefaultJarvisVoiceProfile)
        val arr = JSONArray(raw)
        val profiles = (0 until arr.length()).mapNotNull { i ->
            decode(arr.getJSONObject(i))
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
                .getOrDefault(ResourcePreference.BALANCED)
        )
    }.getOrNull()

    companion object {
        private const val KEY_PROFILES = "profiles_json"
    }
}
