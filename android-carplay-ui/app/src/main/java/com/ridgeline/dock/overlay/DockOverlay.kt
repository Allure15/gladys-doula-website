package com.ridgeline.dock.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.ridgeline.dock.dockApp
import com.ridgeline.dock.ui.DockPanel
import com.ridgeline.dock.ui.StatusCluster
import com.ridgeline.dock.ui.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Always-on-top dock over Zlink: bottom 20% (touchable) + status cluster top-right (touch-transparent).
 * Zlink keeps running full-screen underneath; the dock covers its bottom 20%.
 * Needs "Display over other apps" for this package.
 */
class DockOverlay(private val ctx: Context, private val scope: CoroutineScope) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var attached = false

    fun show(): Boolean {
        if (attached) return true
        if (!Settings.canDrawOverlays(ctx)) { Log.w("DockOverlay", "overlay permission missing"); return false }
        val app = ctx.dockApp
        val dockH = wm.currentWindowMetrics.bounds.height() / 5          // 20 % of the display (120 px on 600)

        val panel = DockPanel(ctx, dockH)
        panel.bind(scope)
        wm.addView(panel, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.BOTTOM })

        val cluster = StatusCluster(ctx)
        wm.addView(cluster, WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = ctx.dp(24); y = ctx.dp(8) })
        scope.launch { app.status.bluetooth.collect { cluster.btState = it } }          // three independent flows
        scope.launch { app.status.wifiBars.collect { cluster.wifiBars = it } }
        scope.launch { app.status.carPlayActive.collect { cluster.carPlayActive = it } }
        attached = true
        return true
    }
}
