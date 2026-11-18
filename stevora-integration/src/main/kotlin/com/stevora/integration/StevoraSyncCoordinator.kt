package com.stevora.integration

/** Integrate with the host app's authenticated HA WebSocket, not a separate token store. */
interface StevoraWebSocketTransport {
    suspend fun request(message: String): String
}

sealed interface StevoraSyncResult {
    data class Updated(val manifest: StevoraManifest) : StevoraSyncResult
    data object Unchanged : StevoraSyncResult
    data object NativeHomeAssistantFallback : StevoraSyncResult
}

class StevoraSyncCoordinator(private val transport: StevoraWebSocketTransport) {
    private var cached: StevoraManifest? = null

    /** Call on HA connection established/reconnected or explicit refresh.
     * This does not control devices or modify Home Assistant configuration.
     */
    suspend fun refresh(): StevoraSyncResult {
        val response = try {
            transport.request(StevoraManifestSync.request(1))
        } catch (_: Exception) {
            return StevoraSyncResult.NativeHomeAssistantFallback
        }
        val manifest = try {
            StevoraManifestSync.parseResponse(response)
        } catch (_: Exception) {
            null
        } ?: return StevoraSyncResult.NativeHomeAssistantFallback
        val previous = cached
        if (previous?.installationId == manifest.installationId &&
            previous.revision == manifest.revision) return StevoraSyncResult.Unchanged
        cached = manifest
        return StevoraSyncResult.Updated(manifest)
    }

    fun clearOnServerChange() { cached = null }
}
