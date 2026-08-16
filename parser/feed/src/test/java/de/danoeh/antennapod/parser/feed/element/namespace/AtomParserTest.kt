package de.danoeh.antennapod.parser.feed.element.namespace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.io.File
import java.util.Date

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia

/**
 * Tests for Atom feeds in FeedHandler.
 */
@RunWith(RobolectricTestRunner::class)
class AtomParserTest {

    @Test
    @Throws(Exception::class)
    fun testAtomBasic() {
        val feedFile = FeedParserTestHelper.getFeedFile("feed-atom-testAtomBasic.xml")
        val feed = FeedParserTestHelper.runFeedParser(feedFile)
        assertEquals(Feed.TYPE_ATOM1, feed.getType())
        assertEquals("title", feed.getTitle())
        assertEquals("http://example.com/feed", feed.getFeedIdentifier())
        assertEquals("http://example.com", feed.getLink())
        assertEquals("This is the description", feed.getDescription())
        assertEquals("http://example.com/payment", feed.getPaymentLinks()!![0].url)
        assertEquals("http://example.com/picture", feed.getImageUrl())
        assertEquals(10, feed.getItems()!!.size)
        for (i in feed.getItems()!!.indices) {
            val item: FeedItem = feed.getItems()!![i]
            assertEquals("http://example.com/item-" + i, item.getItemIdentifier())
            assertEquals("item-" + i, item.getTitle())
            assertNull(item.getDescription())
            assertEquals("http://example.com/items/" + i, item.getLink())
            assertEquals(Date(i * 60000L), item.getPubDate())
            assertNull(item.getPaymentLink())
            assertEquals("http://example.com/picture", item.getImageLocation())
            // media
            assertTrue(item.hasMedia())
            val media: FeedMedia? = item.getMedia()
            //noinspection ConstantConditions
            assertEquals("http://example.com/media-" + i, media!!.getDownloadUrl())
            assertEquals(1024 * 1024L, media.getSize())
            assertEquals("audio/mp3", media.getMimeType())
            // chapters
            assertNull(item.getChapters())
        }
    }

    @Test
    @Throws(Exception::class)
    fun testEmptyRelLinks() {
        val feedFile = FeedParserTestHelper.getFeedFile("feed-atom-testEmptyRelLinks.xml")
        val feed = FeedParserTestHelper.runFeedParser(feedFile)
        assertEquals(Feed.TYPE_ATOM1, feed.getType())
        assertEquals("title", feed.getTitle())
        assertEquals("http://example.com/feed", feed.getFeedIdentifier())
        assertEquals("http://example.com", feed.getLink())
        assertEquals("This is the description", feed.getDescription())
        assertNull(feed.getPaymentLinks())
        assertEquals("http://example.com/picture", feed.getImageUrl())
        assertEquals(1, feed.getItems()!!.size)

        // feed entry
        val item: FeedItem = feed.getItems()!![0]
        assertEquals("http://example.com/item-0", item.getItemIdentifier())
        assertEquals("item-0", item.getTitle())
        assertNull(item.getDescription())
        assertEquals("http://example.com/items/0", item.getLink())
        assertEquals(Date(0), item.getPubDate())
        assertNull(item.getPaymentLink())
        assertEquals("http://example.com/picture", item.getImageLocation())
        // media
        assertFalse(item.hasMedia())
        // chapters
        assertNull(item.getChapters())
    }

    @Test
    @Throws(Exception::class)
    fun testLogoWithWhitespace() {
        val feedFile = FeedParserTestHelper.getFeedFile("feed-atom-testLogoWithWhitespace.xml")
        val feed = FeedParserTestHelper.runFeedParser(feedFile)
        assertEquals("title", feed.getTitle())
        assertEquals("http://example.com/feed", feed.getFeedIdentifier())
        assertEquals("http://example.com", feed.getLink())
        assertEquals("This is the description", feed.getDescription())
        assertEquals("http://example.com/payment", feed.getPaymentLinks()!![0].url)
        assertEquals("https://example.com/image.png", feed.getImageUrl())
        assertEquals(0, feed.getItems()!!.size)
    }
}
