package de.test.antennapod.ui

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.MediumTest
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import org.junit.After
import org.junit.Before
import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Test for the UITestUtils. Makes sure that all URLs are reachable and that the class does not cause any crashes.
 */
@MediumTest
class UITestUtilsTest {

    private lateinit var uiTestUtils: UITestUtils

    @Before
    @Throws(Exception::class)
    fun setUp() {
        uiTestUtils = UITestUtils(InstrumentationRegistry.getInstrumentation().getTargetContext())
        uiTestUtils.setup()
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        uiTestUtils.tearDown()
    }

    @Test
    @Throws(Exception::class)
    fun testAddHostedFeeds() {
        uiTestUtils.addHostedFeedData()
        val feeds = uiTestUtils.hostedFeeds
        assertNotNull(feeds)
        assertFalse(feeds.isEmpty())

        for (feed in feeds) {
            testUrlReachable(feed.getDownloadUrl()!!)
            for (item in feed.getItems()!!) {
                if (item.hasMedia()) {
                    testUrlReachable(item.getMedia()!!.getDownloadUrl()!!)
                }
            }
        }
    }

    @Throws(Exception::class)
    fun testUrlReachable(strUtl: String) {
        val url = URL(strUtl)
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestMethod("GET")
        conn.connect()
        val rc = conn.getResponseCode()
        assertEquals(HttpURLConnection.HTTP_OK, rc)
        conn.disconnect()
    }

    @Throws(Exception::class)
    private fun addLocalFeedDataCheck(downloadEpisodes: Boolean) {
        uiTestUtils.addLocalFeedData(downloadEpisodes)
        assertNotNull(uiTestUtils.hostedFeeds)
        assertFalse(uiTestUtils.hostedFeeds.isEmpty())

        for (feed in uiTestUtils.hostedFeeds) {
            assertTrue(feed.getId() != 0L)
            for (item in feed.getItems()!!) {
                assertTrue(item.getId() != 0L)
                if (item.hasMedia()) {
                    assertTrue(item.getMedia()!!.getId() != 0L)
                    if (downloadEpisodes) {
                        assertTrue(item.getMedia()!!.isDownloaded())
                        assertNotNull(item.getMedia()!!.getLocalFileUrl())
                        val file = File(item.getMedia()!!.getLocalFileUrl()!!)
                        assertTrue(file.exists())
                    }
                }
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testAddLocalFeedDataNoDownload() {
        addLocalFeedDataCheck(false)
    }

    @Test
    @Throws(Exception::class)
    fun testAddLocalFeedDataDownload() {
        addLocalFeedDataCheck(true)
    }
}
