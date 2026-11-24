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
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The Glow glass material, drawn in four layers like a real pane of frosted glass:
 *
 * 1. the blurred wallpaper behind it ([bindBlur], Android 12+ where blur is on),
 * 2. a translucent fill that is a little brighter at the top than at the bottom,
 * 3. a soft sheen on the upper part, the light falling on the pane,
 * 4. a hairline rim that catches the light: bright at the top-left edge, fading along
 *    the sides and coming back faintly at the bottom.
 *
 * Over a light wallpaper the fill turns into a dark tint so white text keeps its contrast.
 */
class GlowGlassDrawable @JvmOverloads constructor(
    context: Context,
    radiusPx: Float,
    private val level: GlowGlass.Level,
    val onLight: Boolean = GlowGlass.isLightWallpaper(context),
    /** Fill opacity 0..255 that replaces the level's own (the dock's transparency setting). */
    private val fillAlphaOverride: Int = -1,
) : Drawable(), Drawable.Callback {

    private val density = context.resources.displayMetrics.density
    private val rimWidth = max(1f, density)

    var radius: Float = radiusPx
        set(value) {
            if (field == value) return
            field = value
            GlowBlur.setCornerRadius(blur, value)
            invalidateSelf()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = rimWidth
    }
    private val rect = RectF()
    private val rimRect = RectF()
    private var drawAlpha = 255
    private var shadersFor = Rect()

    private var blur: Drawable? = null
    private var blurHost: View? = null
    private var blurRadiusPx = 0
    private var hostAlpha = 1f

    /**
     * The system has turned blur off while the glass is on screen (battery saver, thermal
     * limits, the user's setting). The fill gets denser so text stays readable without it.
     */
    private var blurOff = false
        set(value) {
            if (field == value) return
            field = value
            shadersFor = Rect()
            invalidateSelf()
        }
    private var blurListener: Consumer<Boolean>? = null

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        if (b != shadersFor) buildShaders(b)
        rect.set(b)
        blur?.let {
            if (it.bounds != b) it.bounds = b
            it.draw(canvas)
        }
        val r = min(radius, min(rect.width(), rect.height()) / 2f)
        fill.alpha = drawAlpha
        sheen.alpha = drawAlpha
        rim.alpha = drawAlpha
        canvas.drawRoundRect(rect, r, r, fill)
        canvas.drawRoundRect(rect, r, r, sheen)
        val inset = rimWidth / 2f
        rimRect.set(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
        canvas.drawRoundRect(rimRect, max(0f, r - inset), max(0f, r - inset), rim)
    }

    private fun buildShaders(b: Rect) {
        shadersFor = Rect(b)
        val top = b.top.toFloat()
        val bottom = b.bottom.toFloat()
        val base = when {
            fillAlphaOverride >= 0 -> fillAlphaOverride
            onLight -> level.darkAlpha
            else -> level.lightAlpha
        }
        val alpha = if (blurOff) min(base * BLUR_OFF_BOOST, MAX_BLUR_OFF_ALPHA) else base.toFloat()
        val (fr, fg, fb) = if (onLight) Triple(22, 16, 32) else Triple(255, 255, 255)
        fill.shader = LinearGradient(
            0f, top, 0f, bottom,
            Color.argb(clamp(alpha * 1.2f), fr, fg, fb),
            Color.argb(clamp(alpha * 0.8f), fr, fg, fb),
            Shader.TileMode.CLAMP,
        )
        val sheenAlpha = if (onLight) 16 else 30
        sheen.shader = LinearGradient(
            0f, top, 0f, top + (bottom - top) * 0.55f,
            Color.argb(sheenAlpha, 255, 255, 255),
            Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP,
        )
        val s = level.strokeAlpha
        rim.shader = LinearGradient(
            b.left.toFloat(), top, b.right.toFloat(), bottom,
            intArrayOf(
                Color.argb(clamp(s * 2.6f), 255, 255, 255),
                Color.argb(clamp(s * 0.5f), 255, 255, 255),
                Color.argb(clamp(s * 0.35f), 255, 255, 255),
                Color.argb(clamp(s * 1.1f), 255, 255, 255),
            ),
            floatArrayOf(0f, 0.38f, 0.68f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    private fun clamp(v: Float): Int = v.roundToInt().coerceIn(0, 255)

    /**
     * Blurs the wallpaper behind this glass while [host] (the view that draws it) is in a
     * window. The blur follows the host's on-screen alpha, so it fades with it.
     */
    fun bindBlur(host: View, blurRadiusDp: Int = 28) {
        if (blurHost === host) return
        blurHost = host
        blurRadiusPx = (blurRadiusDp * density).roundToInt()
        host.addOnAttachStateChangeListener(attachListener)
        if (host.isAttachedToWindow) attachListener.onViewAttachedToWindow(host)
    }

    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        val host = blurHost
        val current = blur
        if (host != null && current != null) {
            val a = effectiveAlpha(host)
            if (a != hostAlpha) {
                hostAlpha = a
                current.alpha = (255 * a * (drawAlpha / 255f)).roundToInt()
                host.invalidate()
            }
        }
        true
    }

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurListener == null) {
                val listener = Consumer<Boolean> { enabled -> onBlurEnabledChanged(v, enabled) }
                val added = runCatching {
                    v.context.getSystemService(WindowManager::class.java)
                        ?.addCrossWindowBlurEnabledListener(v.context.mainExecutor, listener)
                }.isSuccess
                if (added) blurListener = listener
            }
            attachBlur(v)
        }

        override fun onViewDetachedFromWindow(v: View) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurListener?.let { listener ->
                    runCatching {
                        v.context.getSystemService(WindowManager::class.java)
                            ?.removeCrossWindowBlurEnabledListener(listener)
                    }
                }
            }
            blurListener = null
            detachBlur(v)
        }
    }

    private fun onBlurEnabledChanged(v: View, enabled: Boolean) {
        blurOff = !enabled
        if (!v.isAttachedToWindow) return
        if (enabled) attachBlur(v) else detachBlur(v)
        v.invalidate()
    }

    private fun attachBlur(v: View) {
        if (blur != null) return
        blur = GlowBlur.create(v, blurRadiusPx, radius)?.also {
            it.callback = this@GlowGlassDrawable
            hostAlpha = effectiveAlpha(v)
            it.alpha = (255 * hostAlpha * (drawAlpha / 255f)).roundToInt()
        }
        if (blur != null) v.viewTreeObserver.addOnPreDrawListener(preDraw)
        invalidateSelf()
    }

    private fun detachBlur(v: View) {
        v.viewTreeObserver.removeOnPreDrawListener(preDraw)
        blur?.callback = null
        blur = null
        invalidateSelf()
    }

    /** Alpha of [view] as it appears on screen: its own times every parent's. */
    private fun effectiveAlpha(view: View): Float {
        var a = 1f
        var v: View? = view
        while (v != null) {
            if (v.visibility != View.VISIBLE) return 0f
            a *= v.alpha
            v = v.parent as? View
        }
        return a.coerceIn(0f, 1f)
    }

    override fun setAlpha(alpha: Int) {
        if (drawAlpha == alpha) return
        drawAlpha = alpha
        blur?.alpha = (255 * hostAlpha * (alpha / 255f)).roundToInt()
        invalidateSelf()
    }

    override fun getAlpha(): Int = drawAlpha

    override fun getOutline(outline: Outline) {
        val b = bounds
        outline.setRoundRect(b, min(radius, min(b.width(), b.height()) / 2f))
        outline.alpha = drawAlpha / 255f * 0.5f
    }

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun invalidateDrawable(who: Drawable) {
        invalidateSelf()
        // The host may draw this glass by hand (not as its background), so it would not
        // recognise the drawable in View.invalidateDrawable()
        blurHost?.invalidate()
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) =
        scheduleSelf(what, `when`)

    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)

    private companion object {
        const val BLUR_OFF_BOOST = 1.7f
        const val MAX_BLUR_OFF_ALPHA = 200f
    }
}
