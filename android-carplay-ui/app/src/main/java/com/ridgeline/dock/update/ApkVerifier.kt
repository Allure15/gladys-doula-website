package com.ridgeline.dock.update

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest

sealed interface Verdict {
    class Ok(val versionCode: Long, val versionName: String) : Verdict
    /** [silent] = expected/benign (e.g. same version already installed): don't show an error. */
    class Rejected(val reason: String, val silent: Boolean = false) : Verdict
}

/** Same package, strictly higher versionCode, identical signing certificate(s) as the installed app. */
object ApkVerifier {
    fun verify(ctx: Context, apk: File): Verdict {
        val pm = ctx.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val cand = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return Verdict.Rejected("Not a readable APK")
        if (cand.packageName != ctx.packageName) return Verdict.Rejected("Wrong package ${cand.packageName}", silent = true)
        val installed = pm.getPackageInfo(ctx.packageName, flags)
        if (cand.longVersionCode <= installed.longVersionCode)
            return Verdict.Rejected("Version ${cand.longVersionCode} is not newer than ${installed.longVersionCode}", silent = true)
        val a = certs(cand.signingInfo?.apkContentsSigners?.map { it.toByteArray() })
        val b = certs(installed.signingInfo?.apkContentsSigners?.map { it.toByteArray() })
        if (a.isEmpty() || a != b) return Verdict.Rejected("Signature mismatch - refusing to install")
        return Verdict.Ok(cand.longVersionCode, cand.versionName ?: "?")
    }

    private fun certs(l: List<ByteArray>?): Set<String> =
        l.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it).joinToString("") { b -> "%02x".format(b) } }.toSet()
}
