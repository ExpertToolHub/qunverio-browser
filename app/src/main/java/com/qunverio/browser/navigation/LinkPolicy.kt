package com.qunverio.browser.navigation

enum class LinkDecision { LOAD_IN_WEBVIEW, ASK_EXTERNAL, BLOCK }

/**
 * Decides what to do with a navigation that a web page requests.
 * - http/https load inside the browser.
 * - A small allow-list of schemes may open another app, but only after the user
 *   confirms, and only if the navigation came from a real user gesture.
 * - Everything else (javascript:, file:, content:, data:, intent:, market: ...) is blocked.
 */
object LinkPolicy {
    private val EXTERNAL_ALLOWED = setOf("tel", "mailto", "sms", "smsto", "geo")

    fun decide(scheme: String?, url: String, hasGesture: Boolean): LinkDecision {
        val s = scheme?.lowercase() ?: return LinkDecision.BLOCK
        return when {
            s == "http" || s == "https" -> LinkDecision.LOAD_IN_WEBVIEW
            s == "about" && url.equals("about:blank", ignoreCase = true) -> LinkDecision.LOAD_IN_WEBVIEW
            s in EXTERNAL_ALLOWED -> if (hasGesture) LinkDecision.ASK_EXTERNAL else LinkDecision.BLOCK
            else -> LinkDecision.BLOCK
        }
    }
}
