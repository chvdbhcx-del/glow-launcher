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
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.animation.LinearInterpolator
import androidx.core.view.ViewCompat
import com.android.launcher3.BubbleTextView
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.ShortcutAndWidgetContainer
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.AppPairInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.widget.LauncherAppWidgetHostView
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The iOS-style "jiggle" edit mode of the Glow home screen:
 *
 * - every icon, folder and widget on the home screen and in the dock gently
 *   wiggles while [LauncherState.EDIT_MODE] is active;
 * - each item gets a round "−" badge in its top-left corner, tapping it asks
 *   for confirmation and removes the item from the home screen (the app stays
 *   in the App Library).
 *
 * Drawing and touch handling are called from [ShortcutAndWidgetContainer].
 */
object GlowEditMode {

    /** Full wiggle cycle, iOS uses roughly a quarter of a second. */
    private const val PERIOD_MS = 260f
    private const val ICON_DEGREES = 1.7f
    private const val WIDGET_DEGREES = 0.45f
    private const val BADGE_FADE_MS = 180L

    private var jiggle: ValueAnimator? = null
    private var launcherRef: WeakReference<Launcher>? = null

    /** 0 = badges hidden, 1 = fully shown. Animated together with the state. */
    private var badgeProgress = 0f
    private var badgeAnim: ValueAnimator? = null

    private var pressedView: WeakReference<View>? = null
    private var pressedContainer: WeakReference<ShortcutAndWidgetContainer>? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val minusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tmpRect = Rect()
    private val tmpPoint = FloatArray(2)
    private val tmpMatrix = Matrix()

    /** TalkBack "Remove" actions added to home screen items while editing. */
    private val removeActions = WeakHashMap<View, Int>()

    // ---------------------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------------------

    @JvmStatic
    fun isEditing(launcher: Launcher): Boolean =
        launcher.isInState(LauncherState.EDIT_MODE) ||
            launcher.stateManager.state == LauncherState.EDIT_MODE

