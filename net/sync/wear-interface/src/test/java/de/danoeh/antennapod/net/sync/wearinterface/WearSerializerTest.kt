package de.danoeh.antennapod.net.sync.wearinterface

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import java.util.Arrays
import java.util.Collections
import java.util.Date
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
class WearSerializerTest {
    @Test
    fun testEpisodesRoundTrip() {
        val item = FeedItem()
        item.setId(42L)
        item.setTitle("Test Episode")
        item.setPubDate(Date(1000000L))
        val media = FeedMedia(0L, item, 3600000, 90000, 0L, null, null, null, 0L, null, 0, 0L)
        item.setMedia(media)

        val bytes = WearSerializer.episodesToBytes(Collections.singletonList(item))
        val result = WearSerializer.episodesFromBytes(bytes)

        assertEquals(1, result.size)
        assertEquals(42L, result[0].getId())
        assertEquals("Test Episode", result[0].getTitle())
        assertEquals(1000000L, result[0].getPubDate()!!.time)
        assertEquals(3600000, result[0].getMedia()!!.getDuration())
        assertEquals(90000, result[0].getMedia()!!.getPosition())
    }

    @Test
    fun testEpisodesRoundTripEmpty() {
        val bytes = WearSerializer.episodesToBytes(Collections.emptyList())
        val result = WearSerializer.episodesFromBytes(bytes)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testEpisodesRoundTripNullTitle() {
        val item = FeedItem()
        item.setId(1L)
        item.setTitle(null)

        val bytes = WearSerializer.episodesToBytes(Collections.singletonList(item))
        val result = WearSerializer.episodesFromBytes(bytes)

        assertEquals(1, result.size)
        assertEquals("", result[0].getTitle())
    }

    @Test
    fun testFeedsRoundTrip() {
        val feed = Feed(null, null)
        feed.setId(7L)
        feed.setTitle("Test Feed")

        val bytes = WearSerializer.feedsToBytes(Collections.singletonList(feed))
        val result = WearSerializer.feedsFromBytes(bytes)

        assertEquals(1, result.size)
        assertEquals(7L, result[0].getId())
        assertEquals("Test Feed", result[0].getTitle())
    }

    @Test
    fun testFeedsRoundTripEmpty() {
        val bytes = WearSerializer.feedsToBytes(Collections.emptyList())
        val result = WearSerializer.feedsFromBytes(bytes)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testFeedsRoundTripMultiple() {
        val feed1 = Feed(null, null)
        feed1.setId(1L)
        feed1.setTitle("Feed One")
        val feed2 = Feed(null, null)
        feed2.setId(2L)
        feed2.setTitle("Feed Two")

        val bytes = WearSerializer.feedsToBytes(Arrays.asList(feed1, feed2))
        val result = WearSerializer.feedsFromBytes(bytes)

        assertEquals(2, result.size)
        assertEquals(1L, result[0].getId())
        assertEquals("Feed One", result[0].getTitle())
        assertEquals(2L, result[1].getId())
        assertEquals("Feed Two", result[1].getTitle())
    }

    @Test
    fun testNowPlayingRoundTrip() {
        val item = FeedItem()
        item.setId(99L)
        item.setTitle("Now Playing")
        item.setPubDate(Date(2000000L))
        val media = FeedMedia(0L, item, 7200000, 120000, 0L, null, null, null, 0L, null, 0, 0L)
        item.setMedia(media)

        val bytes = WearSerializer.nowPlayingToBytes(item, true)
        val result = WearSerializer.nowPlayingFromBytes(bytes)

        assertTrue(result != null)
        assertEquals(99L, result!!.item.getId())
        assertEquals("Now Playing", result.item.getTitle())
        assertEquals(7200000, result.item.getMedia()!!.getDuration())
        assertEquals(120000, result.item.getMedia()!!.getPosition())
        assertTrue(result.isPlaying)
    }

    @Test
    fun testNowPlayingFromBytesEmpty() {
        val result = WearSerializer.nowPlayingFromBytes(ByteArray(0))
        assertNull(result)
    }
}
