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
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import kotlin.math.pow

/**
 * How often and how recently each app is opened from Glow. The App Library shows the
 * most used apps of a category as its big icons, like iOS. Scores fade with a two-week
 * half-life, so an app used a lot last month gives way to today's habits.
 */
object GlowLaunchStats {

    private const val PREFS = "glow_launch_stats"
    private const val HALF_LIFE_MS = 14.0 * 24 * 60 * 60 * 1000

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(info: ItemInfo): String? {
        val component = info.targetComponent ?: return null
        return component.flattenToShortString() + "#" + info.user.hashCode()
    }

    /** Stored as "score|timeMillis". */
    private fun score(raw: String?, now: Long): Double {
        val parts = raw?.split('|') ?: return 0.0
        val value = parts.getOrNull(0)?.toDoubleOrNull() ?: return 0.0
        val time = parts.getOrNull(1)?.toLongOrNull() ?: return 0.0
        return value * 0.5.pow((now - time).coerceAtLeast(0) / HALF_LIFE_MS)
    }

    @JvmStatic
    fun record(context: Context, info: ItemInfo) {
        val key = key(info) ?: return
        runCatching {
            val prefs = prefs(context)
            val now = System.currentTimeMillis()
            val next = score(prefs.getString(key, null), now) + 1.0
            prefs.edit().putString(key, "$next|$now").apply()
        }
    }

    /** [apps] with the most used first; apps never opened keep their given order. */
    fun ranked(context: Context, apps: List<AppInfo>): List<AppInfo> {
        val prefs = runCatching { prefs(context).all }.getOrNull() ?: return apps
        if (prefs.isEmpty()) return apps
        val now = System.currentTimeMillis()
        val scores = apps.associateWith { app -> key(app)?.let { score(prefs[it] as? String, now) } ?: 0.0 }
        return apps.sortedByDescending { scores[it] ?: 0.0 }
    }
}
