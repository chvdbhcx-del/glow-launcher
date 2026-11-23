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

package app.lawnchair.search.algorithms

import java.util.Locale

/**
 * Russian-friendly app search: "телеграм" finds Telegram, "ащтук" typed on the wrong
 * keyboard layout finds "Ozon", "sber" finds "Сбер".
 */
object GlowSearchVariants {

    private const val EN = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`"
    private const val RU = "йцукенгшщзхъфывапролджэячсмитьбюё"

    private val CYR_TO_LAT = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "i", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "",
        'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
    )

    /** Swaps the keyboard layout (QWERTY <-> ЙЦУКЕН), or null when nothing changes. */
    fun swapLayout(query: String): String? {
        var changed = false
        val out = query.map { c ->
            val lower = c.lowercaseChar()
            val en = EN.indexOf(lower)
            val ru = RU.indexOf(lower)
            when {
                en >= 0 -> { changed = true; RU[en] }
                ru >= 0 -> { changed = true; EN[ru] }
                else -> lower
            }
        }.joinToString("")
        return out.takeIf { changed && it != query.lowercase(Locale.ROOT) }
    }

    /** Cyrillic written in Latin letters ("телеграм" -> "telegram"); other characters stay. */
    fun toLatin(text: String): String = buildString {
        text.lowercase(Locale.ROOT).forEach { c -> append(CYR_TO_LAT[c] ?: c.toString()) }
    }

    /** Lower-case query forms to try, the query itself first. */
    fun variants(query: String): List<String> {
        val q = query.lowercase(Locale.getDefault()).trim()
        if (q.isEmpty()) return listOf(q)
        return listOfNotNull(q, swapLayout(q), toLatin(q).takeIf { it != q }).distinct()
    }

    /**
     * True when [title] (or the app's [pkg]) matches one of the [variants]: as typed, on
     * the other layout, or both sides transliterated to Latin.
     */
    fun matches(variants: List<String>, title: String, pkg: String?, plain: (String, String) -> Boolean): Boolean {
        val latinTitle = toLatin(title)
        for (v in variants) {
            if (v.isEmpty()) continue
            if (plain(v, title)) return true
            if (plain(toLatin(v), latinTitle)) return true
            if (v.length >= 3 && pkg != null && toLatin(v).let { it.all { c -> c.isLetterOrDigit() } && pkg.contains(it) }) return true
        }
        return false
    }
}
