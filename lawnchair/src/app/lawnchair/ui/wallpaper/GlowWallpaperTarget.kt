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
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.android.launcher3.R

/** Shared bits of the Wallpapers and Themes screens: where to apply, and a busy dialog. */
internal object GlowWallpaperTarget {

    /** Asks Home screen / Lock screen / Both, then calls [onPick] with the WallpaperManager flags. */
    fun ask(activity: Activity, onPick: (Int) -> Unit) {
        val labels = arrayOf(
            activity.getString(R.string.glow_wallpaper_home),
            activity.getString(R.string.glow_wallpaper_lock),
            activity.getString(R.string.glow_wallpaper_both),
        )
        val flags = intArrayOf(
            WallpaperManager.FLAG_SYSTEM,
            WallpaperManager.FLAG_LOCK,
            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
        )
        AlertDialog.Builder(activity)
            .setTitle(R.string.glow_wallpaper_apply)
            .setItems(labels) { _, which -> onPick(flags[which]) }
            .show()
    }

    /** A small non-cancelable "Applying…" dialog shown while the wallpaper is written. */
    fun showBusy(activity: Activity): AlertDialog {
        val density = activity.resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(ProgressBar(activity))
            addView(TextView(activity).apply {
                setText(R.string.glow_wallpaper_applying)
                textSize = 16f
                setPadding(pad, 0, 0, 0)
            })
        }
        return AlertDialog.Builder(activity)
            .setView(content)
            .setCancelable(false)
            .show()
    }
}
