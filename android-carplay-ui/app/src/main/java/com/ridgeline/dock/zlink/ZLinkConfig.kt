package com.ridgeline.dock.zlink

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Zlink is located at runtime (app label / package contains "zlink" or "zjinnova") so no package
 * name has to be guessed. The broadcast actions below are still PLACEHOLDERS until the real
 * ones are seen in logcat; until then CarPlay-active / readiness stay at their defaults.
 */
object ZLinkConfig {
    const val FALLBACK_PACKAGE = "com.zjinnova.zlink"          // only tried if label search finds nothing

    const val ACTION_SESSION = "com.ridgeline.dock.ZLINK_SESSION"   // extras: active, mode_mask
    const val ACTION_READY = "com.ridgeline.dock.ZLINK_READY"       // extras: bt_discoverable, hotspot_ready
    const val ACTION_WIFI = "com.ridgeline.dock.ZLINK_WIFI"         // extras: bars (0..4)

    fun findLaunchIntent(ctx: Context): Intent? {
        val pm = ctx.packageManager
        val hit = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).firstOrNull {
            val pkg = it.activityInfo.packageName.lowercase(); val label = it.loadLabel(pm).toString().lowercase()
            "zlink" in label || "zlink" in pkg || "zjinnova" in pkg
        }
        val i = if (hit != null) Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(hit.activityInfo.packageName, hit.activityInfo.name))
        else pm.getLaunchIntentForPackage(FALLBACK_PACKAGE)
        return i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    fun detected(ctx: Context): String = findLaunchIntent(ctx)?.component?.flattenToShortString() ?: "NOT FOUND"

    /** Brings Zlink to the foreground (starting it if needed). */
    fun launchUi(ctx: Context): Boolean {
        val i = findLaunchIntent(ctx)
        if (i == null) { Log.w("ZLink", "Zlink app not found"); return false }
        return try { ctx.startActivity(i); Log.i("ZLink", "launched ${i.component}"); true }
        catch (e: Exception) { Log.w("ZLink", "launch failed: $e"); false }
    }
}
