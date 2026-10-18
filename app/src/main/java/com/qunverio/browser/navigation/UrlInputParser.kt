package com.qunverio.browser.navigation

import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder

/** Result of interpreting whatever the user typed into the address bar. */
sealed interface ParsedInput {
    data class Navigate(val url: String) : ParsedInput
    data class Search(val url: String) : ParsedInput
    data class Rejected(val reason: String) : ParsedInput
}

/**
 * Turns raw address-bar text into a safe action.
 * Only http/https may ever be loaded from typed input. Everything else with a
 * scheme (javascript:, file:, content:, data:, intent: ...) is rejected.
 */
object UrlInputParser {

    const val GOOGLE_SEARCH = "https://www.google.com/search?q=%s"

    private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.\\-]*):(.*)$", RegexOption.DOT_MATCHES_ALL)
    private val PORT_ONLY = Regex("^\\d{1,5}(?:[/?#].*)?$")
    private const val LABEL = "[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]*[\\p{L}\\p{N}])?"
    private val HOSTNAME = Regex("^$LABEL(?:\\.$LABEL)+$")

    fun parse(raw: String, searchTemplate: String = GOOGLE_SEARCH): ParsedInput {
        val input = raw.trim()
        if (input.isEmpty()) return ParsedInput.Rejected("Enter a web address or a search term.")

        val hasWhitespace = input.any { it.isWhitespace() }
        val match = SCHEME.matchEntire(input)

        if (match != null) {
            val scheme = match.groupValues[1].lowercase()
            val rest = match.groupValues[2]
            val looksLikeHostAndPort = PORT_ONLY.matches(rest) // e.g. "localhost:8080"
            if (!looksLikeHostAndPort) {
                val isWeb = scheme == "http" || scheme == "https"
                return if (!isWeb && hasWhitespace) {
                    search(input, searchTemplate) // e.g. "note:buy milk" is just words
                } else {
                    parseWithScheme(scheme, rest)
                }
            }
        }

        if (hasWhitespace) return search(input, searchTemplate)

        val hostUrl = asHostUrl(input)
        return if (hostUrl != null) ParsedInput.Navigate(hostUrl) else search(input, searchTemplate)
    }

    private fun parseWithScheme(scheme: String, rest: String): ParsedInput {
        if (scheme != "http" && scheme != "https") {
            return ParsedInput.Rejected("Unsupported address type: $scheme:")
        }
        val normalized = "$scheme:$rest"
        val uri = try {
            URI(normalized)
        } catch (e: URISyntaxException) {
            return ParsedInput.Rejected("That address isn't valid.")
        }
        if (uri.host.isNullOrEmpty()) return ParsedInput.Rejected("That address isn't valid.")
        if (uri.userInfo != null) {
            return ParsedInput.Rejected("Addresses with embedded usernames or passwords are blocked.")
        }
        return ParsedInput.Navigate(normalized)
    }

    /** Returns a full URL if [input] (no whitespace, no scheme) looks like a host, else null. */
    private fun asHostUrl(input: String): String? {
        val end = input.indexOfFirst { it == '/' || it == '?' || it == '#' }
            .let { if (it < 0) input.length else it }
        val authority = input.substring(0, end)
        if (authority.isEmpty() || authority.contains('@')) return null

        var host = authority
        val colon = authority.lastIndexOf(':')
        if (colon >= 0) {
            val port = authority.substring(colon + 1)
            val portNumber = port.toIntOrNull()
            if (port.isEmpty() || port.length > 5 || portNumber == null || portNumber !in 1..65535) return null
            host = authority.substring(0, colon)
        }

        return when {
            host.equals("localhost", ignoreCase = true) -> "http://$input"
            isIpv4(host) -> "http://$input" // bare IPs are usually LAN devices without HTTPS
            isDomain(host) -> "https://$input"
            else -> null
        }
    }

    private fun isIpv4(host: String): Boolean {
        val parts = host.split('.')
        return parts.size == 4 && parts.all { p ->
            p.isNotEmpty() && p.length <= 3 && p.all { it in '0'..'9' } && p.toInt() in 0..255
        }
    }

    private fun isDomain(host: String): Boolean {
        if (!HOSTNAME.matches(host)) return false
        val tld = host.substringAfterLast('.')
        return tld.startsWith("xn--", ignoreCase = true) || (tld.length >= 2 && tld.all { it.isLetter() })
    }

    private fun search(query: String, template: String): ParsedInput {
        val safeTemplate =
            if (template.startsWith("https://") && template.contains("%s")) template else GOOGLE_SEARCH
        val encoded = URLEncoder.encode(query, "UTF-8")
        return ParsedInput.Search(safeTemplate.replace("%s", encoded))
    }
}
