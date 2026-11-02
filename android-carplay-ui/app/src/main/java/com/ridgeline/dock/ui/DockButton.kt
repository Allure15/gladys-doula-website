package com.ridgeline.dock.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Icon (white on dark) with optional label underneath. [on] tints icon + label with the accent. */
class DockButton(ctx: Context, icon: Int, label: String?, iconDp: Int = 52) : LinearLayout(ctx) {
    private val img = ImageView(ctx).apply { setImageResource(icon) }
    private val txt = TextView(ctx).apply { text = label; textSize = 17f; typeface = android.graphics.Typeface.DEFAULT_BOLD; letterSpacing = .02f; gravity = Gravity.CENTER }

    init {
        orientation = VERTICAL; gravity = Gravity.CENTER; isClickable = true; isFocusable = true
        addView(img, LayoutParams(ctx.dp(iconDp), ctx.dp(iconDp)))
        if (label != null) addView(txt, LayoutParams(-2, -2).apply { topMargin = ctx.dp(2) })
        background = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable().apply { setColor(0xFF1D242C.toInt()); cornerRadius = ctx.dp(14).toFloat() })
        }
        setOn(false)
    }

    fun setOn(on: Boolean) {
        val c = if (on) 0xFF4DA3FF.toInt() else 0xFFF2F4F7.toInt()
        img.setColorFilter(c); txt.setTextColor(c)
    }
}
