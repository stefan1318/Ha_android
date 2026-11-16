package io.homeassistant.companion.android.stevora

/**
 * Product defaults. Server addresses and authentication remain managed by
 * Home Assistant Companion and are never hard-coded in this fork.
 */
internal object StevoraConfiguration {
    const val HOME_PANEL_PATH = "/stevora"
    const val WAKE_PHRASE = "Hey Stevora"

    // Hey Stevora is offered only when a real, validated model is packaged.
    const val WAKE_WORD_MODEL_NAME = "Hey Stevora"

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
