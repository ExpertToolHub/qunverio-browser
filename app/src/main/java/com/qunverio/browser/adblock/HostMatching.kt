package com.qunverio.browser.adblock

/** True if [host] or any parent domain of it (but never a bare TLD) is in [set]. */
fun hostInSet(set: Set<String>, host: String): Boolean {
    var h = host
    while (true) {
        if (set.contains(h)) return true
        val dot = h.indexOf('.')
        if (dot < 0) return false
        h = h.substring(dot + 1)
        if (!h.contains('.')) return false
    }
}

private val TWO_LEVEL_SUFFIXES = setOf(
    "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au",
    "co.in", "net.in", "org.in", "ac.in", "gov.in", "co.jp", "com.br", "com.cn",
    "co.nz", "co.za", "com.mx", "com.tr", "com.sg", "com.hk", "co.kr", "com.ar",
)

/** Approximation of "example.com" for "a.b.example.com" (small built-in public-suffix subset). */
fun registrableDomain(host: String): String {
    if (host.all { it.isDigit() || it == '.' }) return host // IPv4
    val labels = host.split('.')
    if (labels.size <= 2) return host
    val last2 = labels.takeLast(2).joinToString(".")
    return if (last2 in TWO_LEVEL_SUFFIXES) labels.takeLast(3).joinToString(".") else last2
}

fun isSameSite(a: String, b: String): Boolean = registrableDomain(a) == registrableDomain(b)
