package io.homeassistant.companion.android.stevora

import org.json.JSONObject

/**
 * Strictly informational STEVORA OS discovery contract.
 *
 * This parser does not authenticate, request permissions or activate device commands.
 * It rejects unversioned/malformed responses and does not infer missing capabilities.
 */
internal data class StevoraOsDiscovery(
    val apiVersion: Int,
    val installationId: String,
    val capabilities: Set<String>,
    val homePanelPath: String,
) {
    fun supports(capability: String): Boolean = capability in capabilities

    companion object {
        private val allowedCapabilities = setOf(
            "rooms", "devices", "manifest", "translations", "permissions",
            "home_panel", "voice", "providers", "automations", "presence",
        )

        fun parse(json: String): StevoraOsDiscovery? = try {
            val root = JSONObject(json)
            val apiVersion = root.optInt("api_version", -1)
            val installationId = root.optString("installation_id").trim()
            val array = root.optJSONArray("capabilities")
            if (apiVersion != 1 || installationId.isBlank() || array == null) {
                null
            } else {
                val capabilities = (0 until array.length())
                    .mapNotNull { index -> array.optString(index).takeIf { it in allowedCapabilities } }
                    .toSet()
                StevoraOsDiscovery(
                    apiVersion = apiVersion,
                    installationId = installationId,
                    capabilities = capabilities,
                    homePanelPath = StevoraConfiguration.panelPathOrDefault(
                        root.optString("home_panel_path").takeIf { it.isNotBlank() },
                    ),
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
