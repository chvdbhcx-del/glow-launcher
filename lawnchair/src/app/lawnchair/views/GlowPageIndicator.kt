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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import app.lawnchair.hotseat.DisabledHotseat
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.patrykmichalik.opto.core.setBlocking
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Home screen page indicator styles of Glow, drawn by PageIndicatorDots:
 *
 * - [STYLE_SEARCH]: iOS style, a frosted "Search" pill above the dock that turns
 *   into page dots while the pages are being swiped; tapping it opens search.
 * - [STYLE_DOTS]: plain dots, [STYLE_CAPSULE]: dots on a glass capsule,
 *   [STYLE_LINE]: a thin track with a moving segment, [STYLE_NONE]: hidden.
 */
object GlowPageIndicator {

    const val STYLE_SEARCH = "search"
    const val STYLE_DOTS = "dots"
    const val STYLE_CAPSULE = "capsule"
    const val STYLE_LINE = "line"
    const val STYLE_NONE = "none"

    val STYLES = listOf(STYLE_SEARCH, STYLE_DOTS, STYLE_CAPSULE, STYLE_LINE, STYLE_NONE)

    const val PILL_HEIGHT_DP = 32f

    /** Tall enough for the pill to settle a fixed gap above the dock (see getGlowPillCenterY). */
    private const val VIEW_HEIGHT_DP = 76

    /** One glass pill per indicator view; the home one blurs the wallpaper behind it. */
    private val pills = java.util.WeakHashMap<View, GlowGlassDrawable>()
    private val pillBounds = android.graphics.Rect()
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()

    @JvmStatic
    fun getStyle(context: Context): String {
        val style = PreferenceManager.getInstance(context).glowPageIndicatorStyle.get()
        return if (style in STYLES) style else STYLE_SEARCH
    }

    /** Height the home screen indicator view needs for [style], or -1 to keep the default. */
    @JvmStatic
    fun getViewHeight(context: Context, style: String): Int =
        if (style == STYLE_SEARCH) (VIEW_HEIGHT_DP * context.resources.displayMetrics.density).toInt() else -1

    /**
     * Changes the style. Only the search style touches the dock: it hides the old search
     * bar there (two searches stacked above the dock). Other styles leave the dock search
     * setting as the user set it instead of turning a search bar on behind their back.
     */
    fun setStyle(launcher: Launcher, style: String) {
        val prefs = PreferenceManager.getInstance(launcher)
        if (prefs.glowPageIndicatorStyle.get() == style) return
        MODEL_EXECUTOR.execute {
            if (style == STYLE_SEARCH) {
                PreferenceManager2.getInstance(launcher).hotseatMode.setBlocking(DisabledHotseat)
            }
            // Recreates the launcher, which re-reads both preferences
            MAIN_EXECUTOR.execute { prefs.glowPageIndicatorStyle.set(style) }
        }
    }

    /** The dock search bar mode in effect: none while the "Search" pill is shown. */
    @JvmStatic
    fun effectiveHotseatMode(context: Context, mode: app.lawnchair.hotseat.HotseatMode): app.lawnchair.hotseat.HotseatMode =
        if (getStyle(context) == STYLE_SEARCH) DisabledHotseat else mode

