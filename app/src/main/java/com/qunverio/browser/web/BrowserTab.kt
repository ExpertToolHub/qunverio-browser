package com.qunverio.browser.web

import android.os.Bundle
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * One browser tab. The [webView] only exists while the tab is "live": background
 * tabs beyond the live limit are saved into [savedState] and their WebView destroyed.
 */
class BrowserTab(val id: Long, var pendingUrl: String?) {
    val state = BrowserState()
    var webView: WebView? = null
    var savedState: Bundle? = null
    var lastActive: Long = System.currentTimeMillis()
    var mobileUserAgent: String = ""

    /** Bumped to force the UI to attach a freshly created WebView. */
    var generation by mutableIntStateOf(0)
}
