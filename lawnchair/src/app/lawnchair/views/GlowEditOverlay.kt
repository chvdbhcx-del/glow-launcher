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

    /** Where the running show / hide fade is heading, null when none is running. */
    private var fadingTo: Boolean? = null

    init {
        visibility = GONE
        clipChildren = false
        clipToPadding = false

        editButton = createPill(R.string.glow_edit) { showMenu() }
        addView(
            editButton,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START)
                .apply {
                    topMargin = dp(6) - touchInset()
                    marginStart = dp(18)
                },
        )
        doneButton = createPill(R.string.glow_done) {
            launcher.stateManager.goToState(LauncherState.NORMAL)
        }
        addView(
            doneButton,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
                .apply {
                    topMargin = dp(6) - touchInset()
                    marginEnd = dp(18)
                },
        )
    }

    /** Glass pill, 34dp tall to the eye, with a 48dp touch area and a button role for TalkBack. */
    private fun createPill(@StringRes label: Int, onClick: (View) -> Unit) =
        GlowGlass.pill(context, context.getString(label), PILL_HEIGHT, textSp = 16f, realBlur = true).apply {
            minWidth = dp(72)
            setOnClickListener(onClick)
        }

    /** Space between the visible pill and the edge of its 48dp touch area. */
    private fun touchInset(): Int = ((dp(GlowGlass.MIN_TOUCH_DP) - dp(PILL_HEIGHT)) / 2).coerceAtLeast(0)

    // ---------------------------------------------------------------------------------------
    // "Edit" menu
    // ---------------------------------------------------------------------------------------

    private fun showMenu() {
        menu?.dismiss()
        val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            launcher.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // Dark glass that blurs the wallpaper inside its own (animated) bounds, so the
            // blur grows together with the menu instead of popping in as a full-size box
            background = GlowGlassDrawable(
                context, dp(16).toFloat(), GlowGlass.Level.CARD, true, if (canBlur) 150 else 238,
            ).also { if (canBlur) it.bindBlur(this, 30) }
            clipToOutline = true
        }
        val items = listOf<Triple<Int, Int, () -> Unit>>(
            Triple(R.string.glow_add_widget, R.drawable.ic_glow_widgets) { launcher.openWidgetPicker() },
            Triple(R.string.glow_customize, R.drawable.ic_glow_customize) {
                GlowCustomizeSheet(launcher).show()
            },
            Triple(R.string.glow_change_wallpaper, R.drawable.ic_glow_wallpaper) {
                launcher.startActivity(Intent(launcher, GlowWallpaperActivity::class.java))
            },
            Triple(R.string.glow_themes, R.drawable.ic_glow_themes) {
                launcher.startActivity(Intent(launcher, GlowThemesActivity::class.java))
            },
            Triple(R.string.glow_update_check, R.drawable.ic_glow_update) {
                app.lawnchair.update.GlowUpdater.checkNow(launcher)
            },
            Triple(R.string.glow_launcher_settings, R.drawable.ic_glow_settings) {
                launcher.startActivity(PreferenceActivity.createIntent(launcher, Root))
            },
        )
            // Play and local builds don't update themselves
            .filter { it.first != R.string.glow_update_check || app.lawnchair.update.GlowUpdater.isSupported() }
        val dialog = object : Dialog(launcher) {
            // Tap outside or Back: shrink back into the "Edit" pill instead of vanishing
            override fun cancel() {
                if (!animateMenu(list, editButton, show = false) { super.cancel() }) super.cancel()
            }
        }
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
                    // Close at once: the action may open a sheet or another screen
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
            // LEFT, not START: window x offsets are always measured from the left edge
            setGravity(Gravity.TOP or Gravity.LEFT)
            val loc = IntArray(2)
            editButton.getLocationOnScreen(loc)
            val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
            attributes = attributes.apply {
                x = if (rtl) loc[0] + editButton.width - dp(250) else loc[0]
                y = loc[1] + editButton.height - touchInset() + dp(8)
                dimAmount = 0.12f
                // The menu grows out of the "Edit" pill (see animateMenu), not from the centre
                windowAnimations = 0
            }
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        dialog.setOnDismissListener { if (menu === dialog) menu = null }
        menu = dialog
        dialog.show()
        animateMenu(list, editButton, show = true) {}
    }

    /**
     * Grows the menu out of the "Edit" pill (or shrinks it back into it). Returns false when
     * system animations are off, then [end] is not called and the caller acts at once.
     */
    private fun animateMenu(menu: View, anchor: View, show: Boolean, end: () -> Unit): Boolean {
        if (!GlowGlass.animationsEnabled()) {
            menu.alpha = 1f
            return false
        }
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        menu.animate().cancel()
        // Pivot on the pill: the top corner of the menu that sits under it
        menu.pivotX = if (rtl) dp(250) - anchor.width / 2f else anchor.width / 2f
        menu.pivotY = 0f
        if (show) {
            menu.alpha = 0f
            menu.scaleX = 0.6f
            menu.scaleY = 0.6f
            menu.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(GlowGlass.Motion.MEDIUM)
                .setInterpolator(GlowGlass.Motion.POP)
                .withEndAction(end)
                .start()
        } else {
            menu.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f)
                .setDuration(GlowGlass.Motion.SHORT)
                .setInterpolator(GlowGlass.Motion.EXIT)
                .withEndAction(end)
                .start()
        }
        return true
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
            GlowGlass.asButton(this)
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
        fadingTo = null
        animate().alpha(0f).setDuration(FADE_DURATION).setInterpolator(GlowGlass.Motion.EXIT).start()
    }

    override fun onDragEnd() {
        if (launcher.isInState(LauncherState.EDIT_MODE) || visibility == VISIBLE) {
            animate().cancel()
            fadingTo = null
            animate().alpha(if (launcher.stateManager.state == LauncherState.EDIT_MODE) 1f else 0f)
                .setDuration(FADE_DURATION).setInterpolator(GlowGlass.Motion.STANDARD).start()
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
        // The state "complete" callback must not cut short a fade that is heading the same way
        if (!animate && fadingTo == show) return
        animate().cancel()
        fadingTo = null
        val animated = animate && GlowGlass.animationsEnabled()
        if (show) {
            if (visibility != VISIBLE) {
                alpha = if (animated) 0f else 1f
                // The pills drop in from the status bar
                translationY = if (animated) -dp(10).toFloat() else 0f
                visibility = VISIBLE
            }
            if (animated) {
                fadingTo = true
                animate().alpha(1f).translationY(0f).setDuration(FADE_DURATION + 60)
                    .setInterpolator(GlowGlass.Motion.ENTER)
                    .withEndAction { fadingTo = null }.start()
            } else {
                alpha = 1f
                translationY = 0f
            }
        } else if (visibility == VISIBLE) {
            if (animated) {
                fadingTo = false
                animate().alpha(0f).translationY(-dp(10).toFloat()).setDuration(FADE_DURATION)
                    .setInterpolator(GlowGlass.Motion.EXIT)
                    .withEndAction {
                        fadingTo = null
                        visibility = GONE
                    }.start()
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
