package com.ridgeline.dock.overlay

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import kotlinx.coroutines.flow.MutableSharedFlow

enum class TurnSignal { LEFT, RIGHT, HAZARD }

object VehicleSignals {
    val turn = MutableSharedFlow<TurnSignal>(extraBufferCapacity = 16)
}

/**
 * Small STATIC green arrow in the top corner. Shown while signalling, hidden 2 s after the
 * last signal event. FLAG_NOT_TOUCHABLE makes the whole window transparent to touches,
 * so CarPlay keeps receiving them.
 */
class TurnSignalOverlay(private val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val leftArrow = ArrowView(ctx, pointsRight = false)
    private val rightArrow = ArrowView(ctx, pointsRight = true)
    private val root = FrameLayout(ctx).apply {
        val d = ctx.resources.displayMetrics.density
        setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), 0)
        val sz = (48 * d).toInt()
        addView(leftArrow, FrameLayout.LayoutParams(sz, sz * 3 / 4, Gravity.START or Gravity.TOP))
        addView(rightArrow, FrameLayout.LayoutParams(sz, sz * 3 / 4, Gravity.END or Gravity.TOP))
        visibility = View.INVISIBLE
    }
    private var attached = false
    private val hide = Runnable { root.visibility = View.INVISIBLE }

    fun show(sig: TurnSignal) {
        if (!attach()) return
        leftArrow.visibility = if (sig != TurnSignal.RIGHT) View.VISIBLE else View.INVISIBLE
        rightArrow.visibility = if (sig != TurnSignal.LEFT) View.VISIBLE else View.INVISIBLE
        root.visibility = View.VISIBLE
        handler.removeCallbacks(hide)
        handler.postDelayed(hide, 2000)
    }

    private fun attach(): Boolean {
        if (attached) return true
        if (!Settings.canDrawOverlays(ctx)) {
            Log.w("Overlay", "SYSTEM_ALERT_WINDOW not granted: adb shell appops set ${ctx.packageName} SYSTEM_ALERT_WINDOW allow")
            return false
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }
        wm.addView(root, lp)
        attached = true
        return true
    }

    private class ArrowView(ctx: Context, val pointsRight: Boolean) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3DDC84.toInt() }
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val p = Path().apply {
                moveTo(0f, h * .3f); lineTo(w * .5f, h * .3f); lineTo(w * .5f, 0f); lineTo(w, h / 2)
                lineTo(w * .5f, h); lineTo(w * .5f, h * .7f); lineTo(0f, h * .7f); close()
            }
            if (!pointsRight) { c.translate(w, 0f); c.scale(-1f, 1f) }
            c.drawPath(p, paint)
        }
    }
}
