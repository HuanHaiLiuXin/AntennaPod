package de.test.antennapod.playback

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.database.LongList
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter
import de.test.antennapod.EspressoTestUtils
import de.test.antennapod.IgnoreOnCi
import de.test.antennapod.ui.UITestUtils
import org.awaitility.Awaitility
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import java.util.concurrent.TimeUnit

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import java.util.concurrent.TimeUnit.MILLISECONDS
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Test cases for starting and ending playback from the MainActivity and AudioPlayerActivity.
 */
@LargeTest
@IgnoreOnCi
class PlaybackTest {
    @get:Rule
    val activityTestRule = ActivityTestRule(MainActivity::class.java, false, false)

    private lateinit var uiTestUtils: UITestUtils
    protected lateinit var context: Context

    @Before
    @Throws(Exception::class)
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        EspressoTestUtils.clearPreferences()
        EspressoTestUtils.clearDatabase()

        uiTestUtils = UITestUtils(context)
        uiTestUtils.setup()
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        activityTestRule.finishActivity()
        EspressoTestUtils.tryKillPlaybackService()
        uiTestUtils.tearDown()
    }

    @Test
    @Throws(Exception::class)
    fun testContinuousPlaybackOnMultipleEpisodes() {
        setContinuousPlaybackPreference(true)
        uiTestUtils.addLocalFeedData(true)
        activityTestRule.launchActivity(Intent())

        val queue = DBReader.getQueue()
        val first = queue.get(0)
        val second = queue.get(1)

        playFromQueue(0)
        Awaitility.await().atMost(2L, TimeUnit.SECONDS).until {
            first.getMedia()!!.getId() == PlaybackPreferences.getCurrentlyPlayingFeedMediaId()
        }
        Awaitility.await().atMost(6L, TimeUnit.SECONDS).until {
            second.getMedia()!!.getId() == PlaybackPreferences.getCurrentlyPlayingFeedMediaId()
        }
    }


    @Test
    @Throws(Exception::class)
    fun testReplayEpisodeContinuousPlaybackOn() {
        replayEpisodeCheck(true)
    }

    @Test
    @Throws(Exception::class)
    fun testReplayEpisodeContinuousPlaybackOff() {
        replayEpisodeCheck(false)
    }

    @Test
    @Throws(Exception::class)
    fun testSmartMarkAsPlayed_Skip_Average() {
        doTestSmartMarkAsPlayed_Skip_ForEpisode(0)
    }

    @Test
    @Throws(Exception::class)
    fun testSmartMarkAsPlayed_Skip_LastEpisodeInQueue() {
        doTestSmartMarkAsPlayed_Skip_ForEpisode(-1)
    }

    @Test
    @Throws(Exception::class)
    fun testStartLocal() {
        uiTestUtils.addLocalFeedData(true)
        activityTestRule.launchActivity(Intent())
        DBWriter.clearQueue()!!.get()
        startLocalPlayback()
    }

    @Test
    @Throws(Exception::class)
    fun testPlayingItemAddsToQueue() {
        uiTestUtils.addLocalFeedData(true)
        activityTestRule.launchActivity(Intent())
        DBWriter.clearQueue()!!.get()
        val queue = DBReader.getQueue()
        assertEquals(0, queue.size)
        startLocalPlayback()
        Awaitility.await().atMost(1L, TimeUnit.SECONDS).until {
            1 == DBReader.getQueue().size
        }
    }

    @Test
    @Throws(Exception::class)
    fun testContinousPlaybackOffSingleEpisode() {
        setContinuousPlaybackPreference(false)
        uiTestUtils.addLocalFeedData(true)
        activityTestRule.launchActivity(Intent())
        DBWriter.clearQueue()!!.get()
        startLocalPlayback()
    }

    protected fun setContinuousPlaybackPreference(value: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean(UserPreferences.PREF_FOLLOW_QUEUE, value).commit()
    }

    protected fun setSkipKeepsEpisodePreference(value: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean(UserPreferences.PREF_SKIP_KEEPS_EPISODE, value).commit()
    }

    protected fun setSmartMarkAsPlayedPreference(smartMarkAsPlayedSecs: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString(UserPreferences.PREF_SMART_MARK_AS_PLAYED_SECS,
                Integer.toString(smartMarkAsPlayedSecs, 10))
                .commit()
    }

    private fun skipEpisode() {
        context.sendBroadcast(MediaButtonStarter.createIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT))
    }

    protected fun pauseEpisode() {
        context.sendBroadcast(MediaButtonStarter.createIntent(context, KeyEvent.KEYCODE_MEDIA_PAUSE))
    }

    protected fun startLocalPlayback() {
        EspressoTestUtils.clickBottomNavOverflow(R.string.episodes_label)

        val episodes = DBReader.getEpisodes(0, 10,
                FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD)
        val allEpisodesMatcher: Matcher<View> = allOf(withId(R.id.recyclerView), isDisplayed(),
                hasMinimumChildCount(2))
        onView(isRoot()).perform(EspressoTestUtils.waitForView(allEpisodesMatcher, 1000L))
        onView(allEpisodesMatcher).perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(0, EspressoTestUtils.clickChildViewWithId(R.id.secondaryActionButton)))

        val media = episodes.get(0).getMedia()!!
        Awaitility.await().atMost(1L, TimeUnit.SECONDS).until {
            media.getId() == PlaybackPreferences.getCurrentlyPlayingFeedMediaId()
        }
    }

    /**
     *
     * @param itemIdx The 0-based index of the episode to be played in the queue.
     */
    protected fun playFromQueue(itemIdx: Int) {
        val queue = DBReader.getQueue()

        val queueMatcher: Matcher<View> = allOf(withId(R.id.recyclerView), isDisplayed(), hasMinimumChildCount(2))
        onView(isRoot()).perform(EspressoTestUtils.waitForView(queueMatcher, 1000L))
        onView(queueMatcher).perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(itemIdx, EspressoTestUtils.clickChildViewWithId(R.id.secondaryActionButton)))

        val media = queue.get(itemIdx).getMedia()!!
        Awaitility.await().atMost(1L, TimeUnit.SECONDS).until {
            media.getId() == PlaybackPreferences.getCurrentlyPlayingFeedMediaId()
        }

    }

    /**
     * Check if an episode can be played twice without problems.
     */
    @Throws(Exception::class)
    protected fun replayEpisodeCheck(followQueue: Boolean) {
        setContinuousPlaybackPreference(followQueue)
        uiTestUtils.addLocalFeedData(true)
        DBWriter.clearQueue()!!.get()
        activityTestRule.launchActivity(Intent())
        val episodes = DBReader.getEpisodes(0, 10,
                FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD)

        startLocalPlayback()
        val media = episodes.get(0).getMedia()!!
        Awaitility.await().atMost(1L, TimeUnit.SECONDS).until {
            media.getId() == PlaybackPreferences.getCurrentlyPlayingFeedMediaId()
        }

        Awaitility.await().atMost(5L, TimeUnit.SECONDS).until {
            media.getId() != PlaybackPreferences.getCurrentlyPlayingFeedMediaId()
        }

        startLocalPlayback()

        Awaitility.await().atMost(1L, TimeUnit.SECONDS).until {
            media.getId() == PlaybackPreferences.getCurrentlyPlayingFeedMediaId()
        }
    }

    @Throws(Exception::class)
    protected fun doTestSmartMarkAsPlayed_Skip_ForEpisode(itemIdxNegAllowed: Int) {
        setSmartMarkAsPlayedPreference(60)
        // ensure when an episode is skipped, it is removed due to smart as played
        setSkipKeepsEpisodePreference(false)
        uiTestUtils.setMediaFileName("30sec.mp3")
        uiTestUtils.addLocalFeedData(true)

        val queue = DBReader.getQueueIDList()
        val fiIdx: Int
        if (itemIdxNegAllowed >= 0) {
            fiIdx = itemIdxNegAllowed
        } else { // negative index: count from the end, with -1 being the last one, etc.
            fiIdx = queue.size() + itemIdxNegAllowed
        }
        val feedItemId = queue.get(fiIdx)
        queue.removeIndex(fiIdx)
        assertFalse(queue.contains(feedItemId)) // Verify that episode is in queue only once

        activityTestRule.launchActivity(Intent())
        playFromQueue(fiIdx)

        skipEpisode()

        //  assert item no longer in queue (needs to wait till skip is asynchronously processed)
        Awaitility.await()
                .atMost(5000L, MILLISECONDS)
                .until { !DBReader.getQueueIDList().contains(feedItemId) }
        assertTrue(DBReader.getFeedItem(feedItemId)!!.isPlayed())
    }
}
