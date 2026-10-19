package com.qunverio.browser.adblock

/**
 * Reads the host-level subset of Adblock Plus / EasyList syntax:
 *   ||example.com^              block
 *   ||example.com^$third-party  block
 *   @@||example.com^            allow (exception)
 * Everything else (cosmetic ## rules, URL paths, wildcards, resource-type or
 * domain= options) is skipped on purpose, because applying it partially would
 * block more than the list authors intended.
 */
object FilterListParser {
    class Result {
        val block = HashSet<String>()
        val allow = HashSet<String>()
    }

    private val HOST = Regex("^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$")

    fun parseInto(lines: Sequence<String>, out: Result) {
        for (raw in lines) parseLine(raw, out)
    }

    fun parseLine(raw: String, out: Result) {
        val line = raw.trim()
        if (line.isEmpty() || line[0] == '!' || line[0] == '[') return
        if (line.contains("##") || line.contains("#@#") || line.contains("#?#") || line.contains("#\$#")) return

        val isException = line.startsWith("@@")
        val body = if (isException) line.substring(2) else line
        if (!body.startsWith("||")) return

        var rest = body.substring(2)
        val dollar = rest.indexOf('$')
        if (dollar >= 0) {
            val options = rest.substring(dollar + 1).split(',')
            if (!options.all { it == "third-party" || it == "3p" }) return
            rest = rest.substring(0, dollar)
        }
        if (rest.endsWith("^")) rest = rest.dropLast(1)

        val host = rest.lowercase()
        if (!host.contains('.') || !HOST.matches(host)) return
        if (isException) out.allow.add(host) else out.block.add(host)
    }
}
