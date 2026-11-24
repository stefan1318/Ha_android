package io.homeassistant.companion.android.common.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.util.launchAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

/**
 * Handles the "Disable" action on the beacon-scanning foreground notification, fired as an explicit
 * [ACTION_STOP_BEACON_SCANNING] broadcast by MonitoringManager. It exists only so that
 * common-module code has a [BroadcastReceiver] to target with that PendingIntent.
 */
class StopBeaconScanningReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun bluetoothSensorManager(): BluetoothSensorManager
    }

    private val receiverScope = CoroutineScope(Dispatchers.Default + Job())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP_BEACON_SCANNING) return
        val manager = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
            .bluetoothSensorManager()
        launchAsync(receiverScope) {
            manager.enableDisableBeaconMonitor(false)
        }
    }

    companion object {
        private const val ACTION_STOP_BEACON_SCANNING = "io.homeassistant.companion.android.STOP_BEACON_SCANNING"

        /** Intent that, when broadcast, stops beacon scanning. Use it to build the notification's "Disable" action. */
        fun stopScanningIntent(context: Context): Intent =
            Intent(context, StopBeaconScanningReceiver::class.java).apply {
                action = ACTION_STOP_BEACON_SCANNING
            }
    }
}
