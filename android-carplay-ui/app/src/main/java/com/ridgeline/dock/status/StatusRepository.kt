package com.ridgeline.dock.status

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import com.ridgeline.dock.zlink.ZLinkClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Bluetooth = adapter on + HFP (calls/mic) connected. Says nothing about CarPlay media. */
data class BtState(val enabled: Boolean, val hfpConnected: Boolean)

/**
 * Three INDEPENDENT flows. Nothing derives one from another:
 *  - bluetooth:    system Bluetooth adapter + HFP profile
 *  - wifiBars:     0..4 (ZLink-reported if available, else system Wi-Fi RSSI)
 *  - carPlayActive: ZLink session with a CarPlay mode bit set
 */
class StatusRepository(private val ctx: Context, zlink: ZLinkClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _bt = MutableStateFlow(readBt())
    val bluetooth: StateFlow<BtState> = _bt.asStateFlow()

    private val _wifi = MutableStateFlow(0)
    val wifiBars: StateFlow<Int> = _wifi.asStateFlow()

    val carPlayActive: StateFlow<Boolean> =
        zlink.session.map { it.carPlayActive }.stateIn(scope, SharingStarted.Eagerly, false)

    init {
        ctx.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { _bt.value = readBt() }
        }, IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        })
        scope.launch {
            while (isActive) {
                _wifi.value = zlink.wifiBars.value ?: systemWifiBars()
                delay(2000)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun readBt(): BtState {
        val a = BluetoothAdapter.getDefaultAdapter() ?: return BtState(false, false)
        val hfp = a.isEnabled && a.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
        return BtState(a.isEnabled, hfp)
    }

    @Suppress("DEPRECATION")
    private fun systemWifiBars(): Int {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wm.connectionInfo ?: return 0
        if (info.networkId == -1) return 0
        return wm.calculateSignalLevel(info.rssi).coerceIn(0, 4)
    }
}
