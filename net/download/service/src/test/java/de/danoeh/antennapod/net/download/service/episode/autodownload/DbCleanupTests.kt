package de.danoeh.antennapod.net.download.service.episode.autodownload

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

import java.io.File
import java.io.IOException
import java.util.ArrayList
import java.util.Date

import androidx.test.platform.app.InstrumentationRegistry

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.storage.preferences.UserPreferences

import de.danoeh.antennapod.storage.database.PodDBAdapter
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test class for DBTasks.
 */
@RunWith(RobolectricTestRunner::class)
open class DbCleanupTests {

    companion object {
        const val EPISODE_CACHE_SIZE = 5
    }

    private var cleanupAlgorithm: Int = 0

    lateinit var context: Context

    private lateinit var destFolder: File

    constructor() {
        setCleanupAlgorithm(UserPreferences.EPISODE_CLEANUP_DEFAULT)
    }

    protected fun setCleanupAlgorithm(cleanupAlgorithm: Int) {
        this.cleanupAlgorithm = cleanupAlgorithm
    }

    @Before
    open fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        destFolder = File(context.getCacheDir(), "DbCleanupTests")
        //noinspection ResultOfMethodCallIgnored
        destFolder.mkdir()
        cleanupDestFolder(destFolder)
        assertNotNull(destFolder)
        assertTrue(destFolder.exists())
        assertTrue(destFolder.canWrite())

        // create new database
        PodDBAdapter.init(context)
        PodDBAdapter.deleteDatabase()
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.close()

        val prefEdit = PreferenceManager
                .getDefaultSharedPreferences(context.getApplicationContext()).edit()
        prefEdit.putString(UserPreferences.PREF_EPISODE_CACHE_SIZE, Integer.toString(EPISODE_CACHE_SIZE))
        prefEdit.putString(UserPreferences.PREF_EPISODE_CLEANUP, Integer.toString(cleanupAlgorithm))
        prefEdit.putBoolean(UserPreferences.PREF_AUTODL_GLOBAL, true)
        prefEdit.commit()

        UserPreferences.init(context)
        PlaybackPreferences.init(context)
        SynchronizationSettings.init(context)
        AutoDownloadManager.setInstance(AutoDownloadManagerImpl())
        SynchronizationQueue.setInstance(SynchronizationQueueStub())
    }

    @After
    open fun tearDown() {
        cleanupDestFolder(destFolder)
        assertTrue(destFolder.delete())

        DBWriter.tearDownTests()
        PodDBAdapter.tearDownTests()
    }

    private fun cleanupDestFolder(destFolder: File) {
        //noinspection ConstantConditions
        for (f in destFolder.listFiles()!!) {
            assertTrue(f.delete())
        }
    }

    protected fun populateItems(numItems: Int, feed: Feed, items: MutableList<FeedItem>,
                                files: MutableList<File>, itemState: Int, addToQueue: Boolean,
                                addToFavorites: Boolean) {
        for (i in 0 until numItems) {
            val item = FeedItem(0L, "title", "id$i", "link", Date(), itemState, feed)

            val f = File(destFolder, "file $i")
            assertTrue(f.createNewFile())
            files.add(f)
            val m = FeedMedia(0L, item, 1, 0, 1L, "m", f.getAbsolutePath(), "url", System.currentTimeMillis(),
                    Date(numItems - i.toLong()), 0, 0L)
            m.setDownloaded(true, System.currentTimeMillis())
            m.setLocalFileUrl(f.getAbsolutePath())
            item.setMedia(m)
            items.add(item)

            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            adapter.setSingleFeedItem(item)
            adapter.close()

            if (addToQueue) {
                DBWriter.addQueueItem(context, item)!!.get()
            }
            if (addToFavorites) {
                DBWriter.addFavoriteItems(mutableListOf(item))!!.get()
            }
        }
    }

    @Test
    @Throws(IOException::class)
    open fun testPerformAutoCleanupShouldNotDeleteBecauseInQueue() {
        val numItems = EPISODE_CACHE_SIZE * 2

        val feed = Feed("url", null, "title")
        val items = ArrayList<FeedItem>()
        feed.setItems(items)
        val files = ArrayList<File>()
        populateItems(numItems, feed, items, files, FeedItem.UNPLAYED, true, false)

        AutoDownloadManager.getInstance()!!.performAutoCleanup(context)
        for (file in files) {
            assertTrue(file.exists())
        }
    }

    @Test
    @Throws(IOException::class)
    open fun testPerformAutoCleanupShouldNotDeleteBecauseInQueue_withFeedsWithNoMedia() {
        val numItems = EPISODE_CACHE_SIZE * 2

        val feed = Feed("url", null, "title")
        val items = ArrayList<FeedItem>()
        feed.setItems(items)
        val files = ArrayList<File>()
        populateItems(numItems, feed, items, files, FeedItem.UNPLAYED, true, false)
        feed.getItems()!![0].setMedia(null)

        AutoDownloadManager.getInstance()!!.performAutoCleanup(context)
        for (file in files) {
            assertTrue(file.exists())
        }
    }

    @Test
    @Throws(IOException::class)
    fun testPerformAutoCleanupShouldDeleteBecauseNotInQueue() {
        val numItems = EPISODE_CACHE_SIZE * 2

        val feed = Feed("url", null, "title")
        val items = ArrayList<FeedItem>()
        feed.setItems(items)
        val files = ArrayList<File>()
        populateItems(numItems, feed, items, files, FeedItem.PLAYED, false, false)

        AutoDownloadManager.getInstance()!!.performAutoCleanup(context)
        for (i in files.indices) {
            if (i < EPISODE_CACHE_SIZE) {
                assertTrue(files[i].exists())
            } else {
                assertFalse(files[i].exists())
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testPerformAutoCleanupShouldNotDeleteBecauseFavorite() {
        val numItems = EPISODE_CACHE_SIZE * 2

        val feed = Feed("url", null, "title")
        val items = ArrayList<FeedItem>()
        feed.setItems(items)
        val files = ArrayList<File>()
        populateItems(numItems, feed, items, files, FeedItem.PLAYED, false, true)

        AutoDownloadManager.getInstance()!!.performAutoCleanup(context)
        for (file in files) {
            assertTrue(file.exists())
        }
    }
}
