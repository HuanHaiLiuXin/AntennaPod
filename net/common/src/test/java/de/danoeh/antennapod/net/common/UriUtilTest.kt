package de.danoeh.antennapod.net.common

import org.junit.Test

import org.junit.Assert.assertEquals

/**
 * Test class for URIUtil
 */
class UriUtilTest {

    @Test
    fun testGetURIFromRequestUrlShouldNotEncode() {
        val testUrl = "http://example.com/this%20is%20encoded"
        assertEquals(testUrl, UriUtil.getURIFromRequestUrl(testUrl).toString())
    }

    @Test
    fun testGetURIFromRequestUrlShouldEncode() {
        val testUrl = "http://example.com/this is not encoded"
        val expected = "http://example.com/this%20is%20not%20encoded"
        assertEquals(expected, UriUtil.getURIFromRequestUrl(testUrl).toString())
    }
}
