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
import android.app.WallpaperManager
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.General
import app.lawnchair.views.GlowGlass
import com.android.launcher3.R
import com.patrykmichalik.opto.core.setBlocking
import java.util.concurrent.Executors

/**
 * Glow themes: one tap sets a matching wallpaper (home and lock screen) and
 * accent color. Opened from the edit mode toolbar.
 */
class GlowThemesActivity : Activity() {

    private data class GlowTheme(
        @StringRes val name: Int,
        @DrawableRes val wallpaper: Int,
        val accent: Int,
    )

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        // Dark status and navigation bar icons on the white page
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(20))
        }
        THEMES.forEach { list.addView(createCard(it)) }

        list.addView(TextView(this).apply {
            setText(R.string.glow_theme_more_settings)
            setTextColor(ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(26).toFloat()
                setStroke(dp(2), ACCENT)
            }
            setOnClickListener {
                startActivity(PreferenceActivity.createIntent(this@GlowThemesActivity, General))
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(8)
        })

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            fitsSystemWindows = true
        }
        root.addView(TextView(this).apply {
            setText(R.string.glow_themes)
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(20), dp(20), dp(20), dp(12))
        })
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
        ))
        setContentView(root)
    }

    private fun createCard(theme: GlowTheme): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(16))
        }

        val preview = FrameLayout(this).apply {
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(24).toFloat())
                }
            }
        }
        preview.addView(ImageView(this).apply {
            setImageResource(theme.wallpaper)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        // Mock glass dock with accent "icons" so the theme reads at a glance
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.argb(77, 255, 255, 255))
                setStroke(dp(1), Color.argb(120, 255, 255, 255))
            }
        }
        repeat(4) { i ->
            dock.addView(View(this).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (i % 2 == 0) theme.accent else Color.WHITE)
                }
            }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(8), 0, dp(8), 0) })
        }
        preview.addView(dock, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            dp(64),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply { bottomMargin = dp(16) })
        card.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220)))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(10), 0, 0)
        }
        row.addView(TextView(this).apply {
            setText(theme.name)
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            setText(R.string.glow_theme_apply)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(24), 0, dp(24), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(ACCENT)
            }
            setOnClickListener { applyTheme(theme) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)))
        card.addView(row)
        return card
    }

    private fun applyTheme(theme: GlowTheme) {
        val appContext = applicationContext
        executor.execute {
            val ok = runCatching {
                resources.openRawResource(theme.wallpaper).use { stream ->
                    WallpaperManager.getInstance(appContext).setStream(
                        stream,
                        null,
                        true,
                        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
                    )
                }
            }.isSuccess
            if (ok) {
                // Written here on the background thread: setBlocking waits for the disk
                runCatching {
                    PreferenceManager2.getInstance(appContext).accentColor
                        .setBlocking(ColorOption.CustomColor(theme.accent))
                }
            }
            runOnUiThread {
                Toast.makeText(
                    appContext,
                    if (ok) R.string.glow_theme_applied else R.string.glow_wallpaper_failed,
                    Toast.LENGTH_SHORT,
                ).show()
                if (ok) finish()
            }
        }
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
        private val ACCENT = GlowGlass.ACCENT

        private val THEMES = listOf(
            GlowTheme(R.string.glow_theme_violet, R.drawable.glow_wallpaper_violet, Color.rgb(0xB1, 0x4F, 0xE8)),
            GlowTheme(R.string.glow_theme_rose, R.drawable.glow_wallpaper_rose, Color.rgb(0xE0, 0x4F, 0x76)),
            GlowTheme(R.string.glow_theme_frost, R.drawable.glow_wallpaper_frost, Color.rgb(0x6F, 0x8F, 0xD8)),
            GlowTheme(R.string.glow_theme_peach, R.drawable.glow_wallpaper_peach, Color.rgb(0xF0, 0x8A, 0x5D)),
        )
    }
}
