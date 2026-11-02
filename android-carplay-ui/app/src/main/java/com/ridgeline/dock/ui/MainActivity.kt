package com.ridgeline.dock.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.ridgeline.dock.boot.ZLinkEarlyService

/**
 * Launcher icon = "start everything". Has no UI of its own: asks for the overlay permission
 * if missing, otherwise (re)starts the service, which launches Zlink and shows the dock.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow 'Display over other apps' for Ridgeline Dock, then open it again", Toast.LENGTH_LONG).show()
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        } else {
            startForegroundService(Intent(this, ZLinkEarlyService::class.java))
        }
        finish()
    }
}
