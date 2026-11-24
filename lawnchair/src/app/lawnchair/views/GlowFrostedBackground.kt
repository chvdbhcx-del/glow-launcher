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

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View

/**
 * Turns an opaque sheet background (the widget picker) into frosted glass: the wallpaper
 * behind it is blurred and the sheet's own surface colour is drawn slightly see-through
 * on top. The surface keeps the theme's colour, so text keeps its contrast in light and
 * dark themes. Without blur (older Android, battery saver) the sheet stays opaque.
 */
class GlowFrostedBackground private constructor(
    private val base: Drawable,
    private val radiusPx: Float,
    private val blurRadiusPx: Int,
) : Drawable(), Drawable.Callback {

    private var blur: Drawable? = null
    private var host: View? = null

    override fun draw(canvas: Canvas) {
        val b = bounds
        val glass = blur
        if (glass != null) {
            // Only the top corners are rounded; the blur region runs past the bottom edge
            if (glass.bounds != b) glass.setBounds(b.left, b.top, b.right, b.bottom + radiusPx.toInt())
            glass.draw(canvas)
            base.alpha = SURFACE_ALPHA
        } else {
            base.alpha = 255
        }
        if (base.bounds != b) base.bounds = b
        base.draw(canvas)
    }

    override fun onBoundsChange(bounds: Rect) {
        base.bounds = bounds
    }

    private val attach = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            if (blur != null) return
            blur = GlowBlur.create(v, blurRadiusPx, radiusPx)?.also { it.callback = this@GlowFrostedBackground }
            invalidateSelf()
        }

        override fun onViewDetachedFromWindow(v: View) {
            blur?.callback = null
            blur = null
        }
    }

    override fun setAlpha(alpha: Int) {
        blur?.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun invalidateDrawable(who: Drawable) {
        invalidateSelf()
        host?.invalidate()
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = scheduleSelf(what, `when`)

    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)

    companion object {
        private const val SURFACE_ALPHA = 222

        /** Replaces [view]'s background with a frosted version of it. */
        @JvmStatic
        fun apply(view: View, cornerRadiusPx: Float) {
            val base = view.background?.mutate() ?: return
            val density = view.resources.displayMetrics.density
            val frosted = GlowFrostedBackground(base, cornerRadiusPx, (30 * density).toInt())
            frosted.host = view
            view.background = frosted
            view.addOnAttachStateChangeListener(frosted.attach)
            if (view.isAttachedToWindow) frosted.attach.onViewAttachedToWindow(view)
        }
    }
}
