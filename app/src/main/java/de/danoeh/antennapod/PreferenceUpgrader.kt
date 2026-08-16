package de.danoeh.antennapod

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager

import de.danoeh.antennapod.system.CrashReportWriter
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager
import org.apache.commons.lang3.StringUtils

import java.util.concurrent.TimeUnit

import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences
import de.danoeh.antennapod.ui.screen.AllEpisodesFragment
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.storage.preferences.UserPreferences.EnqueueLocation
import de.danoeh.antennapod.ui.screen.queue.QueueFragment
import de.danoeh.antennapod.ui.swipeactions.SwipeAction
import de.danoeh.antennapod.ui.swipeactions.SwipeActions

class PreferenceUpgrader {
    companion object {
        private const val PREF_CONFIGURED_VERSION = "version_code"
        private const val PREF_NAME = "app_version"

        private lateinit var prefs: SharedPreferences

        @JvmStatic
        fun checkUpgrades(context: Context) {
            prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val upgraderPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val oldVersion = upgraderPrefs.getInt(PREF_CONFIGURED_VERSION, -1)
            val newVersion = BuildConfig.VERSION_CODE

            if (oldVersion != newVersion) {
                CrashReportWriter.getFile().delete()

                upgrade(oldVersion, newVersion, context)
                upgraderPrefs.edit().putInt(PREF_CONFIGURED_VERSION, newVersion).apply()
            }
        }

        private fun upgrade(oldVersion: Int, newVersion: Int, context: Context) {
            if (oldVersion == -1) {
                //New installation
                return
            }
            if (oldVersion < 1070196) {
                // migrate episode cleanup value (unit changed from days to hours)
                val oldValueInDays = UserPreferences.getEpisodeCleanupValue()
                if (oldValueInDays > 0) {
                    UserPreferences.setEpisodeCleanupValue(oldValueInDays * 24)
                } // else 0 or special negative values, no change needed
            }
            if (oldVersion < 1070197) {
                if (prefs.getBoolean("prefMobileUpdate", false)) {
                    prefs.edit().putString("prefMobileUpdateAllowed", "everything").apply()
                }
            }
            if (oldVersion < 1070300) {
                if (prefs.getBoolean("prefEnableAutoDownloadOnMobile", false)) {
                    UserPreferences.setAllowMobileAutoDownload(true)
                }
                when (prefs.getString("prefMobileUpdateAllowed", "images")) {
                    "everything" -> {
                        UserPreferences.setAllowMobileFeedRefresh(true)
                        UserPreferences.setAllowMobileEpisodeDownload(true)
                        UserPreferences.setAllowMobileImages(true)
                    }
                    "images" -> UserPreferences.setAllowMobileImages(true)
                    "nothing" -> UserPreferences.setAllowMobileImages(false)
                }
            }
            if (oldVersion < 1070400) {
                val theme = UserPreferences.getTheme()
                if (theme == UserPreferences.ThemePreference.LIGHT) {
                    prefs.edit().putString(UserPreferences.PREF_THEME, "system").apply()
                }

                UserPreferences.setQueueLocked(false)
                UserPreferences.setStreamOverDownload(false)

                if (!prefs.contains(UserPreferences.PREF_ENQUEUE_LOCATION)) {
                    val keyOldPrefEnqueueFront = "prefQueueAddToFront"
                    val enqueueAtFront = prefs.getBoolean(keyOldPrefEnqueueFront, false)
                    val enqueueLocation = if (enqueueAtFront) EnqueueLocation.FRONT else EnqueueLocation.BACK
                    UserPreferences.setEnqueueLocation(enqueueLocation)
                }
            }
            if (oldVersion < 2010300) {
                // Migrate hardware button preferences
                if (prefs.getBoolean("prefHardwareForwardButtonSkips", false)) {
                    prefs.edit().putString(UserPreferences.PREF_HARDWARE_FORWARD_BUTTON,
                            KeyEvent.KEYCODE_MEDIA_NEXT.toString()).apply()
                }
                if (prefs.getBoolean("prefHardwarePreviousButtonRestarts", false)) {
                    prefs.edit().putString(UserPreferences.PREF_HARDWARE_PREVIOUS_BUTTON,
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS.toString()).apply()
                }
            }
            if (oldVersion < 2040000) {
                val swipePrefs = context.getSharedPreferences(SwipeActions.PREF_NAME, Context.MODE_PRIVATE)
                swipePrefs.edit().putString(SwipeActions.KEY_PREFIX_SWIPEACTIONS + QueueFragment.TAG,
                        SwipeAction.REMOVE_FROM_QUEUE + "," + SwipeAction.REMOVE_FROM_QUEUE).apply()
            }
            if (oldVersion < 2050000) {
                prefs.edit().putBoolean(UserPreferences.PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS, true).apply()
            }
            if (oldVersion < 2080000) {
                // Migrate drawer feed counter setting to reflect removal of
                // "unplayed and in inbox" (0), by changing it to "unplayed" (2)
                val feedCounterSetting = prefs.getString(UserPreferences.PREF_DRAWER_FEED_COUNTER, "1")
                if (feedCounterSetting == "0") {
                    prefs.edit().putString(UserPreferences.PREF_DRAWER_FEED_COUNTER, "2").apply()
                }

                val sleepTimerPreferences =
                        context.getSharedPreferences(SleepTimerPreferences.PREF_NAME, Context.MODE_PRIVATE)
                val timeUnits = arrayOf(TimeUnit.SECONDS, TimeUnit.MINUTES, TimeUnit.HOURS)
                val value = java.lang.Long.parseLong(SleepTimerPreferences.lastTimerValue())
                val unit = timeUnits[sleepTimerPreferences.getInt("LastTimeUnit", 1)]
                SleepTimerPreferences.setLastTimer(unit.toMinutes(value).toString())

                if (prefs.getString(UserPreferences.PREF_EPISODE_CACHE_SIZE, "20")
                        == context.getString(R.string.pref_episode_cache_unlimited)) {
                    prefs.edit().putString(UserPreferences.PREF_EPISODE_CACHE_SIZE,
                            "" + UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED).apply()
                }
            }
            if (oldVersion < 3000007) {
                if (prefs.getString("prefBackButtonBehavior", "") == "drawer") {
                    prefs.edit().putBoolean(UserPreferences.PREF_BACK_OPENS_DRAWER, true).apply()
                }
            }
            if (oldVersion < 3010000) {
                if (prefs.getString(UserPreferences.PREF_THEME, "system") == "2") {
                    prefs.edit()
                            .putString(UserPreferences.PREF_THEME, "1")
                            .putBoolean(UserPreferences.PREF_THEME_BLACK, true)
                            .apply()
                }
                UserPreferences.setAllowMobileSync(true)
                if (prefs.getString(UserPreferences.PREF_UPDATE_INTERVAL_MINUTES, ":")!!.contains(":")) {
                    // Unset or "time of day"
                    prefs.edit().putString(UserPreferences.PREF_UPDATE_INTERVAL_MINUTES, "12").apply()
                }
            }
            if (oldVersion < 3020000) {
                NotificationManagerCompat.from(context).deleteNotificationChannel("auto_download")
            }

            if (oldVersion < 3030000) {
                val allEpisodesPreferences =
                        context.getSharedPreferences(AllEpisodesFragment.PREF_NAME, Context.MODE_PRIVATE)
                val oldEpisodeSort = allEpisodesPreferences.getString(UserPreferences.PREF_SORT_ALL_EPISODES, "")
                if (!StringUtils.isAllEmpty(oldEpisodeSort)) {
                    prefs.edit().putString(UserPreferences.PREF_SORT_ALL_EPISODES, oldEpisodeSort).apply()
                }

                val oldEpisodeFilter = allEpisodesPreferences.getString("filter", "")
                if (!StringUtils.isAllEmpty(oldEpisodeFilter)) {
                    prefs.edit().putString(UserPreferences.PREF_FILTER_ALL_EPISODES, oldEpisodeFilter).apply()
                }
            }
            if (oldVersion < 3070000) {
                // If autodownloads are enabled, we will start deleting episodes.
                // To prevent accidents, force off the deletions.
                if (!UserPreferences.isEnableAutodownloadGlobal()) {
                    prefs.edit().putString(UserPreferences.PREF_EPISODE_CLEANUP,
                            "" + UserPreferences.EPISODE_CLEANUP_NULL).apply()
                }
            }
            if (newVersion == 3070003) {
                // Enable bottom navigation for beta users, so only this exact app version
                UserPreferences.setBottomNavigationEnabled(true)
            }
            if (oldVersion < 3100000) {
                // Migrate refresh interval from hours to minutes
                UserPreferences.setUpdateInterval(60L * UserPreferences.getUpdateInterval())
                FeedUpdateManager.getInstance()!!.restartUpdateAlarm(context, true)
            }
        }
    }
}
