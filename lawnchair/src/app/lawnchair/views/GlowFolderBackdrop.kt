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
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import java.lang.ref.WeakReference

/**
 * iOS-style backdrop for opened home screen folders: the home screen and the
 * dock behind the folder are blurred (Android 12+) and dimmed while the folder
 * is open, so the folder panel reads as frosted glass.
 */
object GlowFolderBackdrop {

    private const val OPEN_MS = 280L
    private const val CLOSE_MS = 220L
    private const val BLUR_DP = 22f
    private const val BLUR_STEPS = 8
    private const val DIM_ALPHA = 70
    private const val DIM_ALPHA_NO_BLUR = 120

    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var launcherRef: WeakReference<Launcher>? = null
    private var lastBlurStep = -1

    @JvmStatic
    fun show(launcher: Launcher) {
        if (!launcher.isInState(LauncherState.NORMAL) && !launcher.isInState(LauncherState.EDIT_MODE)) {
            return
        }
        animateTo(launcher, 1f, OPEN_MS)
    }

    @JvmStatic
    fun hide(launcher: Launcher, animate: Boolean) {
        if (progress == 0f && animator == null) return
        if (animate && GlowGlass.animationsEnabled()) {
            animateTo(launcher, 0f, CLOSE_MS)
        } else {
            animator?.cancel()
            apply(launcher, 0f)
        }
    }

    /** Clears the state kept for a launcher instance that is going away. */
    @JvmStatic
    fun onLauncherDestroyed(launcher: Launcher) {
        if (launcherRef?.get() !== launcher) return
        animator?.cancel()
        animator = null
        progress = 0f
        lastBlurStep = -1
        launcherRef = null
    }

    private fun animateTo(launcher: Launcher, target: Float, duration: Long) {
        animator?.cancel()
        if (launcherRef?.get() !== launcher) {
            // New launcher instance: start from a clean state
            progress = 0f
            lastBlurStep = -1
        }
        launcherRef = WeakReference(launcher)
        if (!GlowGlass.animationsEnabled()) {
            apply(launcher, target)
            return
        }
        val ref = WeakReference(launcher)
        animator = ValueAnimator.ofFloat(progress, target).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val l = ref.get()
                if (l == null || l.isDestroyed) it.cancel() else apply(l, it.animatedValue as Float)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (animator === animation) animator = null
                }
            })
            start()
        }
    }

    private fun targets(launcher: Launcher): List<View> =
        listOfNotNull(launcher.workspace, launcher.hotseat, launcher.workspace?.pageIndicator as? View)

    private fun apply(launcher: Launcher, value: Float) {
        progress = value
        val canBlur = GlowGlass.canAffordBlur(launcher)
        val dim = ((if (canBlur) DIM_ALPHA else DIM_ALPHA_NO_BLUR) * value).toInt()
        // The blur radius moves in a few steps: a new RenderEffect every frame is costly
        val step = if (canBlur) Math.round(value * BLUR_STEPS) else 0
        val updateBlur = step != lastBlurStep
        lastBlurStep = step
        val radius = BLUR_DP * launcher.resources.displayMetrics.density * step / BLUR_STEPS
        targets(launcher).forEach { view ->
            if (value <= 0f) {
                if (view.foreground is ColorDrawable) view.foreground = null
            } else {
                val fg = view.foreground as? ColorDrawable
                    ?: ColorDrawable(Color.BLACK).also { view.foreground = it }
                fg.alpha = dim
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (updateBlur || value <= 0f)) {
                view.setRenderEffect(
                    if (canBlur && radius >= 0.5f) {
                        RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                    } else {
                        null
                    },
                )
            }
        }
    }
}
