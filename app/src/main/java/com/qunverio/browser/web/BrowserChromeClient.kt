package com.qunverio.browser.web

import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Progress, titles and new-window (target=_blank / window.open) handling.
 * Permission prompts (camera, mic, location) are intentionally NOT implemented yet,
 * so WebView's defaults apply and nothing is granted silently.
 */
class BrowserChromeClient(
    private val state: BrowserState,
    private val onNewWindow: (WebView.WebViewTransport) -> Boolean,
    private val onClose: () -> Unit,
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        state.progress = newProgress
        state.isLoading = newProgress < 100
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        if (!title.isNullOrBlank()) state.title = title
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean {
        if (!isUserGesture) return false // no pop-ups without a tap
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        if (!onNewWindow(transport)) return false
        resultMsg.sendToTarget()
        return true
    }

    override fun onCloseWindow(window: WebView) {
        onClose()
    }
}