    /** Bounds of the search pill, for touch handling. */
    @JvmStatic
    fun getSearchPillBounds(view: View, out: RectF) {
        val d = view.resources.displayMetrics.density
        textPaint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 15f, view.resources.displayMetrics)
        val label = view.context.getString(R.string.label_search)
        val width = 16f * d + 16f * d + 6f * d + textPaint.measureText(label) + 16f * d
        val h = PILL_HEIGHT_DP * d
        val cx = view.width / 2f
        val cy = pillCenterY(view)
        out.set(cx - width / 2f, cy - h / 2f, cx + width / 2f, cy + h / 2f)
    }

    /** Where the search pill sits vertically inside [view]. */
    private fun pillCenterY(view: View): Float =
        (view as? com.android.launcher3.pageindicators.PageIndicatorDots)?.glowPillCenterY ?: (view.height / 2f)

    @JvmStatic
    fun openSearch(launcher: Launcher) {
        val search = launcher.appsView.searchUiManager
        search.setDirectFocus(true)
        // The field says what the user tapped ("Search"), not "App Library"
        search.editText?.setHint(R.string.label_search)
        search.editText?.showKeyboard()
        launcher.stateManager.goToState(LauncherState.ALL_APPS)
    }

    /**
     * Draws the indicator.
     *
     * @param position current page position, fractional while scrolling
     * @param dotsAlpha 0..1, how visible the page dots are (they auto-hide on the home screen)
     * @param onLightWallpaper glass turns into a dark tint and bare dots turn dark, so they
     *   stay readable over a light wallpaper
     */
    @JvmStatic
    @JvmOverloads
    fun draw(
        view: View,
        canvas: Canvas,
        style: String,
        numPages: Int,
        position: Float,
        dotsAlpha: Float,
        isRtl: Boolean,
        onLightWallpaper: Boolean = false,
    ) {
        val d = view.resources.displayMetrics.density
        val cx = view.width / 2f
        val cy = view.height / 2f
        val pos = if (isRtl) numPages - 1 - position else position
        when (style) {
            STYLE_NONE -> Unit
            STYLE_SEARCH -> {
                val py = pillCenterY(view)
                val dotsWidth = dotsWidth(numPages, d)
                getSearchPillBounds(view, rect)
                // While swiping the pill morphs to the width of the dots.
                val searchWidth = rect.width()
                val dotsPillWidth = dotsWidth + 24f * d
                val showDots = if (numPages > 1) dotsAlpha.coerceIn(0f, 1f) else 0f
                val morph = GlowGlass.Motion.STANDARD.getInterpolation(showDots)
                val width = searchWidth + (dotsPillWidth - searchWidth) * morph
                val h = PILL_HEIGHT_DP * d
                rect.set(cx - width / 2f, py - h / 2f, cx + width / 2f, py + h / 2f)
                drawPill(view, canvas, rect, 1f, onLightWallpaper)
                // Hand-over instead of a cross-fade: the label leaves during the first half,
                // the dots arrive during the second, so the two never overlap in a smudge
                val searchAlpha = (1f - showDots * 2f).coerceIn(0f, 1f)
                val dotsIn = (showDots * 2f - 1f).coerceIn(0f, 1f)
                if (dotsIn > 0f) drawDots(canvas, cx, py, numPages, pos, d, dotsIn, GlowGlass.ON_GLASS)
                if (searchAlpha > 0f) drawSearchLabel(view, canvas, rect, d, searchAlpha)
            }
            STYLE_LINE -> {
                if (numPages < 2 || dotsAlpha <= 0f) return
                val trackWidth = min(numPages * 18f * d, 140f * d)
                val h = 3f * d
                rect.set(cx - trackWidth / 2f, cy - h / 2f, cx + trackWidth / 2f, cy + h / 2f)
                dotPaint.color = bareColor(onLightWallpaper)
                dotPaint.alpha = (90 * dotsAlpha).toInt()
                canvas.drawRoundRect(rect, h / 2f, h / 2f, dotPaint)
                val segment = trackWidth / numPages
                val left = rect.left + segment * pos.coerceIn(0f, (numPages - 1).toFloat())
                rect.set(left, rect.top, left + segment, rect.bottom)
                dotPaint.alpha = (255 * dotsAlpha).toInt()
                canvas.drawRoundRect(rect, h / 2f, h / 2f, dotPaint)
            }
            STYLE_CAPSULE -> {
                if (numPages < 2 || dotsAlpha <= 0f) return
                val width = dotsWidth(numPages, d) + 24f * d
                val h = 22f * d
                rect.set(cx - width / 2f, cy - h / 2f, cx + width / 2f, cy + h / 2f)
                drawPill(view, canvas, rect, dotsAlpha, onLightWallpaper)
                drawDots(canvas, cx, cy, numPages, pos, d, dotsAlpha, GlowGlass.ON_GLASS)
            }
            else -> {
                if (numPages < 2 || dotsAlpha <= 0f) return
                drawDots(canvas, cx, cy, numPages, pos, d, dotsAlpha, bareColor(onLightWallpaper))
            }
        }
    }

    private fun dotsWidth(numPages: Int, d: Float): Float {
        val n = max(numPages, 1)
        return n * 7f * d + (n - 1) * 8f * d
    }

    /** Dots drawn straight on the wallpaper: white, or dark over a light wallpaper. */
    private fun bareColor(onLightWallpaper: Boolean): Int =
        if (onLightWallpaper) GlowGlass.ON_LIGHT_WALLPAPER else GlowGlass.ON_GLASS

    private fun drawPill(view: View, canvas: Canvas, r: RectF, alpha: Float, onLightWallpaper: Boolean) {
        var glass = pills[view]
        if (glass == null || glass.onLight != onLightWallpaper) {
            glass = GlowGlassDrawable(view.context, r.height() / 2f, GlowGlass.Level.PILL, onLightWallpaper)
            // Only the real home screen indicator blurs; previews in sheets stay light
            if (view is com.android.launcher3.pageindicators.PageIndicatorDots) glass.bindBlur(view, 24)
            pills[view] = glass
        }
        glass.radius = r.height() / 2f
        r.roundOut(pillBounds)
        glass.bounds = pillBounds
        glass.alpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        glass.draw(canvas)
    }

    private fun drawDots(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        numPages: Int,
        pos: Float,
        d: Float,
        alpha: Float,
        color: Int,
    ) {
        dotPaint.color = color
        val radius = 3.5f * d
        val step = 7f * d + 8f * d
        var x = cx - step * (numPages - 1) / 2f
        for (i in 0 until numPages) {
            // The active dot is bright, its neighbours fade in as the page moves.
            val activeness = (1f - abs(pos - i)).coerceIn(0f, 1f)
            dotPaint.alpha = ((90 + 165 * activeness) * alpha).toInt()
            canvas.drawCircle(x, cy, radius, dotPaint)
            x += step
        }
    }

    private fun drawSearchLabel(view: View, canvas: Canvas, pill: RectF, d: Float, alpha: Float) {
        val label = view.context.getString(R.string.label_search)
        textPaint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 15f, view.resources.displayMetrics)
        textPaint.alpha = (255 * alpha).toInt()
        iconPaint.alpha = (255 * alpha).toInt()
        iconPaint.strokeWidth = 1.8f * d
        val iconSize = 14f * d
        val contentWidth = iconSize + 6f * d + textPaint.measureText(label)
        val startX = pill.centerX() - contentWidth / 2f
        val cy = pill.centerY()
        // Magnifier: a ring and a short handle.
        val ringR = iconSize * 0.36f
        val ringCx = startX + ringR + iconSize * 0.05f
        val ringCy = cy - iconSize * 0.08f
        canvas.drawCircle(ringCx, ringCy, ringR, iconPaint)
        val off = ringR * 0.72f
        canvas.drawLine(ringCx + off, ringCy + off, startX + iconSize * 0.95f, cy + iconSize * 0.45f, iconPaint)
        val fm = textPaint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(label, startX + iconSize + 6f * d, baseline, textPaint)
    }
}
