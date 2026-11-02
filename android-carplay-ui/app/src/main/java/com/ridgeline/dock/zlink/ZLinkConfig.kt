package com.ridgeline.dock.zlink

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * !! VERIFY ON THE UNIT !!  Everything here is the vendor-facing seam and is a
 * placeholder until checked against the real ZLink build:
 *   adb shell pm list packages | grep -i -E "zlink|link"
 *   adb shell dumpsys package <pkg> | grep -A3 -i "Service\|Receiver"
 *   adb logcat | grep -i zlink     (watch the broadcasts while pairing a phone)
 */
object ZLinkConfig {
    const val PACKAGE = "com.zjinnova.zlink"           // assumption
    const val SERVICE_CLASS = "com.zjinnova.zlink.ZLinkService" // assumption

    const val ACTION_SESSION = "com.ridgeline.dock.ZLINK_SESSION"   // extras: active, mode_mask
    const val ACTION_READY = "com.ridgeline.dock.ZLINK_READY"       // extras: bt_discoverable, hotspot_ready
    const val ACTION_WIFI = "com.ridgeline.dock.ZLINK_WIFI"         // extras: bars (0..4)

    fun startVendorService(ctx: Context) {
        try {
            ctx.startForegroundService(Intent().setComponent(ComponentName(PACKAGE, SERVICE_CLASS)))
        } catch (e: Exception) {
            Log.w("ZLink", "vendor service start failed (already running or wrong name): $e")
        }
    }
}
