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

class McuVehicleControl : VehicleControl {
    private val _states = MutableStateFlow(VehicleModule.entries.associateWith { false })
    override val states = _states.asStateFlow()

    override fun toggle(module: VehicleModule) {
        val on = !(_states.value[module] ?: false)
        // TODO(hardware): send the real MCU/CAN command for `module` -> `on`
        // (vendor MCU serial/broadcast, or the ESP32 relay board). Local state only until then.
        Log.i("Vehicle", "$module -> $on (no hardware transport wired yet)")
        _states.value = _states.value + (module to on)
    }
}
