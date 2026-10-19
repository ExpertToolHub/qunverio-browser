package com.qunverio.browser.web

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class SecurityState { NONE, SECURE, INSECURE, ERROR }

fun securityFor(url: String): SecurityState = when {
    url.startsWith("https://", ignoreCase = true) -> SecurityState.SECURE
    url.startsWith("http://", ignoreCase = true) -> SecurityState.INSECURE
    else -> SecurityState.NONE
}

fun hostOf(url: String?): String? = if (url == null) null else Uri.parse(url).host?.lowercase()

/** UI-observable state of one tab. */
class BrowserState {
    var pageUrl by mutableStateOf<String?>(null)
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var isLoading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var security by mutableStateOf(SecurityState.NONE)
    var errorMessage by mutableStateOf<String?>(null)
    var rendererGone by mutableStateOf(false)
    var pendingExternalUrl by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var desktopSite by mutableStateOf(false)
    var blockedCount by mutableIntStateOf(0)
}
