package de.test.antennapod.service.download

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.LargeTest

import java.io.File
import java.io.IOException

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.net.download.service.feed.remote.Downloader
import de.danoeh.antennapod.net.download.service.feed.remote.HttpDownloader
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.model.download.DownloadRequest
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.model.download.DownloadError
import de.test.antennapod.util.service.download.HTTPBin
import org.junit.After
import org.junit.Before
import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

@LargeTest
class HttpDownloaderTest {
    companion object {
        private const val TAG = "HttpDownloaderTest"
        private const val DOWNLOAD_DIR = "testdownloads"
    }

    private var url404: String? = null
    private var urlAuth: String? = null
    private var destDir: File? = null
    private lateinit var httpServer: HTTPBin

    @After
    @Throws(Exception::class)
    fun tearDown() {
        val contents = destDir!!.listFiles()
        for (f in contents!!) {
            assertTrue(f.delete())
        }

        httpServer.stop()
    }

    @Before
    @Throws(Exception::class)
    fun setUp() {
        UserPreferences.init(InstrumentationRegistry.getInstrumentation().getTargetContext())
        val context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        destDir = context.getExternalFilesDir(DOWNLOAD_DIR)
        if (destDir == null) { // Emulator without SD card
            destDir = File(context.getFilesDir(), DOWNLOAD_DIR)
            destDir!!.mkdirs()
        }
        assertNotNull(destDir)
        assertTrue(destDir!!.exists())
        httpServer = HTTPBin()
        httpServer.start()
        url404 = httpServer.getBaseUrl() + "/status/404"
        urlAuth = httpServer.getBaseUrl() + "/basic-auth/user/passwd"
    }

    private fun setupFeedFile(downloadUrl: String, title: String, deleteExisting: Boolean): Feed {
        val feedfile = Feed(downloadUrl, "")
        val fileUrl = File(destDir, title).getAbsolutePath()
        val file = File(fileUrl)
        if (deleteExisting) {
            Log.d(TAG, "Deleting file: " + file.delete())
        }
        feedfile.setLocalFileUrl(fileUrl)
        return feedfile
    }

    private fun download(url: String, title: String, expectedResult: Boolean): Downloader {
        return download(url, title, expectedResult, true, null, null)
    }

    private fun download(url: String, title: String, expectedResult: Boolean, deleteExisting: Boolean,
                         username: String?, password: String?): Downloader {
        val feedFile = setupFeedFile(url, title, deleteExisting)
        val request = DownloadRequest(feedFile.getLocalFileUrl()!!, url, title, 0L, Feed.FEEDFILETYPE_FEED,
                username, password, Bundle(), false)
        val downloader: Downloader = HttpDownloader(request)
        downloader.call()
        val status: DownloadResult = downloader.result
        assertNotNull(status)
        assertEquals(expectedResult, status.isSuccessful())
        // the file should not exist if the download has failed and deleteExisting was true
        assertTrue(!deleteExisting || File(feedFile.getLocalFileUrl()!!).exists() == expectedResult)
        return downloader
    }

    @Test
    fun testPassingHttp() {
        download(httpServer.getBaseUrl() + "/status/200", "test200", true)
    }

    @Test
    fun testRedirect() {
        download(httpServer.getBaseUrl() + "/redirect/4", "testRedirect", true)
    }

    @Test
    fun testGzip() {
        download(httpServer.getBaseUrl() + "/gzip/100", "testGzip", true)
    }

    @Test
    fun test404() {
        download(url404!!, "test404", false)
    }

    @Test
    fun testCancel() {
        val url = httpServer.getBaseUrl() + "/delay/3"
        val feedFile = setupFeedFile(url, "delay", true)
        val downloader: Downloader = HttpDownloader(DownloadRequest(feedFile.getLocalFileUrl()!!,
                url, "delay", 0L, Feed.FEEDFILETYPE_FEED, null, null, Bundle(), false))
        val t = object : Thread() {
            override fun run() {
                downloader.call()
            }
        }
        t.start()
        downloader.cancel()
        try {
            t.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        val result = downloader.result
        assertFalse(result.isSuccessful())
    }

    @Test
    fun testDeleteOnFailShouldDelete() {
        val downloader = download(url404!!, "testDeleteOnFailShouldDelete", false, true, null, null)
        assertFalse(File(downloader.getDownloadRequest().getDestination()).exists())
    }

    @Test
    @Throws(IOException::class)
    fun testDeleteOnFailShouldNotDelete() {
        val filename = "testDeleteOnFailShouldDelete"
        val dest = File(destDir, filename)
        dest.delete()
        assertTrue(dest.createNewFile())
        val downloader = download(url404!!, filename, false, false, null, null)
        assertTrue(File(downloader.getDownloadRequest().getDestination()).exists())
    }

    @Test
    @Throws(InterruptedException::class)
    fun testAuthenticationShouldSucceed() {
        download(urlAuth!!, "testAuthSuccess", true, true, "user", "passwd")
    }

    @Test
    fun testAuthenticationShouldFail() {
        val downloader = download(urlAuth!!, "testAuthSuccess", false, true, "user", "Wrong passwd")
        assertEquals(DownloadError.ERROR_UNAUTHORIZED, downloader.result.getReason())
    }
}