    /** Called by [GlowEditOverlay] when the launcher enters or leaves edit mode. */
    fun setActive(launcher: Launcher, active: Boolean) {
        if (launcherRef?.get() !== launcher) {
            // A new launcher instance (recreated after a theme or grid change): drop the old one
            jiggle?.cancel()
            jiggle = null
        }
        launcherRef = WeakReference(launcher)
        animateBadges(launcher, if (active) 1f else 0f)
        if (active) {
            updateRemoveActions(launcher, true)
            if (jiggle?.isRunning == true || !shouldJiggle(launcher)) return
            val ref = WeakReference(launcher)
            jiggle = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val l = ref.get()
                    if (l == null || l.isDestroyed) it.cancel() else applyJiggle(l, it.currentPlayTime)
                }
                start()
            }
        } else {
            jiggle?.cancel()
            jiggle = null
            updateRemoveActions(launcher, false)
            forEachContainer(launcher) { container ->
                for (i in 0 until container.childCount) {
                    container.getChildAt(i).rotation = 0f
                }
            }
        }
    }

    /** Called when the launcher is destroyed, so nothing keeps it alive. */
    @JvmStatic
    fun onLauncherDestroyed(launcher: Launcher) {
        if (launcherRef?.get() !== launcher) return
        jiggle?.cancel()
        jiggle = null
        badgeAnim?.cancel()
        badgeAnim = null
        badgeProgress = 0f
        removeActions.clear()
        launcherRef = null
        pressedView = null
        pressedContainer = null
    }

    /**
     * No wiggle when animations are off in the system settings ("Remove animations")
     * or TalkBack is on: the "−" badges alone mark the edit mode.
     */
    private fun shouldJiggle(launcher: Launcher): Boolean {
        if (!GlowGlass.animationsEnabled()) return false
        val am = launcher.getSystemService(AccessibilityManager::class.java)
        return am?.isTouchExplorationEnabled != true
    }

    private fun updateRemoveActions(launcher: Launcher, add: Boolean) {
        removeActions.forEach { (view, id) -> ViewCompat.removeAccessibilityAction(view, id) }
        removeActions.clear()
        if (!add) return
        forEachContainer(launcher) { container ->
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (!hasBadge(container, child)) continue
                val title = (child.tag as? ItemInfo)?.title?.toString().orEmpty()
                val label = launcher.getString(R.string.glow_remove_badge, title)
                val id = ViewCompat.addAccessibilityAction(child, label) { view, _ ->
                    confirmRemove(launcher, view)
                    true
                }
                if (id != View.NO_ID) removeActions[child] = id
            }
        }
    }

    private fun animateBadges(launcher: Launcher, target: Float) {
        badgeAnim?.cancel()
        badgeAnim = ValueAnimator.ofFloat(badgeProgress, target).apply {
            duration = BADGE_FADE_MS
            val ref = WeakReference(launcher)
            addUpdateListener {
                badgeProgress = it.animatedValue as Float
                ref.get()?.let { l -> forEachContainer(l) { c -> c.invalidate() } }
            }
            start()
        }
    }

    private fun applyJiggle(launcher: Launcher, playTime: Long) {
        val phase = (playTime % PERIOD_MS.toLong()) / PERIOD_MS * 2f * PI.toFloat()
        var index = 0
        // Only the page on screen and its neighbours move; the rest is not visible anyway.
        val current = launcher.workspace?.nextPage ?: 0
        forEachContainer(launcher, current - 1..current + 1) { container ->
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                val amplitude =
                    if (child is LauncherAppWidgetHostView) WIDGET_DEGREES else ICON_DEGREES
                // Neighbouring items move in opposite directions with a small offset,
                // so the grid looks alive instead of rocking as one block.
                val offset = (index % 2) * PI.toFloat() + (index % 3) * 0.45f
                child.rotation = amplitude * sin(phase + offset)
                index++
            }
            // The "−" badges are drawn by the container and follow each child's rotation
            container.invalidate()
        }
    }

    private inline fun forEachContainer(
        launcher: Launcher,
        pages: IntRange? = null,
        block: (ShortcutAndWidgetContainer) -> Unit,
    ) {
        val workspace = launcher.workspace ?: return
        for (i in 0 until workspace.childCount) {
            if (pages != null && i !in pages) continue
            val page = workspace.getChildAt(i) as? CellLayout ?: continue
            block(page.shortcutsAndWidgets)
        }
        launcher.hotseat?.pageLayouts?.forEach { block(it.shortcutsAndWidgets) }
    }

    // ---------------------------------------------------------------------------------------
    // Badges
    // ---------------------------------------------------------------------------------------

    private fun hasBadge(container: ShortcutAndWidgetContainer, child: View): Boolean {
        if (container.containerType == CellLayout.FOLDER) return false
        if (child.visibility != View.VISIBLE || child.alpha < 0.05f) return false
        val tag = child.tag
        return tag is WorkspaceItemInfo || tag is FolderInfo || tag is AppPairInfo ||
            tag is LauncherAppWidgetInfo
    }

    /** Badge centre in the child's own (untransformed) coordinates. */
    private fun badgeCenter(child: View, out: FloatArray) {
        val d = child.resources.displayMetrics.density
        when (child) {
            is BubbleTextView -> {
                child.getIconBounds(tmpRect)
                out[0] = tmpRect.left + 3 * d
                out[1] = tmpRect.top + 3 * d
            }
            is FolderIcon -> {
                child.getPreviewBounds(tmpRect)
                out[0] = tmpRect.left + 3 * d
                out[1] = tmpRect.top + 3 * d
            }
            else -> {
                out[0] = 10 * d
                out[1] = 10 * d
            }
        }
    }

    @JvmStatic
    fun drawBadges(container: ShortcutAndWidgetContainer, canvas: Canvas) {
        val progress = badgeProgress
        if (progress <= 0f) return
        val d = container.resources.displayMetrics.density
        val radius = 11f * d * (0.6f + 0.4f * progress)
        val alpha = (255 * progress).toInt()
        fillPaint.color = Color.argb((235 * progress).toInt(), 236, 236, 242)
        strokePaint.color = Color.argb((60 * progress).toInt(), 0, 0, 0)
        strokePaint.strokeWidth = 0.75f * d
        minusPaint.color = Color.argb(alpha, 40, 40, 48)
        minusPaint.strokeWidth = 2f * d
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (!hasBadge(container, child)) continue
            badgeCenter(child, tmpPoint)
            canvas.save()
            canvas.translate(child.left.toFloat(), child.top.toFloat())
            canvas.concat(child.matrix)
            val cx = tmpPoint[0]
            val cy = tmpPoint[1]
            canvas.drawCircle(cx, cy, radius, fillPaint)
            canvas.drawCircle(cx, cy, radius, strokePaint)
            val half = radius * 0.45f
            canvas.drawLine(cx - half, cy, cx + half, cy, minusPaint)
            canvas.restore()
        }
    }

    private fun findBadgeHit(container: ShortcutAndWidgetContainer, x: Float, y: Float): View? {
        val slop = 18f * container.resources.displayMetrics.density
        for (i in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(i)
            if (!hasBadge(container, child)) continue
            // Map the touch into the child's local space, undoing its wiggle.
            tmpPoint[0] = x - child.left
            tmpPoint[1] = y - child.top
            if (child.matrix.invert(tmpMatrix)) tmpMatrix.mapPoints(tmpPoint)
            val tx = tmpPoint[0]
            val ty = tmpPoint[1]
            badgeCenter(child, tmpPoint)
            if (hypot(tx - tmpPoint[0], ty - tmpPoint[1]) <= slop) return child
        }
        return null
    }

    /**
     * Intercepts taps on remove badges. Returns true when the event was consumed.
     */
    @JvmStatic
    fun dispatchBadgeTouch(container: ShortcutAndWidgetContainer, ev: MotionEvent): Boolean {
        val launcher = launcherRef?.get() ?: return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedView = null
                pressedContainer = null
                if (badgeProgress < 0.9f || !launcher.isInState(LauncherState.EDIT_MODE)) {
                    return false
                }
                val hit = findBadgeHit(container, ev.x, ev.y) ?: return false
                pressedView = WeakReference(hit)
                pressedContainer = WeakReference(container)
                container.parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> return pressedContainer?.get() === container
            MotionEvent.ACTION_UP -> {
                if (pressedContainer?.get() !== container) return false
                val view = pressedView?.get()
                pressedView = null
                pressedContainer = null
                if (view != null && findBadgeHit(container, ev.x, ev.y) === view) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    confirmRemove(launcher, view)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                val mine = pressedContainer?.get() === container
                pressedView = null
                pressedContainer = null
                return mine
            }
        }
        return pressedContainer?.get() === container
    }

    private fun confirmRemove(launcher: Launcher, view: View) {
        val info = view.tag as? ItemInfo ?: return
        val title = info.title?.toString()?.takeIf { it.isNotBlank() }
            ?: launcher.getString(R.string.glow_library_other)
        val message = when (info) {
            is FolderInfo -> R.string.glow_remove_folder_message
            is LauncherAppWidgetInfo -> R.string.glow_remove_widget_message
            else -> R.string.glow_remove_app_message
        }
        val builder = AlertDialog.Builder(launcher, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(launcher.getString(R.string.glow_remove_title, title))
            .setMessage(message)
            .setNegativeButton(R.string.glow_cancel, null)

        val pkg = (info as? WorkspaceItemInfo)
            ?.takeIf { it.itemType == com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION }
            ?.targetPackage
        if (pkg != null) {
            builder.setPositiveButton(R.string.glow_remove_from_home) { _, _ ->
                launcher.removeItem(view, info, true, "Glow edit mode remove badge")
            }
            builder.setNeutralButton(R.string.glow_delete_app) { _, _ ->
                runCatching {
                    launcher.startActivity(
                        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null))
                            .putExtra(Intent.EXTRA_USER, info.user)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        } else {
            builder.setPositiveButton(R.string.glow_remove) { _, _ ->
                launcher.removeItem(view, info, true, "Glow edit mode remove badge")
            }
        }
        builder.show()
    }
}
