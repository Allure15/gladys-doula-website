package com.ridgeline.dock.ui

import android.content.Context
import android.widget.TextView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun <T : TextView> T.styled(): T = apply {
    setTextColor(0xFFF2F4F7.toInt()); setHintTextColor(0xFF4A525C.toInt())
}
