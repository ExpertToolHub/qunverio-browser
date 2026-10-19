package com.qunverio.browser

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.qunverio.browser.adblock.FilterEngine
import com.qunverio.browser.adblock.FilterManager
import com.qunverio.browser.adblock.hostInSet
import com.qunverio.browser.data.BookmarkStore
import com.qunverio.browser.data.HistoryStore
import com.qunverio.browser.data.SettingsStore
import com.qunverio.browser.navigation.ParsedInput
import com.qunverio.browser.navigation.UrlInputParser
import com.qunverio.browser.ui.BrowserActions
import com.qunverio.browser.ui.BrowserScreen
import com.qunverio.browser.ui.LibraryScreen
import com.qunverio.browser.ui.QunverioTheme
import com.qunverio.browser.ui.SettingsScreen
import com.qunverio.browser.ui.TabSwitcherScreen
import com.qunverio.browser.web.SecurityState
import com.qunverio.browser.web.TabManager
import com.qunverio.browser.web.hostOf
import com.qunverio.browser.web.securityFor
import java.io.File

private enum class Screen { BROWSER, TABS, SETTINGS, LIBRARY }

class BrowserActivity : ComponentActivity() {

    private lateinit var settings: SettingsStore
    private lateinit var engine: FilterEngine
    private lateinit var filters: FilterManager
    private lateinit var history: HistoryStore
    private lateinit var bookmarks: BookmarkStore
    private lateinit var manager: TabManager

    private var screen by mutableStateOf(Screen.BROWSER)
    private var libraryTab by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settings = SettingsStore(this)
        engine = FilterEngine()
        filters = FilterManager(this, engine, settings)
        history = HistoryStore(File(filesDir, "history.json"))
        bookmarks = BookmarkStore(File(filesDir, "bookmarks.json"))
        manager = TabManager(this, settings, engine, history)

        filters.loadAsync()
        filters.updateIfStale()
        manager.restoreOrCreate()

        val actions = BrowserActions(
            onSubmit = ::submit,
            onBack = ::goBack,
            onForward = {
                val tab = manager.current
                val wv = tab?.webView
                if (tab != null && wv != null && !tab.state.rendererGone && wv.canGoForward()) wv.goForward()
            },
            onHome = ::goHome,
            onReloadOrStop = ::reloadOrStop,
            onRetry = ::retry,
            onShare = ::shareLink,
            onCopy = ::copyLink,
            onToggleDesktop = {
                manager.current?.let { manager.setDesktop(it, !it.state.desktopSite) }
            },
            onToggleSiteAdBlock = ::toggleSiteAdBlock,
            onToggleBookmark = {
                val state = manager.current?.state
                val url = state?.pageUrl
                if (state != null && url != null) {
                    val wasBookmarked = bookmarks.contains(url)
                    bookmarks.toggle(url, state.title)
                    state.message = if (wasBookmarked) "Bookmark removed" else "Bookmark added"
                }
            },
            onNewTab = { manager.newTab(null) },
            onOpenTabs = { screen = Screen.TABS },
            onOpenSettings = { screen = Screen.SETTINGS },
            onOpenBookmarks = { libraryTab = 0; screen = Screen.LIBRARY },
            onOpenHistory = { libraryTab = 1; screen = Screen.LIBRARY },
            onConfirmExternal = ::openExternal,
            onDismissExternal = { manager.current?.state?.pendingExternalUrl = null },
        )

