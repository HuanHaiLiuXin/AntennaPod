package de.danoeh.antennapod.storage.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent

import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager

import de.danoeh.antennapod.model.feed.FeedOrder
import org.json.JSONArray
import org.json.JSONException

import java.io.File
import java.io.IOException
import java.net.Proxy
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.ArrayList
import java.util.Arrays
import java.util.HashSet
import java.util.Locale

import de.danoeh.antennapod.model.download.ProxyConfig
import de.danoeh.antennapod.model.feed.FeedCounter
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.model.feed.SubscriptionsFilter

/**
 * Provides access to preferences set by the user in the settings screen. A
 * private instance of this class must first be instantiated via
 * init() or otherwise every public method will throw an Exception
 * when called.
 */
abstract class UserPreferences private constructor() {

    enum class ThemePreference {
        LIGHT, DARK, BLACK, SYSTEM
    }

    enum class EnqueueLocation {
        BACK, FRONT, AFTER_CURRENTLY_PLAYING, RANDOM
    }

    companion object {
        private const val TAG = "UserPreferences"

        // User Interface
        const val PREF_THEME = "prefTheme"
        const val PREF_THEME_BLACK = "prefThemeBlack"
        const val PREF_TINTED_COLORS = "prefTintedColors"
        const val PREF_HIDDEN_DRAWER_ITEMS = "prefHiddenDrawerItems"
        const val PREF_DRAWER_ITEM_ORDER = "prefDrawerItemOrder"
        const val PREF_DRAWER_FEED_ORDER = "prefDrawerFeedOrder"
        const val PREF_DRAWER_FEED_COUNTER = "prefDrawerFeedIndicator"
        const val PREF_EXPANDED_NOTIFICATION = "prefExpandNotify"
        const val PREF_USE_EPISODE_COVER = "prefEpisodeCover"
        const val PREF_SHOW_TIME_LEFT = "showTimeLeft"
        const val PREF_PERSISTENT_NOTIFICATION = "prefPersistNotify"
        const val PREF_FULL_NOTIFICATION_BUTTONS = "prefFullNotificationButtons"
        private const val PREF_SHOW_DOWNLOAD_REPORT = "prefShowDownloadReport"
        const val PREF_DEFAULT_PAGE = "prefDefaultPage"
        const val PREF_FILTER_FEED = "prefSubscriptionsFilter"
        const val PREF_SUBSCRIPTION_TITLE = "prefSubscriptionTitle"
        const val PREF_BACK_OPENS_DRAWER = "prefBackButtonOpensDrawer"
        const val PREF_BOTTOM_NAVIGATION = "prefBottomNavigation"
        private const val PREF_PARENTAL_CONTROL_PASSWORD = "prefParentalControlPassword"
        const val PREF_PARENTAL_CONTROL_ENABLED = "prefParentalControlEnabled"
        const val PREF_PARENTAL_CONTROL_REQUIRE_SUBSCRIBE = "prefParentalControlRequireSubscribe"

        const val PREF_GLOBAL_DEFAULT_SORTED_ORDER = "prefGlobalDefaultSortedOrder"
        const val PREF_QUEUE_KEEP_SORTED = "prefQueueKeepSorted"
        const val PREF_QUEUE_KEEP_SORTED_ORDER = "prefQueueKeepSortedOrder"
        const val PREF_NEW_EPISODES_ACTION = "prefNewEpisodesAction"
        private const val PREF_DOWNLOADS_SORTED_ORDER = "prefDownloadSortedOrder"
        private const val PREF_INBOX_SORTED_ORDER = "prefInboxSortedOrder"

        // Episode
        const val PREF_SORT_ALL_EPISODES = "prefEpisodesSort"
        const val PREF_FILTER_ALL_EPISODES = "prefEpisodesFilter"

        // Playback
        const val PREF_PAUSE_ON_HEADSET_DISCONNECT = "prefPauseOnHeadsetDisconnect"
        const val PREF_UNPAUSE_ON_HEADSET_RECONNECT = "prefUnpauseOnHeadsetReconnect"
        const val PREF_UNPAUSE_ON_BLUETOOTH_RECONNECT = "prefUnpauseOnBluetoothReconnect"
        const val PREF_HARDWARE_FORWARD_BUTTON = "prefHardwareForwardButton"
        const val PREF_HARDWARE_PREVIOUS_BUTTON = "prefHardwarePreviousButton"
        const val PREF_FOLLOW_QUEUE = "prefFollowQueue"
        const val PREF_SKIP_KEEPS_EPISODE = "prefSkipKeepsEpisode"
        const val PREF_FAVORITE_KEEPS_EPISODE = "prefFavoriteKeepsEpisode"
        const val PREF_AUTO_DELETE = "prefAutoDelete"
        private const val PREF_AUTO_DELETE_LOCAL = "prefAutoDeleteLocal"
        const val PREF_SMART_MARK_AS_PLAYED_SECS = "prefSmartMarkAsPlayedSecs"
        private const val PREF_PLAYBACK_SPEED_ARRAY = "prefPlaybackSpeedArray"
        const val PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS = "prefPauseForFocusLoss"
        private const val PREF_TIME_RESPECTS_SPEED = "prefPlaybackTimeRespectsSpeed"
        const val PREF_STREAM_OVER_DOWNLOAD = "prefStreamOverDownload"

        // Network
        private const val PREF_ENQUEUE_DOWNLOADED = "prefEnqueueDownloaded"
        const val PREF_ENQUEUE_LOCATION = "prefEnqueueLocation"
        const val PREF_UPDATE_INTERVAL_MINUTES = "prefAutoUpdateIntervall"
        const val PREF_MOBILE_UPDATE = "prefMobileUpdateTypes"
        const val PREF_EPISODE_CLEANUP = "prefEpisodeCleanup"
        const val PREF_EPISODE_CACHE_SIZE = "prefEpisodeCacheSize"
        const val PREF_AUTODL_GLOBAL = "prefEnableAutoDl"
        const val PREF_AUTODL_QUEUE = "prefEnableAutoDlQueue"
        const val PREF_ENABLE_AUTODL_ON_BATTERY = "prefEnableAutoDownloadOnBattery"
        private const val PREF_PROXY_TYPE = "prefProxyType"
        private const val PREF_PROXY_HOST = "prefProxyHost"
        private const val PREF_PROXY_PORT = "prefProxyPort"
        private const val PREF_PROXY_USER = "prefProxyUser"
        private const val PREF_PROXY_PASSWORD = "prefProxyPassword"

        // Services
        private const val PREF_GPODNET_NOTIFICATIONS = "pref_gpodnet_notifications"

        // Other
        private const val PREF_DATA_FOLDER = "prefDataFolder"
        const val PREF_DELETE_REMOVES_FROM_QUEUE = "prefDeleteRemovesFromQueue"
        const val PREF_DOWNLOADS_BUTTON_ACTION = "prefDownloadsButtonAction"
        private const val PREF_AUTOMATIC_EXPORT_FOLDER = "prefAutomaticExportFolder"

        // Mediaplayer
        private const val PREF_PLAYBACK_SPEED = "prefPlaybackSpeed"
        const val PREF_PLAYBACK_SKIP_SILENCE = "prefSkipSilence"
        private const val PREF_FAST_FORWARD_SECS = "prefFastForwardSecs"
        private const val PREF_REWIND_SECS = "prefRewindSecs"
        private const val PREF_QUEUE_LOCKED = "prefQueueLocked"

        // Experimental
        const val EPISODE_CLEANUP_QUEUE = -1
        const val EPISODE_CLEANUP_NULL = -2
        const val EPISODE_CLEANUP_EXCEPT_FAVORITE = -3
        const val EPISODE_CLEANUP_DEFAULT = 0

        // Constants
        const val NOTIFICATION_BUTTON_SKIP = 2
        const val NOTIFICATION_BUTTON_NEXT_CHAPTER = 3
        const val NOTIFICATION_BUTTON_PLAYBACK_SPEED = 4
        const val NOTIFICATION_BUTTON_SLEEP_TIMER = 5
        const val EPISODE_CACHE_SIZE_UNLIMITED = -1
        const val DEFAULT_PAGE_REMEMBER = "remember"

        private var context: Context? = null
        private var prefs: SharedPreferences? = null

        /**
         * Sets up the UserPreferences class.
         *
         * @throws IllegalArgumentException if context is null
         */
        @JvmStatic
        fun init(context: Context) {
            Log.d(TAG, "Creating new instance of UserPreferences")

            UserPreferences.context = context.applicationContext
            UserPreferences.prefs = PreferenceManager.getDefaultSharedPreferences(context)

            createNoMediaFile()
        }

        @JvmStatic
        fun setTheme(theme: ThemePreference) {
            when (theme) {
                ThemePreference.LIGHT -> prefs!!.edit().putString(PREF_THEME, "0").apply()
                ThemePreference.DARK -> prefs!!.edit().putString(PREF_THEME, "1").apply()
                else -> prefs!!.edit().putString(PREF_THEME, "system").apply()
            }
        }

        @JvmStatic
        fun getTheme(): ThemePreference {
            when (prefs!!.getString(PREF_THEME, "system")) {
                "0" -> return ThemePreference.LIGHT
                "1" -> return ThemePreference.DARK
                else -> return ThemePreference.SYSTEM
            }
        }

        @JvmStatic
        fun isParentalControlPasswordSet(): Boolean {
            return prefs!!.contains(PREF_PARENTAL_CONTROL_PASSWORD)
        }

        @JvmStatic
        fun verifyParentalControlPassword(password: String?): Boolean {
            val stored = prefs!!.getString(PREF_PARENTAL_CONTROL_PASSWORD, null)
            return stored != null && stored == password
        }

        @JvmStatic
        fun setParentalControlPassword(password: String?) {
            prefs!!.edit().putString(PREF_PARENTAL_CONTROL_PASSWORD, password).apply()
        }

        @JvmStatic
        fun clearParentalControlPassword() {
            prefs!!.edit().remove(PREF_PARENTAL_CONTROL_PASSWORD).apply()
        }

        @JvmStatic
        fun isParentalControlRequireSubscribeSet(): Boolean {
            return prefs!!.getBoolean(PREF_PARENTAL_CONTROL_REQUIRE_SUBSCRIBE, true)
        }

        @JvmStatic
        fun getIsBlackTheme(): Boolean {
            return prefs!!.getBoolean(PREF_THEME_BLACK, false)
        }

        @JvmStatic
        fun getIsThemeColorTinted(): Boolean {
            return Build.VERSION.SDK_INT >= 31 && prefs!!.getBoolean(PREF_TINTED_COLORS, false)
        }

        @JvmStatic
        fun getHiddenDrawerItems(): List<String?> {
            val hiddenItems = prefs!!.getString(PREF_HIDDEN_DRAWER_ITEMS, "")
            return ArrayList(Arrays.asList(*TextUtils.split(hiddenItems, ",")))
        }

        @JvmStatic
        fun getVisibleDrawerItemOrder(): List<String?> {
            val itemOrderStr = prefs!!.getString(PREF_DRAWER_ITEM_ORDER, "")
            val itemOrderTags = ArrayList(Arrays.asList(*TextUtils.split(itemOrderStr, ",")))
            val hiddenItemTags = getHiddenDrawerItems()
            val sectionTags = context!!.resources.getStringArray(R.array.nav_drawer_section_tags)
            Arrays.sort(sectionTags) { a, b -> Integer.signum(
                    indexOfOrMaxValue(itemOrderTags, a) - indexOfOrMaxValue(itemOrderTags, b)) }
            val finalItemTags = ArrayList<String>()
            for (sectionTag in sectionTags) {
                if (hiddenItemTags.contains(sectionTag)) {
                    continue
                }
                finalItemTags.add(sectionTag)
            }
            return finalItemTags
        }

        private fun indexOfOrMaxValue(haystack: List<String?>, needle: String): Int {
            val index = haystack.indexOf(needle)
            return if (index == -1) Integer.MAX_VALUE else index
        }

        @JvmStatic
        fun setDrawerItemOrder(hiddenItems: List<String?>, visibleItemsOrder: List<String?>) {
            prefs!!.edit().putString(PREF_HIDDEN_DRAWER_ITEMS, TextUtils.join(",", hiddenItems)).apply()
            prefs!!.edit().putString(PREF_DRAWER_ITEM_ORDER, TextUtils.join(",", visibleItemsOrder)).apply()
        }

        @JvmStatic
        fun getFullNotificationButtons(): List<Int> {
            val buttons = TextUtils.split(
                    prefs!!.getString(PREF_FULL_NOTIFICATION_BUTTONS,
                    NOTIFICATION_BUTTON_SKIP.toString() + "," + NOTIFICATION_BUTTON_PLAYBACK_SPEED), ",")

            val notificationButtons = ArrayList<Int>()
            for (button in buttons) {
                notificationButtons.add(Integer.parseInt(button))
            }
            return notificationButtons
        }

        /**
         * Helper function to return whether the specified button should be shown on full
         * notifications.
         *
         * @param buttonId Either NOTIFICATION_BUTTON_REWIND, NOTIFICATION_BUTTON_FAST_FORWARD,
         *                 NOTIFICATION_BUTTON_SKIP, NOTIFICATION_BUTTON_PLAYBACK_SPEED
         *                 or NOTIFICATION_BUTTON_NEXT_CHAPTER.
         * @return {@code true} if button should be shown, {@code false}  otherwise
         */
        private fun showButtonOnFullNotification(buttonId: Int): Boolean {
            return getFullNotificationButtons().contains(buttonId)
        }

        @JvmStatic
        fun showSkipOnFullNotification(): Boolean {
            return showButtonOnFullNotification(NOTIFICATION_BUTTON_SKIP)
        }

        @JvmStatic
        fun showNextChapterOnFullNotification(): Boolean {
            return showButtonOnFullNotification(NOTIFICATION_BUTTON_NEXT_CHAPTER)
        }

        @JvmStatic
        fun showPlaybackSpeedOnFullNotification(): Boolean {
            return showButtonOnFullNotification(NOTIFICATION_BUTTON_PLAYBACK_SPEED)
        }

        @JvmStatic
        fun showSleepTimerOnFullNotification(): Boolean {
            return showButtonOnFullNotification(NOTIFICATION_BUTTON_SLEEP_TIMER)
        }

        @JvmStatic
        fun getFeedOrder(): FeedOrder {
            val value = prefs!!.getString(PREF_DRAWER_FEED_ORDER, "" + FeedOrder.COUNTER.id)
            return FeedOrder.fromOrdinal(Integer.parseInt(value))
        }

        @JvmStatic
        fun setFeedOrder(feedOrder: FeedOrder) {
            prefs!!.edit().putString(PREF_DRAWER_FEED_ORDER, "" + feedOrder.id).apply()
        }

        @JvmStatic
        fun getFeedCounterSetting(): FeedCounter {
            val value = prefs!!.getString(PREF_DRAWER_FEED_COUNTER, "" + FeedCounter.SHOW_NEW.id)
            return FeedCounter.fromOrdinal(Integer.parseInt(value))
        }

        @JvmStatic
        fun setFeedCounterSetting(counter: FeedCounter) {
            prefs!!.edit().putString(PREF_DRAWER_FEED_COUNTER, "" + counter.id).apply()
        }

        /**
         * @return {@code true} if episodes should use their own cover, {@code false}  otherwise
         */
        @JvmStatic
        fun getUseEpisodeCoverSetting(): Boolean {
            return prefs!!.getBoolean(PREF_USE_EPISODE_COVER, true)
        }

        /**
         * @return {@code true} if we should show remaining time or the duration
         */
        @JvmStatic
        fun shouldShowRemainingTime(): Boolean {
            return prefs!!.getBoolean(PREF_SHOW_TIME_LEFT, false)
        }

        /**
         * Sets the preference for whether we show the remain time, if not show the duration. This will
         * send out events so the current playing screen, queue and the episode list would refresh
         *
         * @return {@code true} if we should show remaining time or the duration
         */
        @JvmStatic
        fun setShowRemainTimeSetting(showRemain: Boolean) {
            prefs!!.edit().putBoolean(PREF_SHOW_TIME_LEFT, showRemain).apply()
        }

        @JvmStatic
        fun getAutomaticExportFolder(): String? {
            return prefs!!.getString(PREF_AUTOMATIC_EXPORT_FOLDER, null)
        }

        @JvmStatic
        fun setAutomaticExportFolder(folder: String?) {
            prefs!!.edit().putString(PREF_AUTOMATIC_EXPORT_FOLDER, folder).apply()
        }

        /**
         * Returns notification priority.
         *
         * @return NotificationCompat.PRIORITY_MAX or NotificationCompat.PRIORITY_DEFAULT
         */
        @JvmStatic
        fun getNotifyPriority(): Int {
            if (prefs!!.getBoolean(PREF_EXPANDED_NOTIFICATION, false)) {
                return NotificationCompat.PRIORITY_MAX
            } else {
                return NotificationCompat.PRIORITY_DEFAULT
            }
        }

        /**
         * Returns true if notifications are persistent
         *
         * @return {@code true} if notifications are persistent, {@code false}  otherwise
         */
        @JvmStatic
        fun isPersistNotify(): Boolean {
            return prefs!!.getBoolean(PREF_PERSISTENT_NOTIFICATION, true)
        }

        /**
         * Used for migration of the preference to system notification channels.
         */
        @JvmStatic
        fun getShowDownloadReportRaw(): Boolean {
            return prefs!!.getBoolean(PREF_SHOW_DOWNLOAD_REPORT, true)
        }

        @JvmStatic
        fun enqueueDownloadedEpisodes(): Boolean {
            return prefs!!.getBoolean(PREF_ENQUEUE_DOWNLOADED, true)
        }

        @JvmStatic
        fun getEnqueueLocation(): EnqueueLocation {
            val valStr = prefs!!.getString(PREF_ENQUEUE_LOCATION, EnqueueLocation.BACK.name)
            try {
                return EnqueueLocation.valueOf(valStr!!)
            } catch (t: Throwable) {
                // should never happen but just in case
                Log.e(TAG, "getEnqueueLocation: invalid value '" + valStr + "' Use default.", t)
                return EnqueueLocation.BACK
            }
        }

        @JvmStatic
        fun setEnqueueLocation(location: EnqueueLocation) {
            prefs!!.edit()
                    .putString(PREF_ENQUEUE_LOCATION, location.name)
                    .apply()
        }

        @JvmStatic
        fun isPauseOnHeadsetDisconnect(): Boolean {
            return prefs!!.getBoolean(PREF_PAUSE_ON_HEADSET_DISCONNECT, true)
        }

        @JvmStatic
        fun isUnpauseOnHeadsetReconnect(): Boolean {
            return prefs!!.getBoolean(PREF_UNPAUSE_ON_HEADSET_RECONNECT, true)
        }

        @JvmStatic
        fun isUnpauseOnBluetoothReconnect(): Boolean {
            return prefs!!.getBoolean(PREF_UNPAUSE_ON_BLUETOOTH_RECONNECT, false)
        }

        @JvmStatic
        fun getHardwareForwardButton(): Int {
            return Integer.parseInt(prefs!!.getString(PREF_HARDWARE_FORWARD_BUTTON,
                    java.lang.String.valueOf(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)))
        }

        @JvmStatic
        fun getHardwarePreviousButton(): Int {
            return Integer.parseInt(prefs!!.getString(PREF_HARDWARE_PREVIOUS_BUTTON,
                    java.lang.String.valueOf(KeyEvent.KEYCODE_MEDIA_REWIND)))
        }

        @JvmStatic
        fun isFollowQueue(): Boolean {
            return prefs!!.getBoolean(PREF_FOLLOW_QUEUE, true)
        }

        /**
         * Set to true to enable Continuous Playback
         */
        @JvmStatic
        fun setFollowQueue(value: Boolean) {
            prefs!!.edit().putBoolean(UserPreferences.PREF_FOLLOW_QUEUE, value).apply()
        }

        @JvmStatic
        fun shouldSkipKeepEpisode(): Boolean {
            return prefs!!.getBoolean(PREF_SKIP_KEEPS_EPISODE, true)
        }

        @JvmStatic
        fun shouldFavoriteKeepEpisode(): Boolean {
            return prefs!!.getBoolean(PREF_FAVORITE_KEEPS_EPISODE, true)
        }

        @JvmStatic
        fun isAutoDelete(): Boolean {
            return prefs!!.getBoolean(PREF_AUTO_DELETE, false)
        }

        @JvmStatic
        fun isAutoDeleteLocal(): Boolean {
            return prefs!!.getBoolean(PREF_AUTO_DELETE_LOCAL, false)
        }

        @JvmStatic
        fun getSmartMarkAsPlayedSecs(): Int {
            return Integer.parseInt(prefs!!.getString(PREF_SMART_MARK_AS_PLAYED_SECS, "30"))
        }

        @JvmStatic
        fun shouldDeleteRemoveFromQueue(): Boolean {
            return prefs!!.getBoolean(PREF_DELETE_REMOVES_FROM_QUEUE, false)
        }

        @JvmStatic
        fun shouldDownloadsButtonActionPlay(): Boolean {
            return prefs!!.getBoolean(PREF_DOWNLOADS_BUTTON_ACTION, false)
        }

        @JvmStatic
        fun getPlaybackSpeed(): Float {
            try {
                return java.lang.Float.parseFloat(prefs!!.getString(PREF_PLAYBACK_SPEED, "1.00"))
            } catch (e: NumberFormatException) {
                Log.e(TAG, Log.getStackTraceString(e))
                UserPreferences.setPlaybackSpeed(1.0f)
                return 1.0f
            }
        }

        @JvmStatic
        fun isSkipSilence(): Boolean {
            return prefs!!.getBoolean(PREF_PLAYBACK_SKIP_SILENCE, false)
        }

        @JvmStatic
        fun getPlaybackSpeedArray(): List<Float> {
            return readPlaybackSpeedArray(prefs!!.getString(PREF_PLAYBACK_SPEED_ARRAY, null))
        }

        @JvmStatic
        fun shouldPauseForFocusLoss(): Boolean {
            return prefs!!.getBoolean(PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS, true)
        }

        @JvmStatic
        fun getUpdateInterval(): Long {
            return Integer.parseInt(prefs!!.getString(PREF_UPDATE_INTERVAL_MINUTES, "720")).toLong()
        }

        @JvmStatic
        fun setUpdateInterval(interval: Long) {
            prefs!!.edit().putString(PREF_UPDATE_INTERVAL_MINUTES, java.lang.String.valueOf(interval)).apply()
        }

        @JvmStatic
        fun isAutoUpdateDisabled(): Boolean {
            return getUpdateInterval() == 0L
        }

        private fun isAllowMobileFor(type: String): Boolean {
            val defaultValue = HashSet<String>()
            defaultValue.add("images")
            val allowed = prefs!!.getStringSet(PREF_MOBILE_UPDATE, defaultValue)
            return allowed!!.contains(type)
        }

        @JvmStatic
        fun isAllowMobileFeedRefresh(): Boolean {
            return isAllowMobileFor("feed_refresh")
        }

        @JvmStatic
        fun isAllowMobileSync(): Boolean {
            return isAllowMobileFor("sync")
        }

        @JvmStatic
        fun isAllowMobileEpisodeDownload(): Boolean {
            return isAllowMobileFor("episode_download")
        }

        @JvmStatic
        fun isAllowMobileAutoDownload(): Boolean {
            return isAllowMobileFor("auto_download")
        }

        @JvmStatic
        fun isAllowMobileStreaming(): Boolean {
            return isAllowMobileFor("streaming")
        }

        @JvmStatic
        fun isAllowMobileImages(): Boolean {
            return isAllowMobileFor("images")
        }

        private fun setAllowMobileFor(type: String, allow: Boolean) {
            val defaultValue = HashSet<String>()
            defaultValue.add("images")
            val getValueStringSet = prefs!!.getStringSet(PREF_MOBILE_UPDATE, defaultValue)
            val allowed = HashSet(getValueStringSet)
            if (allow) {
                allowed.add(type)
            } else {
                allowed.remove(type)
            }
            prefs!!.edit().putStringSet(PREF_MOBILE_UPDATE, allowed).apply()
        }

        @JvmStatic
        fun setAllowMobileFeedRefresh(allow: Boolean) {
            setAllowMobileFor("feed_refresh", allow)
        }

        @JvmStatic
        fun setAllowMobileEpisodeDownload(allow: Boolean) {
            setAllowMobileFor("episode_download", allow)
        }

        @JvmStatic
        fun setAllowMobileAutoDownload(allow: Boolean) {
            setAllowMobileFor("auto_download", allow)
        }

        @JvmStatic
        fun setAllowMobileStreaming(allow: Boolean) {
            setAllowMobileFor("streaming", allow)
        }

        @JvmStatic
        fun setAllowMobileImages(allow: Boolean) {
            setAllowMobileFor("images", allow)
        }

        @JvmStatic
        fun setAllowMobileSync(allow: Boolean) {
            setAllowMobileFor("sync", allow)
        }

        /**
         * Returns the capacity of the episode cache. This method will return the
         * negative integer EPISODE_CACHE_SIZE_UNLIMITED if the cache size is set to
         * 'unlimited'.
         */
        @JvmStatic
        fun getEpisodeCacheSize(): Int {
            return Integer.parseInt(prefs!!.getString(PREF_EPISODE_CACHE_SIZE, "20"))
        }

        @JvmStatic
        fun isEnableAutodownloadGlobal(): Boolean {
            return prefs!!.getBoolean(PREF_AUTODL_GLOBAL, false)
        }

        @JvmStatic
        fun isEnableAutodownloadQueue(): Boolean {
            return prefs!!.getBoolean(PREF_AUTODL_QUEUE, false)
        }

        @JvmStatic
        fun isEnableAutodownloadOnBattery(): Boolean {
            return prefs!!.getBoolean(PREF_ENABLE_AUTODL_ON_BATTERY, true)
        }

        @JvmStatic
        fun getFastForwardSecs(): Int {
            return prefs!!.getInt(PREF_FAST_FORWARD_SECS, 30)
        }

        @JvmStatic
        fun getRewindSecs(): Int {
            return prefs!!.getInt(PREF_REWIND_SECS, 10)
        }

        @JvmStatic
        fun setProxyConfig(config: ProxyConfig) {
            val editor = prefs!!.edit()
            editor.putString(PREF_PROXY_TYPE, config.type.name)
            if (TextUtils.isEmpty(config.host)) {
                editor.remove(PREF_PROXY_HOST)
            } else {
                editor.putString(PREF_PROXY_HOST, config.host)
            }
            if (config.port <= 0 || config.port > 65535) {
                editor.remove(PREF_PROXY_PORT)
            } else {
                editor.putInt(PREF_PROXY_PORT, config.port)
            }
            if (TextUtils.isEmpty(config.username)) {
                editor.remove(PREF_PROXY_USER)
            } else {
                editor.putString(PREF_PROXY_USER, config.username)
            }
            if (TextUtils.isEmpty(config.password)) {
                editor.remove(PREF_PROXY_PASSWORD)
            } else {
                editor.putString(PREF_PROXY_PASSWORD, config.password)
            }
            editor.apply()
        }

        @JvmStatic
        fun getProxyConfig(): ProxyConfig {
            val type = Proxy.Type.valueOf(prefs!!.getString(PREF_PROXY_TYPE, Proxy.Type.DIRECT.name)!!)
            val host = prefs!!.getString(PREF_PROXY_HOST, null)
            val port = prefs!!.getInt(PREF_PROXY_PORT, 0)
            val username = prefs!!.getString(PREF_PROXY_USER, null)
            val password = prefs!!.getString(PREF_PROXY_PASSWORD, null)
            return ProxyConfig(type, host, port, username, password)
        }

        @JvmStatic
        fun isQueueLocked(): Boolean {
            return prefs!!.getBoolean(PREF_QUEUE_LOCKED, false)
        }

        @JvmStatic
        fun setFastForwardSecs(secs: Int) {
            prefs!!.edit().putInt(PREF_FAST_FORWARD_SECS, secs).apply()
        }

        @JvmStatic
        fun setRewindSecs(secs: Int) {
            prefs!!.edit().putInt(PREF_REWIND_SECS, secs).apply()
        }

        @JvmStatic
        fun setPlaybackSpeed(speed: Float) {
            prefs!!.edit().putString(PREF_PLAYBACK_SPEED, java.lang.String.valueOf(speed)).apply()
        }

        @JvmStatic
        fun setSkipSilence(skipSilence: Boolean) {
            prefs!!.edit().putBoolean(PREF_PLAYBACK_SKIP_SILENCE, skipSilence).apply()
        }

        @JvmStatic
        fun setPlaybackSpeedArray(speeds: List<Float>) {
            val format = DecimalFormatSymbols(Locale.US)
            format.decimalSeparator = '.'
            val speedFormat = DecimalFormat("0.00", format)
            val jsonArray = JSONArray()
            for (speed in speeds) {
                jsonArray.put(speedFormat.format(speed.toDouble()))
            }
            prefs!!.edit().putString(PREF_PLAYBACK_SPEED_ARRAY, jsonArray.toString()).apply()
        }

        @JvmStatic
        fun gpodnetNotificationsEnabled(): Boolean {
            if (Build.VERSION.SDK_INT >= 26) {
                return true // System handles notification preferences
            }
            return prefs!!.getBoolean(PREF_GPODNET_NOTIFICATIONS, true)
        }

        /**
         * Used for migration of the preference to system notification channels.
         */
        @JvmStatic
        fun getGpodnetNotificationsEnabledRaw(): Boolean {
            return prefs!!.getBoolean(PREF_GPODNET_NOTIFICATIONS, true)
        }

        @JvmStatic
        fun setGpodnetNotificationsEnabled() {
            prefs!!.edit().putBoolean(PREF_GPODNET_NOTIFICATIONS, true).apply()
        }

        @JvmStatic
        fun setFullNotificationButtons(items: List<Int>) {
            val str = TextUtils.join(",", items)
            prefs!!.edit().putString(PREF_FULL_NOTIFICATION_BUTTONS, str).apply()
        }

        @JvmStatic
        fun setQueueLocked(locked: Boolean) {
            prefs!!.edit().putBoolean(PREF_QUEUE_LOCKED, locked).apply()
        }

        private fun readPlaybackSpeedArray(valueFromPrefs: String?): List<Float> {
            if (valueFromPrefs != null) {
                try {
                    val jsonArray = JSONArray(valueFromPrefs)
                    val selectedSpeeds = ArrayList<Float>()
                    for (i in 0 until jsonArray.length()) {
                        selectedSpeeds.add(jsonArray.getDouble(i).toFloat())
                    }
                    return selectedSpeeds
                } catch (e: JSONException) {
                    Log.e(TAG, "Got JSON error when trying to get speeds from JSONArray")
                    e.printStackTrace()
                }
            }
            // If this preference hasn't been set yet, return the default options
            return Arrays.asList(1.0f, 1.25f, 1.5f)
        }

        @JvmStatic
        fun getEpisodeCleanupValue(): Int {
            return Integer.parseInt(prefs!!.getString(PREF_EPISODE_CLEANUP, "" + EPISODE_CLEANUP_NULL))
        }

        @JvmStatic
        fun setEpisodeCleanupValue(episodeCleanupValue: Int) {
            prefs!!.edit().putString(PREF_EPISODE_CLEANUP, Integer.toString(episodeCleanupValue)).apply()
        }

        /**
         * Return the folder where the app stores all of its data. This method will
         * return the standard data folder if none has been set by the user.
         *
         * @param type The name of the folder inside the data folder. May be null
         *             when accessing the root of the data folder.
         * @return The data folder that has been requested or null if the folder could not be created.
         */
        @JvmStatic
        fun getDataFolder(type: String?): File? {
            var dataFolder = getTypeDir(prefs!!.getString(PREF_DATA_FOLDER, null), type)
            if (dataFolder == null || !dataFolder.canWrite()) {
                Log.d(TAG, "User data folder not writable or not set. Trying default.")
                dataFolder = context!!.getExternalFilesDir(type)
            }
            if (dataFolder == null || !dataFolder.canWrite()) {
                Log.d(TAG, "Default data folder not available or not writable. Falling back to internal memory.")
                dataFolder = getTypeDir(context!!.filesDir.absolutePath, type)
            }
            return dataFolder
        }

        private fun getTypeDir(baseDirPath: String?, type: String?): File? {
            if (baseDirPath == null) {
                return null
            }
            val baseDir = File(baseDirPath)
            val typeDir = if (type == null) baseDir else File(baseDir, type)
            if (!typeDir.exists()) {
                if (!baseDir.canWrite()) {
                    Log.e(TAG, "Base dir is not writable " + baseDir.absolutePath)
                    return null
                }
                if (!typeDir.mkdirs()) {
                    Log.e(TAG, "Could not create type dir " + typeDir.absolutePath)
                    return null
                }
            }
            return typeDir
        }

        @JvmStatic
        fun setDataFolder(dir: String?) {
            Log.d(TAG, "setDataFolder(dir: " + dir + ")")
            prefs!!.edit().putString(PREF_DATA_FOLDER, dir).apply()
        }

        /**
         * Create a .nomedia file to prevent scanning by the media scanner.
         */
        private fun createNoMediaFile() {
            val f = File(context!!.getExternalFilesDir(null), ".nomedia")
            if (!f.exists()) {
                try {
                    f.createNewFile()
                } catch (e: IOException) {
                    Log.e(TAG, "Could not create .nomedia file")
                    e.printStackTrace()
                }
                Log.d(TAG, ".nomedia file created")
            }
        }

        @JvmStatic
        fun getDefaultPage(): String? {
            return prefs!!.getString(PREF_DEFAULT_PAGE, "HomeFragment")
        }

        @JvmStatic
        fun setDefaultPage(defaultPage: String?) {
            prefs!!.edit().putString(PREF_DEFAULT_PAGE, defaultPage).apply()
        }

        @JvmStatic
        fun backButtonOpensDrawer(): Boolean {
            return prefs!!.getBoolean(PREF_BACK_OPENS_DRAWER, false)
        }

        @JvmStatic
        fun isBottomNavigationEnabled(): Boolean {
            return prefs!!.getBoolean(PREF_BOTTOM_NAVIGATION, true)
        }

        @JvmStatic
        fun setBottomNavigationEnabled(enabled: Boolean) {
            prefs!!.edit().putBoolean(PREF_BOTTOM_NAVIGATION, enabled).apply()
        }

        @JvmStatic
        fun timeRespectsSpeed(): Boolean {
            return prefs!!.getBoolean(PREF_TIME_RESPECTS_SPEED, false)
        }

        @JvmStatic
        fun isStreamOverDownload(): Boolean {
            return prefs!!.getBoolean(PREF_STREAM_OVER_DOWNLOAD, false)
        }

        @JvmStatic
        fun setStreamOverDownload(stream: Boolean) {
            prefs!!.edit().putBoolean(PREF_STREAM_OVER_DOWNLOAD, stream).apply()
        }

        /**
         * Returns if the queue is in keep sorted mode.
         *
         * @see #getQueueKeepSortedOrder()
         */
        @JvmStatic
        fun isQueueKeepSorted(): Boolean {
            return prefs!!.getBoolean(PREF_QUEUE_KEEP_SORTED, false)
        }

        /**
         * Enables/disables the keep sorted mode of the queue.
         *
         * @see #setQueueKeepSortedOrder(SortOrder)
         */
        @JvmStatic
        fun setQueueKeepSorted(keepSorted: Boolean) {
            prefs!!.edit().putBoolean(PREF_QUEUE_KEEP_SORTED, keepSorted).apply()
        }

        /**
         * Returns the sort order for the queue keep sorted mode.
         * Note: This value is stored independently from the keep sorted state.
         *
         * @see #isQueueKeepSorted()
         */
        @JvmStatic
        fun getQueueKeepSortedOrder(): SortOrder {
            val sortOrderStr = prefs!!.getString(PREF_QUEUE_KEEP_SORTED_ORDER, "use-default")
            return SortOrder.parseWithDefault(sortOrderStr, SortOrder.DATE_NEW_OLD)
        }

        /**
         * Sets the sort order for the queue keep sorted mode.
         *
         * @see #setQueueKeepSorted(boolean)
         */
        @JvmStatic
        fun setQueueKeepSortedOrder(sortOrder: SortOrder?) {
            if (sortOrder == null) {
                return
            }
            prefs!!.edit().putString(PREF_QUEUE_KEEP_SORTED_ORDER, sortOrder.name).apply()
        }

        @JvmStatic
        fun getNewEpisodesAction(): FeedPreferences.NewEpisodesAction {
            val str = prefs!!.getString(PREF_NEW_EPISODES_ACTION,
                    "" + FeedPreferences.NewEpisodesAction.ADD_TO_INBOX.code)
            return FeedPreferences.NewEpisodesAction.fromCode(Integer.parseInt(str))
        }

        /**
         * Returns the sort order for the downloads.
         */
        @JvmStatic
        fun getDownloadsSortedOrder(): SortOrder {
            val sortOrderStr = prefs!!.getString(PREF_DOWNLOADS_SORTED_ORDER, "" + SortOrder.DATE_NEW_OLD.code)
            return SortOrder.fromCodeString(sortOrderStr)!!
        }

        /**
         * Sets the sort order for the downloads.
         */
        @JvmStatic
        fun setDownloadsSortedOrder(sortOrder: SortOrder) {
            prefs!!.edit().putString(PREF_DOWNLOADS_SORTED_ORDER, "" + sortOrder.code).apply()
        }

        @JvmStatic
        fun getInboxSortedOrder(): SortOrder {
            val sortOrderStr = prefs!!.getString(PREF_INBOX_SORTED_ORDER, "" + SortOrder.DATE_NEW_OLD.code)
            return SortOrder.fromCodeString(sortOrderStr)!!
        }

        @JvmStatic
        fun setInboxSortedOrder(sortOrder: SortOrder) {
            prefs!!.edit().putString(PREF_INBOX_SORTED_ORDER, "" + sortOrder.code).apply()
        }

        @JvmStatic
        fun getSubscriptionsFilter(): SubscriptionsFilter {
            val value = prefs!!.getString(PREF_FILTER_FEED, "")
            return SubscriptionsFilter(value)
        }

        @JvmStatic
        fun setSubscriptionsFilter(value: SubscriptionsFilter) {
            prefs!!.edit().putString(PREF_FILTER_FEED, value.serialize()).apply()
        }

        @JvmStatic
        fun shouldShowSubscriptionTitle(): Boolean {
            return prefs!!.getBoolean(PREF_SUBSCRIPTION_TITLE, false)
        }

        @JvmStatic
        fun setShouldShowSubscriptionTitle(show: Boolean) {
            prefs!!.edit().putBoolean(PREF_SUBSCRIPTION_TITLE, show).apply()
        }

        @JvmStatic
        fun setPrefGlobalSortedOrder(sortOrder: SortOrder) {
            prefs!!.edit().putString(PREF_GLOBAL_DEFAULT_SORTED_ORDER, "" + sortOrder.code).apply()
        }

        @JvmStatic
        fun getPrefGlobalSortedOrder(): SortOrder {
            return SortOrder.fromCodeString(prefs!!.getString(PREF_GLOBAL_DEFAULT_SORTED_ORDER,
                    "" + SortOrder.DATE_NEW_OLD.code))!!
        }

        @JvmStatic
        fun setAllEpisodesSortOrder(s: SortOrder) {
            prefs!!.edit().putString(PREF_SORT_ALL_EPISODES, "" + s.code).apply()
        }

        @JvmStatic
        fun getAllEpisodesSortOrder(): SortOrder {
            return SortOrder.fromCodeString(prefs!!.getString(PREF_SORT_ALL_EPISODES,
                    "" + SortOrder.DATE_NEW_OLD.code))!!
        }

        @JvmStatic
        fun getPrefFilterAllEpisodes(): String? {
            return prefs!!.getString(PREF_FILTER_ALL_EPISODES, "")
        }

        @JvmStatic
        fun setPrefFilterAllEpisodes(filter: String?) {
            prefs!!.edit().putString(PREF_FILTER_ALL_EPISODES, filter).apply()
        }
    }
}
