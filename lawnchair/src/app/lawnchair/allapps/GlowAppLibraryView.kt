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
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Process
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.lawnchair.flowerpot.Flowerpot
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import java.util.Locale

/**
 * Glow "App Library" (iOS style): the app drawer shows automatic categories as glass tiles.
 * Each tile shows three apps that open with one tap plus a mini grid that opens the whole
 * category, like a folder. Search stays on top; "A–Z" switches to the classic list.
 */
class GlowAppLibraryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private data class Category(val label: String, val apps: List<AppInfo>)

    private val launcher: Launcher = Launcher.getLauncher(context)
    private val density = resources.displayMetrics.density

    private val scroll = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        clipToPadding = false
        // Soft edge where tiles slide under the search bar instead of a hard cut
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength((32 * resources.displayMetrics.density).toInt())
    }
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    private var folderOverlay: View? = null
    private var categories: List<Category> = emptyList()
    private var builtForWidth = 0
    private var generation = 0

    /** Supplies the apps to show (already filtered to the personal profile). */
    var appsProvider: (() -> List<AppInfo>)? = null

    /** Called when the user taps "A–Z" to see the classic alphabetical list. */
    var onShowAllApps: (() -> Unit)? = null

    init {
        scroll.addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setBottomInset(0)
    }

    fun setBottomInset(bottom: Int) {
        val side = dp(SIDE_PADDING_DP)
        scroll.setPadding(side, dp(4), side, bottom + dp(24))
    }

    fun canScrollUp(): Boolean = folderOverlay != null || scroll.canScrollVertically(-1)

    fun scrollToTop() {
        scroll.scrollTo(0, 0)
    }

    /** Recomputes categories in the background and rebuilds the tiles. */
    fun rebuild() {
        val apps = appsProvider?.invoke() ?: return
        val gen = ++generation
        val appContext = context.applicationContext
        // Flowerpot's manager must be obtained on the main thread
        val pots = runCatching { Flowerpot.Manager.getInstance(appContext).getAllPots().toList() }
            .getOrDefault(emptyList())
        MODEL_EXECUTOR.execute {
            val result = runCatching { categorize(appContext, apps, pots) }.getOrDefault(emptyList())
            MAIN_EXECUTOR.execute {
                if (gen != generation) return@execute
                categories = result
                builtForWidth = 0
                buildTiles()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != builtForWidth) post { buildTiles() }
    }

    private fun buildTiles() {
        val width = width
        if (width <= 0) return
        builtForWidth = width
        content.removeAllViews()

        // "A–Z" pill to switch to the classic alphabetical list
        content.addView(TextView(context).apply {
            text = context.getString(R.string.glow_library_all_apps)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            background = glass(dp(16).toFloat())
            setOnClickListener { onShowAllApps?.invoke() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)).apply {
            gravity = Gravity.END
            bottomMargin = dp(12)
        })

        val inner = width - scroll.paddingLeft - scroll.paddingRight
        val gap = dp(TILE_GAP_DP)
        val tileSize = (inner - gap) / 2
        if (tileSize <= 0) return

        categories.chunked(2).forEach { pair ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { index, category ->
                row.addView(createTile(category, tileSize), LinearLayout.LayoutParams(
                    tileSize, LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { if (index == 1) marginStart = gap })
            }
            content.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(14) })
        }
    }

    private fun createTile(category: Category, tileSize: Int): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val pad = dp(12)
        val innerGap = dp(10)
        val cell = (tileSize - 2 * pad - innerGap) / 2
        val square = FrameLayout(context).apply {
            background = glass(dp(26).toFloat())
            clipChildren = false
        }
        val apps = category.apps
        val bigCount = if (apps.size <= 4) apps.size else 3
        for (i in 0 until bigCount) {
            square.addView(createBigIcon(apps[i]), cellParams(i, cell, pad, innerGap))
        }
        if (apps.size > 4) {
            square.addView(createMiniGrid(apps.subList(3, minOf(7, apps.size)), cell) {
                openCategory(category)
            }, cellParams(3, cell, pad, innerGap))
        }
        column.addView(square, LinearLayout.LayoutParams(tileSize, tileSize))
        column.addView(TextView(context).apply {
            text = category.label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            maxLines = 1
            setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0x55000000)
            setOnClickListener { openCategory(category) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        })
        return column
    }

    private fun cellParams(index: Int, cell: Int, pad: Int, gap: Int) =
        LayoutParams(cell, cell).apply {
            leftMargin = pad + (index % 2) * (cell + gap)
            topMargin = pad + (index / 2) * (cell + gap)
        }

    private fun createBigIcon(app: AppInfo): View {
        val icon = LayoutInflater.from(context)
            .inflate(R.layout.all_apps_icon, this, false) as BubbleTextView
        icon.applyFromApplicationInfo(app)
        icon.setTextVisibility(false)
        icon.setCenterVertically(true)
        icon.setOnClickListener(launcher.itemOnClickListener)
        icon.setOnLongClickListener(launcher.allAppsItemLongClickListener)
        icon.setLongPressTimeoutFactor(1f)
        return icon
    }

    private fun createMiniGrid(apps: List<AppInfo>, cell: Int, onClick: () -> Unit): View {
        val grid = FrameLayout(context).apply {
            isClickable = true
            setOnClickListener { onClick() }
            contentDescription = context.getString(R.string.glow_library_open_category)
        }
        val gap = dp(4)
        val mini = (cell - gap) / 2
        apps.forEachIndexed { i, app ->
            grid.addView(ImageView(context).apply {
                setImageDrawable(app.newIcon(context))
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(mini, mini).apply {
                leftMargin = (i % 2) * (mini + gap)
                topMargin = (i / 2) * (mini + gap)
            })
        }
        return grid
    }

    private fun openCategory(category: Category) {
        closeCategory(animate = false)
        val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        if (canBlur) {
            // Frosted backdrop: blur the library behind the open category
            val radius = 28 * density
            scroll.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
        }
        val overlay = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(if (canBlur) 70 else 170, 0, 0, 0))
            isClickable = true
            setOnClickListener { closeCategory() }
            alpha = 0f
        }
        val cardWidth = width - 2 * dp(20)
        val cardPad = dp(16)
        val columns = 4
        val cellWidth = (cardWidth - 2 * cardPad) / columns
        val cellHeight = launcher.deviceProfile.allAppsProfile.cellHeightPx

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(dp(30).toFloat(), fillAlpha = if (canBlur) 64 else 90, strokeAlpha = 50)
            setPadding(cardPad, cardPad, cardPad, cardPad)
            isClickable = true
        }
        card.addView(TextView(context).apply {
            text = category.label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), 0, 0, dp(12))
        })
        val grid = GridLayout(context).apply { columnCount = columns }
        category.apps.forEach { app ->
            val icon = LayoutInflater.from(context)
                .inflate(R.layout.all_apps_icon, grid, false) as BubbleTextView
            icon.applyFromApplicationInfo(app)
            icon.setOnClickListener(launcher.itemOnClickListener)
            icon.setOnLongClickListener(launcher.allAppsItemLongClickListener)
            icon.setLongPressTimeoutFactor(1f)
            grid.addView(icon, GridLayout.LayoutParams().apply {
                width = cellWidth
                height = cellHeight
            })
        }
        val rows = (category.apps.size + columns - 1) / columns
        val maxGridHeight = (height * 0.62f).toInt()
        val gridScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(grid)
        }
        card.addView(gridScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            minOf(rows * cellHeight, maxGridHeight),
        ))
        overlay.addView(card, LayoutParams(cardWidth, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        folderOverlay = overlay
        card.scaleX = 0.9f
        card.scaleY = 0.9f
        overlay.animate().alpha(1f).setDuration(160).start()
        card.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    /** Closes an open category. Returns true if one was open. */
    fun closeCategory(animate: Boolean = true): Boolean {
        val overlay = folderOverlay ?: return false
        folderOverlay = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) scroll.setRenderEffect(null)
        if (animate) {
            overlay.animate().alpha(0f).setDuration(140).withEndAction { removeView(overlay) }.start()
        } else {
            removeView(overlay)
        }
        return true
    }

    // Matte frosted material: soft white tint with a faint rim (the drawer behind is already blurred)
    private fun glass(radius: Float, fillAlpha: Int = 40, strokeAlpha: Int = 34) = GradientDrawable().apply {
        cornerRadius = radius
        setColor(Color.argb(fillAlpha, 255, 255, 255))
        setStroke(maxOf(1, density.toInt()), Color.argb(strokeAlpha, 255, 255, 255))
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    companion object {
        private const val SIDE_PADDING_DP = 18
        private const val TILE_GAP_DP = 16
        private const val RECENT_COUNT = 8
        private const val RECENT_WINDOW_MS = 14L * 24 * 60 * 60 * 1000

        /** Personal-profile apps that are not hidden by the user. */
        @JvmStatic
        fun visibleApps(context: Context, apps: Array<AppInfo>): List<AppInfo> {
            val hidden = runCatching {
                PreferenceManager2.getInstance(context).hiddenApps.firstCached()
            }.getOrDefault(emptySet())
            val me = Process.myUserHandle()
            return apps.filter { it.user == me && it.toComponentKey().toString() !in hidden }
        }

        // Flowerpot categories merged into iOS-like groups
        private val POT_GROUPS = mapOf(
            "SOCIAL" to "social", "COMMUNICATION" to "social", "DATING" to "social",
            "ENTERTAINMENT" to "entertainment", "VIDEO" to "entertainment",
            "MUSIC" to "entertainment", "COMICS" to "entertainment", "EVENTS" to "entertainment",
            "PHOTOGRAPHY" to "creativity", "ART_AND_DESIGN" to "creativity",
            "PERSONALIZATION" to "creativity", "BEAUTY" to "creativity",
            "PRODUCTIVITY" to "productivity", "BUSINESS" to "productivity", "FINANCE" to "productivity",
            "TOOLS" to "utilities", "LIBRARIES_AND_DEMO" to "utilities", "WEATHER" to "utilities",
            "AUTO_AND_VEHICLES" to "utilities", "HOUSE_AND_HOME" to "utilities",
            "NEWS" to "reading", "BOOKS_AND_REFERENCE" to "reading", "SPORTS" to "reading",
            "SHOPPING" to "shopping", "FOOD_AND_DRINK" to "shopping", "LIFESTYLE" to "shopping",
            "MAPS_AND_NAVIGATION" to "travel",
            "HEALTH_AND_FITNESS" to "health", "MEDICAL" to "health", "PARENTING" to "health",
            "GAME" to "games",
            "EDUCATION" to "education",
        )

        private val KEYWORDS = listOf(
            "social" to listOf("telegram", "whatsapp", "viber", "messenger", "chat", "gram", "vk.", "instagram", "tiktok", "snapchat", "discord", "signal", "facebook", "twitter"),
            "productivity" to listOf("bank", "pay", "wallet", "finance", "crypto", "money", "invest", "office", "docs", "mail", "calendar", "notes"),
            "utilities" to listOf("vpn", "clean", "file", "calc", "clock", "settings", "launcher", "adguard", "authenticator", "anydesk", "termux", "terminal", "flashlight"),
            "shopping" to listOf("shop", "market", "store", "food", "delivery", "eats", "ozon", "wildberries", "aliexpress"),
            "travel" to listOf("taxi", "bolt", "uber", "maps", "navi", "waze", "yandex.yandexmaps", "booking", "airline"),
            "entertainment" to listOf("music", "video", "youtube", "netflix", "spotify", "player", "tv", "kino", "stream"),
            "creativity" to listOf("photo", "camera", "gallery", "editor", "design", "draw"),
            "games" to listOf("game", "games"),
        )

        private val GROUP_LABELS = mapOf(
            "recent" to R.string.glow_library_recent,
            "social" to R.string.glow_library_social,
            "entertainment" to R.string.glow_library_entertainment,
            "creativity" to R.string.glow_library_creativity,
            "productivity" to R.string.glow_library_productivity,
            "utilities" to R.string.glow_library_utilities,
            "reading" to R.string.glow_library_reading,
            "shopping" to R.string.glow_library_shopping,
            "travel" to R.string.glow_library_travel,
            "health" to R.string.glow_library_health,
            "games" to R.string.glow_library_games,
            "education" to R.string.glow_library_education,
            "other" to R.string.glow_library_other,
        )

        private fun categorize(context: Context, apps: List<AppInfo>, pots: List<Flowerpot>): List<Category> {
            val pm = context.packageManager
            val groups = linkedMapOf<String, MutableList<AppInfo>>()
            val assigned = HashSet<String>()

            // 1. Flowerpot rules (curated package/intent lists)
            for (pot in pots) {
                val group = POT_GROUPS[pot.name.uppercase(Locale.ROOT)] ?: continue
                val matched = runCatching { pot.categorizeApps(apps).values.flatten() }.getOrDefault(emptyList())
                for (app in matched) {
                    val key = app.toComponentKey().toString()
                    if (assigned.add(key)) groups.getOrPut(group) { mutableListOf() }.add(app)
                }
            }

            // 2. Android's own app category, 3. package/label keywords, 4. system apps -> utilities
            for (app in apps) {
                val key = app.toComponentKey().toString()
                if (key in assigned) continue
                val pkg = app.targetPackage ?: continue
                val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                val group = info?.let { androidCategory(it) }
                    ?: keywordCategory(pkg, app.title?.toString())
                    ?: if (info != null && info.flags and ApplicationInfo.FLAG_SYSTEM != 0) "utilities" else "other"
                assigned.add(key)
                groups.getOrPut(group) { mutableListOf() }.add(app)
            }

            val byTitle = compareBy<AppInfo> { it.title?.toString()?.lowercase(Locale.getDefault()) ?: "" }
            val result = mutableListOf<Category>()

            recentlyAdded(pm, apps).takeIf { it.isNotEmpty() }?.let {
                result += Category(context.getString(R.string.glow_library_recent), it)
            }
            groups.filterKeys { it != "other" }
                .entries
                .sortedByDescending { it.value.size }
                .forEach { (group, list) ->
                    result += Category(context.getString(GROUP_LABELS.getValue(group)), list.sortedWith(byTitle))
                }
            groups["other"]?.let { result += Category(context.getString(R.string.glow_library_other), it.sortedWith(byTitle)) }
            return result
        }

        private fun androidCategory(info: ApplicationInfo): String? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
            return when (info.category) {
                ApplicationInfo.CATEGORY_GAME -> "games"
                ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> "entertainment"
                ApplicationInfo.CATEGORY_IMAGE -> "creativity"
                ApplicationInfo.CATEGORY_SOCIAL -> "social"
                ApplicationInfo.CATEGORY_NEWS -> "reading"
                ApplicationInfo.CATEGORY_MAPS -> "travel"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productivity"
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> "utilities"
                else -> null
            }
        }

        private fun keywordCategory(pkg: String, title: String?): String? {
            val haystack = (pkg + " " + (title ?: "")).lowercase(Locale.ROOT)
            return KEYWORDS.firstOrNull { (_, words) -> words.any { it in haystack } }?.first
        }

        private fun recentlyAdded(pm: PackageManager, apps: List<AppInfo>): List<AppInfo> {
            val now = System.currentTimeMillis()
            return apps.mapNotNull { app ->
                val pkg = app.targetPackage ?: return@mapNotNull null
                val installed = runCatching { pm.getPackageInfo(pkg, 0).firstInstallTime }.getOrNull()
                    ?: return@mapNotNull null
                if (now - installed > RECENT_WINDOW_MS) null else app to installed
            }
                .sortedByDescending { it.second }
                .take(RECENT_COUNT)
                .map { it.first }
        }
    }
}
