package de.danoeh.antennapod.storage.database

import android.content.Context
import de.danoeh.antennapod.model.download.DownloadError
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub
import de.danoeh.antennapod.storage.preferences.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

import java.util.ArrayList
import java.util.Collections
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class FeedDatabaseWriterTest {
    private var context: Context? = null

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        UserPreferences.init(context!!)
        PodDBAdapter.init(context!!)
        PodDBAdapter.deleteDatabase()
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.close()
        SynchronizationQueue.setInstance(SynchronizationQueueStub())
    }

    @Test
    fun testStoreNewFeed() {
        val feed = createFeed()
        for (i in 0 until 3) {
            (feed.getItems() as ArrayList<FeedItem>).add(createItem("item-" + i, "Item " + i, feed))
        }
        val updatedFeed = FeedDatabaseWriter.updateFeed(context!!, feed, false)
        val storedItems = DBReader.getFeedItemList(updatedFeed, FeedItemFilter.unfiltered(),
                SortOrder.EPISODE_TITLE_A_Z, 0, Int.MAX_VALUE)
        assertEquals(3, storedItems.size)
        for (i in 0 until 3) {
            assertEquals("item-" + i, storedItems[i].getItemIdentifier())
        }
    }

    @Test
    fun testAddItemsToExistingFeed() {
        var feed = createFeed()
        for (i in 0 until 3) {
            (feed.getItems() as ArrayList<FeedItem>).add(createItem("item-" + i, "Item " + i, feed))
        }
        feed = FeedDatabaseWriter.updateFeed(context!!, feed, false)

        val updatedFeed = createFeed()
        updatedFeed.setId(feed.getId())
        for (i in 3 until 6) {
            (updatedFeed.getItems() as ArrayList<FeedItem>).add(createItem("item-" + i, "Item " + i, feed))
        }
        FeedDatabaseWriter.updateFeed(context!!, updatedFeed, false)

        val dbItems = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                SortOrder.EPISODE_TITLE_A_Z, 0, Int.MAX_VALUE)
        assertEquals(6, dbItems.size)
        for (i in 0 until 6) {
            assertEquals("item-" + i, dbItems[i].getItemIdentifier())
        }
    }

    @Test
    fun testAddOrUpdateItems() {
        var feed = createFeed()
        for (i in 0 until 3) {
            (feed.getItems() as ArrayList<FeedItem>).add(createItem("item-" + i, "Item " + i, feed))
        }
        feed = FeedDatabaseWriter.updateFeed(context!!, feed, false)
        DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                SortOrder.EPISODE_TITLE_A_Z, 0, Int.MAX_VALUE)
        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, Collections.singletonList(feed.getItems()!![2]))!!.get()

        val updatedFeed = createFeed()
        updatedFeed.setId(feed.getId())
        for (i in 2 until 5) {
            (updatedFeed.getItems() as ArrayList<FeedItem>).add(createItem("item-" + i, "Item " + i, feed))
        }
        FeedDatabaseWriter.updateFeed(context!!, updatedFeed, false)

        val dbItems = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                SortOrder.EPISODE_TITLE_A_Z, 0, Int.MAX_VALUE)
        assertEquals(5, dbItems.size)
        for (i in 0 until 5) {
            assertEquals("item-" + i, dbItems[i].getItemIdentifier())
        }
        assertEquals(FeedItem.PLAYED, dbItems[2].getPlayState())
    }

    @Test
    fun testDuplicateItemsInFeed() {
        val feed = createFeed()
        (feed.getItems() as ArrayList<FeedItem>).add(createItem("id1", "Duplicate Title", feed))
        (feed.getItems() as ArrayList<FeedItem>).add(createItem("id2", "Duplicate Title", feed))
        FeedDatabaseWriter.updateFeed(context!!, feed, false) // First update just takes the feed without complaining
        FeedDatabaseWriter.updateFeed(context!!, feed, false)

        val downloadLog = DBReader.getDownloadLog()
        assertEquals(1, downloadLog.size)
        assertEquals(DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE, downloadLog[0].getReason())
    }

    @Test
    fun testGuidUpdated() {
        val feed = createFeed()
        (feed.getItems() as ArrayList<FeedItem>).add(createItem("old-id", "Unique Title", feed))
        FeedDatabaseWriter.updateFeed(context!!, feed, false)

        val newFeed = createFeed()
        (newFeed.getItems() as ArrayList<FeedItem>).add(createItem("new-id", "Unique Title", newFeed))
        val stored = FeedDatabaseWriter.updateFeed(context!!, newFeed, false)

        assertEquals(1, stored.getItems()!!.size)
        assertEquals("new-id", stored.getItems()!![0].getItemIdentifier())
    }

    @Test
    fun testUpdateFeedNewFeed() {
        val numItems = 10

        val feed = createFeed()
        for (i in 0 until numItems) {
            (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item " + i, "id " + i, "link " + i,
                    Date(), FeedItem.UNPLAYED, feed))
        }
        val newFeed = FeedDatabaseWriter.updateFeed(context!!, feed, false)

        assertEquals(feed.getId(), newFeed.getId())
        assertTrue(feed.getId() != 0L)
        for (item in feed.getItems()!!) {
            assertFalse(item.isPlayed())
            assertTrue(item.getId() != 0L)
        }
    }

    /** Two feeds with the same title, but different download URLs should be treated as different feeds. */
    @Test
    fun testUpdateFeedSameTitle() {
        val feed1 = createFeed()
        val feed2 = createFeed()
        feed2.setDownloadUrl("different url")

        val savedFeed1 = FeedDatabaseWriter.updateFeed(context!!, feed1, false)
        val savedFeed2 = FeedDatabaseWriter.updateFeed(context!!, feed2, false)

        assertTrue(savedFeed1.getId() != savedFeed2.getId())
    }

    @Test
    fun testUpdateFeedUpdatedFeed() {
        val numItemsOld = 10
        val numItemsNew = 10

        val feed = createFeed()
        for (i in 0 until numItemsOld) {
            (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item " + i, "id " + i, "link " + i,
                    Date(i.toLong()), FeedItem.PLAYED, feed))
        }
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        // ensure that objects have been saved in db, then reset
        assertTrue(feed.getId() != 0L)
        val feedID = feed.getId()
        feed.setId(0L)
        val itemIDs = ArrayList<Long>()
        for (item in feed.getItems()!!) {
            assertTrue(item.getId() != 0L)
            itemIDs.add(item.getId())
            item.setId(0L)
        }

        for (i in numItemsOld until numItemsNew + numItemsOld) {
            (feed.getItems() as ArrayList<FeedItem>).add(0, FeedItem(0L, "item " + i, "id " + i, "link " + i,
                    Date(i.toLong()), FeedItem.UNPLAYED, feed))
        }

        val newFeed = FeedDatabaseWriter.updateFeed(context!!, feed, false)
        assertNotSame(newFeed, feed)

        updatedFeedTest(newFeed, feedID, itemIDs, numItemsOld, numItemsNew)

        val feedFromDB = DBReader.getFeed(newFeed.getId(), false, 0, Int.MAX_VALUE)
        assertNotNull(feedFromDB)
        assertEquals(newFeed.getId(), feedFromDB!!.getId())
        updatedFeedTest(feedFromDB, feedID, itemIDs, numItemsOld, numItemsNew)
    }

    @Test
    fun testUpdateFeedMediaUrlResetState() {
        val feed = createFeed()
        val item = FeedItem(0L, "item", "id", "link", Date(), FeedItem.PLAYED, feed)
        feed.setItems(Collections.singletonList(item))

        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        // ensure that objects have been saved in db, then reset
        assertTrue(feed.getId() != 0L)
        assertTrue(item.getId() != 0L)

        val media = FeedMedia(item, "url", 1024, "mime/type")
        item.setMedia(media)
        val list = ArrayList<FeedItem>()
        list.add(item)
        feed.setItems(list)

        val newFeed = FeedDatabaseWriter.updateFeed(context!!, feed, false)
        assertNotSame(newFeed, feed)

        val feedFromDB = DBReader.getFeed(newFeed.getId(), false, 0, Int.MAX_VALUE)
        val feedItemFromDB = feedFromDB!!.getItems()!![0]
        assertTrue(feedItemFromDB.isNew())
    }

    @Test
    fun testUpdateFeedRemoveUnlistedItems() {
        val feed = createFeed()
        for (i in 0 until 10) {
            (feed.getItems() as ArrayList<FeedItem>).add(
                    FeedItem(0L, "item " + i, "id " + i, "link " + i, Date(i.toLong()), FeedItem.PLAYED, feed))
        }
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        // delete some items
        (feed.getItems() as ArrayList<FeedItem>).subList(0, 2).clear()
        val newFeed = FeedDatabaseWriter.updateFeed(context!!, feed, true)
        assertEquals(8, newFeed.getItems()!!.size) // 10 - 2 = 8 items

        val feedFromDB = DBReader.getFeed(newFeed.getId(), false, 0, Int.MAX_VALUE)
        assertEquals(8, feedFromDB!!.getItems()!!.size) // 10 - 2 = 8 items
    }

    @Test
    fun testUpdateFeedSetDuplicate() {
        val feed = createFeed()
        for (i in 0 until 10) {
            val item =
                    FeedItem(0L, "item " + i, "id " + i, "link " + i, Date(i.toLong()), FeedItem.PLAYED, feed)
            val media = FeedMedia(item, "download url " + i, 123, "media/mp3")
            item.setMedia(media)
            (feed.getItems() as ArrayList<FeedItem>).add(item)
        }
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        // change the guid of the first item, but leave the download url the same
        val item = feed.getItemAtIndex(0)!!
        item.setItemIdentifier("id 0-duplicate")
        item.setTitle("item 0 duplicate")
        val newFeed = FeedDatabaseWriter.updateFeed(context!!, feed, false)
        assertEquals(10, newFeed.getItems()!!.size) // id 1-duplicate replaces because the stream url is the same

        val feedFromDB = DBReader.getFeed(newFeed.getId(), false, 0, Int.MAX_VALUE)
        assertEquals(10, feedFromDB!!.getItems()!!.size) // id1-duplicate should override id 1

        val updatedItem = feedFromDB.getItemAtIndex(9)!!
        assertEquals("item 0 duplicate", updatedItem.getTitle())
        assertEquals("id 0-duplicate", updatedItem.getItemIdentifier()) // Should use the new ID for sync etc
    }


    @Suppress("SameParameterValue")
    private fun updatedFeedTest(newFeed: Feed, feedID: Long, itemIDs: List<Long>,
                                numItemsOld: Int, numItemsNew: Int) {
        assertEquals(feedID, newFeed.getId())
        assertEquals(numItemsNew + numItemsOld, newFeed.getItems()!!.size)
        Collections.reverse(newFeed.getItems())
        var lastDate = Date(0)
        for (i in 0 until numItemsOld) {
            val item = newFeed.getItems()!![i]
            assertSame(newFeed, item.getFeed())
            assertEquals(itemIDs[i], item.getId())
            assertTrue(item.isPlayed())
            assertTrue(item.getPubDate()!!.getTime() >= lastDate.getTime())
            lastDate = item.getPubDate()!!
        }
        for (i in numItemsOld until numItemsNew + numItemsOld) {
            val item = newFeed.getItems()!![i]
            assertSame(newFeed, item.getFeed())
            assertTrue(item.getId() != 0L)
            assertFalse(item.isPlayed())
            assertTrue(item.getPubDate()!!.getTime() >= lastDate.getTime())
            lastDate = item.getPubDate()!!
        }
    }

    private fun createFeed(): Feed {
        val feed = Feed("url", null, null)
        feed.setItems(ArrayList())
        return feed
    }

    private fun createItem(identifier: String, title: String, feed: Feed): FeedItem {
        val item = FeedItem()
        item.setItemIdentifier(identifier)
        item.setTitle(title)
        item.setMedia(FeedMedia(item, "url-" + title, 2, "mime"))
        item.setFeed(feed)
        return item
    }
}
