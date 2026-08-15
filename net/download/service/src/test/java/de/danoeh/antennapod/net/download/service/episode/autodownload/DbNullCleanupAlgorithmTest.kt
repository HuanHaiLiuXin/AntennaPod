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
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.storage.database.PodDBAdapter
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests that the APNullCleanupAlgorithm is working correctly.
 */
@RunWith(RobolectricTestRunner::class)
class DbNullCleanupAlgorithmTest {

    companion object {
        private const val EPISODE_CACHE_SIZE = 5
    }

    private lateinit var context: Context

    private lateinit var destFolder: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        destFolder = context.getExternalCacheDir()!!
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

        val prefEdit = PreferenceManager.getDefaultSharedPreferences(context
                .getApplicationContext()).edit()
        prefEdit.putString(UserPreferences.PREF_EPISODE_CACHE_SIZE, Integer.toString(EPISODE_CACHE_SIZE))
        prefEdit.putString(UserPreferences.PREF_EPISODE_CLEANUP,
                Integer.toString(UserPreferences.EPISODE_CLEANUP_NULL))
        prefEdit.commit()

        UserPreferences.init(context)
        AutoDownloadManager.setInstance(AutoDownloadManagerImpl())
    }

    @After
    fun tearDown() {
        DBWriter.tearDownTests()
        PodDBAdapter.deleteDatabase()
        PodDBAdapter.tearDownTests()

        cleanupDestFolder(destFolder)
        assertTrue(destFolder.delete())
    }

    private fun cleanupDestFolder(destFolder: File) {
        //noinspection ConstantConditions
        for (f in destFolder.listFiles()!!) {
            assertTrue(f.delete())
        }
    }

    /**
     * A test with no items in the queue, but multiple items downloaded.
     * The null algorithm should never delete any items, even if they're played and not in the queue.
     */
    @Test
    @Throws(IOException::class)
    fun testPerformAutoCleanupShouldNotDelete() {
        val numItems = EPISODE_CACHE_SIZE * 2

        val feed = Feed("url", null, "title")
        val items = ArrayList<FeedItem>()
        feed.setItems(items)
        val files = ArrayList<File>()
        for (i in 0 until numItems) {
            val item = FeedItem(0L, "title", "id" + i, "link", Date(), FeedItem.PLAYED, feed)

            val f = File(destFolder, "file " + i)
            assertTrue(f.createNewFile())
            files.add(f)
            item.setMedia(FeedMedia(0L, item, 1, 0, 1L, "m", f.getAbsolutePath(), "url", System.currentTimeMillis(),
                    Date(numItems - i.toLong()), 0, 0L))
            items.add(item)
        }

        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        assertTrue(feed.getId() != 0L)
        for (item in items) {
            assertTrue(item.getId() != 0L)
            //noinspection ConstantConditions
            assertTrue(item.getMedia()!!.getId() != 0L)
        }
        AutoDownloadManager.getInstance()!!.performAutoCleanup(context)
        for (i in files.indices) {
            assertTrue(files[i].exists())
        }
    }
}
