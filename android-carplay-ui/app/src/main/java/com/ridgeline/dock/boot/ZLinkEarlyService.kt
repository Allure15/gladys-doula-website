package com.ridgeline.dock.boot

import android.app.*
import android.content.Intent
import android.os.IBinder
import com.ridgeline.dock.dockApp
import com.ridgeline.dock.esp.TurnState
import com.ridgeline.dock.overlay.DockOverlay
import com.ridgeline.dock.vehicle.VideoInput
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
        app.espLink.start()
        scope.launch {   // optional: show the video input while a side blinker is on, back to Zlink when it ends
            var prev = TurnState.NONE
            app.espLink.turn.collect { s ->
                if (VideoInput.onBlinker(this@ZLinkEarlyService)) {
                    val side = { t: TurnState -> t == TurnState.LEFT || t == TurnState.RIGHT }
                    if (prev == TurnState.NONE && side(s)) VideoInput.launchIfSet(this@ZLinkEarlyService)
                    else if (side(prev) && s == TurnState.NONE) ZLinkConfig.launchUi(this@ZLinkEarlyService)
                }
                prev = s
            }
        }
        watcher = UpdateWatcher(this, UpdateConfig.watchDirs(this)) { app.update.offer(it) }.also { it.start() }
        return START_STICKY
    }

    override fun onDestroy() { watcher?.stop(); scope.cancel() }
}
