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

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.lawnchair.flowerpot.Flowerpot
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.views.GlowGlass
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate
import kotlin.math.roundToInt

/**
 * Glow "App Library" (iOS style): the app drawer shows automatic categories as glass tiles.
 * Each tile shows three apps that open with one tap plus a mini grid that opens the whole
 * category, like a folder. Search stays on top; "A–Z" switches to an alphabetical list.
 *
 * Everything scrollable is a [RecyclerView], so only the rows on screen exist as views.
 * Categories are computed off the main and model threads, cached per package and
 * rebuilt at most once per burst of app changes.
 */
class GlowAppLibraryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private data class Category(val key: String, val label: String, val apps: List<AppInfo>)

    private val launcher: Launcher = Launcher.getLauncher(context)
    private val density = resources.displayMetrics.density

    private val tilesAdapter = TilesAdapter()
    private val tilesLayout = GridLayoutManager(context, 2).apply {
        spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = if (position == 0) spanCount else 1
        }
    }
    private val tiles = RecyclerView(context).apply {
        layoutManager = tilesLayout
        adapter = tilesAdapter
        itemAnimator = null
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        clipToPadding = false
        // No built-in fading edge: with clipToPadding=false it fades at the padding line and
        // leaves a hard band below it. The library masks the true edges itself (drawChild).
        isVerticalFadingEdgeEnabled = false
    }

    private val edgeMask = android.graphics.Paint().apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
    }
    private var topMask: android.graphics.Shader? = null
    private var bottomMask: android.graphics.Shader? = null
    private var maskSize = 0

    private fun topFade() = dp(TOP_FADE_DP)
    private fun bottomFade() = bottomInset + dp(BOTTOM_FADE_DP)

    /** Fades the tiles out softly under the search bar and above the gesture bar. */
    override fun drawChild(canvas: android.graphics.Canvas, child: View, drawingTime: Long): Boolean {
        if (child !== tiles || width == 0 || height == 0) return super.drawChild(canvas, child, drawingTime)
        val h = height.toFloat()
        val size = height * 31 + bottomFade()
        if (size != maskSize) {
            maskSize = size
            topMask = android.graphics.LinearGradient(
                0f, 0f, 0f, topFade().toFloat(),
                Color.BLACK, Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP,
            )
            bottomMask = android.graphics.LinearGradient(
                0f, h - bottomFade(), 0f, h,
                intArrayOf(Color.TRANSPARENT, 0x99000000.toInt(), Color.BLACK),
                floatArrayOf(0f, 0.55f, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        val save = canvas.saveLayer(0f, 0f, width.toFloat(), h, null)
        val result = super.drawChild(canvas, child, drawingTime)
        if (tiles.canScrollVertically(-1)) {
            edgeMask.shader = topMask
            canvas.drawRect(0f, 0f, width.toFloat(), topFade().toFloat(), edgeMask)
        }
        edgeMask.shader = bottomMask
        canvas.drawRect(0f, h - bottomFade(), width.toFloat(), h, edgeMask)
        canvas.restoreToCount(save)
        return result
    }

    private var folderOverlay: View? = null
    private var categories: List<Category> = emptyList()
    private var generation = 0
    private var bottomInset = 0

    /** Supplies the apps to show (personal and work profile, hidden apps removed). */
    var appsProvider: (() -> List<AppInfo>)? = null

    /** Switches to the classic list, which holds the locked Private Space section. */
    var onShowAllApps: (() -> Unit)? = null

    /** True when a Private Space is set up; the A–Z list then links to it. */
    var hasPrivateSpace: (() -> Boolean)? = null

    private val rebuildRunnable = Runnable { doRebuild() }

    init {
        addView(tiles, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setBottomInset(0)
    }

    fun setBottomInset(bottom: Int) {
        bottomInset = bottom
        val side = dp(SIDE_PADDING_DP)
        tiles.setPadding(side, dp(10), side, bottom + dp(BOTTOM_FADE_DP) + dp(8))
        maskSize = 0
        invalidate()
        listView?.setPadding(0, 0, 0, bottom + dp(24))
    }

    fun canScrollUp(): Boolean = folderOverlay != null ||
        (listView?.canScrollVertically(-1) ?: tiles.canScrollVertically(-1))

    fun scrollToTop() {
        tiles.scrollToPosition(0)
        listView?.scrollToPosition(0)
    }

    /** Schedules a rebuild; bursts of app updates (installs, icon changes) collapse into one. */
    fun rebuild() {
        removeCallbacks(rebuildRunnable)
        if (categories.isEmpty()) post(rebuildRunnable) else postDelayed(rebuildRunnable, REBUILD_DELAY_MS)
    }

    private fun doRebuild() {
        val apps = appsProvider?.invoke() ?: return
        val gen = ++generation
        val appContext = context.applicationContext
        // Flowerpot's manager must be obtained on the main thread
        val pots = runCatching { Flowerpot.Manager.getInstance(appContext).getAllPots().toList() }
            .getOrDefault(emptyList())
        UI_HELPER_EXECUTOR.execute {
            val result = runCatching { categorize(appContext, apps, pots) }.getOrDefault(emptyList())
            MAIN_EXECUTOR.execute {
                if (gen != generation) return@execute
                categories = result
                tilesAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == oldw) return
        val cols = GlowCategoryRules.tileColumns(w / density)
        if (cols != tilesLayout.spanCount) tilesLayout.spanCount = cols
        post { tilesAdapter.notifyDataSetChanged() }
    }

    private fun tileSize(): Int {
        val inner = width - tiles.paddingLeft - tiles.paddingRight
        val cols = tilesLayout.spanCount
        return ((inner - dp(TILE_GAP_DP) * (cols - 1)) / cols).coerceAtLeast(0)
    }

    // ---------------------------------------------------------------------------------------
    // Tiles
    // ---------------------------------------------------------------------------------------

    private class Holder(val root: FrameLayout) : RecyclerView.ViewHolder(root)

    private inner class TilesAdapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount(): Int = if (categories.isEmpty()) 0 else categories.size + 1
        override fun getItemViewType(position: Int): Int = if (position == 0) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(FrameLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT,
                )
            })

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val root = holder.root
            root.removeAllViews()
            if (position == 0) {
                // "A–Z" pill to switch to the alphabetical list
                root.addView(
                    GlowGlass.pill(context, context.getString(R.string.glow_library_all_apps), PILL_HEIGHT_DP).apply {
                        contentDescription = context.getString(R.string.glow_library_all_apps_description)
                        setOnClickListener { openList() }
                    },
                    LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END),
                )
                root.setPadding(0, 0, 0, dp(4))
                return
            }
            val index = position - 1
            val size = tileSize()
            if (size <= 0 || index >= categories.size) return
            val cols = tilesLayout.spanCount
            val column = index % cols
            val gap = dp(TILE_GAP_DP)
            // Distribute the gap so every tile keeps the same size
            root.setPadding(gap * column / cols, 0, gap - gap * (column + 1) / cols, dp(14))
            root.addView(
                createTile(categories[index], size),
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL),
            )
        }
    }

    /**
     * Geometry of a tile: four equal slots with the same spacing between the slots as
     * around them, so big icons and the mini grid line up on one grid.
     */
    private class TileGeometry(tileSize: Int, density: Float) {
        val space = (tileSize * TILE_SPACE_RATIO).roundToInt().coerceAtLeast((8 * density).roundToInt())
        val slot = ((tileSize - 3 * space) / 2).coerceAtLeast(1)
        // Spread any rounding leftover evenly on both sides
        val pad = (tileSize - 2 * slot - space) / 2
        val radius = tileSize * TILE_RADIUS_RATIO

        fun left(index: Int) = pad + (index % 2) * (slot + space)
        fun top(index: Int) = pad + (index / 2) * (slot + space)
    }

    private fun createTile(category: Category, tileSize: Int): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val geo = TileGeometry(tileSize, density)
        val square = FrameLayout(context).apply {
            background = GlowGlass.drawable(context, geo.radius, GlowGlass.Level.TILE)
            clipChildren = false
            clipToPadding = false
        }
        val apps = category.apps
        val bigCount = if (apps.size <= 4) apps.size else 3
        for (i in 0 until bigCount) {
            addBigIcon(square, apps[i], geo, i)
        }
        if (apps.size > 4) {
            val rest = apps.size - 3
            square.addView(
                createMiniGrid(apps.subList(3, minOf(7, apps.size)), geo.slot, category.label, rest) {
                    openCategory(category, square)
                },
                LayoutParams(geo.slot, geo.slot).apply {
                    leftMargin = geo.left(3)
                    topMargin = geo.top(3)
                },
            )
        }
        column.addView(square, LinearLayout.LayoutParams(tileSize, tileSize))
        column.addView(TextView(context).apply {
            text = category.label
            setTextColor(GlowGlass.ON_GLASS)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.01f
            includeFontPadding = false
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), 0x66000000)
            minHeight = dp(GlowGlass.MIN_TOUCH_DP - 18)
            setOnClickListener { openCategory(category, square) }
            contentDescription = context.getString(R.string.glow_library_open_named, category.label, apps.size)
            GlowGlass.asHeading(this)
            GlowGlass.asButton(this)
        }, LinearLayout.LayoutParams(tileSize, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        })
        return column
    }

    private fun bubble(app: AppInfo, parent: ViewGroup): BubbleTextView {
        val icon = LayoutInflater.from(context)
            .inflate(R.layout.all_apps_icon, parent, false) as BubbleTextView
        icon.applyFromApplicationInfo(app)
        icon.setOnClickListener(launcher.itemOnClickListener)
        icon.setOnLongClickListener(launcher.allAppsItemLongClickListener)
        icon.setLongPressTimeoutFactor(1f)
        return icon
    }

    /**
     * A big icon that fills its slot exactly. BubbleTextView draws its icon at a fixed size
     * (with room for a label), so the view is made exactly icon-sized, the hidden label gets
     * no space, and the view is scaled to the slot around its centre.
     */
    private fun addBigIcon(square: FrameLayout, app: AppInfo, geo: TileGeometry, index: Int) {
        val view = bubble(app, square)
        view.setTextVisibility(false)
        view.setCenterVertically(false)
        view.compoundDrawablePadding = 0
        view.setPadding(0, 0, 0, 0)
        val icon = view.iconSize.takeIf { it > 0 } ?: geo.slot
        val scale = geo.slot.toFloat() / icon
        view.pivotX = icon / 2f
        view.pivotY = icon / 2f
        view.scaleX = scale
        view.scaleY = scale
        // The label is hidden, so TalkBack needs the name explicitly
        view.contentDescription = app.contentDescription ?: app.title
        val offset = (geo.slot - icon) / 2
        square.addView(view, LayoutParams(icon, icon).apply {
            leftMargin = geo.left(index) + offset
            topMargin = geo.top(index) + offset
        })
    }

    private fun createMiniGrid(
        apps: List<AppInfo>,
        slot: Int,
        label: String,
        count: Int,
        onClick: () -> Unit,
    ): View {
        val grid = FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            contentDescription = context.getString(R.string.glow_library_more_apps, label, count)
            GlowGlass.asButton(this)
        }
        // Same proportions as the tile itself, one level smaller
        val gap = (slot * MINI_GAP_RATIO).roundToInt().coerceAtLeast(dp(3))
        val mini = (slot - gap) / 2
        val start = (slot - 2 * mini - gap) / 2
        // One row (5 or 6 apps in the category) sits in the middle of the slot, level with
        // the big icon next to it, instead of hanging at the top
        val rows = (apps.size + 1) / 2
        val top = (slot - rows * mini - (rows - 1) * gap) / 2
        apps.forEachIndexed { i, app ->
            grid.addView(ImageView(context).apply {
                setImageDrawable(app.newIcon(context))
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(mini, mini).apply {
                leftMargin = start + (i % 2) * (mini + gap)
                topMargin = top + (i / 2) * (mini + gap)
            })
        }
        return grid
    }

    // ---------------------------------------------------------------------------------------
    // Opened category (like a folder)
    // ---------------------------------------------------------------------------------------

    private fun openCategory(category: Category, from: View?) {
        closeCategory(animate = false)
        val canBlur = GlowGlass.canAffordBlur(context)
        val animate = GlowGlass.animationsEnabled()
        val overlay = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(if (canBlur) 70 else 170, 0, 0, 0))
            isClickable = true
            setOnClickListener { closeCategory() }
            contentDescription = context.getString(R.string.glow_close)
            alpha = if (animate) 0f else 1f
        }
        // Frosted backdrop: the library behind the open category blurs in with the dim
        overlay.animate().setUpdateListener { setTilesBlur(if (canBlur) overlay.alpha else 0f) }
        if (!animate) setTilesBlur(if (canBlur) 1f else 0f)
        val cardWidth = width - 2 * dp(20)
        val cardPad = dp(16)
        val columns = launcher.deviceProfile.numShownAllAppsColumns.coerceIn(3, 6)
        val cellWidth = (cardWidth - 2 * cardPad) / columns
        val cellHeight = launcher.deviceProfile.allAppsProfile.cellHeightPx

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GlowGlass.drawable(context, dp(30).toFloat(), GlowGlass.Level.CARD)
            setPadding(cardPad, cardPad, cardPad, cardPad)
            isClickable = true
        }
        card.addView(TextView(context).apply {
            text = category.label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), 0, 0, dp(12))
            GlowGlass.asHeading(this)
        })
        val apps = category.apps
        val grid = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, columns)
            itemAnimator = null
            isVerticalScrollBarEnabled = false
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = apps.size
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                    object : RecyclerView.ViewHolder(FrameLayout(context).apply {
                        layoutParams = RecyclerView.LayoutParams(cellWidth, cellHeight)
                    }) {}

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val root = holder.itemView as FrameLayout
                    root.removeAllViews()
                    root.addView(bubble(apps[position], root), LayoutParams(cellWidth, cellHeight))
                }
            }
        }
        val rows = (apps.size + columns - 1) / columns
        card.addView(grid, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            minOf(rows * cellHeight, (height * 0.62f).toInt()),
        ))
        overlay.addView(card, LayoutParams(cardWidth, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        folderOverlay = overlay
        folderCard = card
        folderOrigin = from?.let { java.lang.ref.WeakReference(it) }
        if (animate) {
            overlay.animate().alpha(1f).setDuration(GlowGlass.Motion.MEDIUM)
                .setInterpolator(GlowGlass.Motion.STANDARD).start()
            // Like a folder: the card grows out of the tile that was tapped
            card.alpha = 0f
            card.doOnLayout {
                if (!placeOnOrigin(card, from)) {
                    card.scaleX = 0.9f
                    card.scaleY = 0.9f
                }
                card.animate().alpha(1f).setDuration(GlowGlass.Motion.SHORT)
                    .setInterpolator(GlowGlass.Motion.STANDARD).start()
                card.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setDuration(GlowGlass.Motion.LONG)
                    .setInterpolator(GlowGlass.Motion.ENTER)
                    .start()
            }
        }
        card.post { card.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED) }
    }

    // ---------------------------------------------------------------------------------------
    // Alphabetical list (the "A–Z" pill), with an index strip on the right
    // ---------------------------------------------------------------------------------------

    private sealed class Row {
        data class Header(val letter: String) : Row()
        data class App(val app: AppInfo, val divider: Boolean) : Row()
        data object PrivateSpace : Row()
    }

    private var listOverlay: View? = null
    private var listView: RecyclerView? = null

    private fun openList() {
        if (listOverlay != null) return
        val apps = appsProvider?.invoke() ?: return
        val collator = java.text.Collator.getInstance(Locale.getDefault())
        val sorted = apps.sortedWith { a, b -> collator.compare(a.title?.toString() ?: "", b.title?.toString() ?: "") }

        val rows = ArrayList<Row>(sorted.size + 30)
        val sections = linkedMapOf<String, Int>()
        var lastLetter: String? = null
        sorted.forEach { app ->
            val letter = GlowCategoryRules.sectionOf(app.title?.toString())
            if (letter != lastLetter) {
                lastLetter = letter
                sections[letter] = rows.size
                rows += Row.Header(letter)
                rows += Row.App(app, divider = false)
            } else {
                rows += Row.App(app, divider = true)
            }
        }
        if (hasPrivateSpace?.invoke() == true && onShowAllApps != null) rows += Row.PrivateSpace

        val animate = GlowGlass.animationsEnabled()
        val overlay = FrameLayout(context).apply {
            alpha = if (animate) 0f else 1f
            isClickable = true
        }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val back = GlowGlass.pill(context, context.getString(R.string.glow_library_categories), PILL_HEIGHT_DP).apply {
            setOnClickListener { closeList(true) }
        }
        column.addView(back, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.END
            marginEnd = dp(SIDE_PADDING_DP)
        })

        val layoutManager = LinearLayoutManager(context)
        val list = RecyclerView(context).apply {
            this.layoutManager = layoutManager
            itemAnimator = null
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            clipToPadding = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(32))
            setPadding(0, 0, 0, bottomInset + dp(24))
            adapter = ListAdapter(rows)
        }
        column.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        overlay.addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Index strip: tap or slide over a letter to jump to its section (TalkBack users scroll the list)
        val letters = sections.keys.toList()
        val bubble = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GlowGlass.drawable(context, dp(36).toFloat(), GlowGlass.Level.CARD)
            visibility = GONE
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val index = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            letters.forEach { l ->
                addView(TextView(context).apply {
                    text = l
                    setTextColor(Color.argb(230, 255, 255, 255))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setShadowLayer(dp(2).toFloat(), 0f, 0f, 0x88000000.toInt())
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(1)
                    bottomMargin = dp(1)
                })
            }
            var lastJump = -1
            setOnTouchListener { v, ev ->
                if (letters.isEmpty() || v.height == 0) return@setOnTouchListener false
                val group = v as LinearLayout
                val first = group.getChildAt(0)
                val last = group.getChildAt(group.childCount - 1)
                val span = (last.bottom - first.top).coerceAtLeast(1)
                val i = (((ev.y - first.top) / span) * letters.size).toInt().coerceIn(0, letters.size - 1)
                if (i != lastJump) {
                    lastJump = i
                    sections[letters[i]]?.let { layoutManager.scrollToPositionWithOffset(it, 0) }
                    bubble.text = letters[i]
                    if (bubble.visibility != VISIBLE) {
                        bubble.animate().cancel()
                        bubble.alpha = 1f
                        bubble.visibility = VISIBLE
                    }
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                    lastJump = -1
                    // The letter lingers for a moment, then fades instead of blinking out
                    bubble.animate().cancel()
                    if (GlowGlass.animationsEnabled()) {
                        bubble.animate().alpha(0f).setStartDelay(250).setDuration(GlowGlass.Motion.SHORT)
                            .withEndAction { bubble.visibility = GONE }.start()
                    } else {
                        bubble.visibility = GONE
                    }
                }
                v.parent?.requestDisallowInterceptTouchEvent(true)
                true
            }
        }
        overlay.addView(index, LayoutParams(dp(INDEX_WIDTH_DP), LayoutParams.MATCH_PARENT, Gravity.END).apply {
            topMargin = dp(GlowGlass.MIN_TOUCH_DP + 4)
            bottomMargin = bottomInset + dp(24)
        })
        overlay.addView(bubble, LayoutParams(dp(72), dp(72), Gravity.CENTER))

        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        listOverlay = overlay
        listView = list
        if (animate) {
            tiles.animate().alpha(0f).setDuration(GlowGlass.Motion.SHORT)
                .setInterpolator(GlowGlass.Motion.EXIT).start()
            overlay.translationY = dp(16).toFloat()
            overlay.animate().alpha(1f).translationY(0f).setDuration(GlowGlass.Motion.MEDIUM)
                .setInterpolator(GlowGlass.Motion.ENTER).start()
        } else {
            tiles.alpha = 0f
        }
    }

    private inner class ListAdapter(private val rows: List<Row>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = when (rows[position]) {
            is Row.Header -> 0
            is Row.App -> 1
            Row.PrivateSpace -> 2
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = if (viewType == 2) {
                FrameLayout(context).apply {
                    setPadding(0, dp(20), 0, 0)
                    addView(
                        GlowGlass.pill(context, context.getString(R.string.glow_library_private_space), PILL_HEIGHT_DP + 4).apply {
                            setOnClickListener {
                                closeCategory(false)
                                onShowAllApps?.invoke()
                            }
                        },
                        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL),
                    )
                }
            } else if (viewType == 0) {
                TextView(context).apply {
                    setTextColor(Color.argb(220, 255, 255, 255))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(SIDE_PADDING_DP + 4), dp(14), 0, dp(4))
                    setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0x66000000)
                    GlowGlass.asHeading(this)
                }
            } else {
                createListRow()
            }
            view.layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT,
            )
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Header -> (holder.itemView as TextView).text = row.letter
                is Row.App -> bindListRow(holder.itemView as LinearLayout, row.app, row.divider)
                Row.PrivateSpace -> Unit
            }
        }
    }

    private fun createListRow(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        isClickable = true
        isFocusable = true
        val ta = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        foreground = ta.getDrawable(0)
        ta.recycle()
        setOnClickListener(launcher.itemOnClickListener)
        // divider
        addView(View(context).apply { setBackgroundColor(Color.argb(40, 255, 255, 255)) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxOf(1, (density * 0.5f).toInt())).apply {
                marginStart = dp(SIDE_PADDING_DP + 4 + 42 + 14)
                marginEnd = dp(INDEX_WIDTH_DP)
            })
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(GlowGlass.MIN_TOUCH_DP + 8)
            setPadding(dp(SIDE_PADDING_DP + 4), dp(6), dp(INDEX_WIDTH_DP), dp(6))
        }
        row.addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        row.addView(TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0x66000000)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(14)
        })
        addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun bindListRow(view: LinearLayout, app: AppInfo, divider: Boolean) {
        // The launcher's click and long-press handlers read the ItemInfo from the tag
        view.tag = app
        view.contentDescription = app.contentDescription ?: app.title
        view.getChildAt(0).visibility = if (divider) VISIBLE else INVISIBLE
        val row = view.getChildAt(1) as LinearLayout
        (row.getChildAt(0) as ImageView).setImageDrawable(app.newIcon(context))
        (row.getChildAt(1) as TextView).text = app.title
    }

    private fun closeList(animate: Boolean): Boolean {
        val overlay = listOverlay ?: return false
        listOverlay = null
        listView = null
        tiles.animate().cancel()
        if (animate && GlowGlass.animationsEnabled()) {
            tiles.animate().alpha(1f).setDuration(GlowGlass.Motion.MEDIUM)
                .setInterpolator(GlowGlass.Motion.STANDARD).start()
            overlay.animate().alpha(0f).translationY(dp(16).toFloat()).setDuration(GlowGlass.Motion.SHORT)
                .setInterpolator(GlowGlass.Motion.EXIT)
                .withEndAction { removeView(overlay) }.start()
        } else {
            tiles.alpha = 1f
            removeView(overlay)
        }
        return true
    }

    /** Closes an open category or the alphabetical list. Returns true if one was open. */
    fun closeCategory(animate: Boolean = true): Boolean {
        val closedList = closeList(animate)
        val overlay = folderOverlay ?: return closedList
        val card = folderCard
        val origin = folderOrigin?.get()
        folderOverlay = null
        folderCard = null
        folderOrigin = null
        if (animate && GlowGlass.animationsEnabled()) {
            // The card shrinks back into its tile while the blur and dim clear
            if (card != null) {
                card.animate().cancel()
                card.pivotX = 0f
                card.pivotY = 0f
                if (placeOnOrigin(card, origin, animate = true)) {
                    card.animate().alpha(0f).setStartDelay(GlowGlass.Motion.SHORT / 2)
                        .setDuration(GlowGlass.Motion.SHORT)
                        .setInterpolator(GlowGlass.Motion.STANDARD).start()
                }
            }
            overlay.animate().cancel()
            overlay.animate().alpha(0f).setDuration(GlowGlass.Motion.MEDIUM)
                .setInterpolator(GlowGlass.Motion.EXIT)
                .setUpdateListener { setTilesBlur(if (GlowGlass.canAffordBlur(context)) overlay.alpha else 0f) }
                .withEndAction {
                    setTilesBlur(0f)
                    removeView(overlay)
                }.start()
        } else {
            overlay.animate().cancel()
            setTilesBlur(0f)
            removeView(overlay)
        }
        return true
    }

    private var folderCard: View? = null
    private var folderOrigin: java.lang.ref.WeakReference<View>? = null
    private var blurRadius = 0f

    /**
     * Moves [card] (pivot at its top-left corner) onto [origin], the tile it opens from,
     * at once or as an animation. Returns false when there is no usable tile.
     */
    private fun placeOnOrigin(card: View, origin: View?, animate: Boolean = false): Boolean {
        if (origin == null || !origin.isAttachedToWindow || card.width == 0) return false
        val o = IntArray(2)
        val c = IntArray(2)
        origin.getLocationInWindow(o)
        // The card's untransformed position: its layout position inside the overlay
        (card.parent as View).getLocationInWindow(c)
        val scale = origin.width.toFloat() / card.width
        val tx = o[0] - (c[0] + card.left).toFloat()
        val ty = o[1] - (c[1] + card.top).toFloat()
        card.pivotX = 0f
        card.pivotY = 0f
        if (animate) {
            card.animate().translationX(tx).translationY(ty).scaleX(scale).scaleY(scale)
                .setDuration(GlowGlass.Motion.MEDIUM)
                .setInterpolator(GlowGlass.Motion.EXIT)
                .start()
        } else {
            card.translationX = tx
            card.translationY = ty
            card.scaleX = scale
            card.scaleY = scale
        }
        return true
    }

    /** Blurs the tiles behind an open category; follows the fade smoothly, frame by frame. */
    private fun setTilesBlur(fraction: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val radius = 28 * density * fraction.coerceIn(0f, 1f)
        if (kotlin.math.abs(radius - blurRadius) < 0.5f && (radius == 0f) == (blurRadius == 0f)) return
        blurRadius = radius
        tiles.setRenderEffect(
            if (radius < 0.5f) null else RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
        )
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    companion object {
        private const val SIDE_PADDING_DP = 20
        private const val TOP_FADE_DP = 28
        private const val BOTTOM_FADE_DP = 40
        private const val TILE_GAP_DP = 18

        /** Spacing inside a tile, as a share of its width (about 14dp on a phone). */
        private const val TILE_SPACE_RATIO = 0.085f
        private const val TILE_RADIUS_RATIO = 0.17f
        private const val MINI_GAP_RATIO = 0.12f
        private const val PILL_HEIGHT_DP = 32
        private const val INDEX_WIDTH_DP = 30
        private const val RECENT_COUNT = 8
        private const val RECENT_WINDOW_MS = 14L * 24 * 60 * 60 * 1000
        private const val REBUILD_DELAY_MS = 300L

        /** package -> group, filled once per package; cleared for packages that change. */
        private val groupCache = ConcurrentHashMap<String, String>()

        /** package -> first install time. */
        private val installCache = ConcurrentHashMap<String, Long>()

        /** Forgets what is cached about a package (call when it is installed again or updated). */
        @JvmStatic
        fun invalidatePackage(pkg: String) {
            groupCache.remove(pkg)
            installCache.remove(pkg)
        }

        /**
         * Apps of the personal and work profiles that the user has not hidden.
         * [isPrivate] filters out Private Space apps, which have their own locked section.
         */
        @JvmStatic
        fun visibleApps(context: Context, apps: Array<AppInfo>, isPrivate: Predicate<ItemInfo>?): List<AppInfo> {
            val hidden = runCatching {
                PreferenceManager2.getInstance(context).hiddenApps.firstCached()
            }.getOrDefault(emptySet())
            return apps.filter {
                isPrivate?.test(it) != true && it.toComponentKey().toString() !in hidden
            }
        }

        // Flowerpot categories merged into iOS-like groups
        private val POT_GROUPS = mapOf(
            "SOCIAL" to GlowCategoryRules.SOCIAL, "COMMUNICATION" to GlowCategoryRules.SOCIAL,
            "DATING" to GlowCategoryRules.SOCIAL,
            "ENTERTAINMENT" to GlowCategoryRules.ENTERTAINMENT, "VIDEO" to GlowCategoryRules.ENTERTAINMENT,
            "MUSIC" to GlowCategoryRules.ENTERTAINMENT, "COMICS" to GlowCategoryRules.ENTERTAINMENT,
            "EVENTS" to GlowCategoryRules.ENTERTAINMENT,
            "PHOTOGRAPHY" to GlowCategoryRules.CREATIVITY, "ART_AND_DESIGN" to GlowCategoryRules.CREATIVITY,
            "PERSONALIZATION" to GlowCategoryRules.CREATIVITY, "BEAUTY" to GlowCategoryRules.CREATIVITY,
            "PRODUCTIVITY" to GlowCategoryRules.PRODUCTIVITY, "BUSINESS" to GlowCategoryRules.PRODUCTIVITY,
            "FINANCE" to GlowCategoryRules.FINANCE,
            "TOOLS" to GlowCategoryRules.UTILITIES, "LIBRARIES_AND_DEMO" to GlowCategoryRules.UTILITIES,
            "WEATHER" to GlowCategoryRules.UTILITIES, "AUTO_AND_VEHICLES" to GlowCategoryRules.UTILITIES,
            "HOUSE_AND_HOME" to GlowCategoryRules.UTILITIES,
            "NEWS" to GlowCategoryRules.READING, "BOOKS_AND_REFERENCE" to GlowCategoryRules.READING,
            "SPORTS" to GlowCategoryRules.READING,
            "SHOPPING" to GlowCategoryRules.SHOPPING, "FOOD_AND_DRINK" to GlowCategoryRules.SHOPPING,
            "LIFESTYLE" to GlowCategoryRules.SHOPPING,
            "MAPS_AND_NAVIGATION" to GlowCategoryRules.TRAVEL,
            "HEALTH_AND_FITNESS" to GlowCategoryRules.HEALTH, "MEDICAL" to GlowCategoryRules.HEALTH,
            "PARENTING" to GlowCategoryRules.HEALTH,
            "GAME" to GlowCategoryRules.GAMES,
            "EDUCATION" to GlowCategoryRules.EDUCATION,
        )

        private val GROUP_LABELS = mapOf(
            GlowCategoryRules.SOCIAL to R.string.glow_library_social,
            GlowCategoryRules.ENTERTAINMENT to R.string.glow_library_entertainment,
            GlowCategoryRules.CREATIVITY to R.string.glow_library_creativity,
            GlowCategoryRules.PRODUCTIVITY to R.string.glow_library_productivity,
            GlowCategoryRules.FINANCE to R.string.glow_library_finance,
            GlowCategoryRules.UTILITIES to R.string.glow_library_utilities,
            GlowCategoryRules.READING to R.string.glow_library_reading,
            GlowCategoryRules.SHOPPING to R.string.glow_library_shopping,
            GlowCategoryRules.TRAVEL to R.string.glow_library_travel,
            GlowCategoryRules.HEALTH to R.string.glow_library_health,
            GlowCategoryRules.GAMES to R.string.glow_library_games,
            GlowCategoryRules.EDUCATION to R.string.glow_library_education,
            GlowCategoryRules.OTHER to R.string.glow_library_other,
        )

        private fun categorize(context: Context, apps: List<AppInfo>, pots: List<Flowerpot>): List<Category> {
            val pm = context.packageManager
            val groups = linkedMapOf<String, MutableList<AppInfo>>()
            val assigned = HashSet<String>()

            // 1. Flowerpot rules (curated package/intent lists), only for packages not cached yet
            val uncached = apps.filter { it.targetPackage != null && !groupCache.containsKey(it.targetPackage) }
            if (uncached.isNotEmpty()) {
                for (pot in pots) {
                    val group = POT_GROUPS[pot.name.uppercase(Locale.ROOT)] ?: continue
                    val matched = runCatching { pot.categorizeApps(uncached).values.flatten() }.getOrDefault(emptyList())
                    for (app in matched) {
                        val pkg = app.targetPackage ?: continue
                        groupCache.putIfAbsent(pkg, group)
                    }
                }
            }

            // 2. Android's own app category, 3. package/label keywords, 4. system apps -> utilities
            for (app in apps) {
                val key = app.toComponentKey().toString()
                if (!assigned.add(key)) continue
                val pkg = app.targetPackage ?: continue
                val group = groupCache.getOrPut(pkg) {
                    val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                    info?.let { androidCategory(it) }
                        ?: GlowCategoryRules.keywordCategory(pkg, app.title?.toString())
                        ?: if (info != null && info.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
                            GlowCategoryRules.UTILITIES
                        } else {
                            GlowCategoryRules.OTHER
                        }
                }
                groups.getOrPut(group) { mutableListOf() }.add(app)
            }

            val byTitle = compareBy<AppInfo> { it.title?.toString()?.lowercase(Locale.getDefault()) ?: "" }
            val result = mutableListOf<Category>()

            recentlyAdded(pm, apps).takeIf { it.isNotEmpty() }?.let {
                result += Category("recent", context.getString(R.string.glow_library_recent), it)
            }
            groups.filterKeys { it != GlowCategoryRules.OTHER }
                .entries
                .sortedByDescending { it.value.size }
                .forEach { (group, list) ->
                    val label = GROUP_LABELS[group] ?: R.string.glow_library_other
                    result += Category(group, context.getString(label), list.sortedWith(byTitle))
                }
            groups[GlowCategoryRules.OTHER]?.let {
                result += Category(GlowCategoryRules.OTHER, context.getString(R.string.glow_library_other), it.sortedWith(byTitle))
            }
            return result
        }

        private fun androidCategory(info: ApplicationInfo): String? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
            return when (info.category) {
                ApplicationInfo.CATEGORY_GAME -> GlowCategoryRules.GAMES
                ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> GlowCategoryRules.ENTERTAINMENT
                ApplicationInfo.CATEGORY_IMAGE -> GlowCategoryRules.CREATIVITY
                ApplicationInfo.CATEGORY_SOCIAL -> GlowCategoryRules.SOCIAL
                ApplicationInfo.CATEGORY_NEWS -> GlowCategoryRules.READING
                ApplicationInfo.CATEGORY_MAPS -> GlowCategoryRules.TRAVEL
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> GlowCategoryRules.PRODUCTIVITY
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> GlowCategoryRules.UTILITIES
                else -> null
            }
        }

        private fun recentlyAdded(pm: PackageManager, apps: List<AppInfo>): List<AppInfo> {
            val now = System.currentTimeMillis()
            return apps.mapNotNull { app ->
                val pkg = app.targetPackage ?: return@mapNotNull null
                val installed = installCache.getOrPut(pkg) {
                    runCatching { pm.getPackageInfo(pkg, 0).firstInstallTime }.getOrDefault(0L)
                }
                if (installed <= 0L || now - installed > RECENT_WINDOW_MS) null else app to installed
            }
                .sortedByDescending { it.second }
                .distinctBy { it.first.toComponentKey() }
                .take(RECENT_COUNT)
                .map { it.first }
        }
    }
}
