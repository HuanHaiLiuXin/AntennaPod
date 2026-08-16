package de.danoeh.antennapod.net.download.service.episode.autodownload

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.util.Log

import androidx.core.util.Consumer
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry

import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterfaceStub
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.database.PodDBAdapter
import org.awaitility.Awaitility
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.io.File
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.storage.preferences.UserPreferences

/**
 * Test class for [DBWriter].
 */
@RunWith(RobolectricTestRunner::class)
class DbWriterTest {

    companion object {
        private const val TAG = "DBWriterTest"
        private const val TEST_FOLDER = "testDBWriter"
        private const val TIMEOUT = 5L

        private fun assertQueueByItemIds(message: String, vararg itemIdsExpected: Long) {
            val queue = DBReader.getQueue()
            val itemIdsActualList = toItemIds(queue)
            val itemIdsExpectedList = ArrayList<Long>(itemIdsExpected.size)
            for (id in itemIdsExpected) {
                itemIdsExpectedList.add(id)
            }

            assertEquals(message, itemIdsExpectedList, itemIdsActualList)
        }

        private fun toItemIds(items: List<FeedItem>): List<Long> {
            val itemIds = ArrayList<Long>(items.size)
            for (item in items) {
                itemIds.add(item.getId())
            }
            return itemIds
        }
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        UserPreferences.init(context)
        PlaybackPreferences.init(context)
        DownloadServiceInterface.setImpl(DownloadServiceInterfaceStub())
        SynchronizationQueue.setInstance(SynchronizationQueueStub())

        // create new database
        PodDBAdapter.init(context)
        PodDBAdapter.deleteDatabase()
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.close()

        val prefEdit = PreferenceManager.getDefaultSharedPreferences(
                context.getApplicationContext()).edit()
        prefEdit.putBoolean(UserPreferences.PREF_DELETE_REMOVES_FROM_QUEUE, true).commit()
    }

