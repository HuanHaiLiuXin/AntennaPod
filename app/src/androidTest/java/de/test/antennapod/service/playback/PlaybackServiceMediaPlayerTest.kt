package de.test.antennapod.service.playback

import android.content.Context
import android.util.Log

import android.os.Build
import androidx.test.annotation.UiThreadTest
import androidx.test.filters.MediumTest

import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer
import de.danoeh.antennapod.playback.base.PlayerStatus
import de.danoeh.antennapod.playback.service.internal.LocalPSMP
import de.danoeh.antennapod.storage.database.PodDBAdapter
import de.test.antennapod.EspressoTestUtils
import junit.framework.AssertionFailedError

import org.apache.commons.io.IOUtils

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayList
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.model.playback.Playable
import de.test.antennapod.util.service.download.HTTPBin
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue

/**
 * Test class for LocalPSMP
 */
@MediumTest
@Ignore("Tests the old playback service that is disabled with Gradle flags")
class PlaybackServiceMediaPlayerTest {
    companion object {
        private const val TAG = "PsmpTest"
        private const val PLAYABLE_DEST_URL = "psmptestfile.mp3"
        private const val LATCH_TIMEOUT_SECONDS = 3
    }

    private var PLAYABLE_LOCAL_URL: String? = null

    private lateinit var httpServer: HTTPBin
    private lateinit var playableFileUrl: String
    private var psmp: PlaybackServiceMediaPlayer? = null
    @Volatile
    private var assertionError: AssertionFailedError? = null

    @After
    @UiThreadTest
    @Throws(Exception::class)
    fun tearDown() {
        if (Build.VERSION.SDK_INT < 30) {
            return // Test ignored on old Android versions for now
        }
        PodDBAdapter.deleteDatabase()
        httpServer.stop()
        if (psmp != null) {
            psmp!!.shutdown()
            psmp = null
        }
    }

    @Before
    @UiThreadTest
    @Throws(Exception::class)
    fun setUp() {
        assumeTrue(Build.VERSION.SDK_INT >= 30) // Ignored on old Android versions for now

        assertionError = null
        psmp = null
        EspressoTestUtils.clearPreferences()
        EspressoTestUtils.clearDatabase()

        val context = getInstrumentation().getTargetContext()

        httpServer = HTTPBin()
        httpServer.start()
        playableFileUrl = httpServer.getBaseUrl() + "/files/0"

        var cacheDir = context.getExternalFilesDir("testFiles")
        if (cacheDir == null)
            cacheDir = context.getExternalFilesDir("testFiles")
        val dest = File(cacheDir, PLAYABLE_DEST_URL)

        assertNotNull(cacheDir)
        assertTrue(cacheDir!!.canWrite())
        assertTrue(cacheDir!!.canRead())
        if (!dest.exists()) {
            val i = getInstrumentation().getContext().getAssets().open("3sec.mp3")
            val o = FileOutputStream(File(cacheDir, PLAYABLE_DEST_URL))
            IOUtils.copy(i, o)
            o.flush()
            o.close()
            i.close()
        }
        PLAYABLE_LOCAL_URL = dest.getAbsolutePath()
        assertEquals(0, httpServer.serveFile(dest))
    }

    private fun checkPSMPInfo(info: PlaybackServiceMediaPlayer.PSMPInfo) {
        try {
            when (info.getPlayerStatus()) {
                PlayerStatus.PLAYING,
                PlayerStatus.PAUSED,
                PlayerStatus.PREPARED,
                PlayerStatus.PREPARING,
                PlayerStatus.INITIALIZED,
                PlayerStatus.INITIALIZING,
                PlayerStatus.SEEKING -> assertNotNull(info.getPlayable())
                PlayerStatus.STOPPED,
                PlayerStatus.ERROR -> assertNull(info.getPlayable())
                else -> {
                }
            }
        } catch (e: AssertionFailedError) {
            if (assertionError == null)
                assertionError = e
        }
    }

