package com.ridgeline.dock.ui

import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.ridgeline.dock.R
import com.ridgeline.dock.dockApp
import com.ridgeline.dock.update.UpdateState
import com.ridgeline.dock.vehicle.VehicleModule
import com.ridgeline.dock.vehicle.VideoInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The 20% dock: [update banner] / white divider / Trailer | Aux | H | Fog | Video. Hosted in a window overlay. */
class DockPanel(ctx: Context, dockHeightPx: Int) : LinearLayout(ctx) {
    private val app = ctx.dockApp
    private val bannerText = TextView(ctx).styled().apply { textSize = 14f }
    private val banner = LinearLayout(ctx).apply {
        setBackgroundColor(0xF012161B.toInt()); gravity = Gravity.CENTER_VERTICAL; setPadding(ctx.dp(16), ctx.dp(6), ctx.dp(16), ctx.dp(6)); visibility = View.GONE
        addView(bannerText, LayoutParams(0, -2, 1f))
        addView(Button(ctx).apply { text = "Install now"; setOnClickListener { app.update.installNow() } })
        addView(Button(ctx).apply { text = "Cancel"; setOnClickListener { app.update.cancel() } })
    }
    private val modules = mapOf(
        VehicleModule.TRAILER to DockButton(ctx, R.drawable.ic_trailer, "TRAILER"),
        VehicleModule.AUX to DockButton(ctx, R.drawable.ic_power, "AUX"),
        VehicleModule.FOG to DockButton(ctx, R.drawable.ic_fog, "FOG"))
    private val video = DockButton(ctx, R.drawable.ic_video, "VIDEO")
    private val honda = DockButton(ctx, R.drawable.ic_honda_h, null, iconDp = 72)

    init {
        orientation = VERTICAL; setBackgroundColor(0xFF000000.toInt())
        modules.forEach { (m, b) -> b.setOnClickListener { app.vehicle.toggle(m) } }
        video.setOnClickListener { VideoInput.launch(ctx) }
        honda.setOnClickListener { ctx.startActivity(Intent(ctx, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        val row = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            listOf(modules[VehicleModule.TRAILER]!!, modules[VehicleModule.AUX]!!, honda, modules[VehicleModule.FOG]!!, video)
                .forEach { addView(it, LayoutParams(0, -1, if (it === honda) 1.3f else 1f)) }
        }
        addView(banner, LayoutParams(-1, -2))
        addView(View(ctx).apply { setBackgroundColor(0xFFFFFFFF.toInt()) }, LayoutParams(-1, ctx.dp(2)))
        addView(row, LayoutParams(-1, dockHeightPx - ctx.dp(2)))
    }

    fun bind(scope: CoroutineScope): Job = scope.launch {
        launch { app.vehicle.states.collect { s -> modules.forEach { (m, b) -> b.setOn(s[m] == true) } } }
        launch { app.update.state.collect(::renderUpdate) }
    }

    private fun renderUpdate(s: UpdateState) {
        banner.visibility = if (s is UpdateState.Idle) View.GONE else View.VISIBLE
        bannerText.text = when (s) {
            is UpdateState.Staged -> if (s.waitingForIdle) "Update ${s.versionName} ready - waiting for CarPlay to be idle" else "Updating to ${s.versionName} in ${s.secondsLeft}s"
            is UpdateState.Installing -> "Installing update..."
            is UpdateState.Failed -> "Update failed: ${s.reason}"
            else -> ""
        }
    }
}
