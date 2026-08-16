package de.danoeh.antennapod.net.download.service.episode.autodownload

import android.content.Context

import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.Date
import java.util.Random

import androidx.test.platform.app.InstrumentationRegistry

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedCounter
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.FeedOrder
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.database.NavDrawerData
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.storage.database.LongList
import de.danoeh.antennapod.storage.database.PodDBAdapter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner

/**
 * Test class for DBReader.
 */
@Suppress("ConstantConditions")
@RunWith(Enclosed::class)
class DbReaderTest {
    @Ignore("Not a test")
    open class TestBase {
        @Before
        open fun setUp() {
            val context = InstrumentationRegistry.getInstrumentation().getContext()
            UserPreferences.init(context)

            PodDBAdapter.init(context)
            PodDBAdapter.deleteDatabase()
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            adapter.close()
        }

        @After
        open fun tearDown() {
            PodDBAdapter.tearDownTests()
            DBWriter.tearDownTests()
        }
    }

    @RunWith(RobolectricTestRunner::class)
    class SingleTests : TestBase() {
        @Test
        fun testGetFeedList() {
            val feeds = DbTestUtils.saveFeedlist(10, 0, false)
            val savedFeeds = DBReader.getFeedList()
            assertNotNull(savedFeeds)
            assertEquals(feeds.size, savedFeeds.size)
            for (i in feeds.indices) {
                assertEquals(feeds[i].getId(), savedFeeds[i].getId())
            }
        }

        @Test
        fun testGetFeedListSortOrder() {
            val adapter = PodDBAdapter.getInstance()
            adapter.open()

            val lastRefreshed = System.currentTimeMillis()
            val feed1 = Feed(0L, null, "A", "link", "d", null, null, null, "rss", "A", null, "", "", lastRefreshed)
            val feed2 = Feed(0L, null, "b", "link", "d", null, null, null, "rss", "b", null, "", "", lastRefreshed)
            val feed3 = Feed(0L, null, "C", "link", "d", null, null, null, "rss", "C", null, "", "", lastRefreshed)
            val feed4 = Feed(0L, null, "d", "link", "d", null, null, null, "rss", "d", null, "", "", lastRefreshed)
            adapter.setCompleteFeed(feed1, feed2, feed3, feed4)
            adapter.close()

            val saved = DBReader.getFeedList()
            assertNotNull(saved)
            assertEquals("Wrong size: ", 4, saved.size)
            assertEquals("Wrong sort order: ", "A", saved[0].getTitle())
            assertEquals("Wrong sort order: ", "b", saved[1].getTitle())
            assertEquals("Wrong sort order: ", "C", saved[2].getTitle())
            assertEquals("Wrong sort order: ", "d", saved[3].getTitle())
        }

        @Test
        fun testGetFeedListUnfiltered() {
            DbTestUtils.saveFeedlist(10, 0, false)
            val saved = DBReader.getFeedList()
            assertNotNull(saved)
            assertEquals(10, saved.size)
        }

        @Test
        fun testGetFeedListFiltered() {
            val numFeeds = 10
            DbTestUtils.saveFeedlist(numFeeds, 0, false)

            val filter = de.danoeh.antennapod.model.feed.SubscriptionsFilter("")
            filter.hideNonSubscribedFeeds = true
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            val saved = DBReader.getNavDrawerData(filter, FeedOrder.COUNTER,
                    FeedCounter.SHOW_NONE, Feed.STATE_SUBSCRIBED).feeds
            adapter.close()
            assertNotNull(saved)
            assertEquals(10, saved.size)
        }

        @Test
        fun testGetFeed() {
            val feed = Feed("url", null, "title")
            feed.setItems(ArrayList())
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            adapter.setCompleteFeed(feed)
            adapter.close()

            val saved = DBReader.getFeed(feed.getId(), false, 0, Int.MAX_VALUE)
            assertNotNull(saved)
            assertEquals(feed.getId(), saved!!.getId())
            assertNotNull(saved.getItems())
            assertEquals(0, saved.getItems()!!.size)
        }

