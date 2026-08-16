package de.test.antennapod.ui

import android.content.Context
import android.util.Log

import androidx.test.platform.app.InstrumentationRegistry

import de.danoeh.antennapod.event.FeedListUpdateEvent
import de.danoeh.antennapod.event.QueueEvent
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.storage.database.PodDBAdapter
import de.test.antennapod.util.service.download.HTTPBin
import de.test.antennapod.util.syndication.feedgenerator.Rss2Generator
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.EventBus
import org.junit.Assert

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.ArrayList
import java.util.Date
import java.util.Locale

/**
 * Utility methods for UI tests.
 * Starts a web server that hosts feeds, episodes and images.
 */
class UITestUtils(private val context: Context) {

    companion object {
        private val TAG = UITestUtils::class.java.getSimpleName()

        private const val NUM_FEEDS = 5
        private const val NUM_ITEMS_PER_FEED = 10
    }

    private var testFileName = "3sec.mp3"
    private var hostTextOnlyFeeds = false
    private val server = HTTPBin()
    private lateinit var destDir: File
    private lateinit var hostedFeedDir: File
    private lateinit var hostedMediaDir: File

    @JvmField
    val hostedFeeds: MutableList<Feed> = ArrayList()


    @Throws(IOException::class)
    fun setup() {
        destDir = File(context.getFilesDir(), "test/UITestUtils")
        destDir.mkdirs()
        hostedFeedDir = File(destDir, "hostedFeeds")
        hostedFeedDir.mkdir()
        hostedMediaDir = File(destDir, "hostedMediaDir")
        hostedMediaDir.mkdir()
        Assert.assertTrue(destDir.exists())
        Assert.assertTrue(hostedFeedDir.exists())
        Assert.assertTrue(hostedMediaDir.exists())
        server.start()
    }

    @Throws(IOException::class)
    fun tearDown() {
        FileUtils.deleteDirectory(destDir)
        FileUtils.deleteDirectory(hostedMediaDir)
        FileUtils.deleteDirectory(hostedFeedDir)
        server.stop()

        if (localFeedDataAdded) {
            PodDBAdapter.deleteDatabase()
        }
    }

    @Throws(IOException::class)
    fun hostFeed(feed: Feed): String {
        val feedFile = File(hostedFeedDir, feed.getTitle())
        val out = FileOutputStream(feedFile)
        val generator = Rss2Generator()
        generator.writeFeed(feed, out, "UTF-8", 0L)
        out.close()
        val id = server.serveFile(feedFile)
        Assert.assertTrue(id != -1)
        return String.format(Locale.US, "%s/files/%d", server.getBaseUrl(), id)
    }

    private fun hostFile(file: File): String {
        val id = server.serveFile(file)
        Assert.assertTrue(id != -1)
        return String.format(Locale.US, "%s/files/%d", server.getBaseUrl(), id)
    }

    @Throws(IOException::class)
    private fun newMediaFile(name: String): File {
        val mediaFile = File(hostedMediaDir, name)
        if (mediaFile.exists()) {
            mediaFile.delete()
        }
        Assert.assertFalse(mediaFile.exists())

        val `in`: InputStream = InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().open(testFileName)
        Assert.assertNotNull(`in`)

        val out = FileOutputStream(mediaFile)
        IOUtils.copy(`in`, out)
        out.close()

        return mediaFile
    }

    private var feedDataHosted = false

    /**
     * Adds feeds, images and episodes to the webserver for testing purposes.
     */
    @Throws(IOException::class)
    fun addHostedFeedData() {
        if (feedDataHosted) throw IllegalStateException("addHostedFeedData was called twice on the same instance")
        for (i in 0 until NUM_FEEDS) {
            val feed = Feed(0L, null, "Title " + i, "http://example.com/" + i, "Description of feed " + i,
                    "http://example.com/pay/feed" + i, "author " + i, "en", Feed.TYPE_RSS2, "feed" + i, null, null,
                    "http://example.com/feed/src/" + i, System.currentTimeMillis())

            // create items
            val items: MutableList<FeedItem> = ArrayList()
            for (j in 0 until NUM_ITEMS_PER_FEED) {
                val item = FeedItem(0L, "Feed " + (i + 1) + ": Item " + (j + 1), "item" + j,
                        "http://example.com/feed" + i + "/item/" + j, Date(), FeedItem.UNPLAYED, feed)
                items.add(item)

                if (!hostTextOnlyFeeds) {
                    val mediaFile = newMediaFile("feed-" + i + "-episode-" + j + ".mp3")
                    item.setMedia(FeedMedia(j.toLong(), item, 0, 0, mediaFile.length(), "audio/mp3",
                            null, hostFile(mediaFile), 0L, null, 0, 0L))
                }
            }
            feed.setItems(items)
            feed.setDownloadUrl(hostFeed(feed))
            hostedFeeds.add(feed)
        }
        feedDataHosted = true
    }


    private var localFeedDataAdded = false

    /**
     * Adds feeds, images and episodes to the local database. This method will also call addHostedFeedData if it has not
     * been called yet.
     *
     * Adds one item of each feed to the queue and to the playback history.
     *
     * This method should NOT be called if the testing class wants to download the hosted feed data.
     *
     * @param downloadEpisodes true if episodes should also be marked as downloaded.
     */
    @Throws(Exception::class)
    fun addLocalFeedData(downloadEpisodes: Boolean) {
        if (localFeedDataAdded) {
            Log.w(TAG, "addLocalFeedData was called twice on the same instance")
            // might be a flaky test, this is actually not that severe
            return
        }
        if (!feedDataHosted) {
            addHostedFeedData()
        }

        val queue: MutableList<FeedItem> = ArrayList()
        for (feed in hostedFeeds) {
            if (downloadEpisodes) {
                for (item in feed.getItems()!!) {
                    if (item.hasMedia()) {
                        val media = item.getMedia()!!
                        val fileId = StringUtils.substringAfter(media.getDownloadUrl(), "files/").toInt()
                        media.setLocalFileUrl(server.accessFile(fileId)!!.getAbsolutePath())
                        media.setDownloaded(true, System.currentTimeMillis())
                    }
                }
            }

            queue.add(feed.getItems()!!.get(0))
            if (feed.getItems()!!.get(1).hasMedia()) {
                feed.getItems()!!.get(1).getMedia()!!.setLastPlayedTimeHistory(Date())
            }
        }
        localFeedDataAdded = true

        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(*hostedFeeds.toTypedArray())
        adapter.setQueue(queue)
        adapter.close()
        EventBus.getDefault().post(FeedListUpdateEvent(hostedFeeds))
        EventBus.getDefault().post(QueueEvent.setQueue(queue))
    }

    fun setMediaFileName(filename: String) {
        testFileName = filename
    }

    fun setHostTextOnlyFeeds(hostTextOnlyFeeds: Boolean) {
        this.hostTextOnlyFeeds = hostTextOnlyFeeds
    }
}
