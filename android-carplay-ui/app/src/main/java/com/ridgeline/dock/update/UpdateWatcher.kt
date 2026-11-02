package com.ridgeline.dock.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * Watches the LocalSend dir and <usb>/RidgelineUpdate. FileObserver gives instant reaction
 * to new files; ACTION_MEDIA_MOUNTED plus a 15 s rescan covers folders that didn't exist
 * yet when the stick was inserted.
 */
class UpdateWatcher(private val ctx: Context, dirs: List<File>, private val onApk: (File) -> Unit) {
    private var dirs = dirs
    private val observers = HashMap<File, FileObserver>()
    private val handler = Handler(Looper.getMainLooper())
    private val rescan = object : Runnable { override fun run() { scan(); handler.postDelayed(this, 15_000) } }
    private val mounted = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { dirs = UpdateConfig.watchDirs(ctx); handler.postDelayed({ scan() }, 2000) }
    }

    fun start() {
        ctx.registerReceiver(mounted, IntentFilter(Intent.ACTION_MEDIA_MOUNTED).apply { addDataScheme("file") })
        rescan.run()
    }

    fun stop() {
        handler.removeCallbacks(rescan); runCatching { ctx.unregisterReceiver(mounted) }
        observers.values.forEach { it.stopWatching() }; observers.clear()
    }

    private fun scan() {
        dirs = UpdateConfig.watchDirs(ctx)
        for (d in dirs) {
            if (!d.isDirectory) { observers.remove(d)?.stopWatching(); continue }
            d.listFiles { f -> f.extension.equals("apk", true) }?.maxByOrNull { it.lastModified() }?.let(onApk)
            if (d !in observers) {
                observers[d] = object : FileObserver(d, CLOSE_WRITE or MOVED_TO) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path?.endsWith(".apk", true) == true) handler.post { onApk(File(d, path)) }
                    }
                }.also { it.startWatching() }
            }
        }
    }
}
