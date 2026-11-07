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
import android.os.Build
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
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray

/**
 * Glow over-the-air updates from the GitHub releases of the Glow repository.
 *
 * - Automatic: when the home screen resumes, at most every [AUTO_CHECK_INTERVAL_MS],
 *   Glow looks for a newer release and offers it once per version.
 * - Manual: "Check for updates" in the edit menu checks right away.
 *
 * Test builds (tags like `v1.0.0-test15`) follow newer test builds; signed
 * releases follow newer signed releases. The APK is downloaded to the cache
 * and handed to the system installer.
 */
object GlowUpdater {

    private const val TAG = "GlowUpdater"
    private const val RELEASES_URL =
        "https://api.github.com/repos/chvdbhcx-del/glow-launcher/releases?per_page=20"
    private const val AUTO_CHECK_INTERVAL_MS = 3L * 60 * 60 * 1000
    private const val PREFS = "glow_updater"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_DISMISSED = "dismissed_tag"

    private data class Release(val tag: String, val apkUrl: String, val notes: String)

    @Volatile private var busy = false

    private val ownTag: String get() = BuildConfig.GLOW_RELEASE_TAG

    @JvmStatic
    fun maybeAutoCheck(launcher: Launcher) {
        val prefs = launcher.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < AUTO_CHECK_INTERVAL_MS) return
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        check(launcher, manual = false)
    }

    @JvmStatic
    fun checkNow(launcher: Launcher) {
        launcher.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
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
                    val prefs = launcher.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    if (!manual && prefs.getString(KEY_DISMISSED, null) == release.tag) return@onSuccess
                    offer(launcher, release)
                }
            }
        }
    }

    private fun findNewer(): Release? {
        val conn = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Glow-Launcher-Updater")
        }
        val body = try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val array = JSONArray(body)
        val releases = (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            if (o.optBoolean("draft")) return@mapNotNull null
            val assets = o.optJSONArray("assets") ?: return@mapNotNull null
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?: return@mapNotNull null
            Release(o.getString("tag_name"), apk.getString("browser_download_url"), o.optString("body"))
        }
        val own = ownTag
        val ownTest = testNumber(own)
        return if (own.isEmpty() || ownTest != null) {
            // Test channel (or a local build): the highest test number wins.
            releases.filter { testNumber(it.tag) != null }
                .maxByOrNull { testNumber(it.tag)!! }
                ?.takeIf { ownTest == null || testNumber(it.tag)!! > ownTest }
        } else {
            releases.filter { testNumber(it.tag) == null }
                .maxWithOrNull(Comparator { a, b -> compareVersions(a.tag, b.tag) })
                ?.takeIf { compareVersions(it.tag, own) > 0 }
        }
    }

    private fun testNumber(tag: String): Int? =
        Regex("-test(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull()

    private fun compareVersions(a: String, b: String): Int {
        fun parts(v: String) = v.removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    private fun offer(launcher: Launcher, release: Release) {
        val name = release.tag.removePrefix("v")
        AlertDialog.Builder(launcher, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(launcher.getString(R.string.glow_update_available, name))
            .setMessage(R.string.glow_update_message)
            .setPositiveButton(R.string.glow_update_install) { _, _ -> download(launcher, release) }
            .setNegativeButton(R.string.glow_update_later) { _, _ ->
                launcher.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_DISMISSED, release.tag).apply()
            }
            .show()
    }

    private fun download(launcher: Launcher, release: Release) {
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
        val dialog = AlertDialog.Builder(launcher, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.glow_update_downloading)
            .setView(progress)
            .setCancelable(false)
            .show()
        val appContext = launcher.applicationContext
        THREAD_POOL_EXECUTOR.execute {
            val file = runCatching {
                downloadApk(appContext, release.apkUrl) { fraction ->
                    MAIN_EXECUTOR.execute {
                        progress.isIndeterminate = false
                        progress.progress = (fraction * 100).toInt()
                    }
                }
            }
            MAIN_EXECUTOR.execute {
                runCatching { dialog.dismiss() }
                file.onSuccess { install(launcher, it) }
                file.onFailure {
                    Log.w(TAG, "Download failed", it)
                    Toast.makeText(launcher, R.string.glow_update_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun downloadApk(context: Context, url: String, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "Glow-update.apk")
        val partial = File(dir, "Glow-update.apk.part")
        var current = URL(url)
        var conn: HttpURLConnection
        var redirects = 0
        while (true) {
            conn = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 30000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Glow-Launcher-Updater")
                setRequestProperty("Accept", "application/octet-stream")
            }
            val code = conn.responseCode
            if (code in 300..399 && redirects < 5) {
                val location = conn.getHeaderField("Location") ?: error("Redirect without location")
                conn.disconnect()
                current = URL(current, location)
                redirects++
                continue
            }
            if (code != 200) {
                conn.disconnect()
                error("HTTP $code")
            }
            break
        }
        try {
            val total = conn.contentLengthLong.toFloat()
            conn.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReported = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = (done / total * 100).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress(done / total)
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        target.delete()
        if (!partial.renameTo(target)) error("Could not save the update")
        return target
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
