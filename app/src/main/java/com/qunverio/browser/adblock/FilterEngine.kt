package com.qunverio.browser.adblock

/**
 * Domain-based request blocker. Thread-safe: rules are swapped atomically and
 * [shouldBlock] is called from WebView's background request threads.
 *
 * Deliberately conservative to avoid breaking sites:
 * - never blocks requests to the same site as the page (first-party)
 * - only blocks by host (path/regex rules are not supported)
 */
class FilterEngine {
    @Volatile private var block: Set<String> = emptySet()
    @Volatile private var allow: Set<String> = emptySet()

    @Volatile var ruleCount: Int = 0
        private set

    fun replaceRules(block: Set<String>, allow: Set<String>) {
        this.block = block
        this.allow = allow
        ruleCount = block.size
    }

    fun shouldBlock(requestHost: String, pageHost: String?): Boolean {
        if (requestHost.isEmpty()) return false
        if (pageHost != null && isSameSite(requestHost, pageHost)) return false
        if (hostInSet(allow, requestHost)) return false
        return hostInSet(block, requestHost)
    }
}
