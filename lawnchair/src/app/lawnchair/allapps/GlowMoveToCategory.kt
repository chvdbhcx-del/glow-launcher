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

package app.lawnchair.allapps

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.View
import android.widget.Toast
import app.lawnchair.LawnchairLauncher
import app.lawnchair.views.GlowGlass
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.popup.SystemShortcut

/**
 * "Move to folder" in an app's menu inside the App Library: when automatic sorting put
 * an app in the wrong folder (or in "Other"), the user picks the right one. The choice is
 * kept per package and wins over every automatic rule; "Automatic" undoes it.
 */
object GlowMoveToCategory {

    val MOVE = SystemShortcut.Factory { activity: LawnchairLauncher, itemInfo: ItemInfo, view: View ->
        if (itemInfo.itemType != ITEM_TYPE_APPLICATION || itemInfo.container != CONTAINER_ALL_APPS) {
            return@Factory null
        }
        val pkg = itemInfo.targetComponent?.packageName ?: return@Factory null
        if (pkg == activity.packageName) return@Factory null
        Move(activity, itemInfo, view, pkg)
    }

    class Move(
        private val launcher: LawnchairLauncher,
        itemInfo: ItemInfo,
        originalView: View,
        private val packageName: String,
    ) : SystemShortcut<LawnchairLauncher>(
        R.drawable.ic_folder,
        R.string.glow_library_move,
        launcher,
        itemInfo,
        originalView,
    ) {
        override fun onClick(v: View) {
            dismissTaskMenuView()
            MoveSheet(launcher, mItemInfo, packageName).show()
        }
    }

    /**
     * Frosted bottom sheet in the style of the Customize sheet: the app on top, "Automatic"
     * as a wide chip, then every folder as a glass chip in two columns. The current choice
     * is filled with the theme accent and carries a check mark.
     */
    private class MoveSheet(
        private val launcher: LawnchairLauncher,
        private val item: ItemInfo,
        private val packageName: String,
    ) {
        private val dialog = Dialog(launcher)
        private val accent = GlowGlass.accent(launcher)
        private val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            launcher.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true

        private fun dp(value: Int) = GlowGlass.dp(launcher, value)

        fun show() {
            val folders = GlowAppLibraryView.movableCategories(launcher)
            val picked = GlowAppLibraryView.userCategory(launcher, packageName)
            val title = item.title?.toString().orEmpty()

            val root = LinearLayout(launcher).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(10), dp(18), dp(22))
                background = GradientDrawable().apply {
                    val r = dp(28).toFloat()
                    cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                    setColor(if (canBlur) Color.argb(165, 34, 30, 44) else Color.argb(245, 34, 30, 44))
                    setStroke(dp(1).coerceAtLeast(1), Color.argb(36, 255, 255, 255))
                }
            }
            // Grabber
            root.addView(
                View(launcher).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = dp(3).toFloat()
                        setColor(Color.argb(90, 255, 255, 255))
                    }
                },
                LinearLayout.LayoutParams(dp(36), dp(5)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(16)
                },
            )

            // Header: app icon, "Move to folder", app name
            val header = LinearLayout(launcher).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            (item as? ItemInfoWithIcon)?.let { info ->
                runCatching { info.newIcon(launcher) }.getOrNull()?.let { icon ->
                    header.addView(
                        ImageView(launcher).apply { setImageDrawable(icon) },
                        LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) },
                    )
                }
            }
            val titles = LinearLayout(launcher).apply { orientation = LinearLayout.VERTICAL }
            titles.addView(
                TextView(launcher).apply {
                    setText(R.string.glow_library_move)
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    typeface = Typeface.DEFAULT_BOLD
                    GlowGlass.asHeading(this)
                },
            )
            titles.addView(
                TextView(launcher).apply {
                    text = title
                    setTextColor(Color.argb(170, 255, 255, 255))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
            )
            header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            root.addView(
                header,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = dp(18) },
            )

            val grid = LinearLayout(launcher).apply { orientation = LinearLayout.VERTICAL }
            grid.addView(
                chip(launcher.getString(R.string.glow_library_move_auto), picked == null) {
                    choose(null, launcher.getString(R.string.glow_library_move_auto), picked, title)
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(CHIP_HEIGHT_DP))
                    .apply { bottomMargin = dp(GAP_DP) },
            )
            folders.chunked(2).forEach { pair ->
                val row = LinearLayout(launcher).apply { orientation = LinearLayout.HORIZONTAL }
                pair.forEachIndexed { i, (key, label) ->
                    row.addView(
                        chip(label, key == picked) { choose(key, label, picked, title) },
                        LinearLayout.LayoutParams(0, dp(CHIP_HEIGHT_DP), 1f).apply {
                            if (i == 0) marginEnd = dp(GAP_DP / 2) else marginStart = dp(GAP_DP / 2)
                        },
                    )
                }
                if (pair.size == 1) row.addView(View(launcher), LinearLayout.LayoutParams(0, 0, 1f))
                grid.addView(
                    row,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { bottomMargin = dp(GAP_DP) },
                )
            }
            val scroll = ScrollView(launcher).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(grid)
            }
            root.addView(
                scroll,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )

            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(root)
            dialog.setCanceledOnTouchOutside(true)
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.BOTTOM)
                attributes = attributes.apply {
                    dimAmount = 0.25f
                    windowAnimations = android.R.style.Animation_InputMethod
                }
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                if (canBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setBackgroundBlurRadius(dp(30))
                }
            }
            dialog.show()
        }

        private fun chip(label: String, selected: Boolean, onClick: () -> Unit): View {
            val chip = LinearLayout(launcher).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(12), 0)
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    if (selected) {
                        setColor(accent)
                    } else {
                        setColor(Color.argb(34, 255, 255, 255))
                        setStroke(dp(1).coerceAtLeast(1), Color.argb(30, 255, 255, 255))
                    }
                }
                isClickable = true
                isFocusable = true
                isSelected = selected
                contentDescription = label
                setOnClickListener { onClick() }
                GlowGlass.asButton(this)
            }
            chip.foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(50, 255, 255, 255)),
                null,
                GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(Color.WHITE)
                },
            )
            chip.addView(
                TextView(launcher).apply {
                    text = label
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            if (selected) {
                chip.addView(
                    TextView(launcher).apply {
                        text = "✓"
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        typeface = Typeface.DEFAULT_BOLD
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                )
            }
            return chip
        }

        private fun choose(group: String?, label: String, picked: String?, title: String) {
            dialog.dismiss()
            if (group == picked) return
            GlowAppLibraryView.moveApp(launcher, packageName, group)
            Toast.makeText(
                launcher,
                launcher.getString(R.string.glow_library_moved, title, label),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private const val CHIP_HEIGHT_DP = 50
    private const val GAP_DP = 10
}
