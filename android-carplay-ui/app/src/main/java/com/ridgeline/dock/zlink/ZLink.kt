package com.ridgeline.dock.zlink

import android.view.MotionEvent
import android.view.Surface
import kotlinx.coroutines.flow.StateFlow

/** ZLink link-mode bitmask, mirrored exactly from the head unit. */
enum class ZLinkMode(val bit: Int, val label: String, val isCarPlay: Boolean) {
    WIRED_CARPLAY(0x1, "Wired CarPlay", true),
    WIRELESS_CARPLAY(0x2, "Wireless CarPlay", true),
    WIRED_ANDROID_AUTO(0x4, "Wired Android Auto", false),
    WIRELESS_ANDROID_AUTO(0x8, "Wireless Android Auto", false);

    companion object {
        fun fromMask(mask: Int): List<ZLinkMode> = entries.filter { mask and it.bit != 0 }
    }
}

data class ZLinkSession(val active: Boolean, val modeMask: Int) {
    val modes get() = ZLinkMode.fromMask(modeMask)
    val carPlayActive get() = active && modes.any { it.isCarPlay }
}

/** Boot gate: UI starts once Bluetooth is discoverable AND the hotspot is prepared. */
data class ZLinkReadiness(val btDiscoverable: Boolean, val hotspotReady: Boolean) {
    val ready get() = btDiscoverable && hotspotReady
}

/**
 * The CarPlay GUI channel (ZLink protocol layer). Knows nothing about Bluetooth
 * or Wi-Fi state beyond what the ZLink daemon itself reports.
 */
interface ZLinkClient {
    val session: StateFlow<ZLinkSession>
    val readiness: StateFlow<ZLinkReadiness>
    /** Wi-Fi bars 0..4 as reported by ZLink for the phone link; null = not reported. */
    val wifiBars: StateFlow<Int?>

    fun start()
    fun attachSurface(surface: Surface?, width: Int, height: Int)
    fun sendTouch(event: MotionEvent)
}
