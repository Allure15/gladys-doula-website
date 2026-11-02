package com.ridgeline.dock

import android.app.Application
import android.content.Context
import com.ridgeline.dock.can.BlinkerDecoder
import com.ridgeline.dock.can.CanBus
import com.ridgeline.dock.esp.EspLink
import com.ridgeline.dock.status.StatusRepository
import com.ridgeline.dock.update.UpdateCoordinator
import com.ridgeline.dock.vehicle.McuVehicleControl
import com.ridgeline.dock.vehicle.VehicleControl
import com.ridgeline.dock.zlink.BroadcastZLinkClient
import com.ridgeline.dock.zlink.ZLinkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-wide singletons. Each channel (ZLink, status, CAN, update) is separate. */
class DockApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var zlink: ZLinkClient
    lateinit var status: StatusRepository
    lateinit var update: UpdateCoordinator
    lateinit var vehicle: VehicleControl
    lateinit var canBus: CanBus
    lateinit var blinker: BlinkerDecoder
    lateinit var espLink: EspLink

    override fun onCreate() {
        super.onCreate()
        zlink = BroadcastZLinkClient(this)
        status = StatusRepository(this, zlink)
        update = UpdateCoordinator(this, status.carPlayActive, scope)
        vehicle = McuVehicleControl(this)
        canBus = CanBus(this)
        blinker = BlinkerDecoder(this, canBus)
        espLink = EspLink(this)
    }
}

val Context.dockApp: DockApp get() = applicationContext as DockApp
