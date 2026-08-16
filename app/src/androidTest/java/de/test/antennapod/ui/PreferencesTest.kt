package de.test.antennapod.ui

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources

import android.os.Build
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import de.danoeh.antennapod.R
import de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity
import de.danoeh.antennapod.net.download.service.episode.autodownload.APCleanupAlgorithm
import de.danoeh.antennapod.net.download.service.episode.autodownload.APNullCleanupAlgorithm
import de.danoeh.antennapod.net.download.service.episode.autodownload.APQueueCleanupAlgorithm
import de.danoeh.antennapod.net.download.service.episode.autodownload.EpisodeCleanupAlgorithm
import de.danoeh.antennapod.net.download.service.episode.autodownload.EpisodeCleanupAlgorithmFactory
import de.danoeh.antennapod.net.download.service.episode.autodownload.ExceptFavoriteCleanupAlgorithm
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.storage.preferences.UserPreferences.EnqueueLocation
import de.test.antennapod.EspressoTestUtils
import org.awaitility.Awaitility
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import java.util.Arrays

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import java.util.concurrent.TimeUnit.MILLISECONDS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue

@LargeTest
class PreferencesTest {
    private lateinit var res: Resources

    @get:Rule
    val activityTestRule = ActivityTestRule(PreferenceActivity::class.java,
            false,
            false)


    @Before
    fun setUp() {
        EspressoTestUtils.clearDatabase()
        EspressoTestUtils.clearPreferences()
        activityTestRule.launchActivity(Intent())
        val prefs = PreferenceManager.getDefaultSharedPreferences(activityTestRule.getActivity())
        prefs.edit().putBoolean(UserPreferences.PREF_AUTODL_GLOBAL, true).commit()

        res = activityTestRule.getActivity().getResources()
        UserPreferences.init(activityTestRule.getActivity())
    }

    @Test
    fun testEnablePersistentPlaybackControls() {
        // Preference is hidden on Android 11+ where the system controls notification persistence.
        assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
        val persistNotify = UserPreferences.isPersistNotify()
        EspressoTestUtils.clickPreference(R.string.user_interface_label)
        EspressoTestUtils.clickPreference(R.string.pref_persistNotify_title)
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { persistNotify != UserPreferences.isPersistNotify() }
        EspressoTestUtils.clickPreference(R.string.pref_persistNotify_title)
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { persistNotify == UserPreferences.isPersistNotify() }
    }

    @Test
    fun testSetNotificationButtons() {
        EspressoTestUtils.clickPreference(R.string.user_interface_label)
        val buttons = res.getStringArray(R.array.full_notification_buttons_options)
        EspressoTestUtils.clickPreference(R.string.pref_full_notification_buttons_title)
        // First uncheck checkboxes
        onView(withText(buttons[1])).perform(click())
        onView(withText(buttons[2])).perform(click())

        onView(withText(R.string.confirm_label)).perform(click())

        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { UserPreferences.showSkipOnFullNotification() }
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { UserPreferences.showNextChapterOnFullNotification() }
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { !UserPreferences.showPlaybackSpeedOnFullNotification() }
    }

    @Test
    fun testEnqueueLocation() {
        EspressoTestUtils.clickPreference(R.string.playback_pref)
        doTestEnqueueLocation(R.string.enqueue_location_after_current, EnqueueLocation.AFTER_CURRENTLY_PLAYING)
        doTestEnqueueLocation(R.string.enqueue_location_front, EnqueueLocation.FRONT)
        doTestEnqueueLocation(R.string.enqueue_location_back, EnqueueLocation.BACK)
        doTestEnqueueLocation(R.string.enqueue_location_random, EnqueueLocation.RANDOM)
    }

