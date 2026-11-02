package com.ridgeline.dock.can

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File
import java.io.FileInputStream

/**
 * Reads the CAN feed from a serial node and fans it out. The same class serves the
 * Hiworld/MCU path (/dev/tty* from the MCU) and the ESP32 fallback (USB-serial, line format).
 * Android has no public serial API: the app must be able to open the node (system app,
 * or `chmod 666` it from init.rc / `su`). Baud is applied best-effort via `stty`.
 */
class CanBus(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("can", Context.MODE_PRIVATE)
    val frames = MutableSharedFlow<CanFrame>(extraBufferCapacity = 256)
    val analyzer = CanAnalyzer()

    @Volatile var running = false; private set
    @Volatile var lastRxMs = 0L; private set
    @Volatile var lastError: String? = null; private set
    @Volatile var rawBytes = 0L; private set
    private val rawTail = ArrayDeque<String>()
    private var stream: FileInputStream? = null

    var path: String get() = prefs.getString("path", "/dev/ttyS1")!!; set(v) = prefs.edit().putString("path", v).apply()
    var baud: Int get() = prefs.getInt("baud", 38400); set(v) = prefs.edit().putInt("baud", v).apply()
    var esp32Lines: Boolean get() = prefs.getBoolean("esp32", false); set(v) = prefs.edit().putBoolean("esp32", v).apply()
    var autoStart: Boolean get() = prefs.getBoolean("auto", false); set(v) = prefs.edit().putBoolean("auto", v).apply()

    fun startIfEnabled() { if (autoStart) start() }

    @Synchronized fun start() {
        if (running) return
        running = true; lastError = null
        val parser: FrameParser = if (esp32Lines) LineFrameParser() else McuFrameParser()
        val p = path; val b = baud
        Thread({
            try {
                runCatching { Runtime.getRuntime().exec(arrayOf("sh", "-c", "stty -F $p $b raw -echo")).waitFor() }
                val s = FileInputStream(File(p)); stream = s
                val buf = ByteArray(256)
                while (running) {
                    val n = s.read(buf); if (n < 0) break
                    rawBytes += n
                    synchronized(rawTail) {
                        rawTail.addLast(buf.copyOf(n).joinToString(" ") { "%02X".format(it) })
                        while (rawTail.size > 24) rawTail.removeFirst()
                    }
                    parser.feed(buf, n) { f -> lastRxMs = f.tsMs; analyzer.onFrame(f); frames.tryEmit(f) }
                }
            } catch (e: Exception) {
                if (running) { lastError = e.toString(); Log.w("CanBus", e) }
            } finally { running = false }
        }, "can-reader").start()
    }

    @Synchronized fun stop() { running = false; runCatching { stream?.close() } }

    fun rawLines(): List<String> = synchronized(rawTail) { rawTail.toList() }
}
