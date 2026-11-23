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

import java.util.Locale

/**
 * Pure (Android-free) rules that sort apps into App Library groups when neither
 * Flowerpot nor the app's own Android category knows where it belongs.
 *
 * Words are matched against whole package segments and whole words of the label,
 * so "gram" no longer catches every "program" and "tv" no longer catches "tvz".
 */
object GlowCategoryRules {

    const val SOCIAL = "social"
    const val ENTERTAINMENT = "entertainment"
    const val CREATIVITY = "creativity"
    const val PRODUCTIVITY = "productivity"
    const val FINANCE = "finance"
    const val UTILITIES = "utilities"
    const val READING = "reading"
    const val SHOPPING = "shopping"
    const val TRAVEL = "travel"
    const val HEALTH = "health"
    const val GAMES = "games"
    const val EDUCATION = "education"
    const val OTHER = "other"

    /** Exact tokens (package segments or label words). */
    private val TOKENS: List<Pair<String, Set<String>>> = listOf(
        SOCIAL to setOf(
            "telegram", "whatsapp", "viber", "messenger", "messaging", "chat", "vk", "vkontakte",
            "instagram", "tiktok", "musically", "snapchat", "discord", "signal", "facebook",
            "twitter", "threads", "reddit", "linkedin", "wechat", "kakao",
        ),
        FINANCE to setOf(
            "bank", "banking", "pay", "wallet", "finance", "crypto", "money", "invest", "sberbank",
            "tinkoff", "tbank", "alfabank", "vtb", "paypal", "revolut", "binance", "trading",
        ),
        PRODUCTIVITY to setOf(
            "office", "docs", "sheets", "slides", "mail", "gmail", "calendar", "notes", "keep",
            "drive", "notion", "todo", "tasks", "outlook", "teams", "zoom", "slack",
        ),
        UTILITIES to setOf(
            "vpn", "cleaner", "files", "file", "filemanager", "calculator", "calc", "clock",
            "settings", "launcher", "adguard", "authenticator", "anydesk", "termux", "terminal",
            "flashlight", "torch", "weather", "scanner", "keyboard", "browser", "chrome", "firefox",
        ),
        SHOPPING to setOf(
            "shop", "shopping", "market", "store", "food", "delivery", "eats", "ozon", "wildberries",
            "aliexpress", "amazon", "avito", "lamoda", "samokat", "kuper",
        ),
        TRAVEL to setOf(
            "taxi", "bolt", "uber", "maps", "navi", "navigator", "waze", "yandexmaps", "booking",
            "airline", "airlines", "aviasales", "tutu", "metro", "transit",
        ),
        ENTERTAINMENT to setOf(
            "music", "video", "youtube", "netflix", "spotify", "player", "tv", "kino", "kinopoisk",
            "stream", "twitch", "podcast", "podcasts", "radio", "soundcloud", "ivi", "okko",
        ),
        CREATIVITY to setOf(
            "photo", "photos", "camera", "gallery", "editor", "design", "draw", "canva", "capcut",
            "lightroom", "snapseed", "picsart",
        ),
        READING to setOf("news", "books", "reader", "kindle", "wiki", "wikipedia", "litres"),
        HEALTH to setOf("health", "fitness", "workout", "sleep", "steps", "yoga"),
        EDUCATION to setOf("duolingo", "learn", "education", "school", "course", "courses"),
        GAMES to setOf("game", "games", "gaming"),
    )

