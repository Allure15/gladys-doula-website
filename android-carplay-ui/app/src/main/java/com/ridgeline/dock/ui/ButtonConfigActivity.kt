package com.ridgeline.dock.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import com.ridgeline.dock.vehicle.ActionType
import com.ridgeline.dock.vehicle.ButtonActions
import com.ridgeline.dock.vehicle.ButtonConfig
import com.ridgeline.dock.vehicle.VehicleModule

/** Map Trailer / Aux / Fog to a real action. "Test" runs it immediately so you can check the wiring. */
class ButtonConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(32), dp(16), dp(32), dp(16)); setBackgroundColor(0xFF000000.toInt()) }
        col.addView(TextView(this).styled().apply { text = "Configure buttons"; textSize = 22f })
        for (m in listOf(VehicleModule.TRAILER, VehicleModule.AUX, VehicleModule.FOG)) {
            val cfg = ButtonActions.load(this, m)
            col.addView(TextView(this).styled().apply { text = m.name; textSize = 17f; setPadding(0, dp(14), 0, 0) })
            val types = ActionType.entries
            val spin = Spinner(this).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, types.map { it.label }); setSelection(types.indexOf(cfg.type)) }
            val a = EditText(this).styled().apply { setText(cfg.a); setSingleLine() }
            val b = EditText(this).styled().apply { setText(cfg.b); setSingleLine() }
            val c = EditText(this).styled().apply { setText(cfg.c); setSingleLine() }
            fun hints() { val t = types[spin.selectedItemPosition]
                a.hint = t.hintA; b.hint = t.hintB; c.hint = t.hintC
                a.visibility = if (t == ActionType.NONE) View.GONE else View.VISIBLE
                b.visibility = if (t.hintB.isEmpty()) View.GONE else View.VISIBLE
                c.visibility = if (t.hintC.isEmpty()) View.GONE else View.VISIBLE }
            spin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = hints()
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
            hints()
            fun current() = ButtonConfig(types[spin.selectedItemPosition], a.text.toString().trim(), b.text.toString(), c.text.toString())
            val row = LinearLayout(this).apply {
                addView(Button(context).apply { text = "Save"; setOnClickListener { ButtonActions.save(context, m, current()); Toast.makeText(context, "$m saved", Toast.LENGTH_SHORT).show() } })
                addView(Button(context).apply { text = "Test ON"; setOnClickListener { ButtonActions.save(context, m, current()); Thread { val r = ButtonActions.execute(context, m, true); runOnUiThread { Toast.makeText(context, r, Toast.LENGTH_LONG).show() } }.start() } })
                addView(Button(context).apply { text = "Test OFF"; setOnClickListener { ButtonActions.save(context, m, current()); Thread { val r = ButtonActions.execute(context, m, false); runOnUiThread { Toast.makeText(context, r, Toast.LENGTH_LONG).show() } }.start() } })
            }
            col.addView(spin); col.addView(a); col.addView(b); col.addView(c); col.addView(row)
        }
        setContentView(ScrollView(this).apply { addView(col) })
    }
}
