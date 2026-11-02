package com.ridgeline.dock.boot

import android.app.*
import android.content.Intent
import android.os.IBinder
import com.ridgeline.dock.dockApp
import com.ridgeline.dock.overlay.DockOverlay
import com.ridgeline.dock.overlay.TurnSignalOverlay
import com.ridgeline.dock.overlay.VehicleSignals
import com.ridgeline.dock.update.UpdateConfig
import com.ridgeline.dock.update.UpdateWatcher
import com.ridgeline.dock.zlink.ZLinkConfig
import kotlinx.coroutines.*

/**
 * Boot: BOOT_COMPLETED -> this service -> launch Zlink immediately (so the wireless CarPlay
 * handshake starts ASAP) -> show the dock overlay on top. Also hosts the turn-signal overlay,
 * CAN->blinker decoder and USB update watcher. Re-sending the start command (MainActivity,
 * after an update) just re-launches Zlink.
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

        ZLinkConfig.launchUi(this)                      // 1. Zlink first, every time
        if (started) return START_STICKY
        started = true

        val app = dockApp
        app.zlink.start()
        DockOverlay(this, app.scope).show()             // 2. dock on top

        val arrows = TurnSignalOverlay(this)
        scope.launch { VehicleSignals.turn.collect { arrows.show(it) } }
        app.blinker.start(scope)
        app.canBus.startIfEnabled()
        watcher = UpdateWatcher(this, UpdateConfig.watchDirs(this)) { app.update.offer(it) }.also { it.start() }
        return START_STICKY
    }

    override fun onDestroy() { watcher?.stop(); scope.cancel() }
}
