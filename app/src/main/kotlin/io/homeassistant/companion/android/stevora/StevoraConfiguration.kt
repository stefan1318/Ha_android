package io.homeassistant.companion.android.stevora

/**
 * Product defaults. Server addresses and authentication remain managed by
 * Home Assistant Companion and are never hard-coded in this fork.
 */
internal object StevoraConfiguration {
    const val HOME_PANEL_PATH = "/stevora"
    const val WAKE_PHRASE = "Hey Stevora"

    /**
     * Wake-word recognition is unavailable until a validated TFLite model
     * is packaged and the microWakeWord runtime is connected to this setting.
     * Do not enable a fake listener.
     */
    const val WAKE_WORD_MODEL_AVAILABLE = false

    fun panelPathOrDefault(path: String?): String =
        path?.trim()?.takeIf { candidate ->
            candidate.startsWith("/") &&
                !candidate.startsWith("//") &&
                !candidate.contains('\\') &&
                !candidate.contains('?') &&
                !candidate.contains('#') &&
                !candidate.contains("://") &&
                candidate.split('/').none { it == ".." || it == "." }
        } ?: HOME_PANEL_PATH
}
