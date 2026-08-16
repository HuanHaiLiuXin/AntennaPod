package de.danoeh.antennapod.net.download.service.episode.autodownload

import java.io.File
import java.io.IOException
import java.util.ArrayList

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub
import de.danoeh.antennapod.storage.preferences.UserPreferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests that the APQueueCleanupAlgorithm is working correctly.
 */
@RunWith(RobolectricTestRunner::class)
class DbQueueCleanupAlgorithmTest : DbCleanupTests() {

    init {
        setCleanupAlgorithm(UserPreferences.EPISODE_CLEANUP_QUEUE)
        AutoDownloadManager.setInstance(AutoDownloadManagerImpl())
        SynchronizationQueue.setInstance(SynchronizationQueueStub())
    }

    /**
     * For APQueueCleanupAlgorithm we expect even unplayed episodes to be deleted if needed
     * if they aren't in the queue.
     */
    @Test
    @Throws(IOException::class)
    fun testPerformAutoCleanupHandleUnplayed() {
        val numItems = EPISODE_CACHE_SIZE * 2

        val feed = Feed("url", null, "title")
        val items = ArrayList<FeedItem>()
        feed.setItems(items)
        val files = ArrayList<File>()
        populateItems(numItems, feed, items, files, FeedItem.UNPLAYED, false, false)

        AutoDownloadManager.getInstance()!!.performAutoCleanup(context)
        for (i in files.indices) {
            if (i < EPISODE_CACHE_SIZE) {
                assertTrue(files[i].exists())
            } else {
                assertFalse(files[i].exists())
            }
        }
    }
}
