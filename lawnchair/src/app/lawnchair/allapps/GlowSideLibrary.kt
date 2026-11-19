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

import android.animation.Animator
import android.animation.ObjectAnimator
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import app.lawnchair.views.GlowGlass
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.allapps.AllAppsTransitionController
import kotlin.math.abs

/**
 * Glow: the App Library slides in from the right as a page next to the home
 * screen (like iOS) instead of rising from the bottom. The all apps transition
 * progress (0 = shown, 1 = hidden) moves the panel horizontally.
 */
object GlowSideLibrary {

    @JvmStatic
    fun isEnabled(launcher: Launcher): Boolean {
        val dp = launcher.deviceProfile
        // Phones only. Not gated on shouldShowAllAppsOnSheet(): this build turns the
        // all_apps_sheet_for_handheld flag on, which made that check false on every phone
        // and silently kept the library on the vertical drawer.
        return dp.deviceProperties.isPhone && !dp.isVerticalBarLayout
    }

    /** +1 when the library sits to the right of the home screen, -1 in right-to-left layouts. */
    private fun direction(view: View): Int =
        if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1 else 1

    @JvmStatic
    fun applyProgress(launcher: Launcher, progress: Float) {
        val appsView = launcher.appsView ?: return
        val width = launcher.dragLayer?.width?.takeIf { it > 0 } ?: appsView.width
        appsView.translationX = direction(appsView) * progress.coerceIn(0f, 1f) * width
        // The sheet's background is painted by the scrim from the container's position
        launcher.scrimView?.invalidate()
    }

    @JvmStatic
    fun clear(launcher: Launcher) {
        val appsView = launcher.appsView ?: return
        if (appsView.translationX != 0f) {
            appsView.translationX = 0f
            launcher.scrimView?.invalidate()
        }
    }

    /** A rightward swipe on the library drags it away and returns to the home screen. */
    class SwipeBack(private val launcher: Launcher) {
        private val slop = ViewConfiguration.get(launcher).scaledTouchSlop
        private val minFling = 900f * launcher.resources.displayMetrics.density
        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        private var tracker: VelocityTracker? = null
        private var settle: Animator? = null

        fun isDragging() = dragging

        fun onInterceptTouchEvent(view: View, ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                    dragging = false
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain().also { it.addMovement(ev) }
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker?.addMovement(ev)
                    val dx = (ev.x - downX) * direction(view)
                    val dy = ev.y - downY
                    if (!dragging && dx > slop * 2 && dx > abs(dy) * 1.6f &&
                        launcher.isInState(LauncherState.ALL_APPS)
                    ) {
                        dragging = true
                        downX = ev.x
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracker?.recycle()
                    tracker = null
                }
            }
            return dragging
        }

        fun onTouchEvent(view: View, ev: MotionEvent): Boolean {
            val controller = launcher.allAppsController
            val width = view.width.coerceAtLeast(1)
            tracker?.addMovement(ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    controller.setProgress(((ev.x - downX) * direction(view) / width).coerceIn(0f, 1f))
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    val vx = direction(view) * (
                        tracker?.let {
                            it.computeCurrentVelocity(1000)
                            it.xVelocity
                        } ?: 0f
                        )
                    tracker?.recycle()
                    tracker = null
                    val progress = controller.progress
                    val close = ev.actionMasked == MotionEvent.ACTION_UP &&
                        (vx > minFling || (progress > 0.35f && vx > -minFling))
                    if (close) {
                        launcher.stateManager.goToState(LauncherState.NORMAL)
                    } else {
                        settle?.cancel()
                        settle = ObjectAnimator.ofFloat(controller, AllAppsTransitionController.ALL_APPS_PROGRESS, 0f)
                            .setDuration(GlowGlass.Motion.MEDIUM)
                            .apply { interpolator = GlowGlass.Motion.ENTER }
                            .also { anim ->
                                // Any state change (Home pressed, back gesture) takes over from the snap-back
                                val listener = object : com.android.launcher3.statemanager.StateManager.StateListener<LauncherState> {
                                    override fun onStateTransitionStart(toState: LauncherState) {
                                        anim.cancel()
                                    }
                                }
                                launcher.stateManager.addStateListener(listener)
                                anim.addListener(object : android.animation.AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) {
                                        launcher.stateManager.removeStateListener(listener)
                                        if (settle === animation) settle = null
                                    }
                                })
                                anim.start()
                            }
                    }
                }
            }
            return true
        }
    }
}
