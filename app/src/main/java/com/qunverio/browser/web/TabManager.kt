package com.qunverio.browser.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.qunverio.browser.adblock.FilterEngine
import com.qunverio.browser.data.HistoryStore
import com.qunverio.browser.data.SettingsStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Owns all tabs. To keep memory and battery use low, at most [MAX_LIVE] tabs keep a
 * running WebView; older background tabs are saved and rebuilt when selected.
 */
class TabManager(
    private val activity: Activity,
    private val settings: SettingsStore,
    private val engine: FilterEngine,
    private val history: HistoryStore,
) {
    val tabs = mutableStateListOf<BrowserTab>()

    var currentId by mutableLongStateOf(-1L)
        private set

    val current: BrowserTab?
        get() = tabs.firstOrNull { it.id == currentId }

    private var nextId = 1L
    private val prefs = activity.getSharedPreferences("qunverio_tabs", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())

    // ---- tab list ----
    fun newTab(url: String?, select: Boolean = true): BrowserTab {
        val tab = BrowserTab(nextId++, url)
        tab.state.desktopSite = settings.desktopSiteDefault
        if (url != null) {
            tab.state.pageUrl = url
            tab.state.security = securityFor(url)
        }
        tabs.add(tab)
        if (select) select(tab.id)
        return tab
    }

    fun select(id: Long) {
        val target = tabs.firstOrNull { it.id == id } ?: return
        val prev = current
        if (prev != null && prev.id != id) prev.webView?.onPause()
        currentId = id
        target.lastActive = System.currentTimeMillis()
        target.webView?.onResume()
        evictOldTabs()
    }

    fun close(id: Long) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        destroyWebView(tabs[index])
        tabs.removeAt(index)
        if (tabs.isEmpty()) {
            newTab(null)
        } else if (id == currentId) {
            select(tabs[minOf(index, tabs.lastIndex)].id)
        }
    }

    fun closeAll() {
        tabs.forEach { destroyWebView(it) }
        tabs.clear()
        newTab(null)
    }

    fun destroyAll() {
        tabs.forEach { destroyWebView(it) }
    }

    // ---- WebView lifecycle ----
    @SuppressLint("SetJavaScriptEnabled")
    fun ensureWebView(tab: BrowserTab): WebView {
        tab.webView?.let { return it }
        if (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        val wv = WebView(activity)
        wv.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        wv.settings.apply {
            domStorageEnabled = true
            // No local file access, no JavaScript bridges.
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
        }
        wv.webViewClient = BrowserWebViewClient(tab.state, engine, settings) { url, title ->
            if (settings.saveHistory) history.add(url, title)
        }
        wv.webChromeClient = BrowserChromeClient(
            state = tab.state,
            onNewWindow = ::openWindow,
            onClose = { closeByWebView(wv) },
        )
        tab.mobileUserAgent = wv.settings.userAgentString
        tab.webView = wv
        applySettings(tab)
        tab.state.isLoading = false

        val saved = tab.savedState
        val pending = tab.pendingUrl
        tab.savedState = null
        tab.pendingUrl = null
        if (saved != null) {
            if (wv.restoreState(saved) == null) tab.state.pageUrl?.let { wv.loadUrl(it) }
        } else if (pending != null) {
            wv.loadUrl(pending)
        }
        return wv
    }

    /** Returns the tab's WebView detached from any old parent, ready to be shown. */
    fun viewFor(tab: BrowserTab): WebView {
        val wv = ensureWebView(tab)
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.onResume()
        return wv
    }

    private fun destroyWebView(tab: BrowserTab) {
        tab.webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        tab.webView = null
    }

    private fun evictOldTabs() {
        val live = tabs.filter { it.webView != null }
        if (live.size <= MAX_LIVE) return
        live.filter { it.id != currentId }
            .sortedBy { it.lastActive }
            .take(live.size - MAX_LIVE)
            .forEach { evict(it) }
    }

    private fun evict(tab: BrowserTab) {
        val wv = tab.webView ?: return
        val bundle = Bundle()
        wv.saveState(bundle)
        tab.savedState = bundle
        destroyWebView(tab)
        tab.state.isLoading = false
    }

    /** After the renderer process died the old WebView is unusable: rebuild it. */
    fun reviveTab(tab: BrowserTab) {
        val url = tab.state.pageUrl
        destroyWebView(tab)
        tab.savedState = null
        tab.pendingUrl = url
        tab.state.rendererGone = false
        tab.state.errorMessage = null
        tab.generation++
    }

    private fun openWindow(transport: WebView.WebViewTransport): Boolean {
        if (tabs.size >= MAX_TABS) return false
        val tab = newTab(null, select = true)
        transport.webView = ensureWebView(tab)
        return true
    }

    private fun closeByWebView(wv: WebView) {
        // Closing from inside the WebView's own callback is unsafe; defer it.
        main.post {
            tabs.firstOrNull { it.webView === wv }?.let { close(it.id) }
        }
    }

    // ---- settings ----
    @SuppressLint("SetJavaScriptEnabled")
    fun applySettings(tab: BrowserTab) {
        val wv = tab.webView ?: return
        wv.settings.javaScriptEnabled = settings.javascriptEnabled
        wv.settings.textZoom = settings.textZoom
        wv.settings.userAgentString =
            if (tab.state.desktopSite) desktopUserAgent(tab.mobileUserAgent) else tab.mobileUserAgent
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, !settings.blockThirdPartyCookies)
    }

    fun applyAll() {
        tabs.forEach { applySettings(it) }
    }

    fun setDesktop(tab: BrowserTab, enabled: Boolean) {
        tab.state.desktopSite = enabled
        applySettings(tab)
        if (tab.webView != null && !tab.state.rendererGone && tab.state.pageUrl != null &&
            tab.state.errorMessage == null
        ) {
            tab.webView?.reload()
        }
    }

    fun clearCaches() {
        tabs.forEach { it.webView?.clearCache(true) }
    }

    private fun desktopUserAgent(mobile: String): String {
        val chrome = Regex("Chrome/([\\d.]+)").find(mobile)?.groupValues?.get(1) ?: "120.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
    }

    // ---- persistence (URLs and titles only; page state is not stored) ----
    fun saveTabs() {
        val arr = JSONArray()
        tabs.take(MAX_TABS).forEach { t ->
            arr.put(JSONObject().put("u", t.state.pageUrl ?: "").put("t", t.state.title))
        }
        val index = tabs.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        prefs.edit().putString("tabs", arr.toString()).putInt("current", index).apply()
    }

    fun restoreOrCreate() {
        var restored = false
        val json = prefs.getString("tabs", null)
        if (json != null) {
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val url = o.optString("u").ifBlank { null }
                    val tab = newTab(url, select = false)
                    tab.state.title = o.optString("t")
                }
                restored = tabs.isNotEmpty()
            } catch (e: Exception) {
                tabs.clear()
            }
        }
        if (!restored) {
            newTab(null)
        } else {
            val index = prefs.getInt("current", 0).coerceIn(0, tabs.lastIndex)
            select(tabs[index].id)
        }
    }

    private companion object {
        const val MAX_LIVE = 3
        const val MAX_TABS = 30
    }
}
