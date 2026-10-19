package com.qunverio.browser.web

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.qunverio.browser.adblock.FilterEngine
import com.qunverio.browser.adblock.hostInSet
import com.qunverio.browser.data.SettingsStore
import com.qunverio.browser.navigation.LinkDecision
import com.qunverio.browser.navigation.LinkPolicy
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BrowserWebViewClient(
    private val state: BrowserState,
    private val engine: FilterEngine,
    private val settings: SettingsStore,
    private val onVisited: (url: String, title: String) -> Unit,
) : WebViewClient() {

    // Written from request threads and the UI thread, so it must be volatile.
    @Volatile private var pageHost: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingBlocked = AtomicInteger(0)
    private val flushScheduled = AtomicBoolean(false)

    // ---- ad / tracker blocking (runs on WebView background threads) ----
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val host = request.url.host?.lowercase() ?: return null
        if (request.isForMainFrame) {
            pageHost = host
            return null // navigations are never blocked
        }
        if (!settings.adBlockEnabled) return null
        val page = pageHost
        if (page != null && hostInSet(settings.adBlockAllowedHosts, page)) return null
        if (engine.shouldBlock(host, page)) {
            recordBlocked()
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }
        return null
    }

    private fun recordBlocked() {
        pendingBlocked.incrementAndGet()
        if (flushScheduled.compareAndSet(false, true)) {
            mainHandler.postDelayed({
                flushScheduled.set(false)
                state.blockedCount += pendingBlocked.getAndSet(0)
            }, 300)
        }
    }

    // ---- navigation policy ----
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        return when (LinkPolicy.decide(uri.scheme, uri.toString(), request.hasGesture())) {
            LinkDecision.LOAD_IN_WEBVIEW -> false
            LinkDecision.ASK_EXTERNAL -> {
                state.pendingExternalUrl = uri.toString()
                true
            }
            LinkDecision.BLOCK -> {
                if (request.isForMainFrame) {
                    state.message = "Blocked a link that tried to open another app or use an unsupported address."
                }
                true
            }
        }
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        state.errorMessage = null
        state.isLoading = true
        state.blockedCount = 0
        url?.let {
            pageHost = hostOf(it)
            updateUrl(it)
        }
        syncHistory(view)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        if (state.errorMessage == null) url?.let { updateUrl(it) }
        syncHistory(view)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        state.isLoading = false
        if (state.errorMessage == null) {
            url?.let { updateUrl(it) }
            val title = view.title.orEmpty()
            if (title.isNotBlank()) state.title = title
            if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                onVisited(url, title)
            }
        }
        syncHistory(view)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        state.isLoading = false
        state.pageUrl = request.url.toString()
        state.security = SecurityState.NONE
        state.errorMessage = describeError(error.errorCode)
    }

    /** Certificate problems are never bypassed: the load is always cancelled. */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        val failedUrl = error.url
        val isMainFrame = failedUrl == view.url || failedUrl == state.pageUrl
        if (!isMainFrame) return
        state.isLoading = false
        state.pageUrl = failedUrl
        state.security = SecurityState.ERROR
        val reason = when (error.primaryError) {
            SslError.SSL_EXPIRED -> "The site's security certificate has expired."
            SslError.SSL_IDMISMATCH -> "The certificate doesn't match this site's address."
            SslError.SSL_NOTYETVALID -> "The site's security certificate is not valid yet."
            SslError.SSL_UNTRUSTED -> "The certificate was not issued by a trusted authority."
            SslError.SSL_DATE_INVALID -> "The certificate's date is invalid."
            else -> "The site's security certificate is invalid."
        }
        state.errorMessage = "$reason Qunverio blocked this connection to protect your data."
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        state.rendererGone = true
        state.isLoading = false
        state.errorMessage = if (detail.didCrash()) {
            "The page crashed."
        } else {
            "Android closed this page to free up memory."
        }
        return true
    }

    private fun updateUrl(url: String) {
        state.pageUrl = url
        state.security = securityFor(url)
    }

    private fun syncHistory(view: WebView) {
        state.canGoBack = view.canGoBack()
        state.canGoForward = view.canGoForward()
    }

    private fun describeError(code: Int): String = when (code) {
        ERROR_HOST_LOOKUP -> "Can't find this site. Check the address and your internet connection."
        ERROR_CONNECT, ERROR_TIMEOUT, ERROR_IO ->
            "The connection failed or timed out. Check your internet connection and try again."
        ERROR_FAILED_SSL_HANDSHAKE -> "A secure connection could not be established."
        ERROR_REDIRECT_LOOP -> "This page redirects too many times."
        ERROR_BAD_URL -> "That address isn't valid."
        else -> "This page could not be loaded (error $code)."
    }
}
