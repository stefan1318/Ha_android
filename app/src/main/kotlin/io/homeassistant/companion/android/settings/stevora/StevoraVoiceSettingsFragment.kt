package io.homeassistant.companion.android.settings.stevora

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.io.File
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
 * Opt-in local voice recordings. Does not claim to train a wake-word model.
 */
@AndroidEntryPoint
class StevoraVoiceSettingsFragment : PreferenceFragmentCompat() {
    @Inject lateinit var assistConfigManager: AssistConfigManager
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private lateinit var progress: Preference
    private lateinit var record: Preference
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startSample() else progress.summary = "Microphone permission denied"
    }
    private fun sampleDir() = File(requireContext().filesDir, "stevora/voice-samples").apply { mkdirs() }
    private fun sampleFiles() = sampleDir().listFiles()?.filter { it.name.startsWith("sample_") && it.extension == "m4a" }?.sortedBy { it.name }.orEmpty()
    private fun refreshSamples() {
        progress.summary = "${sampleFiles().size}/20 samples stored locally · Training not available"
        record.title = if (recorder == null) "Record voice sample" else "Stop and save recording"
        record.isEnabled = recorder != null || sampleFiles().size < 20
    }
    private fun startSample() {
        if (recorder != null || sampleFiles().size >= 20) return
        val index = (1..20).firstOrNull { !File(sampleDir(), "sample_%02d.m4a".format(it)).exists() } ?: return
        val file = File(sampleDir(), "sample_%02d.m4a".format(index))
        var instance: MediaRecorder? = null
        try {
            instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(requireContext()) else MediaRecorder()
            instance.setAudioSource(MediaRecorder.AudioSource.MIC)
            instance.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            instance.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            instance.setOutputFile(file.absolutePath)
            instance.prepare()
            instance.start()
            recorder = instance
            recordingFile = file
            refreshSamples()
        } catch (_: Exception) {
            instance?.release()
            file.delete()
            progress.summary = "Microphone recording failed"
        }
    }
    private fun stopSample() {
        val active = recorder ?: return
        recorder = null
        try { active.stop() } catch (_: RuntimeException) { recordingFile?.delete() }
        active.release()
        recordingFile = null
        refreshSamples()
    }
    override fun onStop() {
        if (recorder != null) stopSample()
        super.onStop()
    }
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

        val training = PreferenceCategory(requireContext()).apply { title = "Hey Stevora · Voice Training" }
        root.addPreference(training)
        progress = Preference(requireContext()).apply { title = "Local recordings"; isSelectable = false }
        training.addPreference(progress)
        record = Preference(requireContext()).apply {
            summary = "Tap to start or stop recording. Audio remains on this device."
            setOnPreferenceClickListener {
                if (recorder != null) stopSample()
                else if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startSample()
                else permission.launch(Manifest.permission.RECORD_AUDIO)
                true
            }
        }
        training.addPreference(record)
        training.addPreference(Preference(requireContext()).apply {
            title = "Delete latest sample"
            setOnPreferenceClickListener {
                if (recorder == null) sampleFiles().lastOrNull()?.delete()
                refreshSamples()
                true
            }
        })
        training.addPreference(Preference(requireContext()).apply {
            title = "Train and activate Hey Stevora"
            summary = "Unavailable until a real on-device training engine is integrated."
            isEnabled = false
        })
        refreshSamples()
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
