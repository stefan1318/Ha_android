package io.homeassistant.companion.android.stevora

internal enum class StevoraSettingsPage {
    Home, Account, Tools, Management, Rooms, Devices, Assistants, Automation, Climate, Connection, Voice, Privacy, Appearance;

    val parent: StevoraSettingsPage
        get() = when (this) {
            Rooms, Devices, Assistants, Automation, Connection -> Management
            Climate -> Tools
            Appearance -> Account
            else -> Home
        }

    fun accessible(verified: Boolean, admin: Boolean): Boolean = when {
        requiresAdmin -> verified && admin
        this == Home || this == Privacy -> true
        else -> verified
    }

    val requiresAdmin: Boolean get() = this == Management || parent == Management
}

internal data class StevoraChoice(val id: String, val name: String, val context: String = "") {
    companion object {
        fun matching(options: List<StevoraChoice>, query: String): List<StevoraChoice> {
            val terms = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
            if (terms.isEmpty()) return options
            return options.filter { item ->
                val label = "${item.name} ${item.id} ${item.context}"
                terms.all { label.contains(it, ignoreCase = true) }
            }
        }
    }
}
