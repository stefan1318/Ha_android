package io.homeassistant.companion.android.stevora

import android.content.Context
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import org.json.JSONObject

/**
 * Saves ONLY non-secret connection preferences across account switching.
 * Authentication tokens, webhook secrets and device registration are never copied.
 */
internal object StevoraHubSession {
    private const val PREFS = "stevora_hub_relogin"
    private const val KEY = "pending_connection"

    fun remember(context: Context, server: Server) {
        val c = server.connection
        val snapshot = JSONObject().apply {
            put("url", c.externalUrl)
            put("internal", c.internalUrl)
            put("name", server.nameOverride)
            put("ssids", org.json.JSONArray(c.internalSsids))
            put("ethernet", c.internalEthernet)
            put("vpn", c.internalVpn)
            put("prioritize", c.prioritizeInternal)
            put("insecure", c.allowInsecureConnection)
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, snapshot.toString()).commit()
    }

    suspend fun restoreIfMatching(context: Context, manager: ServerManager, newServerId: Int) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return
        val snapshot = try { JSONObject(raw) } catch (_: Exception) { return }
        val server = manager.getServer(newServerId) ?: return
        if (server.connection.externalUrl.trimEnd('/') != snapshot.optString("url").trimEnd('/')) return
        val c = server.connection
        val ssids = snapshot.optJSONArray("ssids")
        val restored = c.copy(
            internalUrl = snapshot.optString("internal").takeIf { it.isNotBlank() && it != "null" },
            internalSsids = if (ssids != null) (0 until ssids.length()).mapNotNull { ssids.optString(it).takeIf(String::isNotBlank) } else emptyList(),
            internalEthernet = if (snapshot.isNull("ethernet")) null else snapshot.optBoolean("ethernet"),
            internalVpn = if (snapshot.isNull("vpn")) null else snapshot.optBoolean("vpn"),
            prioritizeInternal = snapshot.optBoolean("prioritize"),
            allowInsecureConnection = if (snapshot.isNull("insecure")) c.allowInsecureConnection else snapshot.optBoolean("insecure"),
        )
        manager.updateServer(server.copy(
            nameOverride = snapshot.optString("name").takeIf { it.isNotBlank() && it != "null" },
            connection = restored,
        ))
        prefs.edit().remove(KEY).apply()
    }
}
