package com.ridgeline.dock.esp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.ridgeline.dock.overlay.TurnSignal
import com.ridgeline.dock.overlay.VehicleSignals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TurnState { NONE, LEFT, RIGHT, HAZARD }

/**
 * USB-serial link to the ESP32 IO board (firmware: esp32/ridgeline_io). Its own channel:
 * does not touch Bluetooth, Wi-Fi or ZLink. Auto-connects, auto-reconnects, and treats
 * 10 s of silence (the board says HELLO every 3 s) as a dead link.
 */
class EspLink(private val ctx: Context) {
    private val usb = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
    private val handler = Handler(Looper.getMainLooper())
    private val _status = MutableStateFlow("ESP32: waiting for USB device")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _turn = MutableStateFlow(TurnState.NONE)
    val turn: StateFlow<TurnState> = _turn.asStateFlow()

    private var port: UsbSerialPort? = null
    private var io: SerialInputOutputManager? = null
    private var lastRx = 0L
    private var leftOn = false
    private var rightOn = false
    private val line = StringBuilder()
    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> close("ESP32: USB device removed")
                else -> connect()   // attached, or permission answered
            }
        }
    }

    private val listener = object : SerialInputOutputManager.Listener {
        override fun onNewData(data: ByteArray) { handler.post { feed(data) } }
        override fun onRunError(e: Exception) { handler.post { close("ESP32: link error (${e.message})") } }
    }

    fun start() {
        if (started) return
        started = true
        ctx.registerReceiver(receiver, IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED); addAction(ACTION_PERM)
        })
        tick()
    }

    private fun tick() {
        if (port == null) connect()
        else if (System.currentTimeMillis() - lastRx > 10_000) close("ESP32: no data for 10 s")
        handler.postDelayed({ tick() }, 4000)
    }

    @Synchronized
    private fun connect() {
        if (port != null) return
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usb).firstOrNull()
        if (driver == null) { _status.value = "ESP32: no USB serial device found"; return }
        if (!usb.hasPermission(driver.device)) {
            _status.value = "ESP32: waiting for USB permission (tap OK on the radio)"
            val mut = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            usb.requestPermission(driver.device, PendingIntent.getBroadcast(ctx, 0, Intent(ACTION_PERM).setPackage(ctx.packageName), mut))
            return
        }
        val conn = usb.openDevice(driver.device)
        if (conn == null) { _status.value = "ESP32: could not open USB device"; return }
        try {
            val p = driver.ports[0]
            p.open(conn)
            p.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            // DTR/RTS wired to EN/GPIO0 on dev boards: toggling them would RESET the ESP32 (fog flicker)
            runCatching { p.setDTR(false); p.setRTS(false) }
            port = p
            lastRx = System.currentTimeMillis()
            io = SerialInputOutputManager(p, listener).also { it.start() }
            _status.value = "ESP32: connected, waiting for HELLO"
        } catch (e: Exception) {
            Log.w("EspLink", e); _status.value = "ESP32: open failed (${e.message})"
            runCatching { conn.close() }; port = null
        }
    }

    @Synchronized
    private fun close(reason: String) {
        runCatching { io?.stop() }; runCatching { port?.close() }
        io = null; port = null
        leftOn = false; rightOn = false; _turn.value = TurnState.NONE
        _status.value = reason
    }

    /** Blocking write: call from a background thread. Throws if not connected. */
    fun send(text: String) {
        val p = port ?: throw IllegalStateException("ESP32 not connected")
        p.write((text + "\n").toByteArray(), 500)
    }

    private fun feed(data: ByteArray) {
        lastRx = System.currentTimeMillis()
        for (b in data) {
            val ch = b.toInt().toChar()
            if (ch == '\n') { handle(line.toString().trim()); line.setLength(0) }
            else if (line.length < 200) line.append(ch)
        }
    }

    private fun handle(s: String) {
        val t = s.split(' ')
        when (t.firstOrNull()) {
            "HELLO" -> _status.value = "ESP32: connected (${t.getOrNull(1) ?: "?"} v${t.getOrNull(2) ?: "?"})"
            "T" -> {
                val on = t.getOrNull(2) == "1"
                when (t.getOrNull(1)) { "L" -> leftOn = on; "R" -> rightOn = on; else -> return }
                _turn.value = when { leftOn && rightOn -> TurnState.HAZARD; leftOn -> TurnState.LEFT; rightOn -> TurnState.RIGHT; else -> TurnState.NONE }
                if (on) VehicleSignals.turn.tryEmit(when (_turn.value) {
                    TurnState.HAZARD -> TurnSignal.HAZARD; TurnState.LEFT -> TurnSignal.LEFT; TurnState.RIGHT -> TurnSignal.RIGHT; else -> return
                })
            }
            "ACK", "STATE", "PONG" -> Log.i("EspLink", s)
        }
    }

    companion object { const val ACTION_PERM = "com.ridgeline.dock.USB_PERMISSION" }
}
