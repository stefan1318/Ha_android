package io.homeassistant.companion.android.stevora

import android.content.Context

/**
 * Local preferences for a future per-user voice setup flow.
 *
 * Calibration does NOT train a new TFLite model. Voice recording is not
 * performed by this class, and no audio is transmitted or persisted.
 */
internal class StevoraVoicePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "stevora_voice_preferences",
        Context.MODE_PRIVATE,
    )

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(
                KEY_ENABLED,
                value,
            ).apply()
        }

    var sensitivity: Float
        get() = prefs.getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY).coerceIn(0.0f, 1.0f)
        set(value) {
            require(value.isFinite()) { "Sensitivity must be finite" }
            prefs.edit().putFloat(KEY_SENSITIVITY, value.coerceIn(0.0f, 1.0f)).apply()
        }

    var setupCompleted: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETED, value).apply()

    companion object {
        const val DEFAULT_SENSITIVITY = 0.5f
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_SETUP_COMPLETED = "setup_completed"
    }
}
