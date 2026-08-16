package de.danoeh.antennapod.net.download.service.feed.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap

import androidx.test.platform.app.InstrumentationRegistry

import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterfaceStub
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.storage.database.PodDBAdapter
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import org.hamcrest.CoreMatchers.endsWith
import org.hamcrest.CoreMatchers.startsWith
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaMetadataRetriever

import java.io.File
import java.io.IOException
import java.util.ArrayList
import java.util.Arrays
import java.util.Objects

import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter

/**
 * Test local feeds handling in class LocalFeedUpdater.
 */
@RunWith(RobolectricTestRunner::class)
class LocalFeedUpdaterTest {

    companion object {
        /**
         * URL to locate the local feed media files on the external storage (SD card).
         * The exact URL doesn't matter here as access to external storage is mocked
         * (seems not to be supported by Robolectric).
         */
        private const val LOCAL_FEED_URL =
                "content://com.android.externalstorage.documents/tree/primary%3ADownload%2Flocal-feed"
        private const val LOCAL_FEED_DIR1 = "src/test/assets/local-feed1"
        private const val LOCAL_FEED_DIR2 = "src/test/assets/local-feed2"

        /**
         * Create a DocumentFile mock object.
         */
        private fun mockDocumentFile(fileName: String, mimeType: String): FastDocumentFile {
            return FastDocumentFile(fileName, mimeType, Uri.parse("file:///path/" + fileName), 0L, 0L)
        }

        private fun mockLocalFolder(folderName: String): List<FastDocumentFile> {
            val files = ArrayList<FastDocumentFile>()
            for (f in Objects.requireNonNull(File(folderName).listFiles())) {
                val extension = MimeTypeMap.getFileExtensionFromUrl(f.getPath())
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                files.add(FastDocumentFile(f.getName(), mimeType,
                        Uri.parse(f.toURI().toString()), f.length(), f.lastModified()))
            }
            return files
        }
    }

    private lateinit var context: Context

    @Before
    @Throws(Exception::class)
    fun setUp() {
        // Initialize environment
        context = InstrumentationRegistry.getInstrumentation().getContext()
        UserPreferences.init(context)
        PlaybackPreferences.init(context)
        SynchronizationSettings.init(context)
        DownloadServiceInterface.setImpl(DownloadServiceInterfaceStub())

        // Initialize database
        PodDBAdapter.init(context)
        PodDBAdapter.deleteDatabase()
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.close()

        mapDummyMetadata(LOCAL_FEED_DIR1)
        mapDummyMetadata(LOCAL_FEED_DIR2)
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("mp3", "audio/mp3")
    }

    @After
    fun tearDown() {
        DBWriter.tearDownTests()
        PodDBAdapter.tearDownTests()
    }

    /**
     * Test adding a new local feed
     */
    @Test
    @Throws(Exception::class)
    fun testUpdateFeedWithNewFeed() {
        val feed = Feed(LOCAL_FEED_URL, null, "Feed Title")

        val mockedStatic = Mockito.mockStatic(FastDocumentFile::class.java)
        val files = mockLocalFolder(LOCAL_FEED_DIR1)
        mockedStatic.`when`<List<FastDocumentFile>> { FastDocumentFile.list(any(), any()) }.thenReturn(files)

        try {
            val savedFeed = LocalFeedUpdater.updateFeed(feed, context, null)
            assertEquals(2, savedFeed!!.getItems()!!.size)

            (savedFeed.getItems() as ArrayList<FeedItem>).sortBy { it.getTitle() }
            assertEquals("Local MP3 File 1", savedFeed.getItems()!![0].getTitle())
            assertEquals("Local MP3 File 2", savedFeed.getItems()!![1].getTitle())
        } finally {
            mockedStatic.close()
        }
    }

    /**
     * Test updating an existing local feed with no changes
     */
    @Test
    @Throws(Exception::class)
    fun testUpdateFeedWithNoChanges() {
        val feed = Feed(LOCAL_FEED_URL, null, "Feed Title")

        val files = mockLocalFolder(LOCAL_FEED_DIR1)
        val mockedStatic = Mockito.mockStatic(FastDocumentFile::class.java)
        mockedStatic.`when`<List<FastDocumentFile>> { FastDocumentFile.list(any(), any()) }.thenReturn(files)

        var savedFeed: Feed? = null
        try {
            savedFeed = LocalFeedUpdater.updateFeed(feed, context, null)
        } finally {
            mockedStatic.close()
        }

        assertEquals(2, savedFeed!!.getItems()!!.size)
        assertThat(LocalFeedUpdater.getImageUrl(mockLocalFolder(LOCAL_FEED_DIR1), Uri.parse(LOCAL_FEED_URL)),
                startsWith("content://"))
        assertThat(LocalFeedUpdater.getImageUrl(mockLocalFolder(LOCAL_FEED_DIR1), Uri.parse(LOCAL_FEED_URL)),
                endsWith("/folder.jpg"))

        val savedItems = DBReader.getFeedItemList(savedFeed, FeedItemFilter.unfiltered(),
                SortOrder.DATE_NEW_OLD, 0, Int.MAX_VALUE)
        assertEquals(2, savedItems.size)

        val mockedStatic2 = Mockito.mockStatic(FastDocumentFile::class.java)
        mockedStatic2.`when`<List<FastDocumentFile>> { FastDocumentFile.list(any(), any()) }.thenReturn(files)
        try {
            val updatedFeed = LocalFeedUpdater.updateFeed(savedFeed, context, null)
            assertEquals(2, updatedFeed!!.getItems()!!.size)
        } finally {
            mockedStatic2.close()
        }
    }

