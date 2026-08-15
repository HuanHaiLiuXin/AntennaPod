package de.danoeh.antennapod.storage.database

import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.storage.database.FeedItemDuplicateGuesser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import java.util.Date

/**
 * Test class for [FeedItemDuplicateGuesser].
 */
class FeedItemDuplicateGuesserTest {
    companion object {
        private const val MINUTES = 1000 * 60L
        private const val DAYS = 24 * 60 * MINUTES
    }

    @Test
    fun testSameId() {
        assertTrue(FeedItemDuplicateGuesser.seemDuplicates(
                item("id", "Title1", "example.com/episode1", 0, 5 * MINUTES, "audio/*"),
                item("id", "Title2", "example.com/episode2", 0, 20 * MINUTES, "video/*")))
    }

    @Test
    fun testDuplicateDownloadUrl() {
        assertTrue(FeedItemDuplicateGuesser.seemDuplicates(
                item("id1", "Title1", "example.com/episode", 0, 5 * MINUTES, "audio/*"),
                item("id2", "Title2", "example.com/episode", 0, 5 * MINUTES, "audio/*")))
        assertFalse(FeedItemDuplicateGuesser.seemDuplicates(
                item("id1", "Title1", "example.com/episode1", 0, 5 * MINUTES, "audio/*"),
                item("id2", "Title2", "example.com/episode2", 0, 5 * MINUTES, "audio/*")))
    }

    @Test
    fun testOtherAttributes() {
        assertTrue(FeedItemDuplicateGuesser.seemDuplicates(
                item("id1", "Title", "example.com/episode1", 10, 5 * MINUTES, "audio/*"),
                item("id2", "Title", "example.com/episode2", 10, 5 * MINUTES, "audio/*")))
        assertTrue(FeedItemDuplicateGuesser.seemDuplicates(
                item("id1", "Title", "example.com/episode1", 10, 5 * MINUTES, "audio/*"),
                item("id2", "Title", "example.com/episode2", 20, 6 * MINUTES, "audio/*")))
        assertFalse(FeedItemDuplicateGuesser.seemDuplicates(
                item("id1", "Title", "example.com/episode1", 10, 5 * MINUTES, "audio/*"),
                item("id2", "Title", "example.com/episode2", 10, 5 * MINUTES, "video/*")))
        assertTrue(FeedItemDuplicateGuesser.seemDuplicates(
                item("id1", "Title", "example.com/episode1", 10, 5 * MINUTES, "audio/mpeg"),
                item("id2", "Title", "example.com/episode2", 10, 5 * MINUTES, "audio/mp3")))
        assertFalse(FeedItemDuplicateGuesser.seemDuplicates(
                item("id1", "Title", "example.com/episode1", 5 * DAYS, 5 * MINUTES, "audio/*"),
                item("id2", "Title", "example.com/episode2", 2 * DAYS, 5 * MINUTES, "audio/*")))
    }

    @Test
    fun testNoMediaType() {
        assertTrue(FeedItemDuplicateGuesser.seemDuplicates(
                item("id1", "Title", "example.com/episode1", 2 * DAYS, 5 * MINUTES, ""),
                item("id2", "Title", "example.com/episode2", 2 * DAYS, 5 * MINUTES, "")))
    }

    private fun item(guid: String, title: String, downloadUrl: String,
                     date: Long, duration: Long, mime: String): FeedItem {
        val item = FeedItem(0L, title, guid, "link", Date(date), FeedItem.PLAYED, null)
        val media = FeedMedia(item, downloadUrl, duration, mime)
        item.setMedia(media)
        return item
    }
}
