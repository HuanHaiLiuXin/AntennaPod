package de.danoeh.antennapod.storage.importexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.io.StringReader
import java.util.ArrayList

@RunWith(RobolectricTestRunner::class)
class OpmlReaderTest {

    @Test
    @Throws(Exception::class)
    fun testReadSimple() {
        val opml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<opml version=\"2.0\">" +
                "<head><title>Test</title></head>" +
                "<body>" +
                "<outline text=\"Feed Text\" title=\"Feed 1\" type=\"rss\"" +
                " xmlUrl=\"https://example.com/feed1.xml\" htmlUrl=\"https://example.com/1\"/>" +
                "<outline text=\"Feed 2\" title=\"Feed 2\" xmlUrl=\"https://example.com/feed2.xml\"/>" +
                "</body>" +
                "</opml>"
        val reader = OpmlReader()
        val result = reader.readDocument(StringReader(opml))
        assertEquals(2, result.size)
        assertEquals("Feed 1", result[0].getText())
        assertEquals("https://example.com/feed1.xml", result[0].getXmlUrl())
        assertEquals("https://example.com/1", result[0].getHtmlUrl())
        assertEquals("rss", result[0].getType())
        assertEquals("Feed 2", result[1].getText())
        assertEquals("https://example.com/feed2.xml", result[1].getXmlUrl())
    }

    @Test
    @Throws(Exception::class)
    fun testSkipsFeedWithoutXmlUrl() {
        val opml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<opml version=\"2.0\">" +
                "<body>" +
                "<outline text=\"No Url\" title=\"No Url\"/>" +
                "<outline text=\"With Url\" title=\"With Url\" xmlUrl=\"https://example.com/feed.xml\"/>" +
                "</body>" +
                "</opml>"
        val reader = OpmlReader()
        val result = reader.readDocument(StringReader(opml))
        assertEquals(1, result.size)
        assertEquals("With Url", result[0].getText())
    }

    @Test
    @Throws(Exception::class)
    fun testTitlePreferredOverText() {
        val opml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<opml version=\"2.0\">" +
                "<body>" +
                "<outline text=\"Text Attr\" title=\"Title Attr\" xmlUrl=\"https://example.com/feed.xml\"/>" +
                "</body>" +
                "</opml>"
        val reader = OpmlReader()
        val result = reader.readDocument(StringReader(opml))
        assertEquals("Title Attr", result[0].getText())
    }

    @Test
    @Throws(Exception::class)
    fun testTextUsedWhenNoTitle() {
        val opml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<opml version=\"2.0\">" +
                "<body>" +
                "<outline text=\"Text Attr\" xmlUrl=\"https://example.com/feed.xml\"/>" +
                "</body>" +
                "</opml>"
        val reader = OpmlReader()
        val result = reader.readDocument(StringReader(opml))
        assertEquals("Text Attr", result[0].getText())
    }

    @Test
    @Throws(Exception::class)
    fun testXmlUrlUsedAsTextWhenNeitherPresent() {
        val opml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<opml version=\"2.0\">" +
                "<body>" +
                "<outline xmlUrl=\"https://example.com/feed.xml\"/>" +
                "</body>" +
                "</opml>"
        val reader = OpmlReader()
        val result = reader.readDocument(StringReader(opml))
        assertEquals(1, result.size)
        assertEquals("https://example.com/feed.xml", result[0].getText())
    }

    @Test
    @Throws(Exception::class)
    fun testNestedCategoryFeeds() {
        val opml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<opml version=\"2.0\">" +
                "<body>" +
                "<outline text=\"Tech\">" +
                "<outline title=\"Feed 1\" xmlUrl=\"https://example.com/feed1.xml\"/>" +
                "<outline title=\"Feed 2\" xmlUrl=\"https://example.com/feed2.xml\"/>" +
                "</outline>" +
                "</body>" +
                "</opml>"
        val reader = OpmlReader()
        val result = reader.readDocument(StringReader(opml))
        assertEquals(2, result.size)
        assertEquals("Feed 1", result[0].getText())
        assertEquals("Feed 2", result[1].getText())
    }

    @Test
    @Throws(Exception::class)
    fun testHtmlUrlAndTypeAreOptional() {
        val opml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<opml version=\"2.0\">" +
                "<body>" +
                "<outline title=\"Minimal\" xmlUrl=\"https://example.com/feed.xml\"/>" +
                "</body>" +
                "</opml>"
        val reader = OpmlReader()
        val result = reader.readDocument(StringReader(opml))
        assertEquals(1, result.size)
        assertNull(result[0].getHtmlUrl())
        assertNull(result[0].getType())
    }
}
