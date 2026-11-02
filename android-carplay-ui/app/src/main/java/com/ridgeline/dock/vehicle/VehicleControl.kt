package com.ridgeline.dock.vehicle

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VehicleModule { TRAILER, AUX, FOG, VIDEO }

/** Vehicle modules are driven by the Android stereo (MCU/CAN), never by the phone. */
interface VehicleControl {
    val states: StateFlow<Map<VehicleModule, Boolean>>
    fun toggle(module: VehicleModule)
}

class McuVehicleControl(private val ctx: android.content.Context) : VehicleControl {
    private val _states = MutableStateFlow(VehicleModule.entries.associateWith { false })
    override val states = _states.asStateFlow()

    override fun toggle(module: VehicleModule) {
        val on = !(_states.value[module] ?: false)
        _states.value = _states.value + (module to on)
        Thread {   // blocking serial / broadcast work off the UI thread; result shown as a toast
            val msg = ButtonActions.execute(ctx, module, on)
            Log.i("Vehicle", msg)
            if (msg.contains("FAILED") || msg.contains("no action configured")) _states.value = _states.value + (module to !on)   // don't show ON when nothing happened
            android.os.Handler(android.os.Looper.getMainLooper()).post { android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show() }
        }.start()
    }
}
