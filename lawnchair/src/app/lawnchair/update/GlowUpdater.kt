/*
 * Copyright 2026 Glow Launcher contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.update

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.android.launcher3.BuildConfig
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONArray

/**
 * Glow over-the-air updates from the GitHub releases of the Glow repository.
 *
 * Only the GitHub channel with a release tag updates itself (Play builds must not,
 * local builds have a different package). Every download is checked before it is
 * handed to the system installer:
 *  - only HTTPS to GitHub hosts, redirects included;
 *  - the size and SHA-256 published next to the APK;
 *  - same package name, a higher versionCode and the same signing certificate as
 *    the installed Glow.
 */
object GlowUpdater {

    private const val TAG = "GlowUpdater"
    private const val RELEASES_URL =
        "https://api.github.com/repos/chvdbhcx-del/glow-launcher/releases?per_page=20"
    private const val AUTO_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
    private const val MAX_API_BYTES = 2L * 1024 * 1024
    private const val MAX_APK_BYTES = 200L * 1024 * 1024
    private const val PREFS = "glow_updater"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_DISMISSED = "dismissed_tag"
    private const val KEY_AUTO = "auto_check"

    @Volatile private var busy = false
    @Volatile private var cancelled = false

    private val ownTag: String get() = BuildConfig.GLOW_RELEASE_TAG

