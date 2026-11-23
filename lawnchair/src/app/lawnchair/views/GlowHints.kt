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

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import app.lawnchair.allapps.GlowSideLibrary
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.views.BaseDragLayer

/**
 * First-run hint: a glass bubble above the dock that tells where the App Library is
 * and how to edit the home screen. Shown once; a tap or the next home press hides it.
 */
object GlowHints {

    private const val PREFS = "glow_hints"
    private const val KEY_LIBRARY = "library_hint_shown"
    private const val SHOW_DELAY_MS = 1200L
    private const val SHOW_FOR_MS = 9000L

    @JvmStatic
    fun maybeShow(launcher: Launcher) {
        val prefs = launcher.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
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
}
