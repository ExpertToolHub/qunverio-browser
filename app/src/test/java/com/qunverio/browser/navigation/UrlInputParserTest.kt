package com.qunverio.browser.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlInputParserTest {

    private fun navigate(input: String) = (UrlInputParser.parse(input) as ParsedInput.Navigate).url
    private fun search(input: String) = (UrlInputParser.parse(input) as ParsedInput.Search).url
    private fun assertRejected(input: String) =
        assertTrue("expected Rejected for '$input'", UrlInputParser.parse(input) is ParsedInput.Rejected)

    @Test fun bareDomainGetsHttps() = assertEquals("https://example.com", navigate("example.com"))

    @Test fun domainWithPathKeepsPath() =
        assertEquals("https://example.com/path?x=1", navigate("example.com/path?x=1"))

    @Test fun fullHttpsUrlIsKept() =
        assertEquals("https://example.com/a?b=c", navigate("https://example.com/a?b=c"))

    @Test fun schemeIsLowercased() = assertEquals("https://Example.com", navigate("HTTPS://Example.com"))

    @Test fun localhostWithPortUsesHttp() = assertEquals("http://localhost:8080", navigate("localhost:8080"))

    @Test fun ipv4UsesHttp() = assertEquals("http://192.168.1.1", navigate("192.168.1.1"))

    @Test fun wordsBecomeSearch() =
        assertEquals("https://www.google.com/search?q=hello+world", search("hello world"))

    @Test fun singleWordWithoutDotIsSearch() =
        assertEquals("https://www.google.com/search?q=kotlin", search("kotlin"))

    @Test fun searchEncodesSpecialCharacters() = assertTrue(search("a&b").contains("q=a%26b"))

    @Test fun invalidPortFallsBackToSearch() {
        assertTrue(UrlInputParser.parse("example.com:99999") is ParsedInput.Search)
    }

    @Test fun outOfRangeIpIsSearch() {
        assertTrue(UrlInputParser.parse("999.1.1.1") is ParsedInput.Search)
    }

    @Test fun textWithColonAndSpacesIsSearch() {
        assertTrue(UrlInputParser.parse("what is: time") is ParsedInput.Search)
        assertTrue(UrlInputParser.parse("note:buy milk") is ParsedInput.Search)
    }

    @Test fun dangerousSchemesAreRejected() {
        assertRejected("javascript:alert(1)")
        assertRejected("file:///sdcard/x")
        assertRejected("content://media/external/file/1")
        assertRejected("data:text/html,hi")
        assertRejected("intent://scan#Intent;end")
    }

    @Test fun embeddedCredentialsAreRejected() = assertRejected("https://user:pw@example.com")

    @Test fun malformedHttpUrlsAreRejected() {
        assertRejected("https://exa mple.com")
        assertRejected("http://")
    }

    @Test fun emptyInputIsRejected() {
        assertRejected("")
        assertRejected("   ")
    }
}
