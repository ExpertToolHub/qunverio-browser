package com.qunverio.browser.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdblockTest {

    private fun parse(vararg lines: String): FilterListParser.Result {
        val r = FilterListParser.Result()
        FilterListParser.parseInto(lines.asSequence(), r)
        return r
    }

    @Test fun parsesHostRules() {
        val r = parse(
            "[Adblock Plus 2.0]",
            "! comment",
            "||ads.example.com^",
            "||Tracker.NET^\$third-party",
            "@@||good.example.com^",
        )
        assertEquals(setOf("ads.example.com", "tracker.net"), r.block)
        assertEquals(setOf("good.example.com"), r.allow)
    }

    @Test fun skipsRulesItCannotApplySafely() {
        val r = parse(
            "example.com##.banner",
            "||example.com/ads/",
            "||example.org^\$script",
            "||example.org^\$domain=foo.com",
            "/banner/*",
            "||nodots^",
            "||bad host.com^",
            "@@||example.net^\$document",
        )
        assertTrue(r.block.isEmpty())
        assertTrue(r.allow.isEmpty())
    }

    @Test fun blocksSubdomainsOfBlockedHost() {
        val e = FilterEngine()
        e.replaceRules(setOf("doubleclick.net"), emptySet())
        assertTrue(e.shouldBlock("ad.doubleclick.net", "news.com"))
        assertTrue(e.shouldBlock("doubleclick.net", "news.com"))
        assertFalse(e.shouldBlock("notdoubleclick.net", "news.com"))
        assertFalse(e.shouldBlock("example.com", "news.com"))
    }

    @Test fun neverBlocksFirstParty() {
        val e = FilterEngine()
        e.replaceRules(setOf("tracker.net"), emptySet())
        assertFalse(e.shouldBlock("cdn.tracker.net", "www.tracker.net"))
    }

    @Test fun exceptionsOverrideBlocks() {
        val e = FilterEngine()
        e.replaceRules(setOf("ads.net"), setOf("allowed.ads.net"))
        assertFalse(e.shouldBlock("allowed.ads.net", "news.com"))
        assertTrue(e.shouldBlock("other.ads.net", "news.com"))
    }

    @Test fun registrableDomains() {
        assertEquals("example.com", registrableDomain("a.b.example.com"))
        assertEquals("bbc.co.uk", registrableDomain("www.bbc.co.uk"))
        assertEquals("192.168.1.1", registrableDomain("192.168.1.1"))
        assertTrue(isSameSite("m.example.com", "www.example.com"))
        assertFalse(isSameSite("example.com", "example.org"))
    }

    @Test fun hostSetNeverMatchesBareTld() {
        assertFalse(hostInSet(setOf("com"), "example.com"))
        assertTrue(hostInSet(setOf("example.com"), "a.example.com"))
    }
}
