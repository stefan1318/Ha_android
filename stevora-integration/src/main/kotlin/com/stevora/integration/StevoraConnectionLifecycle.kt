package com.stevora.integration

/** Bridge from the host app's real connection callbacks.
 * Call onConnected after its authenticated WebSocket becomes ready,
 * onDisconnected when it closes, and onServerChanged before switching instances.
 */
class StevoraConnectionLifecycle(
    private val coordinator: StevoraSyncCoordinator,
    private val onManifest: (StevoraManifest) -> Unit,
    private val onFallback: () -> Unit,
) {
    suspend fun onConnected() {
        when (val result = coordinator.refresh()) {
            is StevoraSyncResult.Updated -> onManifest(result.manifest)
            StevoraSyncResult.NativeHomeAssistantFallback -> onFallback()
            StevoraSyncResult.Unchanged -> Unit
        }
    }
    fun onDisconnected() { /* Keep last manifest until next authorized reconnect. */ }
    fun onServerChanged() { coordinator.clearOnServerChange(); onFallback() }
}
