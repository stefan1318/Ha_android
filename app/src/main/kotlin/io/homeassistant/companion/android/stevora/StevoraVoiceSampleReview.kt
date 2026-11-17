package io.homeassistant.companion.android.stevora

import android.content.Context

/**
 * Per-device, per-speaker human review of enrollment clips.
 * A review approval is NOT biometric verification or proof of the wake phrase.
 */
internal class StevoraVoiceSampleReview(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "stevora_voice_sample_review", Context.MODE_PRIVATE,
    )

    private fun key(profileId: String, fileName: String): String {
        require(Regex("[a-f0-9-]{36}").matches(profileId))
        require(Regex("sample_[0-9]+\\.m4a").matches(fileName))
        return "$profileId/$fileName"
    }

    fun isApproved(profileId: String, fileName: String): Boolean =
        preferences.getBoolean(key(profileId, fileName), false)

    fun setApproved(profileId: String, fileName: String, approved: Boolean) {
        preferences.edit().putBoolean(key(profileId, fileName), approved).apply()
    }

    fun remove(profileId: String, fileName: String) {
        preferences.edit().remove(key(profileId, fileName)).apply()
    }

    fun removeProfile(profileId: String) {
        require(Regex("[a-f0-9-]{36}").matches(profileId))
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith("$profileId/") }.forEach(editor::remove)
        editor.apply()
    }
}
