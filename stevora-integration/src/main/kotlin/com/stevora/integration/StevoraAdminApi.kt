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

    fun request(id: Int, command: String): String =
        JSONObject().put("id", id).put("type", command).toString()

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
