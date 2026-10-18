package com.qunverio.browser.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkPolicyTest {

    @Test fun webLinksLoadInside() {
        assertEquals(LinkDecision.LOAD_IN_WEBVIEW, LinkPolicy.decide("https", "https://a.com", false))
        assertEquals(LinkDecision.LOAD_IN_WEBVIEW, LinkPolicy.decide("HTTP", "http://a.com", false))
        assertEquals(LinkDecision.LOAD_IN_WEBVIEW, LinkPolicy.decide("about", "about:blank", false))
    }

    @Test fun allowedExternalSchemesNeedGesture() {
        assertEquals(LinkDecision.ASK_EXTERNAL, LinkPolicy.decide("tel", "tel:123", true))
        assertEquals(LinkDecision.ASK_EXTERNAL, LinkPolicy.decide("mailto", "mailto:a@b.c", true))
        assertEquals(LinkDecision.BLOCK, LinkPolicy.decide("tel", "tel:123", false))
    }

    @Test fun everythingElseIsBlocked() {
        for (s in listOf("javascript", "file", "content", "data", "intent", "market", "blob", "ftp")) {
            assertEquals(s, LinkDecision.BLOCK, LinkPolicy.decide(s, "$s:x", true))
        }
        assertEquals(LinkDecision.BLOCK, LinkPolicy.decide(null, "x", true))
        assertEquals(LinkDecision.BLOCK, LinkPolicy.decide("about", "about:config", true))
    }
}
