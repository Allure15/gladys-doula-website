package com.ridgeline.dock.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ridgeline.dock.can.CanSnifferActivity
import com.ridgeline.dock.dockApp
import com.ridgeline.dock.update.UpdateConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Opened by the Honda H button. Radio-level tools: CAN diagnostic + manual update fallback. */
class SettingsActivity : ComponentActivity() {
    private val fileManagers = listOf("com.mediatek.filemanager", "com.softwinner.fileexplorer",
        "com.android.rk", "com.estrongs.android.pop", "com.android.documentsui")

    private val pick = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val f = withContext(Dispatchers.IO) {
                File(cacheDir, "manual.apk").also { out -> contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { i.copyTo(it) } } }
            }
            dockApp.update.offer(f, manual = true)      // same signature + version checks
            Toast.makeText(this@SettingsActivity, "Update staged for verification", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(32), dp(24), dp(32), dp(24)); setBackgroundColor(0xFF0B0D10.toInt())
        }
        fun row(text: String, action: () -> Unit) = col.addView(Button(this).apply { this.text = text; setOnClickListener { action() } })
        col.addView(TextView(this).styled().apply { text = "Radio settings"; textSize = 22f })
        col.addView(TextView(this).apply { setTextColor(Color.LTGRAY); text = "Zlink app: ${com.ridgeline.dock.zlink.ZLinkConfig.detected(this@SettingsActivity)}" })
        row("Launch Zlink now") { com.ridgeline.dock.zlink.ZLinkConfig.launchUi(this) }
        row("CAN sniffer / diagnostic") { startActivity(Intent(this, CanSnifferActivity::class.java)) }
        row("Configure buttons (Trailer / Aux / Fog)") { startActivity(Intent(this, ButtonConfigActivity::class.java)) }
        row("Open file manager (update folder)") { openFileManager() }
        row("Pick update APK...") { pick.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) }
        row("Set video input app (now: ${com.ridgeline.dock.vehicle.VideoInput.current(this)})") { com.ridgeline.dock.vehicle.VideoInput.pick(this) }
        row("System settings") { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        row("Close") { finish() }
        col.addView(TextView(this).apply {
            setTextColor(Color.GRAY)
            text = "Updates: LocalSend -> ${UpdateConfig.LOCALSEND_DIR}, or USB -> <stick>/${UpdateConfig.USB_FOLDER}/*.apk"
        })
        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun openFileManager() {
        val intent = fileManagers.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
            ?: Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        try { startActivity(intent) } catch (e: ActivityNotFoundException) { Toast.makeText(this, "No file manager found", Toast.LENGTH_LONG).show() }
    }
}
