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

package app.lawnchair.views

import android.app.WallpaperManager
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import app.lawnchair.allapps.GlowSideLibrary
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.BackupAndRestore
import app.lawnchair.ui.wallpaper.GlowThemes
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR
import com.android.launcher3.views.BaseDragLayer

/**
 * First run: a two-step welcome sheet (pick a Glow look or keep the wallpaper, or restore
 * a backup; then the three gestures worth knowing). Shown once. Installs that already went
 * through it fall back to the small library hint bubble, also shown once.
 */
object GlowHints {

    private const val PREFS = "glow_hints"
    private const val KEY_LIBRARY = "library_hint_shown"
    private const val KEY_WELCOME = "welcome_shown"
    private const val SHOW_DELAY_MS = 1200L
    private const val SHOW_FOR_MS = 9000L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @JvmStatic
    fun isWelcomeDone(context: Context): Boolean = prefs(context).getBoolean(KEY_WELCOME, false)

    @JvmStatic
    fun maybeShow(launcher: Launcher) {
        val prefs = prefs(launcher)
        if (!prefs.getBoolean(KEY_WELCOME, false)) {
            val dragLayer = launcher.dragLayer ?: return
            dragLayer.postDelayed({
                if (launcher.isDestroyed || !launcher.isInState(LauncherState.NORMAL)) return@postDelayed
                if (prefs.getBoolean(KEY_WELCOME, false) || GlowSheet.isAnyShowing()) return@postDelayed
                // The welcome covers the library hint, so neither comes back
                prefs.edit().putBoolean(KEY_WELCOME, true).putBoolean(KEY_LIBRARY, true).apply()
                welcome(launcher)
            }, SHOW_DELAY_MS)
            return
        }
        if (prefs.getBoolean(KEY_LIBRARY, false)) return
        if (!GlowSideLibrary.isEnabled(launcher)) return
        val dragLayer = launcher.dragLayer ?: return
        dragLayer.postDelayed({
            if (launcher.isDestroyed || !launcher.isInState(LauncherState.NORMAL)) return@postDelayed
            if (prefs.getBoolean(KEY_LIBRARY, false)) return@postDelayed
            prefs.edit().putBoolean(KEY_LIBRARY, true).apply()
            show(launcher, dragLayer)
        }, SHOW_DELAY_MS)
    }

    private fun show(launcher: Launcher, dragLayer: BaseDragLayer<*>) {
        val text = launcher.getString(R.string.glow_hint_library) + "\n" +
            launcher.getString(R.string.glow_hint_edit)
        val bubble = GlowGlass.pill(launcher, text, 64, textSp = 14f).apply {
            maxLines = 4
            setLineSpacing(0f, 1.15f)
            val pad = GlowGlass.dp(launcher, 20)
            setPadding(pad, GlowGlass.dp(launcher, 14), pad, GlowGlass.dp(launcher, 14))
            alpha = 0f
            translationY = GlowGlass.dp(launcher, 12).toFloat()
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val lp = BaseDragLayer.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = launcher.deviceProfile.hotseatBarSizePx + GlowGlass.dp(launcher, 48)
            val side = GlowGlass.dp(launcher, 24)
            leftMargin = side
            rightMargin = side
        }
        dragLayer.addView(bubble, lp)
        val hide = Runnable {
            bubble.animate().alpha(0f).setDuration(GlowGlass.Motion.SHORT)
                .withEndAction { (bubble.parent as? ViewGroup)?.removeView(bubble) }.start()
        }
        bubble.setOnClickListener { hide.run() }
        bubble.animate().alpha(1f).translationY(0f).setDuration(GlowGlass.Motion.MEDIUM)
            .setInterpolator(GlowGlass.Motion.ENTER).start()
        bubble.postDelayed(hide, SHOW_FOR_MS)
    }

    /** Step 1: a look. The theme is applied after step 2 so a restyle can't cut it short. */
    private fun welcome(launcher: Launcher) {
        val sheet = GlowSheet(launcher).title(
            launcher.getString(R.string.glow_welcome_title),
            launcher.getString(R.string.glow_welcome_look),
        )
        GlowThemes.ALL.forEach { theme ->
            sheet.choice(launcher.getString(theme.name), swatch = theme.accent) {
                sheet.dismiss()
                tips(launcher, theme)
            }
        }
        sheet.choice(launcher.getString(R.string.glow_welcome_keep), primary = true) {
            sheet.dismiss()
            tips(launcher, null)
        }
        sheet.choice(launcher.getString(R.string.glow_welcome_restore)) {
            sheet.dismiss()
            runCatching {
                launcher.startActivity(PreferenceActivity.createIntent(launcher, BackupAndRestore))
            }
        }
        sheet.show()
    }

    /** Step 2: the gestures that aren't obvious, then the chosen look is applied. */
    private fun tips(launcher: Launcher, theme: GlowThemes.Theme?) {
        val sheet = GlowSheet(launcher).title(launcher.getString(R.string.glow_welcome_tips))
        if (GlowSideLibrary.isEnabled(launcher)) {
            sheet.point("←", launcher.getString(R.string.glow_hint_library))
        }
        sheet.point("✎", launcher.getString(R.string.glow_hint_edit))
        sheet.point("⌕", launcher.getString(R.string.glow_hint_search))
        sheet.choice(launcher.getString(R.string.glow_welcome_start), primary = true) { sheet.dismiss() }
        sheet.onDismiss {
            if (theme != null) {
                val appContext = launcher.applicationContext
                THREAD_POOL_EXECUTOR.execute {
                    GlowThemes.applyBlocking(appContext, theme, WallpaperManager.FLAG_SYSTEM)
                }
            }
        }
        sheet.show()
    }
}