    /** Self-updates only make sense for GitHub builds that know their own release tag. */
    @JvmStatic
    fun isSupported(): Boolean =
        BuildConfig.FLAVOR_channel == "github" && ownTag.isNotEmpty()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @JvmStatic
    fun maybeAutoCheck(launcher: Launcher) {
        if (!isSupported() || busy) return
        val prefs = prefs(launcher)
        if (!prefs.getBoolean(KEY_AUTO, true)) return
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < AUTO_CHECK_INTERVAL_MS) return
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        // Leftovers of an earlier update are no longer needed once we run again.
        val appContext = launcher.applicationContext
        THREAD_POOL_EXECUTOR.execute { updatesDir(appContext).listFiles()?.forEach { it.delete() } }
        check(launcher, manual = false)
    }

    @JvmStatic
    fun checkNow(launcher: Launcher) {
        if (!isSupported()) {
            Toast.makeText(launcher, R.string.glow_update_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        prefs(launcher).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        Toast.makeText(launcher, R.string.glow_update_checking, Toast.LENGTH_SHORT).show()
        check(launcher, manual = true)
    }

    private fun check(launcher: Launcher, manual: Boolean) {
        if (busy) return
        busy = true
        THREAD_POOL_EXECUTOR.execute {
            val result = runCatching { findNewer() }
            MAIN_EXECUTOR.execute {
                busy = false
                if (launcher.isDestroyed || launcher.isFinishing) return@execute
                result.onFailure {
                    Log.w(TAG, "Update check failed", it)
                    if (manual) {
                        Toast.makeText(launcher, R.string.glow_update_failed, Toast.LENGTH_LONG).show()
                    }
                }
                result.onSuccess { release ->
                    if (release == null) {
                        if (manual) {
                            Toast.makeText(launcher, R.string.glow_update_latest, Toast.LENGTH_SHORT).show()
                        }
                        return@onSuccess
                    }
                    if (!manual && prefs(launcher).getString(KEY_DISMISSED, null) == release.tag) {
                        return@onSuccess
                    }
                    offer(launcher, release, manual)
                }
            }
        }
    }

    private fun open(url: URL, accept: String): HttpURLConnection {
        var current = url
        var redirects = 0
        while (true) {
            if (!GlowUpdateLogic.isAllowedUrl(current.protocol, current.host)) {
                throw IOException("Blocked update host: ${current.protocol}://${current.host}")
            }
            val conn = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 20000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Glow-Launcher-Updater")
                setRequestProperty("Accept", accept)
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location == null || ++redirects > 5) throw IOException("Bad redirect")
                current = URL(current, location)
                continue
            }
            if (code != 200) {
                conn.disconnect()
                throw IOException("HTTP $code")
            }
            return conn
        }
    }

    private fun readText(url: String, accept: String): String {
        val conn = open(URL(url), accept)
        try {
            if (conn.contentLengthLong > MAX_API_BYTES) throw IOException("Response too large")
            return conn.inputStream.bufferedReader().use { reader ->
                val out = StringBuilder()
                val buf = CharArray(8192)
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    out.append(buf, 0, n)
                    if (out.length > MAX_API_BYTES) throw IOException("Response too large")
                }
                out.toString()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun findNewer(): GlowUpdateLogic.Release? {
        val array = JSONArray(readText(RELEASES_URL, "application/vnd.github+json"))
        val releases = (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val assets = o.optJSONArray("assets") ?: return@mapNotNull null
            val list = (0 until assets.length()).map { assets.getJSONObject(it) }
            val apk = list.firstOrNull { it.optString("name").endsWith(".apk") } ?: return@mapNotNull null
            val sha = list.firstOrNull { it.optString("name") == apk.optString("name") + ".sha256" }
            GlowUpdateLogic.Release(
                tag = o.optString("tag_name"),
                apkUrl = apk.optString("browser_download_url"),
                sha256Url = sha?.optString("browser_download_url"),
                apkSize = apk.optLong("size", -1L),
                draft = o.optBoolean("draft"),
            )
        }
        return GlowUpdateLogic.pickNewer(releases, ownTag)
    }

    private fun offer(launcher: Launcher, release: GlowUpdateLogic.Release, manual: Boolean) {
        val name = release.tag.removePrefix("v")
        val builder = AlertDialog.Builder(launcher, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(launcher.getString(R.string.glow_update_available, name))
            .setMessage(R.string.glow_update_message)
            .setPositiveButton(R.string.glow_update_install) { _, _ -> download(launcher, release) }
            .setNegativeButton(R.string.glow_update_later) { _, _ ->
                prefs(launcher).edit().putString(KEY_DISMISSED, release.tag).apply()
            }
        if (!manual) {
            builder.setNeutralButton(R.string.glow_update_never_auto) { _, _ ->
                prefs(launcher).edit().putBoolean(KEY_AUTO, false).apply()
                Toast.makeText(launcher, R.string.glow_update_auto_off, Toast.LENGTH_LONG).show()
            }
        } else if (!prefs(launcher).getBoolean(KEY_AUTO, true)) {
            builder.setNeutralButton(R.string.glow_update_auto_on) { _, _ ->
                prefs(launcher).edit().putBoolean(KEY_AUTO, true).apply()
            }
        }
        builder.show()
    }

    private fun download(launcher: Launcher, release: GlowUpdateLogic.Release) {
        if (!canInstall(launcher)) {
            Toast.makeText(launcher, R.string.glow_update_allow_install, Toast.LENGTH_LONG).show()
            runCatching {
                launcher.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${launcher.packageName}".toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        val progress = ProgressBar(launcher, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        cancelled = false
        val dialog = AlertDialog.Builder(launcher, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.glow_update_downloading)
            .setView(progress)
            .setCancelable(false)
            .setNegativeButton(R.string.glow_cancel) { _, _ -> cancelled = true }
            .show()
        val appContext = launcher.applicationContext
        THREAD_POOL_EXECUTOR.execute {
            val file = runCatching {
                val expectedSha = GlowUpdateLogic.parseSha256(
                    readText(release.sha256Url ?: throw IOException("No checksum"), "application/octet-stream"),
                ) ?: throw IOException("Bad checksum file")
                val apk = downloadApk(appContext, release) { fraction ->
                    MAIN_EXECUTOR.execute {
                        progress.isIndeterminate = false
                        progress.progress = (fraction * 100).toInt()
                    }
                }
                verifyApk(appContext, apk, expectedSha)
                apk
            }
            MAIN_EXECUTOR.execute {
                runCatching { dialog.dismiss() }
                file.onSuccess { install(launcher, it) }
                file.onFailure {
                    updatesDir(appContext).listFiles()?.forEach { f -> f.delete() }
                    if (cancelled) return@onFailure
                    Log.w(TAG, "Update failed", it)
                    val msg = if (it is SecurityException) R.string.glow_update_rejected else R.string.glow_update_failed
                    if (!launcher.isDestroyed) Toast.makeText(launcher, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updatesDir(context: Context) = File(context.cacheDir, "updates")

    private fun downloadApk(
        context: Context,
        release: GlowUpdateLogic.Release,
        onProgress: (Float) -> Unit,
    ): File {
        val dir = updatesDir(context).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        if (release.apkSize > MAX_APK_BYTES) throw IOException("APK too large")
        val free = runCatching { StatFs(dir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (release.apkSize > 0 && free < release.apkSize * 2) throw IOException("Not enough space")
        val target = File(dir, "Glow-update.apk")
        val partial = File(dir, "Glow-update.apk.part")
        val conn = open(URL(release.apkUrl), "application/octet-stream")
        var done = 0L
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            if (total > MAX_APK_BYTES) throw IOException("APK too large")
            conn.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var lastReported = -1
                    while (true) {
                        if (cancelled) throw IOException("Cancelled")
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (done > MAX_APK_BYTES) throw IOException("APK too large")
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress(done.toFloat() / total)
                            }
                        }
                    }
                }
            }
            if (release.apkSize > 0 && done != release.apkSize) throw IOException("Incomplete download")
        } catch (e: Exception) {
            partial.delete()
            throw e
        } finally {
            conn.disconnect()
        }
        target.delete()
        if (!partial.renameTo(target)) throw IOException("Could not save the update")
        return target
    }

    /** Throws [SecurityException] when the APK is not a genuine newer Glow build. */
    private fun verifyApk(context: Context, file: File, expectedSha: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        if (GlowUpdateLogic.toHex(digest.digest()) != expectedSha) {
            throw SecurityException("Checksum mismatch")
        }
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val apk = pm.getPackageArchiveInfo(file.path, flags) ?: throw SecurityException("Not an APK")
        val own = pm.getPackageInfo(context.packageName, flags)
        if (apk.packageName != context.packageName) throw SecurityException("Wrong package")
        if (versionCodeOf(apk) <= versionCodeOf(own)) throw SecurityException("Not a newer version")
        val apkCerts = certDigests(apk)
        // Same key, or a rotation that the installed build already trusts (APK Signature Scheme v3)
        if (apkCerts.isEmpty() || apkCerts.intersect(certDigests(own)).isEmpty()) {
            throw SecurityException("Signature mismatch")
        }
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun certDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            info.signatures
        } ?: return emptySet()
        return signatures.map {
            GlowUpdateLogic.toHex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()))
        }.toSet()
    }

    private fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    private fun install(launcher: Launcher, file: File) {
        val uri = FileProvider.getUriForFile(launcher, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        runCatching {
            launcher.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            Toast.makeText(launcher, R.string.glow_update_failed, Toast.LENGTH_LONG).show()
        }
    }
}
