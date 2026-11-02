package com.ridgeline.dock.can

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ridgeline.dock.dockApp
import com.ridgeline.dock.overlay.TurnSignal
import com.ridgeline.dock.ui.dp
import com.ridgeline.dock.ui.styled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * CAN diagnostic. Workflow for the "Hiworld CAN box shows disconnect" problem:
 *  1. Set the serial node + baud, Start. If "NO DATA" persists, the box isn't passing frames
 *     (check CAN Pro profile / wiring) -> ESP32 fallback.
 *  2. Compare the raw-hex pane with the parsed pane to confirm the framing assumption.
 *  3. Switch a turn signal on: bits toggling at blinker cadence appear under "Blinker candidates".
 *  4. Tap a candidate and assign it LEFT / RIGHT / HAZARD. The overlay then follows the car.
 */
class CanSnifferActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var frames: TextView
    private lateinit var raw: TextView
    private lateinit var list: ListView
    private var cands = emptyList<CanAnalyzer.Candidate>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bus = dockApp.canBus
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)); setBackgroundColor(0xFF0B0D10.toInt()) }

        val path = EditText(this).styled().apply { setText(bus.path); setSingleLine() }
        val baud = EditText(this).styled().apply { setText(bus.baud.toString()); inputType = InputType.TYPE_CLASS_NUMBER; setSingleLine() }
        val mode = Button(this)
        fun modeText() { mode.text = if (bus.esp32Lines) "Source: ESP32 lines" else "Source: Hiworld/MCU frames" }
        modeText(); mode.setOnClickListener { bus.esp32Lines = !bus.esp32Lines; modeText() }
        val run = Button(this)
        fun runText() { run.text = if (bus.running) "Stop" else "Start" }
        runText()
        run.setOnClickListener {
            if (bus.running) bus.stop() else {
                bus.path = path.text.toString(); bus.baud = baud.text.toString().toIntOrNull() ?: bus.baud
                bus.analyzer.clear(); bus.start()
            }
            runText()
        }
        val auto = CheckBox(this).apply { text = "Auto-start at boot"; setTextColor(0xFFF2F4F7.toInt()); isChecked = bus.autoStart
            setOnCheckedChangeListener { _, c -> bus.autoStart = c } }
        root.addView(LinearLayout(this).apply {
            addView(path, LinearLayout.LayoutParams(0, WRAP_CONTENT, 2f)); addView(baud, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(mode); addView(run); addView(auto)
        })

        status = mono(); frames = mono(); raw = mono()
        list = ListView(this)
        root.addView(status); root.addView(frames); root.addView(raw)
        root.addView(TextView(this).apply { text = "Blinker candidates (tap to assign):"; setTextColor(0xFF3DDC84.toInt()) })
        root.addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)

        list.setOnItemClickListener { _, _, pos, _ ->
            val c = cands.getOrNull(pos) ?: return@setOnItemClickListener
            AlertDialog.Builder(this).setTitle(c.toString())
                .setItems(arrayOf("LEFT", "RIGHT", "HAZARD")) { _, w ->
                    dockApp.blinker.assign(c, TurnSignal.entries[w])
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                }.show()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) { refresh(); runText(); delay(500) }
            }
        }
    }

    private fun mono() = TextView(this).apply { typeface = Typeface.MONOSPACE; textSize = 12f; setTextColor(0xFFF2F4F7.toInt()) }

    private fun refresh() {
        val bus = dockApp.canBus
        val age = if (bus.lastRxMs == 0L) -1 else System.currentTimeMillis() - bus.lastRxMs
        status.text = buildString {
            append(if (!bus.running) "STOPPED" else if (age in 0..3000) "RECEIVING frames" else
                if (bus.rawBytes > 0) "BYTES BUT NO VALID FRAMES - framing assumption wrong?" else "NO DATA - box not passing CAN (disconnect?)")
            append("   raw bytes: ${bus.rawBytes}")
            bus.lastError?.let { append("\nERROR: $it") }
            append("\n").append(dockApp.blinker.describe())
        }
        frames.text = "Frames (by activity):\n" + bus.analyzer.summary().joinToString("\n")
        raw.text = "Raw tail:\n" + bus.rawLines().takeLast(5).joinToString("\n")
        cands = bus.analyzer.candidates()
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, cands.map { it.toString() })
    }
}
