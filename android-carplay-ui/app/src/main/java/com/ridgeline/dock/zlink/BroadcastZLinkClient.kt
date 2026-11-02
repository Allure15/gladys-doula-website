package com.ridgeline.dock.zlink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Broadcast-based ZLink client. State comes from broadcasts; surface + touch
 * forwarding are the two methods that must be wired to the vendor's binder/SDK
 * (see TODOs) - there is no public ZLink API to call from here.
 */
class BroadcastZLinkClient(private val ctx: Context) : ZLinkClient {
    private val _session = MutableStateFlow(ZLinkSession(false, 0))
    private val _ready = MutableStateFlow(ZLinkReadiness(false, false))
    private val _bars = MutableStateFlow<Int?>(null)
    override val session: StateFlow<ZLinkSession> = _session.asStateFlow()
    override val readiness: StateFlow<ZLinkReadiness> = _ready.asStateFlow()
    override val wifiBars: StateFlow<Int?> = _bars.asStateFlow()

    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                ZLinkConfig.ACTION_SESSION -> _session.value =
                    ZLinkSession(i.getBooleanExtra("active", false), i.getIntExtra("mode_mask", 0))
                ZLinkConfig.ACTION_READY -> _ready.value =
                    ZLinkReadiness(i.getBooleanExtra("bt_discoverable", false), i.getBooleanExtra("hotspot_ready", false))
                ZLinkConfig.ACTION_WIFI -> _bars.value = i.getIntExtra("bars", 0).coerceIn(0, 4)
            }
        }
    }

    @Synchronized
    override fun start() {
        if (started) return
        started = true
        ctx.registerReceiver(receiver, IntentFilter().apply {
            addAction(ZLinkConfig.ACTION_SESSION); addAction(ZLinkConfig.ACTION_READY); addAction(ZLinkConfig.ACTION_WIFI)
        })
    }

    override fun attachSurface(surface: Surface?, width: Int, height: Int) {
        // TODO(vendor): hand `surface` to the ZLink renderer (SDK call / AIDL) at width x height.
        Log.d("ZLink", "attachSurface ${surface != null} ${width}x$height")
    }

    override fun sendTouch(event: MotionEvent) {
        // TODO(vendor): forward touch to ZLink (coordinates are already in the content area's space).
    }
}
