package de.danoeh.antennapod.net.common

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.net.URLEncoder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Test class for {@link UrlChecker}
 */
@RunWith(RobolectricTestRunner::class)
class UrlCheckerTest {

    @Test
    fun testCorrectURLHttp() {
        val input = "http://example.com"
        val out = UrlChecker.prepareUrl(input)
        assertEquals(input, out)
    }

    @Test
    fun testCorrectURLHttps() {
        val input = "https://example.com"
        val out = UrlChecker.prepareUrl(input)
        assertEquals(input, out)
    }

    @Test
    fun testMissingProtocol() {
        val input = "example.com"
        val out = UrlChecker.prepareUrl(input)
        assertEquals("http://example.com", out)
    }

    @Test
    fun testFeedProtocol() {
        val input = "feed://example.com"
        val out = UrlChecker.prepareUrl(input)
        assertEquals("http://example.com", out)
    }

    @Test
    fun testPcastProtocolNoScheme() {
        val input = "pcast://example.com"
        val out = UrlChecker.prepareUrl(input)
        assertEquals("http://example.com", out)
    }

    @Test
    fun testItpcProtocol() {
        val input = "itpc://example.com"
        val out = UrlChecker.prepareUrl(input)
        assertEquals("http://example.com", out)
    }

    @Test
    fun testItpcProtocolWithScheme() {
        val input = "itpc://https://example.com"
        val out = UrlChecker.prepareUrl(input)
        assertEquals("https://example.com", out)
    }

    @Test
    fun testWhiteSpaceUrlShouldNotAppend() {
        val input = "\n http://example.com \t"
        val out = UrlChecker.prepareUrl(input)
        assertEquals("http://example.com", out)
    }

    @Test
    fun testWhiteSpaceShouldAppend() {
        val input = "\n example.com \t"
        val out = UrlChecker.prepareUrl(input)
        assertEquals("http://example.com", out)
    }

    @Test
    fun testAntennaPodSubscribeProtocolNoScheme() {
        val input = "antennapod-subscribe://example.com"
        val out = UrlChecker.prepareUrl(input)
        assertEquals("http://example.com", out)
    }

    @Test
    fun testPcastProtocolWithScheme() {
        val input = "pcast://https://example.com"
        val out = UrlChecker.prepareUrl(input)
        assertEquals("https://example.com", out)
    }

    @Test
    fun testAntennaPodSubscribeProtocolWithScheme() {
        val input = "antennapod-subscribe://https://example.com"
        val out = UrlChecker.prepareUrl(input)
        assertEquals("https://example.com", out)
    }

    @Test
    fun testAntennaPodSubscribeDeeplink() {
        val feed = "http://example.org/podcast.rss"
        assertEquals(feed, UrlChecker.prepareUrl("https://antennapod.org/deeplink/subscribe?url=" + feed))
        assertEquals(feed, UrlChecker.prepareUrl("http://antennapod.org/deeplink/subscribe?url=" + feed))
        assertEquals(feed, UrlChecker.prepareUrl("http://antennapod.org/deeplink/subscribe/?url=" + feed))
        assertEquals(feed, UrlChecker.prepareUrl("https://www.antennapod.org/deeplink/subscribe?url=" + feed))
        assertEquals(feed, UrlChecker.prepareUrl("http://www.antennapod.org/deeplink/subscribe?url=" + feed))
        assertEquals(feed, UrlChecker.prepareUrl("http://www.antennapod.org/deeplink/subscribe/?url=" + feed))
        assertEquals(feed, UrlChecker.prepareUrl("http://www.antennapod.org/deeplink/subscribe?url="
                + URLEncoder.encode(feed, "UTF-8")))
        assertEquals(feed, UrlChecker.prepareUrl("http://www.antennapod.org/deeplink/subscribe?url="
                + "example.org/podcast.rss"))
        assertEquals(feed, UrlChecker.prepareUrl("https://antennapod.org/deeplink/subscribe?url=" + feed + "&title=a"))
        assertEquals(feed, UrlChecker.prepareUrl("https://antennapod.org/deeplink/subscribe?url="
                + URLEncoder.encode(feed, "UTF-8") + "&title=a"))
    }

    @Test
    fun testProtocolRelativeUrlIsAbsolute() {
        val input = "https://example.com"
        val inBase = "http://examplebase.com"
        val out = UrlChecker.prepareUrl(input, inBase)
        assertEquals(input, out)
    }

    @Test
    fun testProtocolRelativeUrlIsRelativeHttps() {
        val input = "//example.com"
        val inBase = "https://examplebase.com"
        val out = UrlChecker.prepareUrl(input, inBase)
        assertEquals("https://example.com", out)
    }

    @Test
    fun testProtocolRelativeUrlIsHttpsWithApSubscribeProtocol() {
        val input = "//example.com"
        val inBase = "antennapod-subscribe://https://examplebase.com"
        val out = UrlChecker.prepareUrl(input, inBase)
        assertEquals("https://example.com", out)
    }

    @Test
    fun testProtocolRelativeUrlBaseUrlNull() {
        val input = "example.com"
        val out = UrlChecker.prepareUrl(input, null)
        assertEquals("http://example.com", out)
    }

    @Test
    fun testUrlEqualsSame() {
        assertTrue(UrlChecker.urlEquals("https://www.example.com/test", "https://www.example.com/test"))
        assertTrue(UrlChecker.urlEquals("https://www.example.com/test", "https://www.example.com/test/"))
        assertTrue(UrlChecker.urlEquals("https://www.example.com/test", "https://www.example.com//test"))
        assertTrue(UrlChecker.urlEquals("https://www.example.com", "https://www.example.com/"))
        assertTrue(UrlChecker.urlEquals("https://www.example.com", "http://www.example.com"))
        assertTrue(UrlChecker.urlEquals("http://www.example.com/", "https://www.example.com/"))
        assertTrue(UrlChecker.urlEquals("https://www.example.com/?id=42", "https://www.example.com/?id=42"))
        assertTrue(UrlChecker.urlEquals("https://example.com/podcast%20test", "https://example.com/podcast test"))
        assertTrue(UrlChecker.urlEquals("https://example.com/?a=podcast%20test", "https://example.com/?a=podcast test"))
        assertTrue(UrlChecker.urlEquals("https://example.com/?", "https://example.com/"))
        assertTrue(UrlChecker.urlEquals("https://example.com/?", "https://example.com"))
        assertTrue(UrlChecker.urlEquals("https://Example.com", "https://example.com"))
        assertTrue(UrlChecker.urlEquals("https://example.com/test", "https://example.com/Test"))
        assertTrue(UrlChecker.urlEquals("antennapod_local:abc", "antennapod_local:abc"))
    }

    @Test
    fun testUrlEqualsDifferent() {
        assertFalse(UrlChecker.urlEquals("https://www.example.com/test", "https://www.example2.com/test"))
        assertFalse(UrlChecker.urlEquals("https://www.example.com/test", "https://www.example.de/test"))
        assertFalse(UrlChecker.urlEquals("https://example.com/", "https://otherpodcast.example.com/"))
        assertFalse(UrlChecker.urlEquals("https://www.example.com/?id=42&a=b", "https://www.example.com/?id=43&a=b"))
        assertFalse(UrlChecker.urlEquals("https://example.com/podcast%25test", "https://example.com/podcast test"))
        assertFalse(UrlChecker.urlEquals("antennapod_local:abc", "https://example.com/"))
    }
}
