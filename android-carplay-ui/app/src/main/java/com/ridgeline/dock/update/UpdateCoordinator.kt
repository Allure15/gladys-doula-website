package com.ridgeline.dock.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

sealed interface UpdateState {
    object Idle : UpdateState
    class Staged(val versionName: String, val waitingForIdle: Boolean, val secondsLeft: Int) : UpdateState
    object Installing : UpdateState
    class Failed(val reason: String) : UpdateState
}

/**
 * Pipeline shared by LocalSend, USB and the manual picker:
 * offer(file) -> verify (package/version/signature) -> stage -> wait for CarPlay idle
 * -> countdown -> PackageInstaller. Only THIS UI package is replaced; ZLink is untouched,
 * and MY_PACKAGE_REPLACED relaunches the UI via BootReceiver.
 * "Install now" overrides the idle wait (a CarPlay session may last the whole drive).
 */
class UpdateCoordinator(private val ctx: Context, private val carPlayActive: StateFlow<Boolean>, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null
    private var lastOffer = ""
    private val stagedFile get() = File(ctx.filesDir, "staged/ui.apk")

    fun offer(src: File, manual: Boolean = false) {
        scope.launch {
            val key = "${src.absolutePath}:${src.lastModified()}:${src.length()}"
            if (!manual && key == lastOffer) return@launch
            lastOffer = key
            val verdict = withContext(Dispatchers.IO) {
                when (val v = ApkVerifier.verify(ctx, src)) {
                    is Verdict.Ok -> { stagedFile.parentFile!!.mkdirs(); src.copyTo(stagedFile, overwrite = true); v }
                    else -> v
                }
            }
            when (verdict) {
                is Verdict.Rejected -> if (!verdict.silent || manual) _state.value = UpdateState.Failed(verdict.reason)
                is Verdict.Ok -> countdown(verdict.versionName)
            }
        }
    }

    private fun countdown(name: String) {
        job?.cancel()
        job = scope.launch {
            var left = UpdateConfig.COUNTDOWN_SECONDS
            while (left > 0) {
                if (carPlayActive.value) { left = UpdateConfig.COUNTDOWN_SECONDS; _state.value = UpdateState.Staged(name, true, left) }
                else { _state.value = UpdateState.Staged(name, false, left); left-- }
                delay(1000)
            }
            install()
        }
    }

    fun installNow() { job?.cancel(); scope.launch { install() } }
    fun cancel() { job?.cancel(); stagedFile.delete(); _state.value = UpdateState.Idle }

    private suspend fun install() {
        _state.value = UpdateState.Installing
        withContext(Dispatchers.IO) {
            try {
                val f = stagedFile
                val pi = ctx.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setAppPackageName(ctx.packageName)
                val id = pi.createSession(params)
                pi.openSession(id).use { s ->
                    f.inputStream().use { i -> s.openWrite("ui.apk", 0, f.length()).use { o -> i.copyTo(o); s.fsync(o) } }
                    val mut = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                    val pend = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallResultReceiver::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or mut)
                    s.commit(pend.intentSender)
                }
            } catch (e: Exception) { _state.value = UpdateState.Failed(e.message ?: e.toString()) }
        }
    }

    fun onInstallResult(status: Int, message: String?, confirm: Intent?) {
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->   // not a priv-app: Android asks the user
                confirm?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            PackageInstaller.STATUS_SUCCESS -> Unit          // process is replaced; BootReceiver relaunches UI
            else -> _state.value = UpdateState.Failed(message ?: "Install failed ($status)")
        }
    }
}
