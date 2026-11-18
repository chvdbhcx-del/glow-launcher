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

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.InsetDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.TextView
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import kotlin.math.max

/**
 * Glow design tokens: one place for the frosted "glass" material, pills and the
 * accessibility basics every Glow control shares (button role, 48dp touch area).
 *
 * The glass adapts to the wallpaper: over a light wallpaper (the launcher's
 * `isWorkspaceDarkText` hint) it turns into a dark tint, so white labels keep
 * their contrast instead of fading into a pale background.
 */
object GlowGlass {

    const val ACCENT = 0xFFB14FE8.toInt()

    /** Text and glyphs on glass. */
    const val ON_GLASS = 0xFFFFFFFF.toInt()

    /** Dark glyphs (page dots) drawn straight on a light wallpaper. */
    const val ON_LIGHT_WALLPAPER = 0xFF1E1828.toInt()

    /**
     * Glow motion tokens. Everything that opens uses [ENTER] (fast start, long soft
     * landing), everything that closes uses [EXIT]; small pop-ups get [POP].
     */
    object Motion {
        const val SHORT = 180L
        const val MEDIUM = 280L
        const val LONG = 400L

        /** Opening: starts gently, glides to a soft stop (no jump on the first frame). */
        @JvmField val ENTER: Interpolator = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)

        /** Closing: eases in and eases out, so nothing stops dead at the end. */
        @JvmField val EXIT: Interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
        @JvmField val STANDARD: Interpolator = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
        @JvmField val POP: Interpolator = OvershootInterpolator(0.7f)
    }

    /** Minimum touch target recommended by Android accessibility guidelines. */
    const val MIN_TOUCH_DP = 48

    enum class Level(val lightAlpha: Int, val darkAlpha: Int, val strokeAlpha: Int) {
        /** Tiles and cards over the blurred App Library. */
        TILE(40, 70, 34),

        /** Pills: Edit / Done, A–Z, Categories. */
        PILL(70, 110, 90),

        /** Opened category card. */
        CARD(64, 96, 50),
    }

    fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    fun dp(context: Context, value: Int): Int = dp(context, value.toFloat())

    /** True when the wallpaper behind the launcher is light (dark text would read better). */
    @JvmStatic
    fun isLightWallpaper(context: Context): Boolean =
        runCatching { Themes.getAttrBoolean(context, R.attr.isWorkspaceDarkText) }.getOrDefault(false)

    /** The glass material: translucent gradient fill, top sheen and a light-catching rim. */
    @JvmStatic
    fun drawable(context: Context, radiusPx: Float, level: Level): GlowGlassDrawable =
        GlowGlassDrawable(context, radiusPx, level)

    /** Fill of the glass [level]: white frost on dark wallpapers, a dark tint on light ones. */
    @JvmStatic
    @JvmOverloads
    fun fillColor(level: Level, onLightWallpaper: Boolean, alpha: Float = 1f): Int =
        if (onLightWallpaper) {
            Color.argb((level.darkAlpha * alpha).toInt(), 22, 16, 32)
        } else {
            Color.argb((level.lightAlpha * alpha).toInt(), 255, 255, 255)
        }

    /** The thin light rim every glass surface has. */
    @JvmStatic
    @JvmOverloads
    fun strokeColor(level: Level, alpha: Float = 1f): Int =
        Color.argb((level.strokeAlpha * alpha).toInt(), 255, 255, 255)

    /**
     * A glass pill with a [heightDp] visual height but a touch area of at least 48dp:
     * the background is inset vertically, the view itself stays tall.
     */
    fun pill(
        context: Context,
        label: CharSequence,
        heightDp: Int,
        textSp: Float = 14f,
        bold: Boolean = false,
        realBlur: Boolean = false,
    ): TextView =
        TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
            typeface = Typeface.create(if (bold) "sans-serif" else "sans-serif-medium", if (bold) Typeface.BOLD else Typeface.NORMAL)
            letterSpacing = 0.01f
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            val inset = max(0, (dp(context, MIN_TOUCH_DP) - dp(context, heightDp)) / 2)
            val glass = GlowGlassDrawable(context, dp(context, heightDp) / 2f, Level.PILL)
            if (realBlur) glass.bindBlur(this)
            // setBackground() replaces the padding with the drawable's, so pad afterwards
            background = InsetDrawable(glass, 0, inset, 0, inset)
            val pad = dp(context, 16)
            setPadding(pad, 0, pad, 0)
            minWidth = dp(context, MIN_TOUCH_DP + 16)
            minHeight = dp(context, MIN_TOUCH_DP)
            minimumHeight = dp(context, MIN_TOUCH_DP)
            isClickable = true
            isFocusable = true
            asButton(this)
        }

    /** TalkBack announces the view as a button. */
    fun asButton(view: View) {
        view.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        }
    }

    /** TalkBack can jump between headings. */
    fun asHeading(view: View) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            view.isAccessibilityHeading = true
        }
    }

    /** False when the user turned animations off ("Remove animations" in accessibility). */
    fun animationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

    /** Real-time blur is skipped on low-RAM devices, where it would stutter. */
    fun canAffordBlur(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            context.getSystemService(ActivityManager::class.java)?.isLowRamDevice != true
}
