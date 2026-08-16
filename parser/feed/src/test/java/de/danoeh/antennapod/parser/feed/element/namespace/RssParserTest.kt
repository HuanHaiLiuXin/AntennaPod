package de.danoeh.antennapod.parser.feed.element.namespace

import android.text.TextUtils
import org.junit.Assert.assertEquals
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
import de.danoeh.antennapod.model.playback.MediaType

/**
 * Tests for RSS feeds in FeedHandler.
 */
@RunWith(RobolectricTestRunner::class)
class RssParserTest {

    @Test
    @Throws(Exception::class)
    fun testRss2Basic() {
        val feedFile = FeedParserTestHelper.getFeedFile("feed-rss-testRss2Basic.xml")
        val feed = FeedParserTestHelper.runFeedParser(feedFile)
        assertEquals(Feed.TYPE_RSS2, feed.getType())
        assertEquals("title", feed.getTitle())
        assertEquals("en", feed.getLanguage())
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
    fun testImageWithWhitespace() {
        val feedFile = FeedParserTestHelper.getFeedFile("feed-rss-testImageWithWhitespace.xml")
        val feed = FeedParserTestHelper.runFeedParser(feedFile)
        assertEquals("title", feed.getTitle())
        assertEquals("http://example.com", feed.getLink())
        assertEquals("This is the description", feed.getDescription())
        assertEquals("http://example.com/payment", feed.getPaymentLinks()!![0].url)
        assertEquals("https://example.com/image.png", feed.getImageUrl())
        assertEquals(0, feed.getItems()!!.size)
    }

    @Test
    @Throws(Exception::class)
    fun testMediaContentMime() {
        val feedFile = FeedParserTestHelper.getFeedFile("feed-rss-testMediaContentMime.xml")
        val feed = FeedParserTestHelper.runFeedParser(feedFile)
        assertEquals("title", feed.getTitle())
        assertEquals("http://example.com", feed.getLink())
        assertEquals("This is the description", feed.getDescription())
        assertEquals("http://example.com/payment", feed.getPaymentLinks()!![0].url)
        assertNull(feed.getImageUrl())
        assertEquals(1, feed.getItems()!!.size)
        val feedItem = feed.getItems()!![0]
        //noinspection ConstantConditions
        assertEquals(MediaType.VIDEO, feedItem.getMedia()!!.getMediaType())
        assertEquals("https://www.example.com/file.mp4", feedItem.getMedia()!!.getDownloadUrl())
    }

    @Test
    @Throws(Exception::class)
    fun testMultipleFundingTags() {
        val feedFile = FeedParserTestHelper.getFeedFile("feed-rss-testMultipleFundingTags.xml")
        val feed = FeedParserTestHelper.runFeedParser(feedFile)
        assertEquals(3, feed.getPaymentLinks()!!.size)
        assertEquals("Text 1", feed.getPaymentLinks()!![0].content)
        assertEquals("https://example.com/funding1", feed.getPaymentLinks()!![0].url)
        assertEquals("Text 2", feed.getPaymentLinks()!![1].content)
        assertEquals("https://example.com/funding2", feed.getPaymentLinks()!![1].url)
        assertTrue(TextUtils.isEmpty(feed.getPaymentLinks()!![2].content))
        assertEquals("https://example.com/funding3", feed.getPaymentLinks()!![2].url)
    }

    @Test
    @Throws(Exception::class)
    fun testPodcastIndexTranscript() {
        val feedFile = FeedParserTestHelper.getFeedFile("feed-rss-testPodcastIndexTranscript.xml")
        val feed = FeedParserTestHelper.runFeedParser(feedFile)
        assertEquals("https://podnews.net/audio/podnews231011.mp3.json", feed.getItems()!![0].getTranscriptUrl())
        assertEquals("application/json", feed.getItems()!![0].getTranscriptType())
    }

    @Test
    @Throws(Exception::class)
    fun testUnsupportedElements() {
        val feedFile = FeedParserTestHelper.getFeedFile("feed-rss-testUnsupportedElements.xml")
        val feed = FeedParserTestHelper.runFeedParser(feedFile)
        assertEquals(1, feed.getItems()!!.size)
        assertEquals("item-0", feed.getItems()!![0].getTitle())
    }
}