    /**
     * Test removing local feed media items which are no longer present
     */
    @Test
    @Throws(Exception::class)
    fun testUpdateFeedWithRemovedMediaItem() {
        val feed = Feed(LOCAL_FEED_URL, null, "Feed Title")

        val files = mockLocalFolder(LOCAL_FEED_DIR1)
        val mockedStatic = Mockito.mockStatic(FastDocumentFile::class.java)
        mockedStatic.`when`<List<FastDocumentFile>> { FastDocumentFile.list(any(), any()) }.thenReturn(files)

        var savedFeed: Feed? = null
        try {
            savedFeed = LocalFeedUpdater.updateFeed(feed, context, null)
        } finally {
            mockedStatic.close()
        }

        assertEquals(2, savedFeed!!.getItems()!!.size)

        val remainingFiles = files.subList(0, 1)
        val mockedStatic2 = Mockito.mockStatic(FastDocumentFile::class.java)
        mockedStatic2.`when`<List<FastDocumentFile>> { FastDocumentFile.list(any(), any()) }
                .thenReturn(remainingFiles)
        try {
            val updatedFeed = LocalFeedUpdater.updateFeed(savedFeed, context, null)
            assertEquals(1, updatedFeed!!.getItems()!!.size)
        } finally {
            mockedStatic2.close()
        }
    }

    /**
     * Test when local feed has same podcast episode twice with different file names
     */
    @Test
    @Throws(Exception::class)
    fun testUpdateFeedWithDuplicateTitles() {
        val feed = Feed(LOCAL_FEED_URL, null, "Feed Title")

        val mockedStatic = Mockito.mockStatic(FastDocumentFile::class.java)
        val files = mockLocalFolder(LOCAL_FEED_DIR2)
        mockedStatic.`when`<List<FastDocumentFile>> { FastDocumentFile.list(any(), any()) }.thenReturn(files)

        try {
            val savedFeed = LocalFeedUpdater.updateFeed(feed, context, null)
            assertEquals(2, savedFeed!!.getItems()!!.size)

            (savedFeed.getItems() as ArrayList<FeedItem>).sortBy { it.getTitle() }
            assertEquals("Local MP3 File 1", savedFeed.getItems()!![0].getTitle())
            assertEquals("Local MP3 File 2", savedFeed.getItems()!![1].getTitle())
        } finally {
            mockedStatic.close()
        }
    }

    /**
     * Test when local feed has same podcast episode twice with different file names
     */
    @Test
    @Throws(Exception::class)
    fun testGetImageUrl() {
        assertThat(LocalFeedUpdater.getImageUrl(mockLocalFolder(LOCAL_FEED_DIR1), Uri.parse(LOCAL_FEED_URL)),
                startsWith("content://"))
        assertThat(LocalFeedUpdater.getImageUrl(mockLocalFolder(LOCAL_FEED_DIR1), Uri.parse(LOCAL_FEED_URL)),
                endsWith("/folder.jpg"))

        val files = mockLocalFolder(LOCAL_FEED_DIR2)
        assertThat(LocalFeedUpdater.getImageUrl(files, Uri.parse(LOCAL_FEED_URL)),
                startsWith("content://"))
        assertThat(LocalFeedUpdater.getImageUrl(files, Uri.parse(LOCAL_FEED_URL)),
                endsWith("/Folder.png"))

        val noImageFiles = ArrayList<FastDocumentFile>()
        noImageFiles.add(mockDocumentFile("some-file.mp3", "audio/mp3"))
        assertThat(LocalFeedUpdater.getImageUrl(noImageFiles, Uri.parse(LOCAL_FEED_URL)),
                startsWith("content://"))
        assertThat(LocalFeedUpdater.getImageUrl(noImageFiles, Uri.parse(LOCAL_FEED_URL)),
                endsWith("/some-file.mp3"))
    }

    /**
     * Map dummy metadata to a local folder
     */
    private fun mapDummyMetadata(localFeedDir: String) {
        for (fileName in Objects.requireNonNull(File(localFeedDir).list())) {
            val path = localFeedDir + '/' + fileName
            ShadowMediaMetadataRetriever.addMetadata(path,
                    MediaMetadataRetriever.METADATA_KEY_DURATION, "10")
            ShadowMediaMetadataRetriever.addMetadata(path,
                    MediaMetadataRetriever.METADATA_KEY_TITLE, fileName)
            ShadowMediaMetadataRetriever.addMetadata(path,
                    MediaMetadataRetriever.METADATA_KEY_DATE, "20200601T222324")
        }
    }
}
