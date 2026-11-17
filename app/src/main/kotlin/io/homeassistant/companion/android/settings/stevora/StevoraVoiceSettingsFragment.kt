package io.homeassistant.companion.android.settings.stevora

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.widget.Toast
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import io.homeassistant.companion.android.stevora.StevoraVoiceProfiles
import androidx.preference.SwitchPreferenceCompat
import io.homeassistant.companion.android.stevora.StevoraVoiceEnrollment
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
import io.homeassistant.companion.android.stevora.StevoraConfiguration
import io.homeassistant.companion.android.stevora.StevoraVoicePreferences

/**
 * Safe opt-in preparation for Hey Stevora. Does not request microphone access,
 * record voice samples or pretend that a trained wake-word model is present.
 */
@AndroidEntryPoint
class StevoraVoiceSettingsFragment : PreferenceFragmentCompat() {
    @Inject lateinit var assistConfigManager: AssistConfigManager
    private var enrollment: StevoraVoiceEnrollment? = null
    private var profiles: StevoraVoiceProfiles? = null
    private var speakerPreference: Preference? = null
    private var learningPreference: SwitchPreferenceCompat? = null
    private var capturePreference: Preference? = null
    private var enrollmentStatus: Preference? = null
    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startEnrollmentRecording()
        else if (isAdded) Toast.makeText(requireContext(), "Microphone permission required.", Toast.LENGTH_LONG).show()
    }

    private fun refreshEnrollment() {
        val capture = enrollment ?: return
        val selected = profiles?.selected()
        capture.profileId = selected?.id
        speakerPreference?.summary = selected?.name ?: "No profile selected"
        learningPreference?.isEnabled = selected != null
        learningPreference?.isChecked = selected?.learningEnabled ?: false
        capturePreference?.isEnabled = selected != null
        enrollmentStatus?.summary = "${capture.sampleCount} / ${StevoraVoiceEnrollment.MAX_SAMPLES} local samples. Voice ID recognition is not yet trained."
        capturePreference?.title = if (capture.isRecording) "Stop and save sample" else "Record Hey Stevora sample"
        capturePreference?.summary = if (capture.isRecording) "Tap to stop" else "Tap to start recording; stored privately on this device."
    }

    private fun startEnrollmentRecording() {
        try {
            enrollment?.start()
            refreshEnrollment()
        } catch (error: Exception) {
            if (isAdded) Toast.makeText(requireContext(), "Unable to record: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStop() {
        enrollment?.stop(save = false)
        refreshEnrollment()
        super.onStop()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val prefs = StevoraVoicePreferences(requireContext())
        enrollment = StevoraVoiceEnrollment(requireContext().applicationContext)
        profiles = StevoraVoiceProfiles(requireContext().applicationContext)
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

        section.addPreference(Preference(requireContext()).apply {
            title = getString(io.homeassistant.companion.android.R.string.stevora_voice_activation)
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

        val enrollmentSection = PreferenceCategory(requireContext()).apply {
            title = "STEVORA Voice ID — test enrollment"
        }
        root.addPreference(enrollmentSection)
        speakerPreference = Preference(requireContext()).apply {
            title = "Active speaker profile"
            setOnPreferenceClickListener {
                val all = profiles?.all().orEmpty()
                if (all.isEmpty()) {
                    Toast.makeText(requireContext(), "Add a speaker profile first.", Toast.LENGTH_SHORT).show()
                } else {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Choose speaker")
                        .setItems(all.map { it.name }.toTypedArray()) { _, index ->
                            enrollment?.stop(save = false)
                            profiles?.select(all[index].id)
                            refreshEnrollment()
                        }.show()
                }
                true
            }
        }
        enrollmentSection.addPreference(speakerPreference!!)
        enrollmentSection.addPreference(Preference(requireContext()).apply {
            title = "Add speaker (shared wall panel)"
            summary = "Each person has separate recordings and learning consent."
            setOnPreferenceClickListener {
                val input = EditText(requireContext())
                input.hint = "Speaker name"
                AlertDialog.Builder(requireContext())
                    .setTitle("Add voice profile")
                    .setView(input)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Add") { _, _ ->
                        try {
                            val profile = profiles?.add(input.text.toString()) ?: return@setPositiveButton
                            enrollment?.stop(save = false)
                            profiles?.select(profile.id)
                            refreshEnrollment()
                        } catch (error: IllegalArgumentException) {
                            Toast.makeText(requireContext(), error.message, Toast.LENGTH_LONG).show()
                        }
                    }.show()
                true
            }
        })
        learningPreference = SwitchPreferenceCompat(requireContext()).apply {
            title = "Allow future confirmed self-learning"
            summary = "Consent only: automatic adaptation is NOT implemented or active."
            isPersistent = false
            setOnPreferenceChangeListener { _, value ->
                val id = profiles?.selectedId() ?: return@setOnPreferenceChangeListener false
                profiles?.setLearningConsent(id, value as Boolean)
                true
            }
        }
        enrollmentSection.addPreference(learningPreference!!)
        enrollmentSection.addPreference(Preference(requireContext()).apply {
            title = "Remove active speaker profile"
            summary = "Deletes that speaker's recordings and local profile."
            setOnPreferenceClickListener {
                val selected = profiles?.selected() ?: return@setOnPreferenceClickListener true
                AlertDialog.Builder(requireContext())
                    .setTitle("Remove ${selected.name}?")
                    .setMessage("All local samples for this speaker will be deleted.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        enrollment?.stop(save = false)
                        enrollment?.profileId = selected.id
                        enrollment?.deleteAll()
                        profiles?.remove(selected.id)
                        refreshEnrollment()
                    }.show()
                true
            }
        })
        enrollmentStatus = Preference(requireContext()).apply {
            title = "Local voice samples"
            isSelectable = false
        }
        enrollmentSection.addPreference(enrollmentStatus!!)
        capturePreference = Preference(requireContext()).apply {
            setOnPreferenceClickListener {
                val capture = enrollment ?: return@setOnPreferenceClickListener true
                if (capture.isRecording) {
                    val saved = capture.stop()
                    Toast.makeText(requireContext(), if (saved) "Sample saved." else "Sample too short; discarded.", Toast.LENGTH_SHORT).show()
                    refreshEnrollment()
                } else if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startEnrollmentRecording()
                } else {
                    microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }
                true
            }
        }
        enrollmentSection.addPreference(capturePreference!!)
        enrollmentSection.addPreference(Preference(requireContext()).apply {
            title = "Delete selected speaker samples"
            summary = "Permanently erase locally recorded samples."
            setOnPreferenceClickListener {
                enrollment?.deleteAll()
                refreshEnrollment()
                Toast.makeText(requireContext(), "Samples deleted.", Toast.LENGTH_SHORT).show()
                true
            }
        })
        enrollmentSection.addPreference(Preference(requireContext()).apply {
            title = "Prototype limitations"
            summary = "Multiple profiles and learning consent are available. Speaker matching and automatic learning are NOT implemented."
            isSelectable = false
        })
        refreshEnrollment()

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
