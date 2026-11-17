package com.krishna.miniremote

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * In-app updates from the repo's `latest-build` release. CI publishes `version.json`
 * ({versionCode, versionName, sha256}) next to MiniRemote.apk.
 *
 * Safety: HTTPS only, the APK must match the published SHA-256, and Android itself refuses
 * to install it over this app unless it carries the same signing key. The user always
 * confirms the install in Android's own dialog.
 */
object Updater {
    private const val BASE = "https://github.com/krishnaar21/mini-remote/releases/download/latest-build/"
    private const val MAX_APK = 60L * 1024 * 1024

    data class Info(val code: Long, val name: String, val sha256: String)

    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }

    fun installedCode(ctx: Context): Long = runCatching {
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
    }.getOrDefault(0L)

    /** The published build, or null if there's none or it isn't newer than this one. */
    suspend fun check(ctx: Context): Info? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(BASE + "version.json").header("Cache-Control", "no-cache").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@withContext null
            val body = r.body?.string()?.take(4096) ?: return@withContext null
            val j = JSONObject(body)
            val info = Info(j.getLong("versionCode"), j.optString("versionName", ""), j.getString("sha256").lowercase())
            require(info.sha256.matches(Regex("[0-9a-f]{64}"))) { "Bad checksum in version.json" }
            info.takeIf { it.code > installedCode(ctx) }
        }
    }

    private fun dir(ctx: Context) = File(ctx.cacheDir, "updates").apply { mkdirs() }

    /** Downloads (or reuses) the APK for [info], checking its SHA-256. Reports progress 0..1. */
    suspend fun download(ctx: Context, info: Info, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val out = File(dir(ctx), "MiniRemote-${info.code}.apk")
        if (out.exists() && sha256(out) == info.sha256) return@withContext out
        // Old downloads are useless once a newer one exists.
        dir(ctx).listFiles()?.forEach { it.delete() }
        val tmp = File(dir(ctx), "download.part")
        http.newCall(Request.Builder().url(BASE + "MiniRemote.apk").build()).execute().use { r ->
            if (!r.isSuccessful) error("Download failed (${r.code})")
            val body = r.body ?: error("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: -1L
            if (total > MAX_APK) error("Download too large")
            val md = MessageDigest.getInstance("SHA-256")
            var got = 0L
            body.byteStream().use { inp ->
                tmp.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = inp.read(buf); if (n < 0) break
                        got += n
                        if (got > MAX_APK) error("Download too large")
                        md.update(buf, 0, n); o.write(buf, 0, n)
                        if (total > 0) progress(got.toFloat() / total)
                    }
                }
            }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            if (hex != info.sha256) { tmp.delete(); error("Download didn't match its checksum") }
        }
        if (!tmp.renameTo(out)) error("Couldn't save the update")
        out
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Whether [apk] is signed with the same key as this installed app. Android refuses to install
     * an update signed differently, so there's no point offering it. Unknown means yes.
     */
    @Suppress("DEPRECATION")
    fun sameSigner(ctx: Context, apk: File): Boolean = runCatching {
        val pm = ctx.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)?.signingInfo
                ?: return@runCatching true
            val certs = if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
            certs.isNullOrEmpty() || certs.any { pm.hasSigningCertificate(ctx.packageName, it.toByteArray(), PackageManager.CERT_INPUT_RAW_X509) }
        } else {
            val theirs = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNATURES)?.signatures
                ?: return@runCatching true
            val mine = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty()
            theirs.toSet() == mine.toSet()
        }
    }.getOrDefault(true)

    /** Android 8+ asks once per app for "install unknown apps". */
    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Hands the APK to Android's installer, which shows its own confirm screen. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
