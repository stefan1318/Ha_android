package io.homeassistant.companion.android.stevora

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Multiple consenting speaker profiles on a shared device / wall panel.
 * Profile names and settings are local. Voice recognition is not implemented.
 */
internal class StevoraVoiceProfiles(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("stevora_voice_profiles", Context.MODE_PRIVATE)

    data class Profile(val id: String, val name: String, val learningEnabled: Boolean)

    fun all(): List<Profile> {
        val data = preferences.getString(KEY_PROFILES, "[]") ?: "[]"
        return try {
            val array = JSONArray(data)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                Profile(item.getString("id"), item.getString("name"), item.optBoolean("learningEnabled", false))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun add(name: String): Profile {
        val cleaned = name.trim()
        require(cleaned.isNotEmpty() && cleaned.length <= 40) { "Name must contain 1–40 characters" }
        require(all().size < MAX_PROFILES) { "Maximum number of profiles reached" }
        val profile = Profile(UUID.randomUUID().toString(), cleaned, false)
        persist(all() + profile)
        if (selectedId() == null) select(profile.id)
        return profile
    }

    fun selectedId(): String? = preferences.getString(KEY_SELECTED, null)

    fun selected(): Profile? = all().firstOrNull { it.id == selectedId() }

    fun select(id: String) {
        require(all().any { it.id == id }) { "Unknown speaker profile" }
        preferences.edit().putString(KEY_SELECTED, id).apply()
    }

    /** Consent toggle for future controlled learning; no automatic learning runs yet. */
    fun setLearningConsent(id: String, enabled: Boolean) {
        require(all().any { it.id == id }) { "Unknown speaker profile" }
        persist(all().map { if (it.id == id) it.copy(learningEnabled = enabled) else it })
    }

    fun remove(id: String) {
        val remaining = all().filterNot { it.id == id }
        persist(remaining)
        if (selectedId() == id) {
            preferences.edit().putString(KEY_SELECTED, remaining.firstOrNull()?.id).apply()
        }
    }

    private fun persist(profiles: List<Profile>) {
        val array = JSONArray()
        profiles.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("learningEnabled", it.learningEnabled))
        }
        preferences.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    companion object {
        const val MAX_PROFILES = 12
        private const val KEY_PROFILES = "profiles"
        private const val KEY_SELECTED = "selected"
    }
}