        @Test
        fun testGetFeedItem() {
            val feed = Feed("url", null, "title")
            feed.setItems(ArrayList())
            (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item", "id", "link", Date(), FeedItem.PLAYED, feed))
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            adapter.setCompleteFeed(feed)
            adapter.close()

            val savedItem = DBReader.getFeedItem(feed.getItems()!![0].getId())
            assertNotNull(savedItem)
            assertEquals(feed.getItems()!![0].getId(), savedItem!!.getId())
        }

        @Test
        fun testGetQueueIDList() {
            val feed = Feed("url", null, "title")
            feed.setItems(ArrayList())
            for (i in 0 until 10) {
                (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item", "id", "link", Date(), FeedItem.PLAYED, feed))
            }
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            adapter.setCompleteFeed(feed)
            adapter.close()

            DBWriter.addQueueItem(InstrumentationRegistry.getInstrumentation().getContext(),
                    *feed.getItems()!!.toTypedArray())!!.get()

            val ids = DBReader.getQueueIDList()
            assertNotNull(ids)
            assertEquals(10, ids.size())
            for (i in 0 until 10) {
                assertEquals(feed.getItems()!![i].getId(), ids.get(i))
            }
        }

        @Test
        fun testGetQueue() {
            val feed = Feed("url", null, "title")
            feed.setItems(ArrayList())
            for (i in 0 until 10) {
                (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item", "id", "link", Date(), FeedItem.PLAYED, feed))
            }
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            adapter.setCompleteFeed(feed)
            adapter.close()

            DBWriter.addQueueItem(InstrumentationRegistry.getInstrumentation().getContext(),
                    *feed.getItems()!!.toTypedArray())!!.get()

            val queue = DBReader.getQueue()
            assertNotNull(queue)
            assertEquals(10, queue.size)
            for (i in 0 until 10) {
                assertEquals(feed.getItems()!![i].getId(), queue[i].getId())
            }
        }

        @Test
        fun testGetEpisodes() {
            val feed = Feed("url", null, "title")
            feed.setItems(ArrayList())
            for (i in 0 until 10) {
                (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item " + i, "id" + i, "link" + i,
                        Date(i.toLong()), FeedItem.PLAYED, feed))
            }
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            adapter.setCompleteFeed(feed)
            adapter.close()

            val episodes = DBReader.getEpisodes(0, Int.MAX_VALUE, FeedItemFilter.unfiltered(),
                    SortOrder.DATE_NEW_OLD)
            assertNotNull(episodes)
            assertEquals(10, episodes.size)
            for (i in episodes.indices) {
                assertEquals(feed.getItems()!![9 - i].getId(), episodes[i].getId())
            }
        }

        @Test
        fun testGetTotalEpisodeCount() {
            val feed = Feed("url", null, "title")
            feed.setItems(ArrayList())
            for (i in 0 until 10) {
                (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item " + i, "id" + i, "link" + i,
                        Date(i.toLong()), FeedItem.PLAYED, feed))
            }
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            adapter.setCompleteFeed(feed)
            adapter.close()

            assertEquals(10, DBReader.getTotalEpisodeCount(FeedItemFilter.unfiltered()))
        }

        @Test
        fun testGetFeedEpisodeCount() {
            val feed = Feed("url", null, "title")
            feed.setItems(ArrayList())
            for (i in 0 until 10) {
                (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item " + i, "id" + i, "link" + i,
                        Date(i.toLong()), FeedItem.PLAYED, feed))
            }
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            adapter.setCompleteFeed(feed)
            adapter.close()

            assertEquals(10, DBReader.getFeedEpisodeCount(feed.getId(), FeedItemFilter.unfiltered()))
        }

        @Test
        fun testGetRandomEpisodes() {
            val feed = Feed("url", null, "title")
            feed.setItems(ArrayList())
            for (i in 0 until 10) {
                (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item " + i, "id" + i, "link" + i,
                        Date(i.toLong()), FeedItem.PLAYED, feed))
            }
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            adapter.setCompleteFeed(feed)
            adapter.close()

