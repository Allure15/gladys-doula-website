package com.ridgeline.dock.vehicle

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ridgeline.dock.dockApp
import java.io.File
import java.io.FileOutputStream

enum class ActionType(val label: String, val hintA: String, val hintB: String, val hintC: String) {
    NONE("Not configured", "", "", ""),
    BROADCAST("Send broadcast", "Intent action (e.g. com.vendor.FOG)", "Boolean extra name (sent true/false)", "Package (optional)"),
    SERIAL("Write to serial port (ESP32 relay / MCU)", "Node@baud (e.g. /dev/ttyACM0@115200)", "ON command text (\\n = newline)", "OFF command text"),
    ESP32("ESP32 output (USB)", "Channel: FOG, CAML or CAMR", "", ""),
    APP("Launch app", "package/activity (flattened component)", "", "")
}

class ButtonConfig(val type: ActionType, val a: String, val b: String, val c: String)

/** Per-button action, stored in prefs and edited in Settings -> Configure buttons. */
object ButtonActions {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("buttons", Context.MODE_PRIVATE)

    fun load(ctx: Context, m: VehicleModule): ButtonConfig {
        val p = prefs(ctx)
        if (!p.contains("$m.type") && m == VehicleModule.FOG) return ButtonConfig(ActionType.ESP32, "FOG", "", "")
        return ButtonConfig(runCatching { ActionType.valueOf(p.getString("$m.type", "NONE")!!) }.getOrDefault(ActionType.NONE),
            p.getString("$m.a", "")!!, p.getString("$m.b", "")!!, p.getString("$m.c", "")!!)
    }

    fun save(ctx: Context, m: VehicleModule, c: ButtonConfig) {
        prefs(ctx).edit().putString("$m.type", c.type.name).putString("$m.a", c.a).putString("$m.b", c.b).putString("$m.c", c.c).apply()
    }

    /** Runs the configured action. Returns a short human-readable result for the toast. */
    fun execute(ctx: Context, m: VehicleModule, on: Boolean): String {
        val cfg = load(ctx, m)
        return try {
            when (cfg.type) {
                ActionType.NONE -> "$m: no action configured (Settings -> Configure buttons)"
                ActionType.BROADCAST -> {
                    ctx.sendBroadcast(Intent(cfg.a).apply {
                        if (cfg.c.isNotBlank()) setPackage(cfg.c)
                        if (cfg.b.isNotBlank()) putExtra(cfg.b, on)
                    })
                    "$m ${if (on) "ON" else "OFF"}: broadcast ${cfg.a}"
                }
                ActionType.SERIAL -> {
                    val (path, baud) = cfg.a.split('@').let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 115200) }
                    runCatching { Runtime.getRuntime().exec(arrayOf("sh", "-c", "stty -F $path $baud raw -echo")).waitFor() }
                    val text = (if (on) cfg.b else cfg.c).replace("\\n", "\n").replace("\\r", "\r")
                    FileOutputStream(File(path)).use { it.write(text.toByteArray()) }
                    "$m ${if (on) "ON" else "OFF"}: wrote to $path"
                }
                ActionType.ESP32 -> {
                    ctx.dockApp.espLink.send("SET ${cfg.a.trim().uppercase()} ${if (on) 1 else 0}")
                    "$m ${if (on) "ON" else "OFF"}: sent to ESP32 (${cfg.a})"
                }
                ActionType.APP -> {
                    ctx.startActivity(Intent(Intent.ACTION_MAIN).setComponent(ComponentName.unflattenFromString(cfg.a))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "$m: launched"
                }
            }
        } catch (e: Exception) { Log.w("Buttons", e); "$m FAILED: ${e.message}" }
    }
}
