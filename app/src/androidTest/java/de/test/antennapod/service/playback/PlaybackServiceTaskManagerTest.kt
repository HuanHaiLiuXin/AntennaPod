package de.test.antennapod.service.playback

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.annotation.UiThreadTest
import androidx.test.filters.LargeTest

import de.danoeh.antennapod.playback.service.internal.PlaybackServiceTaskManager
import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences
import de.danoeh.antennapod.storage.database.PodDBAdapter
import de.danoeh.antennapod.ui.widget.WidgetUpdater
import org.junit.After
import org.junit.Before
import org.junit.Test

import java.util.ArrayList
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.playback.base.PlayerStatus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Test class for PlaybackServiceTaskManager
 */
@LargeTest
class PlaybackServiceTaskManagerTest {

    @After
    fun tearDown() {
        PodDBAdapter.deleteDatabase()
    }

    @Before
    fun setUp() {
        // create new database
        val context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        PodDBAdapter.init(context)
        PodDBAdapter.deleteDatabase()
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.close()
        SleepTimerPreferences.setShakeToReset(false)
        SleepTimerPreferences.setVibrate(false)
    }

    @Test
    fun testInit() {
        val context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        val pstm = PlaybackServiceTaskManager(context, defaultPSTM)
        pstm.shutdown()
    }

    private fun writeTestQueue(pref: String): List<FeedItem> {
        val NUM_ITEMS = 10
        val f = Feed(0L, null, "title", "link", "d", null, null, null, null, "id",
                null, "null", "url", System.currentTimeMillis())
        val items = ArrayList<FeedItem>()
        f.setItems(items)
        for (i in 0 until NUM_ITEMS) {
            items.add(FeedItem(0L, pref + i, pref + i, "link", Date(), FeedItem.PLAYED, f))
        }
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.setCompleteFeed(f)
        adapter.setQueue(f.getItems()!!)
        adapter.close()

        for (item in f.getItems()!!) {
            assertTrue(item.getId() != 0L)
        }
        return f.getItems()!!
    }

    @Test
    @Throws(InterruptedException::class)
    fun testStartPositionSaver() {
        val c = InstrumentationRegistry.getInstrumentation().getTargetContext()
        val NUM_COUNTDOWNS = 2
        val TIMEOUT = 3 * PlaybackServiceTaskManager.POSITION_SAVER_WAITING_INTERVAL
        val countDownLatch = CountDownLatch(NUM_COUNTDOWNS)
        val pstm = PlaybackServiceTaskManager(c, object : PlaybackServiceTaskManager.PSTMCallback {
            override fun positionSaverTick() {
                countDownLatch.countDown()
            }

            override fun requestWidgetState(): WidgetUpdater.WidgetState {
                return WidgetUpdater.WidgetState(PlayerStatus.STOPPED)
            }

            override fun onChapterLoaded(media: Playable) {

            }
        })
        pstm.startPositionSaver()
        countDownLatch.await(TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        pstm.shutdown()
    }

    @Test
    fun testIsPositionSaverActive() {
        val c = InstrumentationRegistry.getInstrumentation().getTargetContext()
        val pstm = PlaybackServiceTaskManager(c, defaultPSTM)
        pstm.startPositionSaver()
        assertTrue(pstm.isPositionSaverActive())
        pstm.shutdown()
    }

    @Test
    fun testCancelPositionSaver() {
        val c = InstrumentationRegistry.getInstrumentation().getTargetContext()
        val pstm = PlaybackServiceTaskManager(c, defaultPSTM)
        pstm.startPositionSaver()
        pstm.cancelPositionSaver()
        assertFalse(pstm.isPositionSaverActive())
        pstm.shutdown()
    }

    @Test
    @Throws(InterruptedException::class)
    fun testStartWidgetUpdater() {
        val c = InstrumentationRegistry.getInstrumentation().getTargetContext()
        val NUM_COUNTDOWNS = 2
        val TIMEOUT = 3 * PlaybackServiceTaskManager.WIDGET_UPDATER_NOTIFICATION_INTERVAL
        val countDownLatch = CountDownLatch(NUM_COUNTDOWNS)
        val pstm = PlaybackServiceTaskManager(c, object : PlaybackServiceTaskManager.PSTMCallback {
            override fun positionSaverTick() {

            }

            override fun requestWidgetState(): WidgetUpdater.WidgetState {
                countDownLatch.countDown()
                return WidgetUpdater.WidgetState(PlayerStatus.STOPPED)
            }

            override fun onChapterLoaded(media: Playable) {

            }
        })
        pstm.startWidgetUpdater()
        countDownLatch.await(TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        pstm.shutdown()
    }

    @Test
    fun testStartWidgetUpdaterAfterShutdown() {
        // Should not throw.
        val c = InstrumentationRegistry.getInstrumentation().getTargetContext()
        val pstm = PlaybackServiceTaskManager(c, defaultPSTM)
        pstm.shutdown()
        pstm.startWidgetUpdater()
    }

    @Test
    fun testIsWidgetUpdaterActive() {
        val c = InstrumentationRegistry.getInstrumentation().getTargetContext()
        val pstm = PlaybackServiceTaskManager(c, defaultPSTM)
        pstm.startWidgetUpdater()
        assertTrue(pstm.isWidgetUpdaterActive())
        pstm.shutdown()
    }

    @Test
    fun testCancelWidgetUpdater() {
        val c = InstrumentationRegistry.getInstrumentation().getTargetContext()
        val pstm = PlaybackServiceTaskManager(c, defaultPSTM)
        pstm.startWidgetUpdater()
        pstm.cancelWidgetUpdater()
        assertFalse(pstm.isWidgetUpdaterActive())
        pstm.shutdown()
    }

    @Test
    fun testCancelAllTasksNoTasksStarted() {
        val c = InstrumentationRegistry.getInstrumentation().getTargetContext()
        val pstm = PlaybackServiceTaskManager(c, defaultPSTM)
        pstm.cancelAllTasks()
        assertFalse(pstm.isPositionSaverActive())
        assertFalse(pstm.isWidgetUpdaterActive())
        pstm.shutdown()
    }

    @Test
    @UiThreadTest
    fun testCancelAllTasksAllTasksStarted() {
        val c = InstrumentationRegistry.getInstrumentation().getTargetContext()
        val pstm = PlaybackServiceTaskManager(c, defaultPSTM)
        pstm.startWidgetUpdater()
        pstm.startPositionSaver()
        pstm.cancelAllTasks()
        assertFalse(pstm.isPositionSaverActive())
        assertFalse(pstm.isWidgetUpdaterActive())
        pstm.shutdown()
    }

    private val defaultPSTM = object : PlaybackServiceTaskManager.PSTMCallback {
        override fun positionSaverTick() {

        }

        override fun requestWidgetState(): WidgetUpdater.WidgetState {
            return WidgetUpdater.WidgetState(PlayerStatus.STOPPED)
        }

        override fun onChapterLoaded(media: Playable) {

        }
    }
}
