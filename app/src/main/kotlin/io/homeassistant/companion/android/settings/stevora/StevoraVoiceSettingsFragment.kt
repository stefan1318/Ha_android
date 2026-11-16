package io.homeassistant.companion.android.settings.stevora

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.settings.assist.AssistConfigManager
import javax.inject.Inject
import kotlinx.coroutines.launch
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
@AndroidEntryPoint
class StevoraVoiceSettingsFragment : PreferenceFragmentCompat() {
    @Inject lateinit var assistConfigManager: AssistConfigManager
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

        val modelStatus = Preference(requireContext()).apply {
            title = "Hey Stevora"
            summary = getString(io.homeassistant.companion.android.R.string.stevora_voice_model_pending)
            isSelectable = false
        }
        section.addPreference(modelStatus)

        section.addPreference(SwitchPreferenceCompat(requireContext()).apply {
            key = "stevora_voice_enabled_ui"
            title = getString(io.homeassistant.companion.android.R.string.stevora_voice_activation)
            summary = getString(io.homeassistant.companion.android.R.string.stevora_voice_activation_summary)
            isPersistent = false
            isEnabled = false
            isChecked = prefs.enabled
            summary = getString(io.homeassistant.companion.android.R.string.stevora_voice_activation_summary)
            setOnPreferenceClickListener {
                startActivity(SettingsActivity.newInstance(requireContext(), SettingsActivity.Deeplink.AssistSettings))
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

        lifecycleScope.launch {
            val model = assistConfigManager.getAvailableModels().firstOrNull {
                it.wakeWord.equals(StevoraConfiguration.WAKE_WORD_MODEL_NAME, ignoreCase = true)
            }
            if (model != null) {
                modelStatus.summary = getString(io.homeassistant.companion.android.R.string.stevora_voice_model_ready)
            }
        }

        section.addPreference(Preference(requireContext()).apply {
            title = getString(io.homeassistant.companion.android.R.string.stevora_voice_privacy)
            summary = getString(io.homeassistant.companion.android.R.string.stevora_voice_privacy_summary)
            isSelectable = false
        })
    }
}
