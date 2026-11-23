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

import android.app.AlertDialog
import android.view.View
import android.widget.Toast
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.SystemShortcut

/**
 * "Move to folder" in an app's menu inside the App Library: when automatic sorting put
 * an app in the wrong folder (or in "Other"), the user picks the right one. The choice is
 * kept per package and wins over every automatic rule; "Automatic" undoes it.
 */
object GlowMoveToCategory {

    val MOVE = SystemShortcut.Factory { activity: LawnchairLauncher, itemInfo: ItemInfo, view: View ->
        if (itemInfo.itemType != ITEM_TYPE_APPLICATION || itemInfo.container != CONTAINER_ALL_APPS) {
            return@Factory null
        }
        val pkg = itemInfo.targetComponent?.packageName ?: return@Factory null
        if (pkg == activity.packageName) return@Factory null
        Move(activity, itemInfo, view, pkg)
    }

    class Move(
        private val launcher: LawnchairLauncher,
        itemInfo: ItemInfo,
        originalView: View,
        private val packageName: String,
    ) : SystemShortcut<LawnchairLauncher>(
        R.drawable.ic_folder,
        R.string.glow_library_move,
        launcher,
        itemInfo,
        originalView,
    ) {
        override fun onClick(v: View) {
            dismissTaskMenuView()
            val folders = GlowAppLibraryView.movableCategories(launcher)
            val picked = GlowAppLibraryView.userCategory(launcher, packageName)
            // First row: back to automatic sorting (checked while nothing was picked by hand)
            val labels = arrayOf(launcher.getString(R.string.glow_library_move_auto)) +
                folders.map { it.second }.toTypedArray()
            val checked = if (picked == null) 0 else folders.indexOfFirst { it.first == picked } + 1
            val title = mItemInfo.title?.toString().orEmpty()
            // Explicit dark dialog theme: the launcher theme gave dark-grey rows on a dark sheet
            AlertDialog.Builder(launcher, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(launcher.getString(R.string.glow_library_move_title, title))
                .setSingleChoiceItems(labels, checked) { dialog, which ->
                    dialog.dismiss()
                    val group = if (which == 0) null else folders[which - 1].first
                    if (group == picked) return@setSingleChoiceItems
                    GlowAppLibraryView.moveApp(launcher, packageName, group)
                    val shown = if (group == null) {
                        launcher.getString(R.string.glow_library_move_auto)
                    } else {
                        folders[which - 1].second
                    }
                    Toast.makeText(
                        launcher,
                        launcher.getString(R.string.glow_library_moved, title, shown),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