    @Test
    @UiThreadTest
    fun testInit() {
        val c = getInstrumentation().getTargetContext()
        psmp = LocalPSMP(c, DefaultPSMPCallback())
    }

    private fun writeTestPlayable(downloadUrl: String, fileUrl: String?): Playable {
        val f = Feed(0L, null, "f", "l", "d", null, null, null, null,
                "i", null, null, "l", System.currentTimeMillis())
        val prefs = FeedPreferences(f.getId(), FeedPreferences.AutoDownloadSetting.GLOBAL,
                FeedPreferences.AutoDeleteAction.NEVER,
                VolumeAdaptionSetting.OFF, FeedPreferences.NewEpisodesAction.NOTHING, null, null)
        f.setPreferences(prefs)
        val items = ArrayList<FeedItem>()
        f.setItems(items)
        val i = FeedItem(0L, "t", "i", "l", Date(), FeedItem.UNPLAYED, f)
        items.add(i)
        val media = FeedMedia(0L, i, 0, 0, 0L, "audio/wav", fileUrl, downloadUrl,
                if (fileUrl == null) 0L else System.currentTimeMillis(), null, 0, 0L)
        i.setMedia(media)
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(f)
        assertTrue(media.getId() != 0L)
        adapter.close()
        return media
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPlayMediaObjectStreamNoStartNoPrepare() {
        val c = getInstrumentation().getTargetContext()
        val countDownLatch = CountDownLatch(2)
        val callback = CancelablePSMPCallback(object : DefaultPSMPCallback() {
            override fun statusChanged(newInfo: PlaybackServiceMediaPlayer.PSMPInfo) {
                try {
                    checkPSMPInfo(newInfo)
                    if (newInfo.getPlayerStatus() == PlayerStatus.ERROR) {
                        throw IllegalStateException("MediaPlayer error")
                    }
                    if (countDownLatch.getCount() == 0L) {
                        fail()
                    } else if (countDownLatch.getCount() == 2L) {
                        assertEquals(PlayerStatus.INITIALIZING, newInfo.getPlayerStatus())
                        countDownLatch.countDown()
                    } else {
                        assertEquals(PlayerStatus.INITIALIZED, newInfo.getPlayerStatus())
                        countDownLatch.countDown()
                    }
                } catch (e: AssertionFailedError) {
                    if (assertionError == null)
                        assertionError = e
                }
            }
        })
        psmp = LocalPSMP(c, callback)
        val p = writeTestPlayable(playableFileUrl, null)
        psmp!!.playMediaObject(p, true, false, false)
        val res = countDownLatch.await(LATCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        if (assertionError != null)
            throw assertionError!!
        assertTrue(res)

        assertSame(PlayerStatus.INITIALIZED, psmp!!.getPSMPInfo().getPlayerStatus())
        assertFalse(psmp!!.isStartWhenPrepared())
        callback.cancel()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPlayMediaObjectStreamStartNoPrepare() {
        val c = getInstrumentation().getTargetContext()
        val countDownLatch = CountDownLatch(2)
        val callback = CancelablePSMPCallback(object : DefaultPSMPCallback() {
            override fun statusChanged(newInfo: PlaybackServiceMediaPlayer.PSMPInfo) {
                try {
                    checkPSMPInfo(newInfo)
                    if (newInfo.getPlayerStatus() == PlayerStatus.ERROR) {
                        throw IllegalStateException("MediaPlayer error")
                    }
                    if (countDownLatch.getCount() == 0L) {
                        fail()
                    } else if (countDownLatch.getCount() == 2L) {
                        assertEquals(PlayerStatus.INITIALIZING, newInfo.getPlayerStatus())
                        countDownLatch.countDown()
                    } else {
                        assertEquals(PlayerStatus.INITIALIZED, newInfo.getPlayerStatus())
                        countDownLatch.countDown()
                    }
                } catch (e: AssertionFailedError) {
                    if (assertionError == null)
                        assertionError = e
                }
            }
        })
        psmp = LocalPSMP(c, callback)
        val p = writeTestPlayable(playableFileUrl, null)
        psmp!!.playMediaObject(p, true, true, false)

        val res = countDownLatch.await(LATCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        if (assertionError != null)
            throw assertionError!!
        assertTrue(res)

        assertSame(PlayerStatus.INITIALIZED, psmp!!.getPSMPInfo().getPlayerStatus())
        assertTrue(psmp!!.isStartWhenPrepared())
        callback.cancel()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPlayMediaObjectStreamNoStartPrepare() {
        val c = getInstrumentation().getTargetContext()
        val countDownLatch = CountDownLatch(4)
        val callback = CancelablePSMPCallback(object : DefaultPSMPCallback() {
            override fun statusChanged(newInfo: PlaybackServiceMediaPlayer.PSMPInfo) {
                try {
                    checkPSMPInfo(newInfo)
                    if (newInfo.getPlayerStatus() == PlayerStatus.ERROR) {
                        throw IllegalStateException("MediaPlayer error")
                    }
                    if (countDownLatch.getCount() == 0L) {
                        fail()
                    } else if (countDownLatch.getCount() == 4L) {
                        assertEquals(PlayerStatus.INITIALIZING, newInfo.getPlayerStatus())
                    } else if (countDownLatch.getCount() == 3L) {
                        assertEquals(PlayerStatus.INITIALIZED, newInfo.getPlayerStatus())
                    } else if (countDownLatch.getCount() == 2L) {
                        assertEquals(PlayerStatus.PREPARING, newInfo.getPlayerStatus())
                    } else if (countDownLatch.getCount() == 1L) {
                        assertEquals(PlayerStatus.PREPARED, newInfo.getPlayerStatus())
                    }
                    countDownLatch.countDown()
                } catch (e: AssertionFailedError) {
                    if (assertionError == null)
                        assertionError = e
                }
            }
        })
        psmp = LocalPSMP(c, callback)
        val p = writeTestPlayable(playableFileUrl, null)
        psmp!!.playMediaObject(p, true, false, true)
        val res = countDownLatch.await(LATCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        if (assertionError != null)
            throw assertionError!!
        assertTrue(res)
        assertSame(PlayerStatus.PREPARED, psmp!!.getPSMPInfo().getPlayerStatus())
        callback.cancel()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPlayMediaObjectStreamStartPrepare() {
        val c = getInstrumentation().getTargetContext()
        val countDownLatch = CountDownLatch(5)
        val callback = CancelablePSMPCallback(object : DefaultPSMPCallback() {
            override fun statusChanged(newInfo: PlaybackServiceMediaPlayer.PSMPInfo) {
                try {
                    checkPSMPInfo(newInfo)
                    if (newInfo.getPlayerStatus() == PlayerStatus.ERROR) {
                        throw IllegalStateException("MediaPlayer error")
                    }
                    if (countDownLatch.getCount() == 0L) {
                        fail()
                    } else if (countDownLatch.getCount() == 5L) {
                        assertEquals(PlayerStatus.INITIALIZING, newInfo.getPlayerStatus())
                    } else if (countDownLatch.getCount() == 4L) {
                        assertEquals(PlayerStatus.INITIALIZED, newInfo.getPlayerStatus())
                    } else if (countDownLatch.getCount() == 3L) {
                        assertEquals(PlayerStatus.PREPARING, newInfo.getPlayerStatus())
                    } else if (countDownLatch.getCount() == 2L) {
                        assertEquals(PlayerStatus.PREPARED, newInfo.getPlayerStatus())
                    } else if (countDownLatch.getCount() == 1L) {
                        assertEquals(PlayerStatus.PLAYING, newInfo.getPlayerStatus())
                    }
                    countDownLatch.countDown()
                } catch (e: AssertionFailedError) {
                    if (assertionError == null)
                        assertionError = e
                }
            }
        })
        psmp = LocalPSMP(c, callback)
        val p = writeTestPlayable(playableFileUrl, null)
        psmp!!.playMediaObject(p, true, true, true)
        val res = countDownLatch.await(LATCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        if (assertionError != null)
            throw assertionError!!
        assertTrue(res)
        assertSame(PlayerStatus.PLAYING, psmp!!.getPSMPInfo().getPlayerStatus())
        callback.cancel()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPlayMediaObjectLocalNoStartNoPrepare() {
        val c = getInstrumentation().getTargetContext()
        val countDownLatch = CountDownLatch(2)
        val callback = CancelablePSMPCallback(object : DefaultPSMPCallback() {
            override fun statusChanged(newInfo: PlaybackServiceMediaPlayer.PSMPInfo) {
                try {
                    checkPSMPInfo(newInfo)
                    if (newInfo.getPlayerStatus() == PlayerStatus.ERROR) {
                        throw IllegalStateException("MediaPlayer error")
                    }
                    if (countDownLatch.getCount() == 0L) {
                        fail()
                    } else if (countDownLatch.getCount() == 2L) {
                        assertEquals(PlayerStatus.INITIALIZING, newInfo.getPlayerStatus())
                        countDownLatch.countDown()
                    } else {
                        assertEquals(PlayerStatus.INITIALIZED, newInfo.getPlayerStatus())
                        countDownLatch.countDown()
                    }
                } catch (e: AssertionFailedError) {
                    if (assertionError == null)
                        assertionError = e
                }
            }
        })
        psmp = LocalPSMP(c, callback)
        val p = writeTestPlayable(playableFileUrl, PLAYABLE_LOCAL_URL)
        psmp!!.playMediaObject(p, false, false, false)
        val res = countDownLatch.await(LATCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        if (assertionError != null)
            throw assertionError!!
        assertTrue(res)
        assertSame(PlayerStatus.INITIALIZED, psmp!!.getPSMPInfo().getPlayerStatus())
        assertFalse(psmp!!.isStartWhenPrepared())
        callback.cancel()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPlayMediaObjectLocalStartNoPrepare() {
        val c = getInstrumentation().getTargetContext()
        val countDownLatch = CountDownLatch(2)
        val callback = CancelablePSMPCallback(object : DefaultPSMPCallback() {
            override fun statusChanged(newInfo: PlaybackServiceMediaPlayer.PSMPInfo) {
                try {
                    checkPSMPInfo(newInfo)
                    if (newInfo.getPlayerStatus() == PlayerStatus.ERROR) {
                        throw IllegalStateException("MediaPlayer error")
                    }
                    if (countDownLatch.getCount() == 0L) {
                        fail()
                    } else if (countDownLatch.getCount() == 2L) {
                        assertEquals(PlayerStatus.INITIALIZING, newInfo.getPlayerStatus())
                        countDownLatch.countDown()
                    } else {
                        assertEquals(PlayerStatus.INITIALIZED, newInfo.getPlayerStatus())
                        countDownLatch.countDown()
                    }
                } catch (e: AssertionFailedError) {
                    if (assertionError == null)
                        assertionError = e
                }
            }
        })
        psmp = LocalPSMP(c, callback)
        val p = writeTestPlayable(playableFileUrl, PLAYABLE_LOCAL_URL)
        psmp!!.playMediaObject(p, false, true, false)
        val res = countDownLatch.await(LATCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        if (assertionError != null)
            throw assertionError!!
        assertTrue(res)
        assertSame(PlayerStatus.INITIALIZED, psmp!!.getPSMPInfo().getPlayerStatus())
        assertTrue(psmp!!.isStartWhenPrepared())
        callback.cancel()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPlayMediaObjectLocalNoStartPrepare() {
        val c = getInstrumentation().getTargetContext()
        val countDownLatch = CountDownLatch(4)
        val callback = CancelablePSMPCallback(object : DefaultPSMPCallback() {
            override fun statusChanged(newInfo: PlaybackServiceMediaPlayer.PSMPInfo) {
                try {
                    checkPSMPInfo(newInfo)
                    if (newInfo.getPlayerStatus() == PlayerStatus.ERROR) {
                        throw IllegalStateException("MediaPlayer error")
                    }
                    if (countDownLatch.getCount() == 0L) {
                        fail()
                    } else if (countDownLatch.getCount() == 4L) {
                        assertEquals(PlayerStatus.INITIALIZING, newInfo.getPlayerStatus())
                    } else if (countDownLatch.getCount() == 3L) {
                        assertEquals(PlayerStatus.INITIALIZED, newInfo.getPlayerStatus())
                    } else if (countDownLatch.getCount() == 2L) {
                        assertEquals(PlayerStatus.PREPARING, newInfo.getPlayerStatus())
                    } else if (countDownLatch.getCount() == 1L) {
                        assertEquals(PlayerStatus.PREPARED, newInfo.getPlayerStatus())
                    }
                    countDownLatch.countDown()
                } catch (e: AssertionFailedError) {
                    if (assertionError == null)
                        assertionError = e
                }
            }
        })
        psmp = LocalPSMP(c, callback)
        val p = writeTestPlayable(playableFileUrl, PLAYABLE_LOCAL_URL)
        psmp!!.playMediaObject(p, false, false, true)
        val res = countDownLatch.await(LATCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        if (assertionError != null)
            throw assertionError!!
        assertTrue(res)
        assertSame(PlayerStatus.PREPARED, psmp!!.getPSMPInfo().getPlayerStatus())
        callback.cancel()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPlayMediaObjectLocalStartPrepare() {
        val c = getInstrumentation().getTargetContext()
        val countDownLatch = CountDownLatch(5)
        val callback = CancelablePSMPCallback(object : DefaultPSMPCallback() {
            override fun statusChanged(newInfo: PlaybackServiceMediaPlayer.PSMPInfo) {
                try {
                    checkPSMPInfo(newInfo)
                    if (newInfo.getPlayerStatus() == PlayerStatus.ERROR) {
                        throw IllegalStateException("MediaPlayer error")
                    }
                    if (countDownLatch.getCount() == 0L) {
                        fail()
                    } else if (countDownLatch.getCount() == 5L) {
                        assertEquals(PlayerStatus.INITIALIZING, newInfo.getPlayerStatus())
                    } else if (countDownLatch.getCount() == 4L) {
                        assertEquals(PlayerStatus.INITIALIZED, newInfo.getPlayerStatus())
                    } else if (countDownLatch.getCount() == 3L) {
                        assertEquals(PlayerStatus.PREPARING, newInfo.getPlayerStatus())
                    } else if (countDownLatch.getCount() == 2L) {
                        assertEquals(PlayerStatus.PREPARED, newInfo.getPlayerStatus())
                    } else if (countDownLatch.getCount() == 1L) {
                        assertEquals(PlayerStatus.PLAYING, newInfo.getPlayerStatus())
                    }

                } catch (e: AssertionFailedError) {
                    if (assertionError == null)
                        assertionError = e
                } finally {
                    countDownLatch.countDown()
                }
            }
        })
        psmp = LocalPSMP(c, callback)
        val p = writeTestPlayable(playableFileUrl, PLAYABLE_LOCAL_URL)
        psmp!!.playMediaObject(p, false, true, true)
        val res = countDownLatch.await(LATCH_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        if (assertionError != null)
            throw assertionError!!
        assertTrue(res)
        assertSame(PlayerStatus.PLAYING, psmp!!.getPSMPInfo().getPlayerStatus())
        callback.cancel()
    }

    @Throws(InterruptedException::class)
    private fun pauseTestSkeleton(initialState: PlayerStatus, stream: Boolean, abandonAudioFocus: Boolean,
                                  reinit: Boolean, timeoutSeconds: Long) {
        val c = getInstrumentation().getTargetContext()
        val latchCount = if (stream && reinit) 2 else 1
        val countDownLatch = CountDownLatch(latchCount)

        val callback = CancelablePSMPCallback(object : DefaultPSMPCallback() {
            override fun statusChanged(newInfo: PlaybackServiceMediaPlayer.PSMPInfo) {
                Log.d(TAG, "pauseTestSkeleton: statusChanged: " + newInfo.getPlayerStatus())
                checkPSMPInfo(newInfo)
                if (newInfo.getPlayerStatus() == PlayerStatus.ERROR) {
                    if (assertionError == null) {
                        assertionError = UnexpectedStateChange(newInfo.getPlayerStatus())
                    }
                } else if (initialState != PlayerStatus.PLAYING) {
                    if (assertionError == null) {
                        assertionError = UnexpectedStateChange(newInfo.getPlayerStatus())
                    }
                } else {
                    when (newInfo.getPlayerStatus()) {
                        PlayerStatus.PAUSED -> if (latchCount.toLong() == countDownLatch.getCount())
                            countDownLatch.countDown()
                        else {
                            if (assertionError == null) {
                                assertionError = UnexpectedStateChange(newInfo.getPlayerStatus())
                            }
                        }
                        PlayerStatus.INITIALIZED -> if (stream && reinit && countDownLatch.getCount() < latchCount.toLong()) {
                            countDownLatch.countDown()
                        } else if (countDownLatch.getCount() < latchCount.toLong()) {
                            if (assertionError == null)
                                assertionError = UnexpectedStateChange(newInfo.getPlayerStatus())
                        }
                        else -> {
                        }
                    }
                }

            }

            override fun shouldStop() {
                if (assertionError == null)
                    assertionError = AssertionFailedError("Unexpected call to shouldStop")
            }
        })
        psmp = LocalPSMP(c, callback)
        val p = writeTestPlayable(playableFileUrl, PLAYABLE_LOCAL_URL)
        if (initialState == PlayerStatus.PLAYING) {
            psmp!!.playMediaObject(p, stream, true, true)
        }
        psmp!!.pause(abandonAudioFocus, reinit)
        val res = countDownLatch.await(timeoutSeconds, TimeUnit.SECONDS)
        if (assertionError != null)
            throw assertionError!!
        assertTrue(res || initialState != PlayerStatus.PLAYING)
        callback.cancel()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPauseDefaultState() {
        pauseTestSkeleton(PlayerStatus.STOPPED, false, false, false, 1L)
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPausePlayingStateNoAbandonNoReinitNoStream() {
        pauseTestSkeleton(PlayerStatus.PLAYING, false, false, false, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPausePlayingStateNoAbandonNoReinitStream() {
        pauseTestSkeleton(PlayerStatus.PLAYING, true, false, false, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPausePlayingStateAbandonNoReinitNoStream() {
        pauseTestSkeleton(PlayerStatus.PLAYING, false, true, false, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPausePlayingStateAbandonNoReinitStream() {
        pauseTestSkeleton(PlayerStatus.PLAYING, true, true, false, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPausePlayingStateNoAbandonReinitNoStream() {
        pauseTestSkeleton(PlayerStatus.PLAYING, false, false, true, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPausePlayingStateNoAbandonReinitStream() {
        pauseTestSkeleton(PlayerStatus.PLAYING, true, false, true, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPausePlayingStateAbandonReinitNoStream() {
        pauseTestSkeleton(PlayerStatus.PLAYING, false, true, true, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPausePlayingStateAbandonReinitStream() {
        pauseTestSkeleton(PlayerStatus.PLAYING, true, true, true, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Throws(InterruptedException::class)
    private fun resumeTestSkeleton(initialState: PlayerStatus, timeoutSeconds: Long) {
        val c = getInstrumentation().getTargetContext()
        val latchCount = if (initialState == PlayerStatus.PAUSED || initialState == PlayerStatus.PLAYING) 2
        else if (initialState == PlayerStatus.PREPARED) 1 else 0
        val countDownLatch = CountDownLatch(latchCount)

        val callback = CancelablePSMPCallback(object : DefaultPSMPCallback() {
            override fun statusChanged(newInfo: PlaybackServiceMediaPlayer.PSMPInfo) {
                Log.d(TAG, "resumeTestSkeleton: statusChanged: " + newInfo.getPlayerStatus())
                checkPSMPInfo(newInfo)
                if (newInfo.getPlayerStatus() == PlayerStatus.ERROR) {
                    if (assertionError == null) {
                        assertionError = UnexpectedStateChange(newInfo.getPlayerStatus())
                    }
                } else if (newInfo.getPlayerStatus() == PlayerStatus.PLAYING) {
                    if (countDownLatch.getCount() == 0L) {
                        if (assertionError == null) {
                            assertionError = UnexpectedStateChange(newInfo.getPlayerStatus())
                        }
                    } else {
                        countDownLatch.countDown()
                    }
                }

            }
        })
        psmp = LocalPSMP(c, callback)
        if (initialState == PlayerStatus.PREPARED || initialState == PlayerStatus.PLAYING || initialState == PlayerStatus.PAUSED) {
            val startWhenPrepared = initialState != PlayerStatus.PREPARED
            psmp!!.playMediaObject(writeTestPlayable(playableFileUrl, PLAYABLE_LOCAL_URL), false, startWhenPrepared, true)
        }
        if (initialState == PlayerStatus.PAUSED) {
            psmp!!.pause(false, false)
        }
        psmp!!.resume()
        val res = countDownLatch.await(timeoutSeconds, TimeUnit.SECONDS)
        if (assertionError != null)
            throw assertionError!!
        assertTrue(res || (initialState != PlayerStatus.PAUSED && initialState != PlayerStatus.PREPARED))
        callback.cancel()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testResumePausedState() {
        resumeTestSkeleton(PlayerStatus.PAUSED, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testResumePreparedState() {
        resumeTestSkeleton(PlayerStatus.PREPARED, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testResumePlayingState() {
        resumeTestSkeleton(PlayerStatus.PLAYING, 1L)
    }

    @Throws(InterruptedException::class)
    private fun prepareTestSkeleton(initialState: PlayerStatus, timeoutSeconds: Long) {
        val c = getInstrumentation().getTargetContext()
        val latchCount = 1
        val countDownLatch = CountDownLatch(latchCount)
        val callback = CancelablePSMPCallback(object : DefaultPSMPCallback() {
            override fun statusChanged(newInfo: PlaybackServiceMediaPlayer.PSMPInfo) {
                Log.d(TAG, "prepareTestSkeleton: statusChanged: " + newInfo.getPlayerStatus())
                checkPSMPInfo(newInfo)
                if (newInfo.getPlayerStatus() == PlayerStatus.ERROR) {
                    if (assertionError == null) {
                        assertionError = UnexpectedStateChange(newInfo.getPlayerStatus())
                    }
                } else {
                    if (initialState == PlayerStatus.INITIALIZED && newInfo.getPlayerStatus()
                            == PlayerStatus.PREPARED) {
                        countDownLatch.countDown()
                    } else if (initialState != PlayerStatus.INITIALIZED && initialState == newInfo.getPlayerStatus()) {
                        countDownLatch.countDown()
                    }
                }
            }
        })
        psmp = LocalPSMP(c, callback)
        val p = writeTestPlayable(playableFileUrl, PLAYABLE_LOCAL_URL)
        if (initialState == PlayerStatus.INITIALIZED
                || initialState == PlayerStatus.PLAYING
                || initialState == PlayerStatus.PREPARED
                || initialState == PlayerStatus.PAUSED) {
            val prepareImmediately = initialState != PlayerStatus.INITIALIZED
            val startWhenPrepared = initialState != PlayerStatus.PREPARED
            psmp!!.playMediaObject(p, false, startWhenPrepared, prepareImmediately)
            if (initialState == PlayerStatus.PAUSED) {
                psmp!!.pause(false, false)
            }
            psmp!!.prepare()
        }

        val res = countDownLatch.await(timeoutSeconds, TimeUnit.SECONDS)
        if (initialState != PlayerStatus.INITIALIZED) {
            assertEquals(initialState, psmp!!.getPSMPInfo().getPlayerStatus())
        }

        if (assertionError != null)
            throw assertionError!!
        assertTrue(res)
        callback.cancel()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPrepareInitializedState() {
        prepareTestSkeleton(PlayerStatus.INITIALIZED, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPreparePlayingState() {
        prepareTestSkeleton(PlayerStatus.PLAYING, 1L)
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPreparePausedState() {
        prepareTestSkeleton(PlayerStatus.PAUSED, 1L)
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPreparePreparedState() {
        prepareTestSkeleton(PlayerStatus.PREPARED, 1L)
    }

    @Throws(InterruptedException::class)
    private fun reinitTestSkeleton(initialState: PlayerStatus, timeoutSeconds: Long) {
        val c = getInstrumentation().getTargetContext()
        val latchCount = 2
        val countDownLatch = CountDownLatch(latchCount)
        val callback = CancelablePSMPCallback(object : DefaultPSMPCallback() {
            override fun statusChanged(newInfo: PlaybackServiceMediaPlayer.PSMPInfo) {
                Log.d(TAG, "reinitTestSkeleton: statusChanged: " + newInfo.getPlayerStatus())
                checkPSMPInfo(newInfo)
                if (newInfo.getPlayerStatus() == PlayerStatus.ERROR) {
                    if (assertionError == null) {
                        assertionError = UnexpectedStateChange(newInfo.getPlayerStatus())
                    }
                } else {
                    if (newInfo.getPlayerStatus() == initialState) {
                        countDownLatch.countDown()
                    } else if (countDownLatch.getCount() < latchCount.toLong() && newInfo.getPlayerStatus()
                            == PlayerStatus.INITIALIZED) {
                        countDownLatch.countDown()
                    }
                }
            }
        })
        psmp = LocalPSMP(c, callback)
        val p = writeTestPlayable(playableFileUrl, PLAYABLE_LOCAL_URL)
        val prepareImmediately = initialState != PlayerStatus.INITIALIZED
        val startImmediately = initialState != PlayerStatus.PREPARED
        psmp!!.playMediaObject(p, false, startImmediately, prepareImmediately)
        if (initialState == PlayerStatus.PAUSED) {
            psmp!!.pause(false, false)
        }
        psmp!!.reinit()
        val res = countDownLatch.await(timeoutSeconds, TimeUnit.SECONDS)
        if (assertionError != null)
            throw assertionError!!
        assertTrue(res)
        callback.cancel()
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testReinitPlayingState() {
        reinitTestSkeleton(PlayerStatus.PLAYING, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testReinitPausedState() {
        reinitTestSkeleton(PlayerStatus.PAUSED, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testPreparedPlayingState() {
        reinitTestSkeleton(PlayerStatus.PREPARED, LATCH_TIMEOUT_SECONDS.toLong())
    }

    @Test
    @UiThreadTest
    @Throws(InterruptedException::class)
    fun testReinitInitializedState() {
        reinitTestSkeleton(PlayerStatus.INITIALIZED, LATCH_TIMEOUT_SECONDS.toLong())
    }

    private class UnexpectedStateChange(status: PlayerStatus) : AssertionFailedError("Unexpected state change: " + status)
}
