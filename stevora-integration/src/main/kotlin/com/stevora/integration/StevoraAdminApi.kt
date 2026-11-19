package com.stevora.integration

import org.json.JSONArray
import org.json.JSONObject

/**
 * Request contract for server-enforced admin operations.
 * A manifest or local account flag never grants administrator permissions.
 */
object StevoraAdminApi {
    const val CAPABILITIES = "stevora/admin/capabilities"
    const val ROOMS_GET = "stevora/admin/rooms/get"
    const val ROOMS_UPDATE = "stevora/admin/rooms/update"
    const val ASSISTANTS_GET = "stevora/admin/assistants/get"
    const val ASSISTANTS_UPDATE = "stevora/admin/assistants/update"
    const val ASSISTANTS_RESOLVE = "stevora/assistants/resolve"

    fun request(id: Int, command: String): String =
        JSONObject().put("id", id).put("type", command).toString()

    data class AssistantPipeline(val id: String, val name: String)
    data class AssistantSettings(val defaultPipeline: String?, val userPipelines: Map<String, String>)

    fun updateAssistants(id: Int, expectedRevision: Int, settings: AssistantSettings): String {
        val userPipelines = JSONObject()
        settings.userPipelines.forEach { (userId, pipelineId) ->
            userPipelines.put(userId, pipelineId)
        }
        return JSONObject()
            .put("id", id)
            .put("type", ASSISTANTS_UPDATE)
            .put("expected_revision", expectedRevision)
            .put("default_pipeline", settings.defaultPipeline ?: JSONObject.NULL)
            .put("user_pipelines", userPipelines)
            .toString()
    }

    data class ManagedEntity(
        val entityId: String,
        val name: String,
        val included: Boolean,
        val disabled: Boolean,
        val hidden: Boolean,
    )
    data class RoomSnapshot(
        val revision: Int,
        val rooms: List<StevoraRoom>,
        val entities: List<ManagedEntity>,
        val visibility: Map<String, String>,
    )
    data class AssistantSnapshot(
        val revision: Int,
        val settings: AssistantSettings,
        val pipelines: List<AssistantPipeline>,
    )

    fun parseRoomsResponse(payload: String): RoomSnapshot? {
        val result = JSONObject(payload).takeIf { it.optBoolean("success", false) }
            ?.optJSONObject("result") ?: return null
        val rooms = result.optJSONArray("rooms")
        val entities = result.optJSONArray("entities")
        val visibility = result.optJSONObject("visibility")
        val roomList = (0 until (rooms?.length() ?: 0)).mapNotNull { i ->
            rooms?.optJSONObject(i)?.let { StevoraRoom(it.optString("id"), it.optString("name")) }
        }
        val entityList = (0 until (entities?.length() ?: 0)).mapNotNull { i ->
            entities?.optJSONObject(i)?.let {
                ManagedEntity(
                    it.optString("entity_id"), it.optString("name"),
                    it.optBoolean("included"), it.optBoolean("disabled"), it.optBoolean("hidden")
                )
            }
        }
        val visibilityMap = mutableMapOf<String, String>()
        visibility?.keys()?.forEach { key -> visibilityMap[key] = visibility.optString(key) }
        return RoomSnapshot(result.optInt("revision"), roomList, entityList, visibilityMap)
    }

    fun parseAssistantsResponse(payload: String): AssistantSnapshot? {
        val result = JSONObject(payload).takeIf { it.optBoolean("success", false) }
            ?.optJSONObject("result") ?: return null
        val settings = result.optJSONObject("settings") ?: return null
        val pipelines = result.optJSONArray("pipelines")
        val pipelineList = (0 until (pipelines?.length() ?: 0)).mapNotNull { i ->
            pipelines?.optJSONObject(i)?.let {
                AssistantPipeline(it.optString("id"), it.optString("name"))
            }
        }
        val overrides = mutableMapOf<String, String>()
        settings.optJSONObject("user_pipelines")?.let { users ->
            users.keys().forEach { key -> overrides[key] = users.optString(key) }
        }
        return AssistantSnapshot(
            result.optInt("revision"),
            AssistantSettings(settings.optString("default_pipeline").takeIf { it.isNotEmpty() }, overrides),
            pipelineList,
        )
    }

    enum class Visibility(val wireValue: String) {
        DEFAULT("default"), INCLUDE("include"), EXCLUDE("exclude")
    }

    data class VisibilityPatch(val entityId: String, val visibility: Visibility)

    fun updateVisibility(id: Int, expectedRevision: Int, patches: List<VisibilityPatch>): String {
        val entities = JSONArray()
        patches.forEach { patch ->
            entities.put(JSONObject()
                .put("entity_id", patch.entityId)
                .put("visibility", patch.visibility.wireValue))
        }
        return JSONObject()
            .put("id", id)
            .put("type", ROOMS_UPDATE)
            .put("expected_revision", expectedRevision)
            .put("entities", entities)
            .toString()
    }

    fun isForbidden(response: JSONObject): Boolean {
        if (response.optBoolean("success", true)) return false
        return response.optJSONObject("error")?.optString("code") in
            setOf("unauthorized", "forbidden")
    }
}
