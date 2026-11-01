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

package app.lawnchair.applock

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.os.UserManager
import android.view.View
import android.widget.Toast
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.SystemShortcut

/**
 * Glow app lock: apps the user locks need the device's biometrics or screen
 * lock before they open from the launcher. Locked apps are stored as
 * "package#serial" in a private preferences file.
 */
object GlowAppLock {

    private const val PREFS = "glow_app_lock"
    private const val KEY_LOCKED = "locked_apps"

    /** How long a successful unlock lets the same app open again without asking. */
    private const val GRACE_MS = 5_000L

    private var lastUnlockedKey: String? = null
    private var lastUnlockedAt = 0L

    private fun key(context: Context, packageName: String, user: UserHandle?): String {
        val serial = context.getSystemService(UserManager::class.java)
            .getSerialNumberForUser(user ?: Process.myUserHandle())
        return "$packageName#$serial"
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun lockedSet(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_LOCKED, emptySet()) ?: emptySet()

    fun isLocked(context: Context, packageName: String, user: UserHandle?): Boolean =
        key(context, packageName, user) in lockedSet(context)

    fun setLocked(context: Context, packageName: String, user: UserHandle?, locked: Boolean) {
        val k = key(context, packageName, user)
        val updated = lockedSet(context).toMutableSet().apply { if (locked) add(k) else remove(k) }
        prefs(context).edit().putStringSet(KEY_LOCKED, updated).apply()
    }

    fun canLock(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java).isDeviceSecure

    internal fun markUnlocked(context: Context, packageName: String, user: UserHandle?) {
        lastUnlockedKey = key(context, packageName, user)
        lastUnlockedAt = SystemClock.elapsedRealtime()
    }

    /**
     * Called before the launcher starts an app. Returns true when the app is
     * locked: the launch is handed to [GlowAppLockActivity], which asks for
     * authentication and then opens the app.
     */
    @JvmStatic
    fun interceptLaunch(context: Context, intent: Intent, item: ItemInfo?): Boolean {
        if (item == null || item.itemType != ITEM_TYPE_APPLICATION) return false
        val packageName = intent.component?.packageName ?: intent.`package` ?: return false
        if (packageName == context.packageName) return false
        if (!isLocked(context, packageName, item.user)) return false
        if (!canLock(context)) return false
        val k = key(context, packageName, item.user)
        if (k == lastUnlockedKey && SystemClock.elapsedRealtime() - lastUnlockedAt < GRACE_MS) {
            return false
        }
        context.startActivity(GlowAppLockActivity.createIntent(context, intent, item.user, packageName))
        return true
    }

    private fun packageOf(itemInfo: ItemInfo): String? =
        if (itemInfo.itemType == ITEM_TYPE_APPLICATION) itemInfo.targetComponent?.packageName else null

    val LOCK = SystemShortcut.Factory { activity: LawnchairLauncher, itemInfo: ItemInfo, view: View ->
        val pkg = packageOf(itemInfo) ?: return@Factory null
        if (pkg == activity.packageName) return@Factory null
        ToggleLock(activity, itemInfo, view, pkg, isLocked(activity, pkg, itemInfo.user))
    }

    val SHARE = SystemShortcut.Factory { activity: LawnchairLauncher, itemInfo: ItemInfo, view: View ->
        val pkg = packageOf(itemInfo) ?: return@Factory null
        Share(activity, itemInfo, view, pkg)
    }

    class ToggleLock(
        private val launcher: LawnchairLauncher,
        itemInfo: ItemInfo,
        originalView: View,
        private val packageName: String,
        private val locked: Boolean,
    ) : SystemShortcut<LawnchairLauncher>(
        if (locked) R.drawable.ic_lock_open else R.drawable.ic_lock,
        if (locked) R.string.glow_app_unlock else R.string.glow_app_lock,
        launcher,
        itemInfo,
        originalView,
    ) {
        override fun onClick(v: View) {
            dismissTaskMenuView()
            if (!locked && !canLock(launcher)) {
                Toast.makeText(launcher, R.string.glow_app_lock_needs_screen_lock, Toast.LENGTH_LONG).show()
                return
            }
            setLocked(launcher, packageName, mItemInfo.user, !locked)
            Toast.makeText(
                launcher,
                if (locked) R.string.glow_app_unlocked else R.string.glow_app_locked,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    class Share(
        private val launcher: LawnchairLauncher,
        itemInfo: ItemInfo,
        originalView: View,
        private val packageName: String,
    ) : SystemShortcut<LawnchairLauncher>(
        R.drawable.ic_glow_share,
        R.string.glow_app_share,
        launcher,
        itemInfo,
        originalView,
    ) {
        override fun onClick(v: View) {
            dismissTaskMenuView()
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=$packageName")
            launcher.startActivity(
                Intent.createChooser(send, launcher.getString(R.string.glow_app_share))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
