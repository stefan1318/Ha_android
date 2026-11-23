package io.homeassistant.companion.android.stevora

import com.stevora.integration.StevoraAdminApi
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineResponse
import org.json.JSONObject

/** Resolve this account's OS assignment before falling back to the hub's preferred pipeline. */
internal suspend fun WebSocketRepository.resolveStevoraPipeline(): AssistPipelineResponse? {
    val result = stevoraManagerCommand(StevoraAdminApi.ASSISTANTS_RESOLVE) ?: return null
    val assignment = JSONObject(result)
    val inventory = getAssistPipelines() ?: return null
    val selected = if (!assignment.isNull("pipeline_id")) {
        assignment.getString("pipeline_id")
    } else if (assignment.optString("source") == "home_assistant_default") {
        inventory.preferredPipeline ?: inventory.pipelines.singleOrNull()?.id
    } else {
        null
    }
    return inventory.pipelines.firstOrNull { it.id == selected }
}