    @After
    fun tearDown() {
        PodDBAdapter.tearDownTests()
        DBWriter.tearDownTests()

        val testDir = context.getExternalFilesDir(TEST_FOLDER)
        assertNotNull(testDir)
        for (f in testDir!!.listFiles()!!) {
            //noinspection ResultOfMethodCallIgnored
            f.delete()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testSetFeedMediaPlaybackInformation() {
        val position = 50
        val lastPlayedTimeStatistics = 1000L

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

        media.setPosition(position)
        media.setLastPlayedTimeStatistics(lastPlayedTimeStatistics)
        DBWriter.setFeedMediaPlaybackInformation(media)!!.get()

        val mediaFromDb = DBReader.getFeedMedia(media.getId())!!
        assertEquals(position, mediaFromDb.getPosition())
        assertEquals(lastPlayedTimeStatistics, mediaFromDb.getLastPlayedTimeStatistics())
    }

    @Test
    @Throws(Exception::class)
    fun testSetFeedMediaLastPlayedTimeHistory() {
        val lastPlayed = Date()

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

        media.setLastPlayedTimeHistory(lastPlayed)
        DBWriter.setFeedMediaLastPlayedTimeHistory(media)!!.get()

        val mediaFromDb = DBReader.getFeedMedia(media.getId())!!
        assertEquals(lastPlayed, mediaFromDb.getLastPlayedTimeHistory())
    }

    @Test
    @Throws(Exception::class)
    fun testResetStatistics() {
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

        media.setPlayedDuration(5000)
        DBWriter.setFeedMedia(media)!!.get()
        DBWriter.resetStatistics()!!.get()

        val mediaFromDb = DBReader.getFeedMedia(media.getId())!!
        assertEquals(0, mediaFromDb.getPlayedDuration())
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeedMediaOfItem() {
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

        val mediaFile = File(context.getExternalFilesDir(TEST_FOLDER), "test.mp3")
        assertTrue(mediaFile.createNewFile())
        media.setDownloaded(true, System.currentTimeMillis())
        media.setLocalFileUrl(mediaFile.getAbsolutePath())
        DBWriter.setMediaDownloadInformation(media)!!.get()
        assertTrue(mediaFile.exists())

        DBWriter.deleteFeedMediaOfItem(context, media)!!.get()
        assertFalse(mediaFile.exists())
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeedMediaOfItemRemovesFromQueue() {
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

        DBWriter.addQueueItem(context, item)!!.get()
        assertEquals(1, DBReader.getQueue().size)

        DBWriter.deleteFeedMediaOfItem(context, media)!!.get()
        assertEquals(0, DBReader.getQueue().size)
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeedMediaOfItemWhenLastEpisodeInQueue() {
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

        DBWriter.addQueueItem(context, item)!!.get()
        assertEquals(1, DBReader.getQueue().size)

        DBWriter.deleteFeedMediaOfItem(context, media)!!.get()
        assertEquals(0, DBReader.getQueue().size)
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeed() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item", "id", "link", Date(), FeedItem.PLAYED, feed))
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        DBWriter.deleteFeed(context, feed.getId())!!.get()
        assertNull(DBReader.getFeed(feed.getId(), false, 0, Int.MAX_VALUE))
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeedItems() {
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

        DBWriter.deleteFeedItems(context, feed.getItems()!!)!!.get()
        val saved = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                SortOrder.DATE_NEW_OLD, 0, Int.MAX_VALUE)
        assertEquals(0, saved.size)
    }

    @Test
    @Throws(Exception::class)
    fun testClearPlaybackHistory() {
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

        DBWriter.addItemToPlaybackHistory(media)!!.get()
        assertNotNull(DBReader.getFeedMedia(media.getId())!!.getLastPlayedTimeHistory())

        DBWriter.clearPlaybackHistory()!!.get()
        assertNull(DBReader.getFeedMedia(media.getId())!!.getLastPlayedTimeHistory())
    }

    @Test
    @Throws(Exception::class)
    fun testAddDownloadStatus() {
        val status = de.danoeh.antennapod.model.download.DownloadResult("title", 1L,
                Feed.FEEDFILETYPE_FEED, false, de.danoeh.antennapod.model.download.DownloadError.ERROR_REQUEST_ERROR,
                "details")
        DBWriter.addDownloadStatus(status)!!.get()

        val log = DBReader.getDownloadLog()
        assertEquals(1, log.size)
        assertEquals(status.getId(), log[0].getId())
    }

    @Test
    @Throws(Exception::class)
    fun testAddQueueItem() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        val item = FeedItem(0L, "item", "id", "link", Date(), FeedItem.PLAYED, feed)
        (feed.getItems() as ArrayList<FeedItem>).add(item)
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        DBWriter.addQueueItem(context, item)!!.get()
        assertEquals(1, DBReader.getQueue().size)
    }

    @Test
    @Throws(Exception::class)
    fun testAddQueueItemAt() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        for (i in 0 until 3) {
            (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item " + i, "id" + i, "link" + i,
                    Date(i.toLong()), FeedItem.PLAYED, feed))
        }
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        DBWriter.addQueueItem(context, feed.getItems()!![0], feed.getItems()!![1])!!.get()
        DBWriter.addQueueItemAt(context, feed.getItems()!![2].getId(), 1)!!.get()

        val queue = DBReader.getQueue()
        assertEquals(3, queue.size)
        assertEquals(feed.getItems()!![0].getId(), queue[0].getId())
        assertEquals(feed.getItems()!![2].getId(), queue[1].getId())
        assertEquals(feed.getItems()!![1].getId(), queue[2].getId())
    }

    @Test
    @Throws(Exception::class)
    fun testRemoveQueueItem() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        for (i in 0 until 3) {
            (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item " + i, "id" + i, "link" + i,
                    Date(i.toLong()), FeedItem.PLAYED, feed))
        }
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        DBWriter.addQueueItem(context, *feed.getItems()!!.toTypedArray())!!.get()
        DBWriter.removeQueueItem(context, false, *longArrayOf(feed.getItems()!![1].getId()))!!.get()

        val queue = DBReader.getQueue()
        assertEquals(2, queue.size)
        assertEquals(feed.getItems()!![0].getId(), queue[0].getId())
        assertEquals(feed.getItems()!![2].getId(), queue[1].getId())
    }

