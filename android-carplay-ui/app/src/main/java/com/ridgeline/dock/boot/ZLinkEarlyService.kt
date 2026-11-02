package com.ridgeline.dock.boot

import android.app.*
import android.content.Intent
import android.os.IBinder
import com.ridgeline.dock.dockApp
import com.ridgeline.dock.overlay.TurnSignalOverlay
import com.ridgeline.dock.overlay.VehicleSignals
import com.ridgeline.dock.ui.MainActivity
import com.ridgeline.dock.update.UpdateConfig
import com.ridgeline.dock.update.UpdateWatcher
import com.ridgeline.dock.zlink.ZLinkConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Boot order:
 *  1. start the vendor ZLink service immediately
 *  2. wait until ZLink is ready (BT discoverable + hotspot prepared; 20 s cap)
 *  3. start the custom UI with live status already available
 * Also hosts the long-lived pieces: turn-signal overlay, CAN->blinker decoder, USB watcher.
 */
class ZLinkEarlyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false
    private var watcher: UpdateWatcher? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("dock", "Dock", NotificationManager.IMPORTANCE_MIN))
        startForeground(1, Notification.Builder(this, "dock").setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Ridgeline Dock").build())
        if (started) return START_STICKY
        started = true

        val app = dockApp
        ZLinkConfig.startVendorService(this)          // 1. ZLink first
        app.zlink.start()

        val overlay = TurnSignalOverlay(this)
        scope.launch { VehicleSignals.turn.collect { overlay.show(it) } }
        app.blinker.start(scope)
        app.canBus.startIfEnabled()
        watcher = UpdateWatcher(this, UpdateConfig.watchDirs(this)) { app.update.offer(it) }.also { it.start() }

        scope.launch {                                 // 2. wait for ZLink, 3. then UI
            withTimeoutOrNull(20_000) { app.zlink.readiness.first { it.ready } }
            startActivity(Intent(this@ZLinkEarlyService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return START_STICKY
    }

    override fun onDestroy() { watcher?.stop(); scope.cancel() }
}
