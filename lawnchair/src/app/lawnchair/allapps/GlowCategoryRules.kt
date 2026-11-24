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

    /**
     * Exact tokens (package segments or label words). Groups are checked in this order,
     * so "Bolt Food" lands in Shopping & Food (food) before Travel (bolt).
     */
    private val TOKENS: List<Pair<String, Set<String>>> = listOf(
        SOCIAL to setOf(
            "telegram", "whatsapp", "viber", "messenger", "messaging", "chat", "vk", "vkontakte",
            "instagram", "tiktok", "musically", "snapchat", "discord", "signal", "facebook",
            "twitter", "threads", "reddit", "linkedin", "wechat", "kakao", "pinterest", "tumblr",
            "mastodon", "bluesky", "skype", "line", "dialer", "contacts", "phone", "sms", "mms",
            "dating", "tinder", "badoo", "bumble", "max", "element", "matrix", "forum", "forums",
            "мессенджер", "чат", "знакомства", "телефон", "контакты",
        ),
        FINANCE to setOf(
            "bank", "banking", "pay", "wallet", "finance", "crypto", "money", "invest", "sberbank",
            "tinkoff", "tbank", "alfabank", "vtb", "paypal", "revolut", "binance", "trading",
            "monobank", "privat24", "privatbank", "pumb", "oschadbank", "raiffeisen", "ukrsibbank",
            "abank", "sensebank", "izibank", "wise", "payoneer", "exchange", "broker", "stocks",
            "bybit", "okx", "trustwallet", "metamask", "банк", "гаманець", "кошелек", "финансы",
        ),
        SHOPPING to setOf(
            "shop", "shopping", "market", "marketplace", "store", "food", "delivery", "eats", "ozon",
            "wildberries", "aliexpress", "amazon", "avito", "lamoda", "samokat", "kuper", "glovo",
            "olx", "rozetka", "prom", "epicentr", "epicentrk", "silpo", "atb", "novus", "fora",
            "auchan", "metro", "varus", "temu", "shein", "joom", "ebay", "etsy", "zara",
            "mcdonalds", "kfc", "burger", "burgerking", "pizza", "sushi", "grill", "restaurant",
            "cafe", "coffee", "bakery", "eda", "dodo", "wolt", "zakaz", "novaposhta",
            "poshta", "ukrposhta", "meest", "cdek", "cashback", "coupons", "deals",
            "магазин", "доставка", "еда", "їжа", "піца", "пицца", "суші", "суши", "кафе",
        ),
        TRAVEL to setOf(
            "taxi", "bolt", "uber", "uklon", "maps", "map", "navi", "navigator", "waze", "yandexmaps",
            "booking", "airline", "airlines", "aviasales", "tutu", "transit", "transport", "bus",
            "train", "rail", "railway", "ukrzaliznytsia", "flix", "flixbus", "blablacar",
            "carsharing", "sharing", "getmancar", "go2go", "easyway", "rent", "rental", "car",
            "cars", "parking", "fuel", "wog", "socar", "airbnb", "hotels", "hotel",
            "trip", "tripadvisor", "skyscanner", "travel", "flights", "flight", "scooter",
            "kiwi", "citymapper", "moovit", "osmand", "2gis", "таксі", "такси", "карти",
            "карты", "транспорт",
        ),
        PRODUCTIVITY to setOf(
            "office", "docs", "sheets", "slides", "mail", "gmail", "calendar", "notes", "keep",
            "drive", "notion", "todo", "tasks", "outlook", "teams", "zoom", "slack", "meet",
            "trello", "jira", "asana", "evernote", "obsidian", "onenote", "word", "excel",
            "powerpoint", "pdf", "dropbox", "onedrive", "cloud", "diia", "gosuslugi", "chatgpt",
            "claude", "gemini", "copilot", "assistant", "ai", "translate", "translator",
            "пошта", "почта", "нотатки", "заметки", "календарь", "календар",
        ),
        UTILITIES to setOf(
            "vpn", "cleaner", "files", "file", "filemanager", "calculator", "calc", "clock",
            "settings", "launcher", "adguard", "authenticator", "anydesk", "termux", "terminal",
            "flashlight", "torch", "weather", "scanner", "keyboard", "browser", "chrome", "firefox",
            "opera", "brave", "edge", "vivaldi", "duckduckgo", "search", "gboard",
            "swiftkey", "backup", "security", "antivirus", "password", "passwords", "bitwarden",
            "lastpass", "proton", "nordvpn", "recorder", "compass", "qr", "barcode",
            "battery", "wifi", "bluetooth", "remote", "kyivstar", "vodafone", "lifecell",
            "mts", "beeline", "megafon", "tele2", "sim", "stk", "4pda", "bot", "id",
            "калькулятор", "погода", "годинник", "часы", "налаштування", "настройки",
        ),
        ENTERTAINMENT to setOf(
            "music", "video", "youtube", "netflix", "spotify", "player", "tv", "kino", "kinopoisk",
            "stream", "twitch", "podcast", "podcasts", "radio", "soundcloud", "ivi", "megogo",
            "sweettv", "kyivstartv", "prime", "disney", "hbo", "apple", "deezer",
            "shazam", "audible", "vlc", "mx", "anime", "movies", "movie", "cinema", "films",
            "film", "series", "rezka", "hdrezka", "yummy", "плеер", "музика", "музыка", "кино",
            "фільми", "фильмы",
        ),
        CREATIVITY to setOf(
            "photo", "photos", "camera", "gallery", "editor", "design", "draw", "canva", "capcut",
            "lightroom", "snapseed", "picsart", "vsco", "inshot", "remini", "facetune", "wallpaper",
            "wallpapers", "theme", "themes", "icon", "icons", "iconpack", "kwgt", "klwp",
            "фото", "камера", "галерея", "редактор",
        ),
        READING to setOf(
            "news", "books", "book", "reader", "kindle", "wiki", "wikipedia", "litres", "medium",
            "rss", "feedly", "pocket", "comics", "manga", "sport", "sports", "flashscore",
            "новини", "новости", "книги",
        ),
        HEALTH to setOf(
            "health", "fitness", "workout", "sleep", "steps", "yoga", "pill", "pills", "meds",
            "medicine", "pharmacy", "apteka", "tabletki", "liki24", "doctor", "helsi", "clinic",
            "period", "flo", "calories", "fit", "gym", "run", "running", "strava", "zepp",
            "здоров", "аптека", "ліки", "лекарства",
        ),
        EDUCATION to setOf(
            "duolingo", "learn", "education", "school", "course", "courses", "english", "language",
            "dictionary", "study", "university", "quiz", "photomath", "brainly", "coursera",
            "udemy", "khan", "освіта", "образование", "школа",
        ),
        GAMES to setOf(
            "game", "games", "gaming", "steam", "playgames", "epic", "roblox", "minecraft", "pubg",
            "durak", "solitaire", "klondike", "poker", "chess", "checkers", "sudoku", "puzzle",
            "mahjong", "domino", "dominoes", "backgammon", "nardy", "uno", "bingo", "slots",
            "дурак", "пасьянс", "шахи", "шахматы", "шашки", "нарды", "нарди", "покер", "судоку",
            "гра", "игра", "игры", "ігри",
        ),
    )

    /**
     * Word endings and stems that are specific enough to match inside a longer word
     * ("hivogram" is a Telegram client, "pixelboard" a keyboard, "sushimaster" food).
     */
    private val STEMS: List<Pair<String, List<String>>> = listOf(
        SOCIAL to listOf("gram:end", "messenger", "chat:end"),
        FINANCE to listOf("bank", "wallet", "crypto"),
        SHOPPING to listOf("sushi", "pizza", "burger", "food", "grill", "market", "shop:end", "delivery"),
        TRAVEL to listOf("taxi", "maps", "carshar"),
        UTILITIES to listOf("board:end", "vpn", "cleaner", "launcher", "browser"),
        ENTERTAINMENT to listOf("music", "player:end", "tube:end", "movie", "kino"),
        CREATIVITY to listOf("camera", "photo", "wallpaper"),
        GAMES to listOf("game"),
    )

    /** Labels and package words that match a stem but mean something else. */
    private val STEM_EXCEPTIONS = setOf("program", "programs", "diagram", "diagrams", "kilogram", "telegram")

    /**
     * Common apps, sorted by hand: their own Android category or name would put
     * them in the wrong group, or nothing would match at all.
     */
    private val KNOWN_PACKAGES: Map<String, String> = buildMap {
        listOf(
            "com.android.settings", "com.google.android.deskclock", "com.android.deskclock",
            "com.google.android.calculator", "com.android.calculator2", "com.google.android.apps.nbu.files",
            "com.google.android.documentsui", "com.android.documentsui", "com.android.chrome",
            "org.mozilla.firefox", "com.google.android.googlequicksearchbox", "com.android.vending",
            "com.opera.browser", "com.opera.mini.native", "com.brave.browser", "com.microsoft.emmx",
            "com.duckduckgo.mobile.android", "com.vivaldi.browser", "com.sec.android.app.sbrowser",
            "com.yandex.browser", "com.adguard.android", "com.adguard.vpn", "com.nordvpn.android",
            "ch.protonvpn.android", "com.expressvpn.vpn", "com.cloudflare.onedotonedotone",
            "org.outline.android.client", "com.v2ray.ang", "com.server.auditor.ssh.client", "com.termux",
            "com.anydesk.anydeskandroid", "com.teamviewer.teamviewer.market.mobile",
            "com.google.android.apps.authenticator2", "com.azure.authenticator", "com.x8bit.bitwarden",
            "com.lastpass.lpandroid", "com.agilebits.onepassword", "com.google.android.inputmethod.latin",
            "com.touchtype.swiftkey", "com.google.android.apps.recorder", "com.google.android.apps.weather",
            "com.samsung.android.weather", "com.accuweather.android", "com.google.android.apps.wellbeing",
            "com.google.android.apps.googleassistant", "com.android.stk",
            "com.google.android.apps.subscriptions.red", "com.google.android.apps.adm",
            "com.kyivstar.mykyivstar", "ua.vodafone.myvodafone", "ua.lifecell.mylifecell",
            "ru.fourpda.client", "com.miui.securitycenter", "com.miui.cleanmaster",
            "com.google.android.apps.security.securityhub", "com.google.android.marvin.talkback",
            "com.google.ar.lens", "com.google.android.apps.searchlite",
        ).forEach { put(it, UTILITIES) }
        listOf(
            "com.google.android.contacts", "com.android.contacts", "com.google.android.dialer",
            "com.android.dialer", "com.google.android.apps.messaging", "com.samsung.android.messaging",
            "com.samsung.android.dialer", "org.telegram.messenger", "org.telegram.messenger.web",
            "org.thunderdog.challegram", "org.telegram.plus", "com.whatsapp", "com.whatsapp.w4b",
            "com.viber.voip", "com.discord", "com.instagram.android", "com.instagram.barcelona",
            "com.facebook.katana", "com.facebook.orca", "com.facebook.lite", "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill", "com.snapchat.android", "com.twitter.android", "com.reddit.frontpage",
            "com.linkedin.android", "com.pinterest", "org.thoughtcrime.securesms", "com.vkontakte.android",
            "ru.oneme.app", "com.skype.raider", "jp.naver.line.android", "com.tencent.mm", "com.kakao.talk",
            "com.tinder", "com.badoo.mobile", "com.bumble.app", "com.google.android.apps.tachyon",
            "org.joinmastodon.android", "xyz.blueskyweb.app", "com.tumblr", "im.vector.app",
        ).forEach { put(it, SOCIAL) }
        listOf(
            "com.google.android.calendar", "com.google.android.gm", "com.google.android.keep",
            "com.google.android.apps.docs", "com.google.android.apps.docs.editors.docs",
            "com.google.android.apps.docs.editors.sheets", "com.google.android.apps.docs.editors.slides",
            "com.google.android.apps.tasks", "com.google.android.apps.translate",
            "com.microsoft.office.outlook", "com.microsoft.teams", "com.microsoft.office.officehubrow",
            "com.microsoft.office.word", "com.microsoft.office.excel", "com.microsoft.office.powerpoint",
            "com.microsoft.office.onenote", "com.microsoft.skydrive", "com.microsoft.todos", "com.notion.id",
            "md.obsidian", "com.evernote", "com.trello", "com.atlassian.android.jira.core", "com.asana.app",
            "com.Slack", "us.zoom.videomeetings", "com.google.android.apps.meetings", "com.dropbox.android",
            "com.adobe.reader", "com.openai.chatgpt", "com.anthropic.claude", "com.google.android.apps.bard",
            "com.microsoft.copilot", "ua.gov.diia.app", "ua.gov.diia.opensource",
            "com.samsung.android.app.notes", "com.google.android.apps.dynamite", "com.yandex.mail",
            "ru.mail.mailapp", "com.readdle.spark", "com.ticktick.task", "com.todoist",
            "com.deepl.mobiletranslator",
        ).forEach { put(it, PRODUCTIVITY) }
        listOf(
            "ua.com.cs.ifobs.mobile.android.pb", "com.ftband.mono", "ua.privatbank.ap24", "ua.pumb.mobile",
            "ua.oschadbank.online", "ua.raiffeisen.myraif", "com.ukrsibbank.client.android",
            "ua.alfabank.mobile.android", "ua.sensebank.mobile", "com.izibank.app", "ua.abank.mobile",
            "com.paypal.android.p2pmobile", "com.revolut.revolut", "com.transferwise.android",
            "com.binance.dev", "com.bybit.app", "com.okinc.okex.gp", "com.wallet.crypto.trustapp",
            "io.metamask", "ru.sberbankmobile", "com.idamob.tinkoff.android", "ru.alfabank.mobile.android",
            "ru.vtb24.mobilebanking.android", "com.google.android.apps.walletnfcrel",
            "com.samsung.android.spay", "com.payoneer.android",
        ).forEach { put(it, FINANCE) }
        listOf(
            "com.amazon.mShop.android.shopping", "com.alibaba.aliexpresshd", "com.einnovation.temu",
            "com.zzkko", "ua.slando", "com.olx.ua", "pl.olx.android", "ua.com.rozetka.shop", "ua.prom.b2c",
            "ua.epicentrk.ecom", "ua.silpo.android", "ua.fozzy.silpo", "ua.atbmarket.app", "ua.novus.app",
            "com.glovo", "com.bolt.deliveryclient", "com.wolt.android", "com.mcdonalds.mobileapp",
            "ua.mcdonalds.app", "com.kfc.ua", "com.ebay.mobile", "com.etsy.android", "com.inditex.zara",
            "com.novaposhta.client", "ua.novaposhta", "ua.ukrposhta.android", "com.meest.mobile",
            "ru.ozon.app.android", "com.wildberries.ru", "com.avito.android", "ru.yandex.eats", "com.joom",
            "com.shein.android",
        ).forEach { put(it, SHOPPING) }
        listOf(
            "com.google.android.apps.maps", "com.waze", "com.ubercab", "ee.mtakso.client", "com.uklon.client",
            "ua.uklon", "com.booking", "com.airbnb.android", "com.tripadvisor.tripadvisor",
            "net.skyscanner.android.main", "com.flixbus.app", "com.comuto", "com.citymapper.app.release",
            "com.tranzmate", "net.osmand", "net.osmand.plus", "ru.dublgis.dgismobile", "ru.yandex.yandexmaps",
            "ru.yandex.taxi", "ru.yandex.yandexnavi", "com.easyway.easyway", "ua.easyway", "com.getmancar",
            "ua.okko.app", "ua.wog.wogapp", "com.ryanair.cheapflights", "com.wizzair.WizzAirApp",
        ).forEach { put(it, TRAVEL) }
        listOf(
            "com.google.android.youtube", "com.google.android.apps.youtube.music",
            "com.google.android.apps.youtube.kids", "com.spotify.music", "com.netflix.mediaclient",
            "com.soundcloud.android", "com.deezer.android.app", "com.shazam.android", "tv.twitch.android.app",
            "com.amazon.avod.thirdpartyclient", "com.disney.disneyplus", "com.hbo.hbonow", "com.wbd.stream",
            "com.megogo.application", "ua.sweet.tv", "ua.kyivstar.tv", "com.google.android.videos",
            "org.videolan.vlc", "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro",
            "com.apple.android.music", "com.audible.application", "ru.kinopoisk", "ru.ivi.client",
            "ru.yandex.music", "com.zvooq.openplay", "com.google.android.apps.podcasts",
            "au.com.shiftyjelly.pocketcasts", "com.plexapp.android", "com.crunchyroll.crunchyroid",
        ).forEach { put(it, ENTERTAINMENT) }
        listOf(
            "com.google.android.apps.photos", "com.google.android.GoogleCamera", "com.android.camera",
            "com.android.camera2", "com.sec.android.app.camera", "com.miui.gallery",
            "com.sec.android.gallery3d", "com.google.android.apps.photosgo", "com.niksoftware.snapseed",
            "com.adobe.lrmobile", "com.picsart.studio", "com.lemon.lvoverseas", "com.canva.editor",
            "com.vsco.cam", "com.camerasideas.instashot", "com.google.android.apps.wallpaper",
            "org.kustom.widget", "org.kustom.wallpaper", "com.google.android.apps.photos.scanner",
        ).forEach { put(it, CREATIVITY) }
        listOf(
            "com.google.android.apps.magazines", "com.google.android.apps.books", "com.amazon.kindle",
            "org.wikipedia", "com.medium.reader", "com.feedly.android", "com.ideashower.readitlater.pro",
            "eu.livesport.FlashScore_com", "ru.litres.android", "com.bookmate",
        ).forEach { put(it, READING) }
        listOf(
            "com.google.android.apps.fitness", "com.sec.android.app.shealth", "com.strava",
            "com.myfitnesspal.android", "com.huami.watch.hmwatchmanager", "com.xiaomi.wearable",
            "com.mi.health", "com.zepp.app", "org.iggymedia.periodtracker", "ua.com.tabletki.ua",
            "ua.liki24.android", "ua.helsi.android", "com.calm.android", "com.headspace.android",
            "com.google.android.apps.healthdata",
        ).forEach { put(it, HEALTH) }
        listOf(
            "com.duolingo", "com.google.android.apps.classroom", "com.microblink.photomath", "co.brainly",
            "com.coursera.android", "com.udemy.android", "org.khanacademy.android",
            "com.quizlet.quizletandroid",
        ).forEach { put(it, EDUCATION) }
        listOf(
            "com.google.android.play.games", "com.valvesoftware.android.steam.community", "com.roblox.client",
            "com.mojang.minecraftpe", "com.tencent.ig", "com.supercell.clashofclans",
            "com.supercell.brawlstars",
        ).forEach { put(it, GAMES) }
    }

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
        TOKENS.firstOrNull { (_, set) -> words.any { it in set } }?.let { return it.first }
        return stemCategory(words)
    }

    private fun stemCategory(words: Set<String>): String? {
        val candidates = words.filter { it.length >= 5 && it !in STEM_EXCEPTIONS }
        for ((group, stems) in STEMS) {
            for (stem in stems) {
                val endOnly = stem.endsWith(":end")
                val s = stem.removeSuffix(":end")
                if (candidates.any { if (endOnly) it.endsWith(s) else it.contains(s) }) return group
            }
        }
        return null
    }

    /** Groups a user can move an app to, in tile order. */
    val MOVABLE_GROUPS: List<String> get() = TILE_ORDER + OTHER

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