    @Test
    @Throws(Exception::class)
    fun testMoveQueueItem() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        for (i in 0 until 3) {
            (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item " + i, "id" + i, "link" + i,
                    Date(i.toLong()), FeedItem.PLAYED, feed))
        }
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        DBWriter.addQueueItem(context, *feed.getItems()!!.toTypedArray())!!.get()
        DBWriter.moveQueueItem(0, 2, false)!!.get()

        val queue = DBReader.getQueue()
        assertEquals(3, queue.size)
        assertEquals(feed.getItems()!![1].getId(), queue[0].getId())
        assertEquals(feed.getItems()!![2].getId(), queue[1].getId())
        assertEquals(feed.getItems()!![0].getId(), queue[2].getId())
    }

    @Test
    @Throws(Exception::class)
    fun testMoveQueueItemsToTop() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        for (i in 0 until 3) {
            (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item " + i, "id" + i, "link" + i,
                    Date(i.toLong()), FeedItem.PLAYED, feed))
        }
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        DBWriter.addQueueItem(context, *feed.getItems()!!.toTypedArray())!!.get()
        DBWriter.moveQueueItemsToTop(listOf(feed.getItems()!![2]))!!.get()

        val queue = DBReader.getQueue()
        assertEquals(feed.getItems()!![2].getId(), queue[0].getId())
    }

    @Test
    @Throws(Exception::class)
    fun testMoveQueueItemsToBottom() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        for (i in 0 until 3) {
            (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item " + i, "id" + i, "link" + i,
                    Date(i.toLong()), FeedItem.PLAYED, feed))
        }
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        DBWriter.addQueueItem(context, *feed.getItems()!!.toTypedArray())!!.get()
        DBWriter.moveQueueItemsToBottom(listOf(feed.getItems()!![0]))!!.get()

        val queue = DBReader.getQueue()
        assertEquals(feed.getItems()!![0].getId(), queue[2].getId())
    }

    @Test
    @Throws(Exception::class)
    fun testClearQueue() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        for (i in 0 until 3) {
            (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item " + i, "id" + i, "link" + i,
                    Date(i.toLong()), FeedItem.PLAYED, feed))
        }
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        DBWriter.addQueueItem(context, *feed.getItems()!!.toTypedArray())!!.get()
        DBWriter.clearQueue()!!.get()
        assertEquals(0, DBReader.getQueue().size)
    }

    @Test
    @Throws(Exception::class)
    fun testToggleFavoriteItem() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        val item = FeedItem(0L, "item", "id", "link", Date(), FeedItem.PLAYED, feed)
        (feed.getItems() as ArrayList<FeedItem>).add(item)
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        DBWriter.toggleFavoriteItem(item)!!.get()
        assertTrue(item.isTagged(FeedItem.TAG_FAVORITE))
        DBWriter.toggleFavoriteItem(item)!!.get()
        assertFalse(item.isTagged(FeedItem.TAG_FAVORITE))
    }

    @Test
    @Throws(Exception::class)
    fun testMarkItemsPlayed() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        val item = FeedItem(0L, "item", "id", "link", Date(), FeedItem.UNPLAYED, feed)
        (feed.getItems() as ArrayList<FeedItem>).add(item)
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        DBWriter.markItemsPlayed(FeedItem.PLAYED, false, listOf(item))!!.get()
        val itemFromDb = DBReader.getFeedItem(item.getId())!!
        assertTrue(itemFromDb.isPlayed())
    }

    @Test
    @Throws(Exception::class)
    fun testRemoveFeedNewFlag() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        val item = FeedItem(0L, "item", "id", "link", Date(), FeedItem.NEW, feed)
        (feed.getItems() as ArrayList<FeedItem>).add(item)
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        DBWriter.removeFeedNewFlag(feed.getId())!!.get()
        val itemFromDb = DBReader.getFeedItem(item.getId())!!
        assertFalse(itemFromDb.isNew())
    }

    @Test
    @Throws(Exception::class)
    fun testSetFeedItem() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        val item = FeedItem(0L, "item", "id", "link", Date(), FeedItem.PLAYED, feed)
        (feed.getItems() as ArrayList<FeedItem>).add(item)
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        item.setTitle("item changed")
        DBWriter.setFeedItem(item, false)!!.get()
        val itemFromDb = DBReader.getFeedItem(item.getId())!!
        assertEquals("item changed", itemFromDb.getTitle())
    }

    @Test
    @Throws(Exception::class)
    fun testSetFeedPreferences() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        feed.setPreferences(de.danoeh.antennapod.model.feed.FeedPreferences(
                feed.getId(), de.danoeh.antennapod.model.feed.FeedPreferences.AutoDownloadSetting.GLOBAL,
                de.danoeh.antennapod.model.feed.FeedPreferences.AutoDeleteAction.GLOBAL,
                de.danoeh.antennapod.model.feed.VolumeAdaptionSetting.OFF,
                de.danoeh.antennapod.model.feed.FeedPreferences.NewEpisodesAction.GLOBAL,
                "username", "password"))
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        feed.getPreferences()!!.setKeepUpdated(false)
        DBWriter.setFeedPreferences(feed.getPreferences()!!)!!.get()
        val feedFromDb = DBReader.getFeed(feed.getId(), false, 0, Int.MAX_VALUE)!!
        assertFalse(feedFromDb.getPreferences()!!.getKeepUpdated())
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateFeedDownloadURL() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        DBWriter.updateFeedDownloadURL("url", "url2")!!.get()
        val feedFromDb = DBReader.getFeed(feed.getId(), false, 0, Int.MAX_VALUE)!!
        assertEquals("url2", feedFromDb.getDownloadUrl())
    }

    @Test
    @Throws(Exception::class)
    fun testSetFeedLastUpdateFailed() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        DBWriter.setFeedLastUpdateFailed(feed.getId(), true)!!.get()
        val feedFromDb = DBReader.getFeed(feed.getId(), false, 0, Int.MAX_VALUE)!!
        assertTrue(feedFromDb.hasLastUpdateFailed())
    }

    @Test
    @Throws(Exception::class)
    fun testSetFeedCustomTitle() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        feed.setCustomTitle("custom")
        DBWriter.setFeedCustomTitle(feed)!!.get()
        val feedFromDb = DBReader.getFeed(feed.getId(), false, 0, Int.MAX_VALUE)!!
        assertEquals("custom", feedFromDb.getCustomTitle())
    }

    @Test
    @Throws(Exception::class)
    fun testSetFeedItemsFilter() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        DBWriter.setFeedItemsFilter(feed.getId(), setOf("unplayed"))!!.get()
        val feedFromDb = DBReader.getFeed(feed.getId(), false, 0, Int.MAX_VALUE)!!
        assertTrue(feedFromDb.getItemFilter()!!.showUnplayed)
    }

    @Test
    @Throws(Exception::class)
    fun testSetFeedItemSortOrder() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        DBWriter.setFeedItemSortOrder(feed.getId(), SortOrder.DATE_NEW_OLD)!!.get()
        val feedFromDb = DBReader.getFeed(feed.getId(), false, 0, Int.MAX_VALUE)!!
        assertEquals(SortOrder.DATE_NEW_OLD, feedFromDb.getSortOrder())
    }

    @Test
    @Throws(Exception::class)
    fun testReorderQueue() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        for (i in 0 until 3) {
            (feed.getItems() as ArrayList<FeedItem>).add(FeedItem(0L, "item " + i, "id" + i, "link" + i,
                    Date(i.toLong()), FeedItem.PLAYED, feed))
        }
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        DBWriter.addQueueItem(context, *feed.getItems()!!.toTypedArray())!!.get()
        DBWriter.reorderQueue(SortOrder.EPISODE_TITLE_Z_A, false)!!.get()

        val queue = DBReader.getQueue()
        assertEquals(3, queue.size)
        assertEquals(feed.getItems()!![2].getId(), queue[0].getId())
        assertEquals(feed.getItems()!![1].getId(), queue[1].getId())
        assertEquals(feed.getItems()!![0].getId(), queue[2].getId())
    }

    @Test
    @Throws(Exception::class)
    fun testRemoveFeedWithDownloadUrl() {
        val feed = Feed("url", null, "title")
        feed.setItems(ArrayList())
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        DBWriter.removeFeedWithDownloadUrl(context, "url")
        assertNull(DBReader.getFeed(feed.getId(), false, 0, Int.MAX_VALUE))
    }

    private fun doInAdapter(action: Consumer<PodDBAdapter>) {
        val adapter = PodDBAdapter.getInstance()
        try {
            adapter.open()
            action.accept(adapter)
        } finally {
            adapter.close()
        }
    }
}
