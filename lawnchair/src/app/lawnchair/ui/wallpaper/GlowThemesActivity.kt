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
import androidx.annotation.StringRes
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.General
import app.lawnchair.views.GlowGlass
import com.android.launcher3.R
import java.util.concurrent.Executors

/**
 * Glow themes: one tap sets a matching wallpaper (home and lock screen) and
 * accent color. Opened from the edit mode toolbar.
 */
class GlowThemesActivity : Activity() {

    /** The theme accent (Themes / Settings), not a fixed violet. */
    private val ACCENT by lazy { GlowGlass.accent(this) }



    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(20))
        }
        GlowThemes.ALL.forEach { list.addView(createCard(it)) }

        list.addView(outlinedButton(R.string.glow_all_wallpapers) {
            startActivity(android.content.Intent(this, GlowWallpaperActivity::class.java))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(8)
        })

        list.addView(outlinedButton(R.string.glow_theme_more_settings) {
            startActivity(PreferenceActivity.createIntent(this@GlowThemesActivity, General))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(8)
        })

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
        }
        GlowWallpaperTarget.stylePage(this, root)
        root.addView(TextView(this).apply {
            setText(R.string.glow_wallpaper_and_themes)
            setTextColor(GlowWallpaperTarget.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(20), dp(20), dp(20), dp(12))
        })
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
        ))
        setContentView(root)
    }

    private fun outlinedButton(@StringRes label: Int, onClick: () -> Unit) = TextView(this).apply {
        setText(label)
        setTextColor(ACCENT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = dp(26).toFloat()
            setStroke(dp(2), ACCENT)
        }
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun createCard(theme: GlowThemes.Theme): View {
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
            setTextColor(GlowWallpaperTarget.TEXT)
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

    private var busy: app.lawnchair.views.GlowSheet? = null

    private fun applyTheme(theme: GlowThemes.Theme) {
        if (busy != null) return
        GlowWallpaperTarget.ask(this) { flags -> applyTheme(theme, flags) }
    }

    private fun applyTheme(theme: GlowThemes.Theme, which: Int) {
        if (busy != null) return
        val appContext = applicationContext
        busy = GlowWallpaperTarget.showBusy(this)
        executor.execute {
            val ok = GlowThemes.applyBlocking(appContext, theme, which)
            runOnUiThread {
                busy?.takeIf { !isDestroyed }?.dismiss()
                busy = null
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
}
