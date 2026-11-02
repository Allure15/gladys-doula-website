package com.ridgeline.dock.vehicle

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * VIDEO button = switch to the radio's video input (AV-in / camera) app.
 * Which app that is varies by ROM, so the user picks it once (Settings -> Set video input app)
 * and it's stored as a component name. Until chosen, the button opens the picker.
 */
object VideoInput {
    private fun prefs(c: Context) = c.getSharedPreferences("video", Context.MODE_PRIVATE)

    fun launch(ctx: Context) {
        val saved = prefs(ctx).getString("component", null)?.let(ComponentName::unflattenFromString)
        if (saved == null) { pick(ctx); return }
        try {
            ctx.startActivity(Intent(Intent.ACTION_MAIN).setComponent(saved).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Video input app not found - choose again", Toast.LENGTH_LONG).show(); pick(ctx)
        }
    }

    fun current(ctx: Context): String = prefs(ctx).getString("component", null) ?: "not set"

    fun pick(ctx: Context) {
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        AlertDialog.Builder(ctx).setTitle("Video input app (AV-in / camera)")
            .setItems(apps.map { "${it.loadLabel(pm)}  (${it.activityInfo.packageName})" }.toTypedArray()) { _, i ->
                val a = apps[i].activityInfo
                prefs(ctx).edit().putString("component", ComponentName(a.packageName, a.name).flattenToString()).apply()
            }.show()
    }
}
