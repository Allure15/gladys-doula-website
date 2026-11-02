package com.ridgeline.dock.update

import android.content.Context
import java.io.File

object UpdateConfig {
    /** Folder on the USB stick that holds the update APK (<usb>/RidgelineUpdate). */
    const val USB_FOLDER = "RidgelineUpdate"
    /** LocalSend receive folder (port 53317, auto-accept). */
    const val LOCALSEND_DIR = "/update"
    const val COUNTDOWN_SECONDS = 10

    fun watchDirs(ctx: Context): List<File> {
        val usb = File("/storage").listFiles().orEmpty()
            .filter { it.isDirectory && it.name != "self" && it.name != "emulated" }
            .map { File(it, USB_FOLDER) }
        return listOf(File(LOCALSEND_DIR)) + usb
    }
}
