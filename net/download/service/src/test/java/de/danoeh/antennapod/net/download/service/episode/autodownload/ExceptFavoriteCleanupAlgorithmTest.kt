package de.danoeh.antennapod.net.download.service.episode.autodownload

import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.io.File
import java.io.IOException
import java.util.ArrayList

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.storage.preferences.UserPreferences

/**
 * Tests that the APFavoriteCleanupAlgorithm is working correctly.
 */
@RunWith(RobolectricTestRunner::class)
class ExceptFavoriteCleanupAlgorithmTest : DbCleanupTests() {
    private val numberOfItems = EPISODE_CACHE_SIZE * 2

    init {
        setCleanupAlgorithm(UserPreferences.EPISODE_CLEANUP_EXCEPT_FAVORITE)
        AutoDownloadManager.setInstance(AutoDownloadManagerImpl())
    }

    @Test
    @Throws(IOException::class)
    fun testPerformAutoCleanupHandleUnplayed() {
        val feed = Feed("url", null, "title")
        val items = ArrayList<FeedItem>()
        feed.setItems(items)
        val files = ArrayList<File>()
        populateItems(numberOfItems, feed, items, files, FeedItem.UNPLAYED, false, false)

        AutoDownloadManager.getInstance()!!.performAutoCleanup(context)
        for (i in files.indices) {
            if (i < EPISODE_CACHE_SIZE) {
                assertTrue("Only enough items should be deleted", files[i].exists())
            } else {
                assertFalse("Expected episode to be deleted", files[i].exists())
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testPerformAutoCleanupDeletesQueued() {
        val feed = Feed("url", null, "title")
        val items = ArrayList<FeedItem>()
        feed.setItems(items)
        val files = ArrayList<File>()
        populateItems(numberOfItems, feed, items, files, FeedItem.UNPLAYED, true, false)

        AutoDownloadManager.getInstance()!!.performAutoCleanup(context)
        for (i in files.indices) {
            if (i < EPISODE_CACHE_SIZE) {
                assertTrue("Only enough items should be deleted", files[i].exists())
            } else {
                assertFalse("Queued episodes should be deleted", files[i].exists())
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testPerformAutoCleanupSavesFavorited() {
        val feed = Feed("url", null, "title")
        val items = ArrayList<FeedItem>()
        feed.setItems(items)
        val files = ArrayList<File>()
        populateItems(numberOfItems, feed, items, files, FeedItem.UNPLAYED, false, true)

        AutoDownloadManager.getInstance()!!.performAutoCleanup(context)
        for (i in files.indices) {
            assertTrue("Favorite episodes should should not be deleted", files[i].exists())
        }
    }

    override fun testPerformAutoCleanupShouldNotDeleteBecauseInQueue() {
        // Yes it should
    }

    override fun testPerformAutoCleanupShouldNotDeleteBecauseInQueue_withFeedsWithNoMedia() {
        // Yes it should
    }
}
