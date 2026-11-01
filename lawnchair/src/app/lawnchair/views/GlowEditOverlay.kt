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

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.Root
import app.lawnchair.ui.wallpaper.GlowThemesActivity
import app.lawnchair.ui.wallpaper.GlowWallpaperActivity
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.statemanager.StateManager

/**
 * Glow edit mode overlay: a close button in the top corner and a bottom bar of
 * glass buttons (Wallpaper / Themes / Widgets / Settings) shown while the
 * launcher is in [LauncherState.EDIT_MODE]. The dock is hidden in that state,
 * so the bar takes its place.
 *
 * The overlay itself is not clickable, so touches outside its buttons fall
 * through to the workspace below.
 */
class GlowEditOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs), StateManager.StateListener<LauncherState> {

    private val launcher: Launcher = Launcher.getLauncher(context)

    init {
        visibility = GONE
        clipChildren = false
        clipToPadding = false

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        bar.addView(createButton(R.drawable.ic_wallpaper, R.string.wallpapers) {
            launcher.startActivity(Intent(launcher, GlowWallpaperActivity::class.java))
        })
        bar.addView(createButton(R.drawable.ic_palette, R.string.glow_themes) {
            launcher.startActivity(Intent(launcher, GlowThemesActivity::class.java))
        })
        bar.addView(createButton(R.drawable.ic_widget, R.string.widget_button_text) {
            launcher.openWidgetPicker()
        })
        bar.addView(createButton(R.drawable.ic_setting, R.string.settings_button_text) {
            launcher.startActivity(PreferenceActivity.createIntent(launcher, Root))
        })
        addView(
            bar,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                bottomMargin = dp(20)
            },
        )

        val size = dp(44)
        val close = ImageView(context).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = glassBackground(size / 2f)
            contentDescription = context.getString(R.string.glow_exit_edit_mode)
            setOnClickListener { launcher.stateManager.goToState(LauncherState.NORMAL) }
        }
        addView(
            close,
            LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
                topMargin = dp(12)
                marginEnd = dp(20)
            },
        )
    }

    private fun createButton(
        @DrawableRes icon: Int,
        @StringRes label: Int,
        onClick: (View) -> Unit,
    ): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        val tile = dp(64)
        val iconView = ImageView(context).apply {
            setImageResource(icon)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = glassBackground(dp(20).toFloat())
            contentDescription = context.getString(label)
            isClickable = true
            isFocusable = true
            setOnClickListener(onClick)
        }
        column.addView(iconView, LinearLayout.LayoutParams(tile, tile))
        val text = TextView(context).apply {
            setText(label)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            maxLines = 1
            setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0x55000000)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        column.addView(
            text,
            LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            },
        )
        return column
    }

    private fun glassBackground(radius: Float) = GradientDrawable().apply {
        setColor(Color.argb(64, 255, 255, 255))
        cornerRadius = radius
        setStroke(dp(1).coerceAtLeast(1), Color.argb(110, 255, 255, 255))
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics,
    ).toInt()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        launcher.stateManager.addStateListener(this)
        applyState(launcher.stateManager.state, animate = false)
    }

    override fun onDetachedFromWindow() {
        launcher.stateManager.removeStateListener(this)
        super.onDetachedFromWindow()
    }

    override fun onStateTransitionStart(toState: LauncherState) {
        applyState(toState, animate = true)
    }

    override fun onStateTransitionComplete(finalState: LauncherState) {
        applyState(finalState, animate = false)
    }

    private fun applyState(state: LauncherState, animate: Boolean) {
        val show = state == LauncherState.EDIT_MODE
        animate().cancel()
        if (show) {
            if (visibility != VISIBLE) {
                alpha = if (animate) 0f else 1f
                visibility = VISIBLE
            }
            if (animate) animate().alpha(1f).setDuration(FADE_DURATION).start() else alpha = 1f
        } else if (visibility == VISIBLE) {
            if (animate) {
                animate().alpha(0f).setDuration(FADE_DURATION)
                    .withEndAction { visibility = GONE }.start()
            } else {
                alpha = 0f
                visibility = GONE
            }
        }
    }

    companion object {
        private const val FADE_DURATION = 150L

        /**
         * Long-pressing empty home screen space opens edit mode, unless the
         * home screen is locked (then the classic options menu is shown).
         */
        @JvmStatic
        fun shouldEnterEditModeOnLongPress(launcher: Launcher): Boolean =
            !PreferenceManager2.getInstance(launcher).lockHomeScreen.firstCached()
    }
}
