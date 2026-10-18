package com.qunverio.browser.web

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

/** UI-observable state of the single browser view (Milestone 1: one page, no tabs yet). */
class BrowserState {
    var pageUrl by mutableStateOf<String?>(null)
    var progress by mutableIntStateOf(0)
    var isLoading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var security by mutableStateOf(SecurityState.NONE)
    var errorMessage by mutableStateOf<String?>(null)
    var rendererGone by mutableStateOf(false)
    var pendingExternalUrl by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
}
