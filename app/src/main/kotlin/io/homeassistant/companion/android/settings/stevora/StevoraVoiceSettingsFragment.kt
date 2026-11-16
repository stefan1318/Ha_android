package io.homeassistant.companion.android.settings.stevora

import android.os.Bundle
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
            title = "STEVORA Voice / Глас"
        }
        root.addPreference(section)

        section.addPreference(Preference(requireContext()).apply {
            title = "Hey Stevora"
            summary = if (StevoraConfiguration.WAKE_WORD_MODEL_AVAILABLE) {
                "Wake-word model ready / Моделът е наличен"
            } else {
                "Wake-word model pending / Очаква се гласов модел. Assist остава достъпен ръчно."
            }
            isSelectable = false
        })

        section.addPreference(SwitchPreferenceCompat(requireContext()).apply {
            key = "stevora_voice_enabled_ui"
            title = "Voice activation / Гласово активиране"
            summary = "Available after model integration / Достъпно след интегриране на модел"
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
            title = "Sensitivity / Чувствителност"
            summary = "Saved locally per app installation / Запазва се локално"
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
            title = "Personal voice calibration / Персонално калибриране"
            summary = "Requires a working model. This screen does not train or record a voice model."
            isEnabled = false
        })

        section.addPreference(Preference(requireContext()).apply {
            title = "Privacy / Поверителност"
            summary = "No voice samples are collected or uploaded by these settings."
            isSelectable = false
        })
    }
}
