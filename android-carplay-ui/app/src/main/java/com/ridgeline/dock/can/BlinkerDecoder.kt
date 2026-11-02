package com.ridgeline.dock.can

import android.content.Context
import com.ridgeline.dock.overlay.TurnSignal
import com.ridgeline.dock.overlay.VehicleSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Maps learned (frame id, byte, mask) -> turn signal and feeds the overlay. */
class BlinkerDecoder(ctx: Context, private val bus: CanBus) {
    class Rule(val id: Int, val byteIdx: Int, val mask: Int, val signal: TurnSignal)

    private val prefs = ctx.getSharedPreferences("blinker", Context.MODE_PRIVATE)
    @Volatile private var rules: List<Rule> = load()

    fun assign(c: CanAnalyzer.Candidate, signal: TurnSignal) {
        val kept = rules.filter { it.signal != signal }
        rules = kept + Rule(c.id, c.byteIdx, c.mask, signal)
        prefs.edit().putString("rules", rules.joinToString(";") { "${it.id},${it.byteIdx},${it.mask},${it.signal}" }).apply()
    }

    fun describe() = rules.joinToString("\n") { "${it.signal}: id 0x%02X byte ${it.byteIdx} mask 0x%02X".format(it.id, it.mask) }
        .ifEmpty { "No blinker mapping learned yet" }

    fun start(scope: CoroutineScope) {
        scope.launch {
            bus.frames.collect { f ->
                for (r in rules) if (r.id == f.id && f.data.size > r.byteIdx &&
                    (f.data[r.byteIdx].toInt() and r.mask) != 0) VehicleSignals.turn.tryEmit(r.signal)
            }
        }
    }

    private fun load(): List<Rule> = prefs.getString("rules", "")!!.split(';').mapNotNull {
        val p = it.split(','); if (p.size != 4) null
        else runCatching { Rule(p[0].toInt(), p[1].toInt(), p[2].toInt(), TurnSignal.valueOf(p[3])) }.getOrNull()
    }
}
