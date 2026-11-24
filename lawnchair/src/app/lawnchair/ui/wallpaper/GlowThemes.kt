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

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.theme.color.ColorOption
import com.android.launcher3.R
import com.patrykmichalik.opto.core.setBlocking

/** The four Glow looks (wallpaper + matching accent), shared by Themes and the welcome sheet. */
object GlowThemes {

    data class Theme(
        @StringRes val name: Int,
        @DrawableRes val wallpaper: Int,
        val accent: Int,
    )

    @JvmField
    val ALL = listOf(
        Theme(R.string.glow_theme_violet, R.drawable.glow_wallpaper_violet, Color.rgb(0xB1, 0x4F, 0xE8)),
        Theme(R.string.glow_theme_rose, R.drawable.glow_wallpaper_rose, Color.rgb(0xE0, 0x4F, 0x76)),
        Theme(R.string.glow_theme_frost, R.drawable.glow_wallpaper_frost, Color.rgb(0x6F, 0x8F, 0xD8)),
        Theme(R.string.glow_theme_peach, R.drawable.glow_wallpaper_peach, Color.rgb(0xF0, 0x8A, 0x5D)),
    )

    /**
     * Sets the theme's wallpaper on [which] (WallpaperManager flags) and its accent.
     * Blocking: call it off the main thread. Returns false when the wallpaper failed.
     */
    fun applyBlocking(context: Context, theme: Theme, which: Int): Boolean {
        val appContext = context.applicationContext
        val ok = runCatching {
            appContext.resources.openRawResource(theme.wallpaper).use { stream ->
                WallpaperManager.getInstance(appContext).setStream(stream, null, true, which)
            }
        }.isSuccess
        if (ok) {
            runCatching {
                PreferenceManager2.getInstance(appContext).accentColor
                    .setBlocking(ColorOption.CustomColor(theme.accent))
            }
        }
        return ok
    }
}