    /** Common apps whose own category or keywords put them in the wrong group. */
    private val KNOWN_PACKAGES: Map<String, String> = mapOf(
        "com.android.settings" to UTILITIES,
        "com.google.android.deskclock" to UTILITIES,
        "com.android.deskclock" to UTILITIES,
        "com.google.android.calculator" to UTILITIES,
        "com.android.calculator2" to UTILITIES,
        "com.google.android.apps.nbu.files" to UTILITIES,
        "com.google.android.documentsui" to UTILITIES,
        "com.android.documentsui" to UTILITIES,
        "com.android.chrome" to UTILITIES,
        "org.mozilla.firefox" to UTILITIES,
        "com.google.android.googlequicksearchbox" to UTILITIES,
        "com.android.vending" to UTILITIES,
        "com.google.android.calendar" to PRODUCTIVITY,
        "com.google.android.gm" to PRODUCTIVITY,
        "com.google.android.keep" to PRODUCTIVITY,
        "com.google.android.apps.docs" to PRODUCTIVITY,
        "com.google.android.contacts" to SOCIAL,
        "com.android.contacts" to SOCIAL,
        "com.google.android.dialer" to SOCIAL,
        "com.android.dialer" to SOCIAL,
        "com.google.android.apps.messaging" to SOCIAL,
        "com.google.android.youtube" to ENTERTAINMENT,
        "com.google.android.apps.youtube.music" to ENTERTAINMENT,
        "com.google.android.apps.photos" to CREATIVITY,
        "com.google.android.GoogleCamera" to CREATIVITY,
        "com.google.android.apps.maps" to TRAVEL,
    )

    /**
     * Fixed order of the library tiles, so categories don't jump around when an app is
     * installed (after "Recently added"); groups not listed keep their size order after these.
     */
    val TILE_ORDER: List<String> = listOf(
        SOCIAL, UTILITIES, PRODUCTIVITY, ENTERTAINMENT, CREATIVITY, FINANCE, SHOPPING,
        TRAVEL, READING, HEALTH, EDUCATION, GAMES,
    )

    /** Group for a well-known package, or null. */
    fun knownCategory(pkg: String): String? = KNOWN_PACKAGES[pkg]

    /** Splits a package and a label into lower-case tokens. */
    fun tokens(pkg: String, title: String?): Set<String> {
        val out = HashSet<String>()
        pkg.lowercase(Locale.ROOT).split('.', '_', '-').filterTo(out) { it.isNotEmpty() }
        title?.lowercase(Locale.getDefault())
            ?.split(Regex("[^\\p{L}\\p{N}]+"))
            ?.filterTo(out) { it.isNotEmpty() }
        return out
    }

    /** Group for an app by its package and label, or null when nothing matches. */
    fun keywordCategory(pkg: String, title: String?): String? {
        val words = tokens(pkg, title)
        return TOKENS.firstOrNull { (_, set) -> words.any { it in set } }?.first
    }

    /** First letter for the A–Z index ("#" for digits, symbols and empty labels). */
    fun sectionOf(title: String?): String {
        val c = title?.trim()?.firstOrNull() ?: return "#"
        if (!c.isLetter()) return "#"
        // "Ё" files under "Е", accented letters under their base letter ("É" -> "E")
        val upper = c.uppercaseChar()
        if (upper == 'Ё') return "Е"
        val base = java.text.Normalizer.normalize(upper.toString(), java.text.Normalizer.Form.NFD)
            .firstOrNull { it.isLetter() } ?: upper
        return base.toString()
    }

    /**
     * Index strip labels that fit [maxLetters] slots: every other letter is replaced by
     * a dot when there are too many, like iOS. Returns (label, section) pairs; a dot
     * jumps to the letter it stands for.
     */
    fun condensedIndex(letters: List<String>, maxLetters: Int): List<Pair<String, String>> {
        if (letters.size <= maxLetters || maxLetters < 3) return letters.map { it to it }
        val step = (letters.size + maxLetters - 1) / maxLetters
        return letters.mapIndexedNotNull { i, l ->
            when {
                i == letters.lastIndex || i % (step * 2) == 0 -> l to l
                i % step == 0 -> "•" to l
                else -> null
            }
        }
    }

    /** Columns for the App Library tiles at a given width (2 on phones, more on tablets). */
    fun tileColumns(widthDp: Float): Int = (widthDp / 180f).toInt().coerceIn(2, 4)
}
