package io.homeassistant.companion.android.settings.stevora

import android.os.Bundle
import android.content.Intent
import io.homeassistant.companion.android.settings.SettingsActivity
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import io.homeassistant.companion.android.stevora.StevoraConfiguration
import io.homeassistant.companion.android.stevora.StevoraVoicePreferences

/**
 * Safe opt-in preparation for Hey Stevora. Does not request microphone access,
 * record voice samples or pretend that a trained wake-word model is present.
 */
class StevoraVoiceSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val prefs = StevoraVoicePreferences(requireContext())
        val root = preferenceManager.createPreferenceScreen(requireContext())
        preferenceScreen = root

        val section = PreferenceCategory(requireContext()).apply {
            title = getString(io.homeassistant.companion.android.R.string.stevora_voice_title)
        }
        root.addPreference(section)

        section.addPreference(Preference(requireContext()).apply {
            title = getString(io.homeassistant.companion.android.R.string.stevora_voice_assist_setup)
            summary = getString(io.homeassistant.companion.android.R.string.stevora_voice_assist_setup_summary)
            setOnPreferenceClickListener {
                startActivity(SettingsActivity.newInstance(requireContext(), SettingsActivity.Deeplink.AssistSettings))
                true
            }
        })

        section.addPreference(Preference(requireContext()).apply {
            title = getString(io.homeassistant.companion.android.R.string.stevora_voice_server_info)
            summary = getString(io.homeassistant.companion.android.R.string.stevora_voice_server_info_summary)
            isSelectable = false
        })

        section.addPreference(Preference(requireContext()).apply {
            title = "Hey Stevora"
            summary = if (StevoraConfiguration.WAKE_WORD_MODEL_AVAILABLE) {
                getString(io.homeassistant.companion.android.R.string.stevora_voice_model_ready)
            } else {
                getString(io.homeassistant.companion.android.R.string.stevora_voice_model_pending)
            }
            isSelectable = false
        })

        section.addPreference(SwitchPreferenceCompat(requireContext()).apply {
            key = "stevora_voice_enabled_ui"
            title = getString(io.homeassistant.companion.android.R.string.stevora_voice_activation)
            summary = getString(io.homeassistant.companion.android.R.string.stevora_voice_activation_summary)
            isPersistent = false
            isEnabled = StevoraConfiguration.WAKE_WORD_MODEL_AVAILABLE
            isChecked = prefs.enabled
            setOnPreferenceChangeListener { _, value ->
                prefs.enabled = value as Boolean
                true
            }
        })

        section.addPreference(SeekBarPreference(requireContext()).apply {
            key = "stevora_voice_sensitivity_ui"
            title = getString(io.homeassistant.companion.android.R.string.stevora_voice_sensitivity)
            summary = getString(io.homeassistant.companion.android.R.string.stevora_voice_sensitivity_summary)
            min = 0
            max = 100
            showSeekBarValue = true
            isPersistent = false
            value = (prefs.sensitivity * 100).toInt()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.sensitivity = (newValue as Int) / 100f
                true
            }
        })

        section.addPreference(Preference(requireContext()).apply {
            title = getString(io.homeassistant.companion.android.R.string.stevora_voice_calibration)
            summary = getString(io.homeassistant.companion.android.R.string.stevora_voice_calibration_pending)
            isEnabled = false
        })

        section.addPreference(Preference(requireContext()).apply {
            title = getString(io.homeassistant.companion.android.R.string.stevora_voice_privacy)
            summary = getString(io.homeassistant.companion.android.R.string.stevora_voice_privacy_summary)
            isSelectable = false
        })
    }
}
