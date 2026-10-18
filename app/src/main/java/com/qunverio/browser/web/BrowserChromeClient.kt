package com.qunverio.browser.web

import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Progress only. Permission prompts (camera, mic, location) are intentionally NOT
 * implemented yet, so WebView's defaults apply and nothing is granted silently.
 */
class BrowserChromeClient(private val state: BrowserState) : WebChromeClient() {
    override fun onProgressChanged(view: WebView, newProgress: Int) {
        state.progress = newProgress
        state.isLoading = newProgress < 100
    }
}
