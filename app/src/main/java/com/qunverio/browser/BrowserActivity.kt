package com.qunverio.browser

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.qunverio.browser.navigation.ParsedInput
import com.qunverio.browser.navigation.UrlInputParser
import com.qunverio.browser.ui.BrowserScreen
import com.qunverio.browser.ui.QunverioTheme
import com.qunverio.browser.web.BrowserChromeClient
import com.qunverio.browser.web.BrowserState
import com.qunverio.browser.web.BrowserWebViewClient
import com.qunverio.browser.web.securityFor

class BrowserActivity : ComponentActivity() {

    private val state = BrowserState()
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        webView = createWebView()
        restore(savedInstanceState)

        setContent {
            QunverioTheme {
                BrowserScreen(
                    state = state,
                    webView = webView,
                    onSubmit = ::submit,
                    onBack = { if (!state.rendererGone && webView.canGoBack()) webView.goBack() },
                    onForward = { if (!state.rendererGone && webView.canGoForward()) webView.goForward() },
                    onReloadOrStop = ::reloadOrStop,
                    onRetry = ::retry,
                    onConfirmExternal = ::openExternal,
                    onDismissExternal = { state.pendingExternalUrl = null },
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        return WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.apply {
                javaScriptEnabled = true
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
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = BrowserWebViewClient(state)
            webChromeClient = BrowserChromeClient(state)
        }
    }

    private fun restore(saved: Bundle?) {
        if (saved == null) return
        val url = saved.getString(KEY_URL)
        state.pageUrl = url
        state.security = url?.let { securityFor(it) } ?: state.security
        val webState = saved.getBundle(KEY_WEB)
        val restored = webState != null && webView.restoreState(webState) != null
        if (!restored && url != null) webView.loadUrl(url)
    }

    private fun submit(raw: String) {
        when (val result = UrlInputParser.parse(raw)) {
            is ParsedInput.Navigate -> navigateTo(result.url)
            is ParsedInput.Search -> navigateTo(result.url)
            is ParsedInput.Rejected -> state.message = result.reason
        }
    }

    private fun navigateTo(url: String) {
        state.pendingExternalUrl = null
        state.pageUrl = url
        state.security = securityFor(url)
        if (state.rendererGone) {
            recreate() // the old WebView is unusable after its renderer died
            return
        }
        state.errorMessage = null
        webView.loadUrl(url)
    }

    private fun reloadOrStop() {
        if (state.rendererGone) return
        when {
            state.isLoading -> {
                webView.stopLoading()
                state.isLoading = false
            }
            state.errorMessage != null -> retry()
            state.pageUrl != null -> webView.reload()
        }
    }

    private fun retry() {
        if (state.rendererGone) {
            recreate()
            return
        }
        val url = state.pageUrl ?: return
        state.errorMessage = null
        webView.loadUrl(url)
    }

    private fun openExternal(url: String) {
        state.pendingExternalUrl = null
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            state.message = "No app found to open this link."
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        state.pageUrl?.let { outState.putString(KEY_URL, it) }
        if (!state.rendererGone) {
            val web = Bundle()
            webView.saveState(web)
            outState.putBundle(KEY_WEB, web)
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    private companion object {
        const val KEY_WEB = "web_state"
        const val KEY_URL = "page_url"
    }
}