    private fun doTestEnqueueLocation(@StringRes optionResId: Int, expected: EnqueueLocation) {
        EspressoTestUtils.clickPreference(R.string.pref_enqueue_location_title)
        onView(withText(optionResId)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { expected == UserPreferences.getEnqueueLocation() }
    }

    @Test
    fun testHeadPhonesDisconnect() {
        EspressoTestUtils.clickPreference(R.string.playback_pref)
        val pauseOnHeadsetDisconnect = UserPreferences.isPauseOnHeadsetDisconnect()
        onView(withText(R.string.pref_pauseOnHeadsetDisconnect_title)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { pauseOnHeadsetDisconnect != UserPreferences.isPauseOnHeadsetDisconnect() }
        onView(withText(R.string.pref_pauseOnHeadsetDisconnect_title)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { pauseOnHeadsetDisconnect == UserPreferences.isPauseOnHeadsetDisconnect() }
    }

    @Test
    fun testHeadPhonesReconnect() {
        assumeTrue(Build.VERSION.SDK_INT < 31) // Setting hidden on Android 12+
        EspressoTestUtils.clickPreference(R.string.playback_pref)
        if (!UserPreferences.isPauseOnHeadsetDisconnect()) {
            onView(withText(R.string.pref_pauseOnHeadsetDisconnect_title)).perform(click())
            Awaitility.await().atMost(1000L, MILLISECONDS)
                    .until { UserPreferences.isPauseOnHeadsetDisconnect() }
        }
        val unpauseOnHeadsetReconnect = UserPreferences.isUnpauseOnHeadsetReconnect()
        onView(withText(R.string.pref_unpauseOnHeadsetReconnect_title)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { unpauseOnHeadsetReconnect != UserPreferences.isUnpauseOnHeadsetReconnect() }
        onView(withText(R.string.pref_unpauseOnHeadsetReconnect_title)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { unpauseOnHeadsetReconnect == UserPreferences.isUnpauseOnHeadsetReconnect() }
    }

    @Test
    fun testBluetoothReconnect() {
        assumeTrue(Build.VERSION.SDK_INT < 31) // Setting hidden on Android 12+
        EspressoTestUtils.clickPreference(R.string.playback_pref)
        if (!UserPreferences.isPauseOnHeadsetDisconnect()) {
            onView(withText(R.string.pref_pauseOnHeadsetDisconnect_title)).perform(click())
            Awaitility.await().atMost(1000L, MILLISECONDS)
                    .until { UserPreferences.isPauseOnHeadsetDisconnect() }
        }
        val unpauseOnBluetoothReconnect = UserPreferences.isUnpauseOnBluetoothReconnect()
        onView(withText(R.string.pref_unpauseOnBluetoothReconnect_title)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { unpauseOnBluetoothReconnect != UserPreferences.isUnpauseOnBluetoothReconnect() }
        onView(withText(R.string.pref_unpauseOnBluetoothReconnect_title)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { unpauseOnBluetoothReconnect == UserPreferences.isUnpauseOnBluetoothReconnect() }
    }

    @Test
    fun testContinuousPlayback() {
        EspressoTestUtils.clickPreference(R.string.playback_pref)
        val continuousPlayback = UserPreferences.isFollowQueue()
        EspressoTestUtils.clickPreference(R.string.pref_followQueue_title)
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { continuousPlayback != UserPreferences.isFollowQueue() }
        EspressoTestUtils.clickPreference(R.string.pref_followQueue_title)
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { continuousPlayback == UserPreferences.isFollowQueue() }
    }

    @Test
    fun testAutoDelete() {
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        onView(withText(R.string.pref_auto_delete_title)).perform(click())
        val autoDelete = UserPreferences.isAutoDelete()
        onView(withText(R.string.pref_auto_delete_playback_title)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { autoDelete != UserPreferences.isAutoDelete() }
        onView(withText(R.string.pref_auto_delete_playback_title)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { autoDelete == UserPreferences.isAutoDelete() }
    }

    @Test
    fun testAutoDeleteLocal() {
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        onView(withText(R.string.pref_auto_delete_title)).perform(click())
        onView(withText(R.string.pref_auto_delete_playback_title)).perform(click())
        assertTrue(UserPreferences.isAutoDelete())
        assertFalse(UserPreferences.isAutoDeleteLocal())

        onView(withText(R.string.pref_auto_local_delete_title)).perform(click())
        onView(withText(R.string.yes)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { UserPreferences.isAutoDeleteLocal() }

        onView(withText(R.string.pref_auto_local_delete_title)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { !UserPreferences.isAutoDeleteLocal() }
    }

    @Test
    fun testPlaybackSpeeds() {
        EspressoTestUtils.clickPreference(R.string.playback_pref)
        EspressoTestUtils.clickPreference(R.string.playback_speed)
        onView(isRoot()).perform(EspressoTestUtils.waitForView(withText("1.25"), 1000L))
        onView(withText("1.25")).check(matches(isDisplayed()))
    }

    @Test
    fun testSetEpisodeCache() {
        val entries = res.getStringArray(R.array.episode_cache_size_entries)
        val values = res.getStringArray(R.array.episode_cache_size_values)
        val entry = entries[entries.size / 2]
        val value = values[values.size / 2].toInt()
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        EspressoTestUtils.clickPreference(R.string.pref_automatic_download_title)
        EspressoTestUtils.clickPreference(R.string.pref_episode_cache_title)
        onView(isRoot()).perform(EspressoTestUtils.waitForView(withText(entry), 1000L))
        onView(withText(entry)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { UserPreferences.getEpisodeCacheSize() == value }
    }

    @Test
    fun testSetEpisodeCacheMin() {
        val entries = res.getStringArray(R.array.episode_cache_size_entries)
        val values = res.getStringArray(R.array.episode_cache_size_values)
        val minEntry = entries[0]
        val minValue = values[0].toInt()

        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        EspressoTestUtils.clickPreference(R.string.pref_automatic_download_title)
        EspressoTestUtils.clickPreference(R.string.pref_episode_cache_title)
        onView(withId(R.id.select_dialog_listview)).perform(swipeDown())
        onView(withText(minEntry)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { UserPreferences.getEpisodeCacheSize() == minValue }
    }

    @Test
    fun testSetEpisodeCacheMax() {
        val entries = res.getStringArray(R.array.episode_cache_size_entries)
        val values = res.getStringArray(R.array.episode_cache_size_values)
        val maxEntry = entries[entries.size - 1]
        val maxValue = values[values.size - 1].toInt()
        onView(withText(R.string.downloads_pref)).perform(click())
        onView(withText(R.string.pref_automatic_download_title)).perform(click())
        onView(withText(R.string.pref_episode_cache_title)).perform(click())
        onView(withId(R.id.select_dialog_listview)).perform(swipeUp())
        onView(withText(maxEntry)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { UserPreferences.getEpisodeCacheSize() == maxValue }
    }

    @Test
    fun testAutomaticDownload() {
        val automaticDownload = UserPreferences.isEnableAutodownloadGlobal()
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        EspressoTestUtils.clickPreference(R.string.pref_automatic_download_title)
        EspressoTestUtils.clickPreference(R.string.pref_automatic_download_title)
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { automaticDownload != UserPreferences.isEnableAutodownloadGlobal() }
        if (!UserPreferences.isEnableAutodownloadGlobal()) {
            EspressoTestUtils.clickPreference(R.string.pref_automatic_download_title)
        }
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { UserPreferences.isEnableAutodownloadGlobal() }
        val enableAutodownloadOnBattery = UserPreferences.isEnableAutodownloadOnBattery()
        EspressoTestUtils.clickPreference(R.string.pref_automatic_download_on_battery_title)
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { enableAutodownloadOnBattery != UserPreferences.isEnableAutodownloadOnBattery() }
        EspressoTestUtils.clickPreference(R.string.pref_automatic_download_on_battery_title)
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { enableAutodownloadOnBattery == UserPreferences.isEnableAutodownloadOnBattery() }
    }

    @Test
    fun testEpisodeCleanupFavoriteOnly() {
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        onView(withText(R.string.pref_auto_delete_title)).perform(click())
        onView(withText(R.string.pref_episode_cleanup_title)).perform(click())
        onView(withId(R.id.select_dialog_listview)).perform(swipeDown())
        onView(withText(R.string.episode_cleanup_except_favorite_removal)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { EpisodeCleanupAlgorithmFactory.build() is ExceptFavoriteCleanupAlgorithm }
    }

    @Test
    fun testEpisodeCleanupQueueOnly() {
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        onView(withText(R.string.pref_auto_delete_title)).perform(click())
        onView(withText(R.string.pref_episode_cleanup_title)).perform(click())
        onView(withId(R.id.select_dialog_listview)).perform(swipeDown())
        onView(withText(R.string.episode_cleanup_queue_removal)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { EpisodeCleanupAlgorithmFactory.build() is APQueueCleanupAlgorithm }
    }

    @Test
    fun testEpisodeCleanupNeverAlg() {
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        onView(withText(R.string.pref_auto_delete_title)).perform(click())
        onView(withText(R.string.pref_episode_cleanup_title)).perform(click())
        onView(withId(R.id.select_dialog_listview)).perform(swipeUp())
        onView(withText(R.string.episode_cleanup_never)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { EpisodeCleanupAlgorithmFactory.build() is APNullCleanupAlgorithm }
    }

    @Test
    fun testEpisodeCleanupClassic() {
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        onView(withText(R.string.pref_auto_delete_title)).perform(click())
        onView(withText(R.string.pref_episode_cleanup_title)).perform(click())
        onView(withId(R.id.select_dialog_listview)).perform(swipeDown())
        onView(withText(R.string.episode_cleanup_after_listening)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until {
                    val alg = EpisodeCleanupAlgorithmFactory.build()
                    if (alg is APCleanupAlgorithm) {
                        val cleanupAlg = alg as APCleanupAlgorithm
                        cleanupAlg.getNumberOfHoursAfterPlayback() == 0
                    } else {
                        false
                    }
                }
    }

    @Test
    fun testEpisodeCleanupNumDays() {
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        onView(withText(R.string.pref_auto_delete_title)).perform(click())
        EspressoTestUtils.clickPreference(R.string.pref_episode_cleanup_title)
        val search = res.getQuantityString(R.plurals.episode_cleanup_days_after_listening, 3, 3)
        onView(withText(search)).perform(scrollTo())
        onView(withText(search)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until {
                    val alg = EpisodeCleanupAlgorithmFactory.build()
                    if (alg is APCleanupAlgorithm) {
                        val cleanupAlg = alg as APCleanupAlgorithm
                        cleanupAlg.getNumberOfHoursAfterPlayback() == 72 // 5 days
                    } else {
                        false
                    }
                }
    }

    @Test
    fun testRewindChange() {
        val seconds = UserPreferences.getRewindSecs()
        val deltas = res.getIntArray(R.array.seek_delta_values)

        EspressoTestUtils.clickPreference(R.string.playback_pref)
        EspressoTestUtils.clickPreference(R.string.pref_rewind)

        val currentIndex = Arrays.binarySearch(deltas, seconds)
        assertTrue(currentIndex >= 0 && currentIndex < deltas.size)  // found?

        // Find next value (wrapping around to next)
        val newIndex = (currentIndex + 1) % deltas.size
        onView(withText(deltas[newIndex].toString() + " seconds")).perform(click())

        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { UserPreferences.getRewindSecs() == deltas[newIndex] }
    }

    @Test
    fun testFastForwardChange() {
        EspressoTestUtils.clickPreference(R.string.playback_pref)
        for (i in 2 downTo 1) { // repeat twice to catch any error where fastforward is tracking rewind
            val seconds = UserPreferences.getFastForwardSecs()
            val deltas = res.getIntArray(R.array.seek_delta_values)

            EspressoTestUtils.clickPreference(R.string.pref_fast_forward)

            val currentIndex = Arrays.binarySearch(deltas, seconds)
            assertTrue(currentIndex >= 0 && currentIndex < deltas.size)  // found?

            // Find next value (wrapping around to next)
            val newIndex = (currentIndex + 1) % deltas.size

            onView(withText(deltas[newIndex].toString() + " seconds")).perform(click())

            Awaitility.await().atMost(1000L, MILLISECONDS)
                    .until { UserPreferences.getFastForwardSecs() == deltas[newIndex] }
        }
    }

    @Test
    fun testDeleteRemovesFromQueue() {
        EspressoTestUtils.clickPreference(R.string.downloads_pref)
        if (!UserPreferences.shouldDeleteRemoveFromQueue()) {
            EspressoTestUtils.clickPreference(R.string.pref_delete_removes_from_queue_title)
            Awaitility.await().atMost(1000L, MILLISECONDS)
                    .until { UserPreferences.shouldDeleteRemoveFromQueue() }
        }
        val deleteRemovesFromQueue = UserPreferences.shouldDeleteRemoveFromQueue()
        onView(withText(R.string.pref_delete_removes_from_queue_title)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { deleteRemovesFromQueue != UserPreferences.shouldDeleteRemoveFromQueue() }
        onView(withText(R.string.pref_delete_removes_from_queue_title)).perform(click())
        Awaitility.await().atMost(1000L, MILLISECONDS)
                .until { deleteRemovesFromQueue == UserPreferences.shouldDeleteRemoveFromQueue() }
    }
}
