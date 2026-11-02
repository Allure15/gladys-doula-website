package com.ridgeline.dock.ui

import android.content.Context
import android.graphics.*
import android.view.View
import androidx.core.content.ContextCompat
import com.ridgeline.dock.R
import com.ridgeline.dock.status.BtState

/**
 * Three independent indicators; each setter touches only its own slot.
 *  [Bluetooth: off=dim, on=white, HFP connected=blue] [Wi-Fi bars 0..4] [CarPlay pill]
 */
class StatusCluster(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bt = ContextCompat.getDrawable(ctx, R.drawable.ic_bluetooth)!!.mutate()
    private val dim = 0xFF4A525C.toInt(); private val fg = 0xFFF2F4F7.toInt()
    private val blue = 0xFF4DA3FF.toInt(); private val green = 0xFF3DDC84.toInt()

    var btState = BtState(false, false); set(v) { field = v; invalidate() }
    var wifiBars = 0; set(v) { field = v.coerceIn(0, 4); invalidate() }
    var carPlayActive = false; set(v) { field = v; invalidate() }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(context.dp(190), context.dp(36))

    override fun onDraw(c: Canvas) {
        val d = context.dp(1).toFloat()
        // slot 1: Bluetooth
        bt.setTint(if (!btState.enabled) dim else if (btState.hfpConnected) blue else fg)
        bt.setBounds(0, 4 * d.toInt(), 28 * d.toInt(), 32 * d.toInt()); bt.draw(c)
        // slot 2: Wi-Fi bars
        for (i in 0 until 4) {
            p.color = if (i < wifiBars) fg else dim
            val bh = (8 + i * 6) * d; val x = (44 + i * 9) * d
            c.drawRoundRect(x, 32 * d - bh, x + 6 * d, 32 * d, 2 * d, 2 * d, p)
        }
        // slot 3: CarPlay session pill
        p.color = if (carPlayActive) green else dim
        val r = RectF(92 * d, 6 * d, 190 * d, 30 * d)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; c.drawRoundRect(r, 12 * d, 12 * d, p)
        p.style = Paint.Style.FILL; p.textSize = 13 * d; p.textAlign = Paint.Align.CENTER
        c.drawText("CarPlay", r.centerX(), r.centerY() + 5 * d, p)
    }
}
