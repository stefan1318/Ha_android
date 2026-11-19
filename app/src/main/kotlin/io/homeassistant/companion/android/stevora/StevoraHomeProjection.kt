package io.homeassistant.companion.android.stevora

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** Per-HA-server read-only discovery snapshot, never a grant of device permissions. */
data class StevoraRoomUi(val id: String, val name: String)
data class StevoraHomeUi(
    val installationId: String,
    val revision: Long,
    val language: String,
    val rooms: List<StevoraRoomUi>,
    val entityMappings: Map<String, List<String>>,
    val controllableEntityIds: Set<String>,
)

/** This cache is keyed by the active Companion server, not by a shared home profile. */
object StevoraHomeProjection {
    private val mutableHome = MutableStateFlow<Map<Int, StevoraHomeUi>>(emptyMap())
    val home: StateFlow<Map<Int, StevoraHomeUi>> = mutableHome

    @Synchronized
    fun apply(serverId: Int, json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            if (obj.optString("schema_version") != "1.0") return false
            val installationId = obj.optString("installation_id")
            if (installationId.isBlank()) return false
            // Server crc32 is unsigned (up to 4,294,967,295); Int would overflow.
            val revision = obj.optLong("revision", -1L)
            if (revision !in 0L..4294967295L) return false
            val roomsJson = obj.optJSONArray("rooms") ?: return false
            val mappingsJson = obj.optJSONObject("mappings") ?: return false
            val rooms = buildList {
                for (i in 0 until roomsJson.length()) {
                    val room = roomsJson.optJSONObject(i) ?: continue
                    val id = room.optString("id")
                    if (id.isNotBlank() && none { it.id == id }) {
                        add(StevoraRoomUi(id, room.optString("name", id)))
                    }
                }
            }
            val validRooms = rooms.map { it.id }.toSet()
            val mappings = validRooms.associateWith { id ->
                val entries = mappingsJson.optJSONArray(id)
                (0 until (entries?.length() ?: 0)).mapNotNull { i ->
                    entries?.optString(i)?.takeIf {
                        it.contains('.') && !it.contains(' ') &&
                            !it.contains("backlight", ignoreCase = true)
                    }
                }.distinct()
            }
            val visibleIds = mappings.values.flatten().toSet()
            // Older manifests without an explicit control list remain safely read-only.
            val controls = obj.optJSONArray("controllable_entities")
            val controllable = (0 until (controls?.length() ?: 0)).mapNotNull { i ->
                controls?.optString(i)?.takeIf { it in visibleIds }
            }.toSet()
            val model = StevoraHomeUi(
                installationId = installationId,
                revision = revision,
                language = obj.optString("preferred_language", "en"),
                rooms = rooms,
                entityMappings = mappings,
                controllableEntityIds = controllable,
            )
            mutableHome.value = mutableHome.value + (serverId to model)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun roomsFor(serverId: Int): List<StevoraRoomUi> = home.value[serverId]?.rooms.orEmpty()

    fun entitiesFor(serverId: Int, mappingKey: String): List<String> =
        home.value[serverId]?.entityMappings?.get(mappingKey).orEmpty()

    @Synchronized
    fun clear(serverId: Int) {
        mutableHome.value = mutableHome.value - serverId
    }
}
