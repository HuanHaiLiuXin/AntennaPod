package de.danoeh.antennapod.storage.importexport

import de.danoeh.antennapod.model.feed.Feed
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.io.StringReader
import java.io.StringWriter
import java.util.Arrays
import java.util.Collections
import java.util.List

@RunWith(RobolectricTestRunner::class)
class OpmlWriterTest {

    private fun createFeed(title: String, downloadUrl: String, link: String?, type: String?): Feed {
        return Feed(0L, null, title, link, null, null, null, null, type, null, null, null, downloadUrl, 0L)
    }

    @Test
    @Throws(Exception::class)
    fun testWriteSimple() {
        val feed1 = createFeed("Feed One", "https://example.com/feed1.xml", "https://example.com/1", "rss")
        val feed2 = createFeed("Feed Two", "https://example.com/feed2.xml", "https://example.com/2", null)
        val writer = StringWriter()
        OpmlWriter.writeDocument(Arrays.asList(feed1, feed2), writer)

        val elements = OpmlReader().readDocument(StringReader(writer.toString()))
        assertEquals(2, elements.size)
        assertEquals("Feed One", elements[0].getText())
        assertEquals("https://example.com/feed1.xml", elements[0].getXmlUrl())
        assertEquals("https://example.com/1", elements[0].getHtmlUrl())
        assertEquals("rss", elements[0].getType())
        assertEquals("Feed Two", elements[1].getText())
        assertEquals("https://example.com/feed2.xml", elements[1].getXmlUrl())
    }

    @Test
    @Throws(Exception::class)
    fun testOnlySubscribedFeedsExported() {
        val subscribed = createFeed("Subscribed", "https://example.com/sub.xml", null, null)
        val notSubscribed = createFeed("Not Subscribed", "https://example.com/unsub.xml", null, null)
        notSubscribed.setState(Feed.STATE_NOT_SUBSCRIBED)
        val archived = createFeed("Archived", "https://example.com/archived.xml", null, null)
        archived.setState(Feed.STATE_ARCHIVED)
        val writer = StringWriter()
        OpmlWriter.writeDocument(Arrays.asList(subscribed, notSubscribed, archived), writer)

        val elements = OpmlReader().readDocument(StringReader(writer.toString()))
        assertEquals(1, elements.size)
        assertEquals("Subscribed", elements[0].getText())
    }

    @Test
    @Throws(Exception::class)
    fun testEmptyFeedList() {
        val writer = StringWriter()
        OpmlWriter.writeDocument(Collections.emptyList(), writer)

        val elements = OpmlReader().readDocument(StringReader(writer.toString()))
        assertEquals(0, elements.size)
    }
}
