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

package app.lawnchair.ui.wallpaper

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import com.android.launcher3.R
import java.util.concurrent.Executors

/**
 * Glow wallpaper gallery: the bundled Glow wallpapers plus a shortcut to the
 * system wallpaper picker. Opened from the edit mode toolbar.
 */
class GlowWallpaperActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            fitsSystemWindows = true
        }

        root.addView(TextView(this).apply {
            setText(R.string.wallpapers)
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(20), dp(20), dp(20), dp(12))
        })

        val grid = GridLayout(this).apply {
            columnCount = 2
            setPadding(dp(14), 0, dp(14), dp(14))
        }
        WALLPAPERS.forEach { grid.addView(createTile(it)) }
        root.addView(ScrollView(this).apply { addView(grid) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
        ))

        root.addView(TextView(this).apply {
            setText(R.string.glow_more_wallpapers)
            setTextColor(ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setStroke(dp(2), ACCENT)
            }
            setOnClickListener { openSystemPicker() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply {
            setMargins(dp(20), dp(8), dp(20), dp(20))
        })

        setContentView(root)
    }

    private fun createTile(@DrawableRes res: Int): View {
        val width = (resources.displayMetrics.widthPixels - dp(28)) / 2 - dp(12)
        val image = ImageView(this).apply {
            setImageResource(res)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(20).toFloat())
                }
            }
            setOnClickListener { askWhereToApply(res) }
        }
        return image.also {
            it.layoutParams = GridLayout.LayoutParams().apply {
                this.width = width
                height = (width * 2.1f).toInt()
                setMargins(dp(6), dp(6), dp(6), dp(6))
            }
        }
    }

    private fun askWhereToApply(@DrawableRes res: Int) {
        val labels = arrayOf(
            getString(R.string.glow_wallpaper_home),
            getString(R.string.glow_wallpaper_lock),
            getString(R.string.glow_wallpaper_both),
        )
        val flags = intArrayOf(
            WallpaperManager.FLAG_SYSTEM,
            WallpaperManager.FLAG_LOCK,
            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.glow_wallpaper_apply)
            .setItems(labels) { _, which -> applyWallpaper(res, flags[which]) }
            .show()
    }

    private fun applyWallpaper(@DrawableRes res: Int, which: Int) {
        val appContext = applicationContext
        executor.execute {
            val ok = runCatching {
                resources.openRawResource(res).use { stream ->
                    WallpaperManager.getInstance(appContext).setStream(stream, null, true, which)
                }
            }.isSuccess
            runOnUiThread {
                Toast.makeText(
                    appContext,
                    if (ok) R.string.glow_wallpaper_applied else R.string.glow_wallpaper_failed,
                    Toast.LENGTH_SHORT,
                ).show()
                if (ok) finish()
            }
        }
    }

    private fun openSystemPicker() {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER)
        runCatching { startActivity(Intent.createChooser(intent, getString(R.string.wallpapers))) }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics,
    ).toInt()

    companion object {
        private val ACCENT = Color.rgb(0xB1, 0x4F, 0xE8)

        private val WALLPAPERS = listOf(
            R.drawable.glow_wallpaper_violet,
            R.drawable.glow_wallpaper_rose,
            R.drawable.glow_wallpaper_frost,
            R.drawable.glow_wallpaper_peach,
        )
    }
}