        setContent {
            val dark = when (settings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
                onDispose {}
            }

            QunverioTheme(darkTheme = dark) {
                when (screen) {
                    Screen.BROWSER -> BrowserScreen(
                        manager = manager,
                        settings = settings,
                        bookmarks = bookmarks,
                        actions = actions,
                    )
                    Screen.TABS -> {
                        BackHandler { screen = Screen.BROWSER }
                        TabSwitcherScreen(
                            manager = manager,
                            onSelect = { id -> manager.select(id); screen = Screen.BROWSER },
                            onClose = { id -> manager.close(id) },
                            onNewTab = { manager.newTab(null); screen = Screen.BROWSER },
                            onCloseAll = { manager.closeAll(); screen = Screen.BROWSER },
                            onBack = { screen = Screen.BROWSER },
                        )
                    }
                    Screen.SETTINGS -> {
                        BackHandler { screen = Screen.BROWSER }
                        SettingsScreen(
                            settings = settings,
                            filters = filters,
                            onBack = { screen = Screen.BROWSER },
                            onChanged = { manager.applyAll() },
                            onClearData = ::clearBrowsingData,
                        )
                    }
                    Screen.LIBRARY -> {
                        BackHandler { screen = Screen.BROWSER }
                        LibraryScreen(
                            initialTab = libraryTab,
                            bookmarks = bookmarks,
                            history = history,
                            onOpen = { url -> navigateTo(url); screen = Screen.BROWSER },
                            onBack = { screen = Screen.BROWSER },
                        )
                    }
                }
            }
        }
    }

    // ---- navigation ----
    private fun submit(raw: String) {
        when (val result = UrlInputParser.parse(raw, settings.searchTemplate)) {
            is ParsedInput.Navigate -> navigateTo(result.url)
            is ParsedInput.Search -> navigateTo(result.url)
            is ParsedInput.Rejected -> manager.current?.state?.message = result.reason
        }
    }

    private fun navigateTo(url: String) {
        val tab = manager.current ?: return
        val state = tab.state
        state.pendingExternalUrl = null
        state.pageUrl = url
        state.security = securityFor(url)
        tab.pendingUrl = null
        tab.savedState = null
        if (state.rendererGone) {
            manager.reviveTab(tab) // rebuilds the WebView and loads state.pageUrl
            return
        }
        state.errorMessage = null
        manager.ensureWebView(tab).loadUrl(url)
    }

    private fun goBack() {
        val tab = manager.current ?: return
        val wv = tab.webView
        if (wv != null && !tab.state.rendererGone && wv.canGoBack()) {
            wv.goBack()
        } else if (manager.tabs.size > 1) {
            manager.close(tab.id)
        }
    }

    private fun goHome() {
        val tab = manager.current ?: return
        val state = tab.state
        if (!state.rendererGone) tab.webView?.stopLoading()
        state.isLoading = false
        state.errorMessage = null
        state.pageUrl = null
        state.security = SecurityState.NONE
    }

    private fun reloadOrStop() {
        val tab = manager.current ?: return
        val state = tab.state
        if (state.rendererGone) return
        when {
            state.isLoading -> {
                tab.webView?.stopLoading()
                state.isLoading = false
            }
            state.errorMessage != null -> retry()
            state.pageUrl != null -> tab.webView?.reload()
        }
    }

    private fun retry() {
        val tab = manager.current ?: return
        val state = tab.state
        if (state.rendererGone) {
            manager.reviveTab(tab)
            return
        }
        val url = state.pageUrl ?: return
        state.errorMessage = null
        manager.ensureWebView(tab).loadUrl(url)
    }

    // ---- menu actions ----
    private fun toggleSiteAdBlock() {
        val tab = manager.current ?: return
        val host = hostOf(tab.state.pageUrl) ?: return
        val currentlyBlocking = !hostInSet(settings.adBlockAllowedHosts, host)
        settings.setSiteAdBlock(host, enabled = !currentlyBlocking)
        if (!tab.state.rendererGone) tab.webView?.reload()
    }

    private fun shareLink() {
        val url = manager.current?.state?.pageUrl ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(send, "Share link"))
    }

    private fun copyLink() {
        val state = manager.current?.state ?: return
        val url = state.pageUrl ?: return
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Link", url))
        state.message = "Link copied"
    }

    private fun openExternal(url: String) {
        val state = manager.current?.state ?: return
        state.pendingExternalUrl = null
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            state.message = "No app found to open this link."
        }
    }

    private fun clearBrowsingData() {
        history.clear()
        manager.clearCaches()
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies(null)
        cookies.flush()
        WebStorage.getInstance().deleteAllData()
        manager.current?.state?.message = "Browsing data cleared"
    }

    // ---- lifecycle ----
    override fun onResume() {
        super.onResume()
        manager.current?.webView?.onResume()
    }

    override fun onPause() {
        manager.current?.webView?.onPause()
        manager.saveTabs()
        super.onPause()
    }

    override fun onDestroy() {
        manager.destroyAll()
        super.onDestroy()
    }
}
