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

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
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
import com.android.launcher3.DropTarget
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.statemanager.StateManager

/**
 * Glow edit mode chrome, modelled on the iOS home screen: an "Edit" pill in the
 * top-left corner that opens a frosted menu (Add Widget / Change Wallpaper /
 * Themes / Settings) and a "Done" pill in the top-right corner. The wiggle and
 * the remove badges are handled by [GlowEditMode].
 *
 * The overlay itself is not clickable, so touches outside its buttons fall
 * through to the workspace below (a tap on empty space also leaves edit mode).
 */
class GlowEditOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs),
    StateManager.StateListener<LauncherState>,
    DragController.DragListener {

    private val launcher: Launcher = Launcher.getLauncher(context)
    private val editButton: TextView
    private val doneButton: TextView
    private var menu: Dialog? = null

    init {
        visibility = GONE
        clipChildren = false
        clipToPadding = false

        editButton = createPill(R.string.glow_edit) { showMenu() }
        addView(
            editButton,
            LayoutParams(LayoutParams.WRAP_CONTENT, dp(PILL_HEIGHT), Gravity.TOP or Gravity.START)
                .apply {
                    topMargin = dp(6)
                    marginStart = dp(18)
                },
        )
        doneButton = createPill(R.string.glow_done) {
            launcher.stateManager.goToState(LauncherState.NORMAL)
        }
        addView(
            doneButton,
            LayoutParams(LayoutParams.WRAP_CONTENT, dp(PILL_HEIGHT), Gravity.TOP or Gravity.END)
                .apply {
                    topMargin = dp(6)
                    marginEnd = dp(18)
                },
        )
    }

    private fun createPill(@StringRes label: Int, onClick: (View) -> Unit) = TextView(context).apply {
        setText(label)
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        includeFontPadding = false
        setPadding(dp(16), 0, dp(16), 0)
        minWidth = dp(72)
        background = glassBackground(dp(PILL_HEIGHT) / 2f, fillAlpha = 70)
        isClickable = true
        isFocusable = true
        setOnClickListener(onClick)
    }

    // ---------------------------------------------------------------------------------------
    // "Edit" menu
    // ---------------------------------------------------------------------------------------

    private fun showMenu() {
        menu?.dismiss()
        val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            launcher.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (canBlur) Color.argb(150, 38, 34, 48) else Color.argb(238, 38, 34, 48))
                setStroke(dp(1).coerceAtLeast(1), Color.argb(40, 255, 255, 255))
            }
            clipToOutline = true
        }
        val items = listOf<Triple<Int, Int, () -> Unit>>(
            Triple(R.string.glow_add_widget, R.drawable.ic_widget) { launcher.openWidgetPicker() },
            Triple(R.string.glow_customize, R.drawable.ic_glow_customize) {
                GlowCustomizeSheet(launcher).show()
            },
            Triple(R.string.glow_change_wallpaper, R.drawable.ic_wallpaper) {
                launcher.startActivity(Intent(launcher, GlowWallpaperActivity::class.java))
            },
            Triple(R.string.glow_themes, R.drawable.ic_palette) {
                launcher.startActivity(Intent(launcher, GlowThemesActivity::class.java))
            },
            Triple(R.string.glow_update_check, R.drawable.ic_glow_update) {
                app.lawnchair.update.GlowUpdater.checkNow(launcher)
            },
            Triple(R.string.glow_launcher_settings, R.drawable.ic_setting) {
                launcher.startActivity(PreferenceActivity.createIntent(launcher, Root))
            },
        )
        val dialog = Dialog(launcher)
        items.forEachIndexed { index, (label, icon, action) ->
            if (index > 0) {
                list.addView(
                    View(context).apply { setBackgroundColor(Color.argb(46, 255, 255, 255)) },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        maxOf(1, (resources.displayMetrics.density * 0.5f).toInt()),
                    ),
                )
            }
            list.addView(
                menuRow(label, icon) {
                    dialog.dismiss()
                    action()
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)),
            )
        }

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(list)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(dp(250), WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.TOP or Gravity.START)
            val loc = IntArray(2)
            editButton.getLocationOnScreen(loc)
            attributes = attributes.apply {
                x = loc[0]
                y = loc[1] + editButton.height + dp(8)
                dimAmount = 0.12f
                windowAnimations = android.R.style.Animation_Dialog
            }
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (canBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setBackgroundBlurRadius(dp(30))
            }
        }
        dialog.setOnDismissListener { if (menu === dialog) menu = null }
        menu = dialog
        dialog.show()
    }

    private fun menuRow(@StringRes label: Int, @DrawableRes icon: Int, onClick: () -> Unit) =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(16), 0)
            isClickable = true
            isFocusable = true
            val ta = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            foreground = ta.getDrawable(0)
            ta.recycle()
            setOnClickListener { onClick() }
            addView(
                TextView(context).apply {
                    setText(label)
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    maxLines = 1
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                ImageView(context).apply {
                    setImageResource(icon)
                    setColorFilter(Color.WHITE)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(dp(20), dp(20)),
            )
        }

    private fun glassBackground(radius: Float, fillAlpha: Int = 64) = GradientDrawable().apply {
        setColor(Color.argb(fillAlpha, 255, 255, 255))
        cornerRadius = radius
        setStroke(dp(1).coerceAtLeast(1), Color.argb(90, 255, 255, 255))
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics,
    ).toInt()

    // ---------------------------------------------------------------------------------------
    // Drag: hide the pills while the drop target bar is shown at the top
    // ---------------------------------------------------------------------------------------

    override fun onDragStart(dragObject: DropTarget.DragObject, options: DragOptions) {
        animate().cancel()
        animate().alpha(0f).setDuration(FADE_DURATION).start()
    }

    override fun onDragEnd() {
        if (launcher.isInState(LauncherState.EDIT_MODE) || visibility == VISIBLE) {
            animate().cancel()
            animate().alpha(if (launcher.stateManager.state == LauncherState.EDIT_MODE) 1f else 0f)
                .setDuration(FADE_DURATION).start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        launcher.stateManager.addStateListener(this)
        launcher.dragController.addDragListener(this)
        applyState(launcher.stateManager.state, animate = false)
    }

    override fun onDetachedFromWindow() {
        launcher.stateManager.removeStateListener(this)
        launcher.dragController.removeDragListener(this)
        GlowEditMode.setActive(launcher, false)
        menu?.dismiss()
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
        GlowEditMode.setActive(launcher, show)
        if (!show) menu?.dismiss()
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
        private const val PILL_HEIGHT = 34

        /**
         * Long-pressing empty home screen space opens edit mode, unless the
         * home screen is locked (then the classic options menu is shown).
         */
        @JvmStatic
        fun shouldEnterEditModeOnLongPress(launcher: Launcher): Boolean =
            !PreferenceManager2.getInstance(launcher).lockHomeScreen.firstCached()
    }
}
