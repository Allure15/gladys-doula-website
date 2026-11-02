package com.ridgeline.dock.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.ridgeline.dock.dockApp

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.dockApp.update.onInstallResult(
            intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1),
            intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        )
    }
}
