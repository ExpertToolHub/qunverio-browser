package com.qunverio.browser.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.qunverio.browser.adblock.registrableDomain
import com.qunverio.browser.navigation.UrlInputParser

data class SearchEngine(val id: String, val name: String, val template: String)

val SEARCH_ENGINES = listOf(
    SearchEngine("google", "Google", "https://www.google.com/search?q=%s"),
    SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    SearchEngine("bing", "Bing", "https://www.bing.com/search?q=%s"),
    SearchEngine("brave", "Brave Search", "https://search.brave.com/search?q=%s"),
    SearchEngine("ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s"),
)

/** Persistent, Compose-observable preferences. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("qunverio_settings", Context.MODE_PRIVATE)

    var searchEngineId by mutableStateOf(prefs.getString(K_SEARCH, "google") ?: "google")
        private set
    var themeMode by mutableStateOf(prefs.getString(K_THEME, "system") ?: "system")
        private set
    var textZoom by mutableIntStateOf(prefs.getInt(K_ZOOM, 100))
        private set
    var desktopSiteDefault by mutableStateOf(prefs.getBoolean(K_DESKTOP, false))
        private set
    var adBlockEnabled by mutableStateOf(prefs.getBoolean(K_ADBLOCK, true))
        private set
    var adBlockAllowedHosts by mutableStateOf<Set<String>>(
        prefs.getStringSet(K_ALLOWED, emptySet())?.toSet() ?: emptySet(),
    )
        private set
    var blockThirdPartyCookies by mutableStateOf(prefs.getBoolean(K_3P_COOKIES, true))
        private set
    var javascriptEnabled by mutableStateOf(prefs.getBoolean(K_JS, true))
        private set
    var saveHistory by mutableStateOf(prefs.getBoolean(K_HISTORY, true))
        private set
    var filterUpdatedAt by mutableLongStateOf(prefs.getLong(K_FILTER_TIME, 0L))
        private set
    var filterRuleCount by mutableIntStateOf(prefs.getInt(K_FILTER_COUNT, 0))
        private set

    val searchTemplate: String
        get() = SEARCH_ENGINES.firstOrNull { it.id == searchEngineId }?.template
            ?: UrlInputParser.GOOGLE_SEARCH

    val searchEngineName: String
        get() = SEARCH_ENGINES.firstOrNull { it.id == searchEngineId }?.name ?: "Google"

    fun setSearchEngine(id: String) { searchEngineId = id; prefs.edit().putString(K_SEARCH, id).apply() }
    fun setThemeMode(mode: String) { themeMode = mode; prefs.edit().putString(K_THEME, mode).apply() }
    fun setTextZoom(zoom: Int) { textZoom = zoom; prefs.edit().putInt(K_ZOOM, zoom).apply() }
    fun setDesktopSiteDefault(v: Boolean) { desktopSiteDefault = v; prefs.edit().putBoolean(K_DESKTOP, v).apply() }
    fun setAdBlockEnabled(v: Boolean) { adBlockEnabled = v; prefs.edit().putBoolean(K_ADBLOCK, v).apply() }
    fun setBlockThirdPartyCookies(v: Boolean) { blockThirdPartyCookies = v; prefs.edit().putBoolean(K_3P_COOKIES, v).apply() }
    fun setJavascriptEnabled(v: Boolean) { javascriptEnabled = v; prefs.edit().putBoolean(K_JS, v).apply() }
    fun setSaveHistory(v: Boolean) { saveHistory = v; prefs.edit().putBoolean(K_HISTORY, v).apply() }
    fun setFilterUpdatedAt(t: Long) { filterUpdatedAt = t; prefs.edit().putLong(K_FILTER_TIME, t).apply() }
    fun setFilterRuleCount(n: Int) { filterRuleCount = n; prefs.edit().putInt(K_FILTER_COUNT, n).apply() }

    /** Per-site switch. Sites are stored by registrable domain (example.com covers www.example.com). */
    fun setSiteAdBlock(host: String, enabled: Boolean) {
        val key = registrableDomain(host)
        val next = if (enabled) adBlockAllowedHosts - key else adBlockAllowedHosts + key
        adBlockAllowedHosts = next
        prefs.edit().putStringSet(K_ALLOWED, next).apply()
    }

    private companion object {
        const val K_SEARCH = "search_engine"
        const val K_THEME = "theme"
        const val K_ZOOM = "text_zoom"
        const val K_DESKTOP = "desktop_default"
        const val K_ADBLOCK = "adblock"
        const val K_ALLOWED = "adblock_allowed_hosts"
        const val K_3P_COOKIES = "block_3p_cookies"
        const val K_JS = "javascript"
        const val K_HISTORY = "save_history"
        const val K_FILTER_TIME = "filter_updated_at"
        const val K_FILTER_COUNT = "filter_rule_count"
    }
}
