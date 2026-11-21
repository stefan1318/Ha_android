package com.stevora.integration

import org.json.JSONObject

/** Adapter for an already authenticated Home Assistant WebSocket transport.
 * The caller must send the request using its existing HA OAuth session.
 */
data class StevoraRoom(val id: String, val name: String)
data class StevoraManifest(
    val schemaVersion: String,
    val installationId: String,
    val language: String,
    val rooms: List<StevoraRoom>,
    val mappings: Map<String, List<String>>,
    val revision: Int,
)

object StevoraManifestSync {
    const val REQUEST_TYPE = "stevora/os/v1/manifest"

    fun request(id: Int, language: String = java.util.Locale.getDefault().language): String =
        JSONObject().put("id", id).put("type", REQUEST_TYPE)
            .put("language", if (language == "bg") "bg" else "en").toString()

    fun parseResponse(payload: String): StevoraManifest? {
        val message = JSONObject(payload)
        if (!message.optBoolean("success", false)) return null
        val result = message.optJSONObject("result") ?: return null
        if (result.optString("schema_version") != "1.0") return null
        val rooms = result.optJSONArray("rooms")
        val roomList = (0 until (rooms?.length() ?: 0)).mapNotNull { i ->
            rooms?.optJSONObject(i)?.let { StevoraRoom(it.optString("id"), it.optString("name")) }
        }
        val mappings = result.optJSONObject("mappings")
        val mappingValues = mutableMapOf<String, List<String>>()
        if (mappings != null) {
            val keys = mappings.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val values = mappings.optJSONArray(key)
                mappingValues[key] = (0 until (values?.length() ?: 0)).map { values!!.optString(it) }
            }
        }
        return StevoraManifest(
            schemaVersion = result.getString("schema_version"),
            installationId = result.getString("installation_id"),
            language = result.optString("preferred_language", "en"),
            rooms = roomList,
            mappings = mappingValues,
            revision = result.optInt("revision", 1),
        )
    }
}
