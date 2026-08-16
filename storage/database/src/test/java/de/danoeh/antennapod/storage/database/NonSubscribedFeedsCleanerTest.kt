package de.danoeh.antennapod.storage.database

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterfaceStub
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.storage.preferences.UserPreferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.util.ArrayList
import java.util.Collections
import java.util.Date
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class NonSubscribedFeedsCleanerTest {

    @Test
    fun testSubscribed() {
        val feed = createFeed()
        feed.setLastRefreshAttempt(System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(200, TimeUnit.DAYS))
        assertFalse(NonSubscribedFeedsCleaner.shouldDelete(feed))

        feed.setState(Feed.STATE_NOT_SUBSCRIBED)
        assertTrue(NonSubscribedFeedsCleaner.shouldDelete(feed))
    }

    @Test
    fun testOldDate() {
        val feed = createFeed()
        feed.setState(Feed.STATE_NOT_SUBSCRIBED)
        feed.setLastRefreshAttempt(System.currentTimeMillis())
        assertFalse(NonSubscribedFeedsCleaner.shouldDelete(feed))

        feed.setLastRefreshAttempt(System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS))
        assertFalse(NonSubscribedFeedsCleaner.shouldDelete(feed))

        feed.setLastRefreshAttempt(System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(200, TimeUnit.DAYS))
        assertTrue(NonSubscribedFeedsCleaner.shouldDelete(feed))

        feed.setLastRefreshAttempt(System.currentTimeMillis() + TimeUnit.MILLISECONDS.convert(200, TimeUnit.DAYS))
        assertFalse(NonSubscribedFeedsCleaner.shouldDelete(feed))
    }

    @Test
    fun testPlayedItem() {
        val feed = createFeed()
        feed.setState(Feed.STATE_NOT_SUBSCRIBED)
        val item = createItem(feed)
        (feed.getItems() as ArrayList<FeedItem>).add(item)

        item.setPlayed(false)
        feed.setLastRefreshAttempt(System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(10, TimeUnit.DAYS))
        assertTrue(NonSubscribedFeedsCleaner.shouldDelete(feed))

        item.setPlayed(true)
        feed.setLastRefreshAttempt(System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(10, TimeUnit.DAYS))
        assertFalse(NonSubscribedFeedsCleaner.shouldDelete(feed))

        item.setPlayed(true)
        feed.setLastRefreshAttempt(System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(100, TimeUnit.DAYS))
        assertTrue(NonSubscribedFeedsCleaner.shouldDelete(feed))
    }

    @Test
    fun testQueuedItem() {
        val feed = createFeed()
        feed.setState(Feed.STATE_NOT_SUBSCRIBED)
        feed.setLastRefreshAttempt(System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(200, TimeUnit.DAYS))
        (feed.getItems() as ArrayList<FeedItem>).add(createItem(feed))
        assertTrue(NonSubscribedFeedsCleaner.shouldDelete(feed))

        val queuedItem = createItem(feed)
        queuedItem.addTag(FeedItem.TAG_QUEUE)
        (feed.getItems() as ArrayList<FeedItem>).add(queuedItem)
        assertFalse(NonSubscribedFeedsCleaner.shouldDelete(feed))
    }

    @Test
    fun testFavoriteItem() {
        val feed = createFeed()
        feed.setState(Feed.STATE_NOT_SUBSCRIBED)
        feed.setLastRefreshAttempt(System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(200, TimeUnit.DAYS))
        (feed.getItems() as ArrayList<FeedItem>).add(createItem(feed))
        assertTrue(NonSubscribedFeedsCleaner.shouldDelete(feed))

        val queuedItem = createItem(feed)
        queuedItem.addTag(FeedItem.TAG_FAVORITE)
        (feed.getItems() as ArrayList<FeedItem>).add(queuedItem)
        assertFalse(NonSubscribedFeedsCleaner.shouldDelete(feed))
    }

    @Test
    fun testDownloadedItem() {
        val feed = createFeed()
        feed.setState(Feed.STATE_NOT_SUBSCRIBED)
        feed.setLastRefreshAttempt(System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(200, TimeUnit.DAYS))
        (feed.getItems() as ArrayList<FeedItem>).add(createItem(feed))
        assertTrue(NonSubscribedFeedsCleaner.shouldDelete(feed))

        val queuedItem = createItem(feed)
        queuedItem.getMedia()!!.setDownloaded(true, System.currentTimeMillis())
        (feed.getItems() as ArrayList<FeedItem>).add(queuedItem)
        assertFalse(NonSubscribedFeedsCleaner.shouldDelete(feed))
    }

    @Test
    fun integrationTest() {
        val context = InstrumentationRegistry.getInstrumentation().getContext()
        val longAgo = System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(200, TimeUnit.DAYS)

        // Initialize database
        PlaybackPreferences.init(context)
        UserPreferences.init(context)
        DownloadServiceInterface.setImpl(DownloadServiceInterfaceStub())
        PodDBAdapter.init(context)
        PodDBAdapter.deleteDatabase()
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.close()

        val subscribedFeed = createFeed()
        val nonSubscribedFeed = createFeed()
        nonSubscribedFeed.setState(Feed.STATE_NOT_SUBSCRIBED)
        nonSubscribedFeed.setLastRefreshAttempt(longAgo)
        val nonSubscribedFeedFavorite = createFeed()
        nonSubscribedFeedFavorite.setState(Feed.STATE_NOT_SUBSCRIBED)
        nonSubscribedFeedFavorite.setLastRefreshAttempt(longAgo)
        (nonSubscribedFeedFavorite.getItems() as ArrayList<FeedItem>).add(createItem(nonSubscribedFeedFavorite))

        DBWriter.setCompleteFeed(subscribedFeed, nonSubscribedFeedFavorite, nonSubscribedFeed)!!.get()
        DBWriter.addFavoriteItems(Collections.singletonList(nonSubscribedFeedFavorite.getItems()!![0]))!!.get()

        NonSubscribedFeedsCleaner.deleteOldNonSubscribedFeeds(context)

        val feeds = DBReader.getFeedList()
        assertEquals(2, feeds.size)
        assertEquals(subscribedFeed.getId(), feeds[0].getId())
        assertEquals(nonSubscribedFeedFavorite.getId(), feeds[1].getId())
    }

    private fun createFeed(): Feed {
        val feed = Feed(0L, null, "title", "http://example.com", "This is the description",
                "http://example.com/payment", "Daniel", "en", null, "http://example.com/feed",
                "http://example.com/image", null, "http://example.com/feed", System.currentTimeMillis())
        feed.setItems(ArrayList())
        return feed
    }

    private fun createItem(feed: Feed): FeedItem {
        val item = FeedItem(0L, "Item", "ItemId", "url", Date(), FeedItem.PLAYED, feed)
        val media = FeedMedia(item, "http://download.url.net/", 1234567L, "audio/mpeg")
        media.setId(item.getId())
        item.setMedia(media)
        return item
    }
}