            val episodes = DBReader.getRandomEpisodes(10, Random().nextInt())
            assertNotNull(episodes)
            assertEquals(10, episodes.size)
        }

        @Test
        fun testGetDownloadLog() {
            val feed = Feed("url", null, "title")
            feed.setItems(ArrayList())
            for (i in 0 until 10) {
                (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item " + i, "id" + i, "link" + i,
                        Date(i.toLong()), FeedItem.PLAYED, feed))
            }
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            adapter.setCompleteFeed(feed)
            adapter.close()

            val log = DBReader.getDownloadLog()
            assertNotNull(log)
            assertEquals(0, log.size)
        }

        @Test
        fun testGetFeedMedia() {
            val feed = Feed("url", null, "title")
            feed.setItems(ArrayList())
            val item = FeedItem(0L, "item", "id", "link", Date(), FeedItem.PLAYED, feed)
            val media = FeedMedia(item, "url", 1, "audio/mp3")
            item.setMedia(media)
            (feed.getItems() as ArrayList<FeedItem>).add(item)
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            adapter.setCompleteFeed(feed)
            adapter.close()

            val savedMedia = DBReader.getFeedMedia(media.getId())
            assertNotNull(savedMedia)
            assertEquals(media.getId(), savedMedia!!.getId())
        }

        @Test
        fun testGetMonthlyTimeStatistics() {
            val feed = Feed("url", null, "title")
            feed.setItems(ArrayList())
            val item = FeedItem(0L, "item", "id", "link", Date(), FeedItem.PLAYED, feed)
            val media = FeedMedia(item, "url", 1, "audio/mp3")
            item.setMedia(media)
            (feed.getItems() as ArrayList<FeedItem>).add(item)
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            adapter.setCompleteFeed(feed)
            adapter.close()

            DBWriter.setFeedMediaPlaybackInformation(media)!!.get()
            val stats = DBReader.getMonthlyTimeStatistics()
            assertNotNull(stats)
            assertTrue(stats.isNotEmpty())
        }

        @Test
        fun testGetNavDrawerData() {
            val numFeeds = 10
            DbTestUtils.saveFeedlist(numFeeds, 0, false)

            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            val navDrawerData = DBReader.getNavDrawerData(null, FeedOrder.COUNTER,
                    FeedCounter.SHOW_NONE, Feed.STATE_SUBSCRIBED)
            adapter.close()

            assertNotNull(navDrawerData)
            assertEquals(numFeeds, navDrawerData.feeds.size)
        }

        @Test
        fun testGetAllTags() {
            DbTestUtils.saveFeedlist(10, 0, false)

            val tags = DBReader.getAllTags(Feed.STATE_SUBSCRIBED)
            assertNotNull(tags)
            assertTrue(tags.size >= 2)
        }

        @Test
        fun testSearchFeedItems() {
            val feed = Feed("url", null, "title")
            feed.setItems(ArrayList())
            for (i in 0 until 10) {
                (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item " + i, "id" + i, "link" + i,
                        Date(i.toLong()), FeedItem.PLAYED, feed))
            }
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            adapter.setCompleteFeed(feed)
            adapter.close()

            val items = DBReader.searchFeedItems(feed.getId(), "item 1", FeedItemFilter.unfiltered())
            assertNotNull(items)
            assertFalse(items.isEmpty())
        }

        @Test
        fun testSearchFeeds() {
            DbTestUtils.saveFeedlist(10, 0, false)
            val feeds = DBReader.searchFeeds("feed 1", FeedItemFilter.unfiltered())
            assertNotNull(feeds)
            assertFalse(feeds.isEmpty())
        }

        @Test
        fun testGetFeedItemByGuidOrEpisodeUrl() {
            val feed = Feed("url", null, "title")
            feed.setItems(ArrayList())
            (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item", "id", "link", Date(), FeedItem.PLAYED, feed))
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            adapter.setCompleteFeed(feed)
            adapter.close()

            val item = DBReader.getFeedItemByGuidOrEpisodeUrl("id", "link")
            assertNotNull(item)
            assertEquals(feed.getItems()!![0].getId(), item!!.getId())
        }

        @Test
        fun testGetFeedItemsWithUrl() {
            val feed = Feed("url", null, "title")
            feed.setItems(ArrayList())
            val item = FeedItem(0L, "item", "id", "link", Date(), FeedItem.PLAYED, feed)
            val media = FeedMedia(item, "url", 1, "audio/mp3")
            item.setMedia(media)
            (feed.getItems() as ArrayList<FeedItem>).add(item)
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            adapter.setCompleteFeed(feed)
            adapter.close()

            val items = DBReader.getFeedItemsWithUrl(listOf("url"))
            assertNotNull(items)
            assertEquals(1, items.size)
        }
    }

    @RunWith(ParameterizedRobolectricTestRunner::class)
    class ParametrizedTests : TestBase() {
        companion object {
            @ParameterizedRobolectricTestRunner.Parameters(name = "{index}: {0}")
            @JvmStatic
            fun data(): List<Array<Any>> {
                return Arrays.asList(arrayOf(0), arrayOf(1), arrayOf(5))
            }
        }

        @ParameterizedRobolectricTestRunner.Parameter
        @JvmField
        var paramOffset: Int = 0

        @Test
        fun testGetEpisodes() {
            val numItems = 10
            val paramLimit = numItems

            val feed = Feed("url", null, "title")
            feed.setItems(ArrayList())
            for (i in 0 until numItems) {
                (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item " + i, "id" + i, "link" + i,
                        Date(i.toLong()), FeedItem.PLAYED, feed))
            }
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            adapter.setCompleteFeed(feed)
            adapter.close()

            val episodes = DBReader.getEpisodes(paramOffset, paramLimit, FeedItemFilter.unfiltered(),
                    SortOrder.DATE_NEW_OLD)
            assertNotNull(episodes)
            assertEquals(numItems - paramOffset, episodes.size)
            for (i in episodes.indices) {
                assertEquals(feed.getItems()!![numItems - 1 - paramOffset - i].getId(), episodes[i].getId())
            }
        }

        @Test
        fun testGetEpisodesInHistory() {
            val numItems = 10
            val paramLimit = numItems

            val feed = Feed("url", null, "title")
            feed.setItems(ArrayList())
            val ids = LongArray(numItems)
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            for (i in 0 until numItems) {
                val item = FeedItem(0L, "item " + i, "id" + i, "link" + i,
                        Date(i.toLong()), FeedItem.PLAYED, feed)
                val media = FeedMedia(0L, item, 10, 0, 1L, "audio/mp3", null, "url",
                        System.currentTimeMillis(), Date(i.toLong()), 10, 0L)
                item.setMedia(media)
                (feed.getItems() as ArrayList<FeedItem>).add(item)
                adapter.setSingleFeedItem(item)
                ids[i] = item.getId()
            }
            adapter.close()

            val numReturnedItems = numItems - paramOffset
            val saved = DBReader.getEpisodes(paramOffset, paramLimit,
                    FeedItemFilter(FeedItemFilter.IS_IN_HISTORY), SortOrder.COMPLETION_DATE_NEW_OLD)
            assertNotNull(saved)
            assertEquals(java.lang.String.format("Wrong size with offset %d and limit %d: ",
                            paramOffset, paramLimit),
                    numReturnedItems, saved.size)
            for (i in 0 until numReturnedItems) {
                val item = saved[i]
                assertNotNull(item.getMedia()!!.getLastPlayedTimeHistory())
                assertEquals(java.lang.String.format("Wrong sort order with offset %d and limit %d: ",
                                paramOffset, paramLimit),
                        item.getId(), ids[paramOffset + i])
            }
        }
    }
}
