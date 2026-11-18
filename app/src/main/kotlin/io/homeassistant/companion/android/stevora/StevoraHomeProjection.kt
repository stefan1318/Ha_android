package io.homeassistant.companion.android.stevora

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** Read-only presentation model for the active server. Device actions must still use
 * Home Assistant's authenticated service APIs and permission checks.
 */
data class StevoraRoomUi(val id: String, val name: String)
data class StevoraHomeUi(
    val installationId: String,
    val revision: Int,
    val language: String,
    val rooms: List<StevoraRoomUi>,
    val entityMappings: Map<String, List<String>>,
)

/** Consumers can collect [home] to render rooms and available entity IDs.
 * Always scope by HA server ID; never mix homes or persist private entity maps.
 */
object StevoraHomeProjection {
    private val mutableHome = MutableStateFlow<Map<Int, StevoraHomeUi>>(emptyMap())
    val home: StateFlow<Map<Int, StevoraHomeUi>> = mutableHome

    fun apply(serverId: Int, json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            if (obj.optString("schema_version") != "1.0") return false
            val installationId = obj.optString("installation_id")
            if (installationId.isBlank()) return false
            val roomsJson = obj.optJSONArray("rooms")
            val rooms = buildList {
                for (i in 0 until (roomsJson?.length() ?: 0)) {
                    val room = roomsJson?.optJSONObject(i) ?: continue
                    val id = room.optString("id")
                    if (id.isNotBlank()) add(StevoraRoomUi(id, room.optString("name", id)))
                }
            }
            val mappingsJson = obj.optJSONObject("mappings")
            val mappings = mutableMapOf<String, List<String>>()
            if (mappingsJson != null) {
                val keys = mappingsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val entities = mappingsJson.optJSONArray(key) ?: continue
                    mappings[key] = (0 until entities.length()).mapNotNull { i ->
                        entities.optString(i).takeIf { it.contains('.') && !it.contains(' ') }
                    }
                }
            }
            val model = StevoraHomeUi(
                installationId, obj.optInt("revision", 1),
                obj.optString("preferred_language", "en"), rooms, mappings,
            )
            mutableHome.value = mutableHome.value + (serverId to model)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clear(serverId: Int) {
        mutableHome.value = mutableHome.value - serverId
    }
}
