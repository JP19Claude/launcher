package dev.hearth.launcher.data

import android.content.pm.ApplicationInfo

/** Folders of the App Library, in the order they are shown. */
enum class LibraryCategory(val title: String) {
    Social("Soziale Netze"),
    Productivity("Produktivität & Finanzen"),
    Entertainment("Unterhaltung"),
    Creativity("Kreativität"),
    Information("Information & Lesen"),
    Shopping("Shopping & Essen"),
    Travel("Reisen"),
    Health("Gesundheit & Fitness"),
    Games("Spiele"),
    Utilities("Dienstprogramme"),
    Other("Andere"),
}

/** Words in a package name or label that hint at a category, checked in this order. */
private val Keywords: List<Pair<LibraryCategory, List<String>>> = listOf(
    LibraryCategory.Social to listOf(
        "whatsapp", "telegram", "signal", "messenger", "instagram", "facebook", "snapchat", "tiktok",
        "twitter", "threads", "discord", "reddit", "mastodon", "bluesky", "linkedin", "messag", "sms",
        "mms", "chat", "dialer", "contacts", "phone", "kontakte", "telefon", "nachrichten", "facetime", "duo",
    ),
    LibraryCategory.Productivity to listOf(
        "bank", "paypal", "finance", "finanz", "wallet", "sparkasse", "volksbank", "n26", "revolut",
        "trade", "office", "docs", "sheets", "slides", "drive", "gmail", "mail", "outlook", "calendar",
        "kalender", "notes", "notiz", "notion", "keep", "todo", "task", "claude", "anthropic", "chatgpt",
        "openai", "gemini", "copilot", "slack", "teams", "zoom", "word", "excel", "powerpoint", "dropbox",
    ),
    LibraryCategory.Entertainment to listOf(
        "youtube", "netflix", "spotify", "music", "musik", "video", "twitch", "disney", "primevideo",
        "podcast", "radio", "player", "deezer", "soundcloud", "joyn", "zdf", "ard", "crunchyroll",
    ),
    LibraryCategory.Creativity to listOf(
        "camera", "kamera", "photo", "foto", "gallery", "galerie", "snapseed", "lightroom", "canva",
        "capcut", "picsart", "editor", "draw", "paint",
    ),
    LibraryCategory.Information to listOf(
        "news", "kindle", "book", "reader", "weather", "wetter", "wiki", "tagesschau", "spiegel",
        "zeitung", "magazin", "rss", "audible", "translate", "übersetz",
    ),
    LibraryCategory.Shopping to listOf(
        "amazon", "ebay", "shop", "vinted", "zalando", "lieferando", "food", "wolt", "aliexpress",
        "temu", "kleinanzeigen", "otto", "lidl", "aldi", "rewe", "mcdonald", "burgerking", "store",
    ),
    LibraryCategory.Travel to listOf(
        "maps", "karten", "uber", "bolt", "bahn", "dbnavigator", "booking", "airbnb", "flight", "navi",
        "waze", "flix", "lufthansa", "ryanair", "transit", "öpnv",
    ),
    LibraryCategory.Health to listOf(
        "fit", "health", "gesundheit", "sleep", "schlaf", "strava", "workout", "runtastic", "yoga",
        "meditat", "calm", "headspace",
    ),
    LibraryCategory.Utilities to listOf(
        "settings", "einstellungen", "clock", "uhr", "calculator", "rechner", "files", "dateien",
        "manager", "vpn", "scanner", "recorder", "compass", "flashlight", "browser", "chrome", "firefox",
        "opera", "brave", "launcher", "authenticator", "security", "backup", "cleaner", "tool",
    ),
)

/**
 * Picks the App Library folder for an app: the category the app declares,
 * otherwise a guess from its name, otherwise "Utilities" for system apps.
 */
fun categorize(appInfo: ApplicationInfo?, packageName: String, label: String): LibraryCategory {
    when (appInfo?.category) {
        ApplicationInfo.CATEGORY_GAME -> return LibraryCategory.Games
        ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> return LibraryCategory.Entertainment
        ApplicationInfo.CATEGORY_IMAGE -> return LibraryCategory.Creativity
        ApplicationInfo.CATEGORY_SOCIAL -> return LibraryCategory.Social
        ApplicationInfo.CATEGORY_NEWS -> return LibraryCategory.Information
        ApplicationInfo.CATEGORY_MAPS -> return LibraryCategory.Travel
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> return LibraryCategory.Productivity
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> return LibraryCategory.Utilities
        else -> Unit
    }
    val haystack = (packageName + " " + label).lowercase()
    Keywords.firstOrNull { (_, words) -> words.any { it in haystack } }?.let { return it.first }
    val isSystem = appInfo != null && appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
    return if (isSystem) LibraryCategory.Utilities else LibraryCategory.Other
}
