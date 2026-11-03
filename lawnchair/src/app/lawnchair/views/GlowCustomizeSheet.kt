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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.ui.preferences.iconPackIntents
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.patrykmichalik.opto.core.setBlocking

/**
 * The "Customize" sheet of the Glow edit mode: a frosted bottom sheet with four
 * tabs (Icons / Shape / Size / Grid) that change the home screen in place.
 */
class GlowCustomizeSheet(private val launcher: Launcher) {

    private val context: Context get() = launcher
    private val prefs = PreferenceManager.getInstance(launcher)
    private val prefs2 = PreferenceManager2.getInstance(launcher)
    private val dialog = Dialog(launcher)
    private val content = FrameLayout(launcher)
    private val tabs = mutableListOf<TextView>()

    fun show() {
        val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            launcher.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(24))
            background = GradientDrawable().apply {
                val r = dp(28).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setColor(if (canBlur) Color.argb(160, 34, 30, 44) else Color.argb(242, 34, 30, 44))
                setStroke(dp(1).coerceAtLeast(1), Color.argb(36, 255, 255, 255))
            }
        }
        // Grabber
        root.addView(
            View(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(3).toFloat()
                    setColor(Color.argb(90, 255, 255, 255))
                }
            },
            LinearLayout.LayoutParams(dp(36), dp(5)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(14)
            },
        )
        root.addView(
            TextView(context).apply {
                setText(R.string.glow_customize)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(12) },
        )

        val tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.argb(40, 255, 255, 255))
            }
        }
        listOf(
            R.string.glow_customize_icons,
            R.string.glow_customize_shape,
            R.string.glow_customize_size,
            R.string.glow_customize_grid,
            R.string.glow_customize_indicator,
        ).forEachIndexed { index, label ->
            val tab = TextView(context).apply {
                setText(label)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setOnClickListener { selectTab(index) }
            }
            tabs += tab
            tabRow.addView(tab, LinearLayout.LayoutParams(0, dp(32), 1f))
        }
        root.addView(tabRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
        root.addView(
            content,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(SHEET_CONTENT_HEIGHT))
                .apply { topMargin = dp(14) },
        )

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            attributes = attributes.apply {
                dimAmount = 0.1f
                windowAnimations = android.R.style.Animation_InputMethod
            }
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (canBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setBackgroundBlurRadius(dp(30))
            }
        }
        selectTab(0)
        dialog.show()
    }

    fun dismiss() = dialog.dismiss()

    private fun selectTab(index: Int) {
        tabs.forEachIndexed { i, tab ->
            tab.background = if (i == index) {
                GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(Color.argb(70, 255, 255, 255))
                }
            } else {
                null
            }
        }
        content.removeAllViews()
        content.addView(
            when (index) {
                0 -> iconsPage()
                1 -> shapePage()
                2 -> sizePage()
                3 -> gridPage()
                else -> indicatorPage()
            },
        )
    }

    // ---------------------------------------------------------------------------------------
    // Pages
    // ---------------------------------------------------------------------------------------

    private fun iconsPage(): View {
        val row = optionRow()
        val pm = launcher.packageManager
        val current = prefs.iconPackPackage.get()
        val packs = mutableListOf<Triple<String, String, Drawable?>>()
        packs += Triple("", launcher.getString(R.string.system_icons), launcher.getDrawable(R.drawable.ic_launcher_home))
        iconPackIntents
            .flatMap { pm.queryIntentActivities(it, 0) }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString() }
            .forEach { packs += Triple(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }

        val options = mutableListOf<View>()
        packs.forEach { (pkg, name, icon) ->
            val preview = ImageView(context).apply {
                setImageDrawable(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            val option = option(preview, name, selected = pkg == current)
            option.setOnClickListener {
                markSelected(options, option)
                prefs.iconPackPackage.set(pkg)
            }
            options += option
            row.addView(option)
        }
        return scroller(row)
    }

    private fun shapePage(): View {
        val row = optionRow()
        val current = prefs2.iconShape.firstCached().toString()
        val shapes = listOf(
            IconShape.Cupertino to R.string.glow_shape_rounded,
            IconShape.Squircle to R.string.glow_shape_squircle,
            IconShape.Circle to R.string.glow_shape_circle,
            IconShape.RoundedSquare to R.string.glow_shape_soft_square,
            IconShape.Teardrop to R.string.glow_shape_teardrop,
            IconShape.Square to R.string.glow_shape_square,
        )
        val options = mutableListOf<View>()
        shapes.forEach { (shape, label) ->
            val option = option(ShapePreview(context, shape), context.getString(label), shape.toString() == current)
            option.setOnClickListener {
                markSelected(options, option)
                MODEL_EXECUTOR.execute { prefs2.iconShape.setBlocking(shape) }
            }
            options += option
            row.addView(option)
        }
        return scroller(row)
    }

    private fun sizePage(): View {
        val row = optionRow()
        val current = prefs2.homeIconSizeFactor.firstCached()
        val sizes = listOf(
            0.86f to R.string.glow_size_small,
            1.0f to R.string.glow_size_medium,
            1.14f to R.string.glow_size_large,
        )
        val options = mutableListOf<View>()
        sizes.forEach { (factor, label) ->
            val preview = ShapePreview(context, prefs2.iconShape.firstCached(), scale = 0.55f + 0.3f * (factor - 0.86f) / 0.28f)
            val option = option(preview, context.getString(label), kotlin.math.abs(current - factor) < 0.02f)
            option.setOnClickListener {
                markSelected(options, option)
                MODEL_EXECUTOR.execute { prefs2.homeIconSizeFactor.setBlocking(factor) }
            }
            options += option
            row.addView(option)
        }
        return scroller(row)
    }

    private fun gridPage(): View {
        val row = optionRow()
        val currentCols = prefs.workspaceColumns.get()
        val currentRows = prefs.workspaceRows.get()
        val grids = listOf(4 to 5, 4 to 6, 5 to 5, 5 to 6, 6 to 6)
        val options = mutableListOf<View>()
        grids.forEach { (cols, rows) ->
            val option = option(
                GridPreview(context, cols, rows),
                context.getString(R.string.glow_grid_format, cols, rows),
                cols == currentCols && rows == currentRows,
            )
            option.setOnClickListener {
                if (cols == prefs.workspaceColumns.get() && rows == prefs.workspaceRows.get()) {
                    return@setOnClickListener
                }
                markSelected(options, option)
                // The launcher rebuilds its grid, so close the sheet first.
                dialog.dismiss()
                prefs.batchEdit {
                    prefs.workspaceColumns.set(cols)
                    prefs.workspaceRows.set(rows)
                }
                MAIN_EXECUTOR.handler.post {
                    InvariantDeviceProfile.INSTANCE.get(launcher).onPreferencesChanged(launcher)
                }
            }
            options += option
            row.addView(option)
        }
        return scroller(row)
    }

    private fun indicatorPage(): View {
        val row = optionRow()
        val current = GlowPageIndicator.getStyle(launcher)
        val styles = listOf(
            GlowPageIndicator.STYLE_SEARCH to R.string.glow_indicator_search,
            GlowPageIndicator.STYLE_DOTS to R.string.glow_indicator_dots,
            GlowPageIndicator.STYLE_CAPSULE to R.string.glow_indicator_capsule,
            GlowPageIndicator.STYLE_LINE to R.string.glow_indicator_line,
            GlowPageIndicator.STYLE_NONE to R.string.glow_indicator_none,
        )
        val options = mutableListOf<View>()
        styles.forEach { (style, label) ->
            val option = option(IndicatorPreview(context, style), context.getString(label), style == current)
            option.setOnClickListener {
                markSelected(options, option)
                // The launcher restarts to rebuild the dock, so close the sheet first.
                dialog.dismiss()
                GlowPageIndicator.setStyle(launcher, style)
            }
            options += option
            row.addView(option)
        }
        return scroller(row)
    }

    // ---------------------------------------------------------------------------------------
    // Building blocks
    // ---------------------------------------------------------------------------------------

    private fun optionRow() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun scroller(row: View) = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(row)
    }

    private fun option(preview: View, label: String, selected: Boolean): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), 0, dp(6), 0)
            isClickable = true
            isFocusable = true
            contentDescription = label
        }
        val frame = FrameLayout(context).apply {
            tag = TAG_FRAME
            background = optionBackground(selected)
            addView(preview, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
        }
        column.addView(frame, LinearLayout.LayoutParams(dp(76), dp(76)))
        column.addView(
            TextView(context).apply {
                text = label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) },
        )
        return column
    }

    private fun markSelected(options: List<View>, selected: View) {
        options.forEach { option ->
            option.findViewWithTag<View>(TAG_FRAME)?.background = optionBackground(option === selected)
        }
    }

    private fun optionBackground(selected: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(20).toFloat()
        setColor(Color.argb(if (selected) 54 else 26, 255, 255, 255))
        if (selected) setStroke(dp(2), ACCENT) else setStroke(dp(1).coerceAtLeast(1), Color.argb(30, 255, 255, 255))
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        launcher.resources.displayMetrics,
    ).toInt()

    /** Draws an icon shape filled with the Glow pink-violet gradient. */
    private class ShapePreview(
        context: Context,
        private val shape: IconShape,
        private val scale: Float = 0.8f,
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pathMatrix = Matrix()

        override fun onDraw(canvas: Canvas) {
            val size = minOf(width, height) * scale
            val left = (width - size) / 2f
            val top = (height - size) / 2f
            paint.shader = LinearGradient(
                left, top, left + size, top + size,
                Color.rgb(255, 122, 200), Color.rgb(140, 82, 255), Shader.TileMode.CLAMP,
            )
            val path = shape.getMaskPath()
            pathMatrix.setScale(size / 100f, size / 100f)
            pathMatrix.postTranslate(left, top)
            path.transform(pathMatrix)
            canvas.drawPath(path, paint)
        }
    }

    /** A scaled-down home screen page indicator. */
    private class IndicatorPreview(context: Context, private val style: String) : View(context) {
        override fun onDraw(canvas: Canvas) {
            canvas.save()
            canvas.scale(0.45f, 0.45f, width / 2f, height / 2f)
            GlowPageIndicator.draw(
                this, canvas, style, 3, 1f,
                if (style == GlowPageIndicator.STYLE_SEARCH) 0f else 1f,
                false,
            )
            canvas.restore()
        }
    }

    /** A tiny phone screen with a cols x rows grid of dots. */
    private class GridPreview(context: Context, private val cols: Int, private val rows: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 255, 255, 255) }
        private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(120, 255, 255, 255)
        }

        override fun onDraw(canvas: Canvas) {
            val d = resources.displayMetrics.density
            frame.strokeWidth = 1.2f * d
            val h = height * 0.92f
            val w = h * 0.55f
            val left = (width - w) / 2f
            val top = (height - h) / 2f
            canvas.drawRoundRect(left, top, left + w, top + h, 6 * d, 6 * d, frame)
            val pad = 4 * d
            val cellW = (w - 2 * pad) / cols
            val cellH = (h - 2 * pad) / rows
            val r = minOf(cellW, cellH) * 0.3f
            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    canvas.drawCircle(left + pad + cellW * (x + 0.5f), top + pad + cellH * (y + 0.5f), r, paint)
                }
            }
        }
    }

    companion object {
        private const val SHEET_CONTENT_HEIGHT = 104
        private const val TAG_FRAME = "glow_option_frame"
        private val ACCENT = Color.rgb(0xB1, 0x4F, 0xE8)
    }
}
