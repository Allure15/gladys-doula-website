package com.ridgeline.dock.ui

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ridgeline.dock.R
import com.ridgeline.dock.dockApp
import com.ridgeline.dock.update.UpdateState
import com.ridgeline.dock.vehicle.VehicleModule
import kotlinx.coroutines.launch

/**
 * 1024x600 landscape. Top 80% = ZLink content (weight 4), bottom 20% = fixed dock (weight 1)
 * -> 480 px / 120 px on the unit. The dock is native and never overlaps the CarPlay surface.
 */
class MainActivity : ComponentActivity() {
    private lateinit var cluster: StatusCluster
    private lateinit var banner: LinearLayout
    private lateinit var bannerText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = dockApp
        app.zlink.start()

        val surface = object : SurfaceView(this) {
            override fun onTouchEvent(e: MotionEvent): Boolean { app.zlink.sendTouch(e); return true }
        }.apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(h: SurfaceHolder) {}
                override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) = app.zlink.attachSurface(h.surface, w, ht)
                override fun surfaceDestroyed(h: SurfaceHolder) = app.zlink.attachSurface(null, 0, 0)
            })
        }
        cluster = StatusCluster(this)
        bannerText = TextView(this).styled().apply { textSize = 14f }
        banner = LinearLayout(this).apply {
            setBackgroundColor(0xE612161B.toInt()); gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(6), dp(16), dp(6))
            visibility = View.GONE
            addView(bannerText, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Button(context).apply { text = "Install now"; setOnClickListener { app.update.installNow() } })
            addView(Button(context).apply { text = "Cancel"; setOnClickListener { app.update.cancel() } })
        }
        val content = FrameLayout(this).apply {
            addView(surface, FrameLayout.LayoutParams(-1, -1))
            addView(cluster, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(8) })
            addView(banner, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        }

        val trailer = DockButton(this, R.drawable.ic_trailer, "TRAILER")
        val aux = DockButton(this, R.drawable.ic_power, "AUX")
        val honda = DockButton(this, R.drawable.ic_honda_h, null, iconDp = 72)
        val fog = DockButton(this, R.drawable.ic_fog, "FOG")
        val video = DockButton(this, R.drawable.ic_video, "VIDEO")
        val vehicle = mapOf(VehicleModule.TRAILER to trailer, VehicleModule.AUX to aux, VehicleModule.FOG to fog, VehicleModule.VIDEO to video)
        vehicle.forEach { (m, b) -> b.setOnClickListener { app.vehicle.toggle(m) } }
        video.setOnClickListener { com.ridgeline.dock.vehicle.VideoInput.launch(this) }   // video input, not a relay
        honda.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        val dock = LinearLayout(this).apply {
            setBackgroundColor(0xFF000000.toInt()); gravity = Gravity.CENTER_VERTICAL
            listOf(trailer, aux, honda, fog, video).forEach { addView(it, LinearLayout.LayoutParams(0, -1, if (it === honda) 1.3f else 1f)) }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF000000.toInt())
            addView(content, LinearLayout.LayoutParams(-1, 0, 4f))
            addView(View(context).apply { setBackgroundColor(0xFFFFFFFF.toInt()) }, LinearLayout.LayoutParams(-1, dp(2)))   // white divider
            addView(dock, LinearLayout.LayoutParams(-1, 0, 1f))
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { app.status.bluetooth.collect { cluster.btState = it } }        // independent
                launch { app.status.wifiBars.collect { cluster.wifiBars = it } }        // independent
                launch { app.status.carPlayActive.collect { cluster.carPlayActive = it } } // independent
                launch { app.vehicle.states.collect { s -> vehicle.forEach { (m, b) -> b.setOn(s[m] == true) } } }
                launch { app.update.state.collect(::renderUpdate) }
            }
        }
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) @Suppress("DEPRECATION") window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}
