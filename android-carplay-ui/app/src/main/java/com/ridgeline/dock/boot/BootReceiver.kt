package com.ridgeline.dock.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** BOOT_COMPLETED and MY_PACKAGE_REPLACED (after a self-update) -> early service -> UI. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.startForegroundService(Intent(context, ZLinkEarlyService::class.java))
    }
}
