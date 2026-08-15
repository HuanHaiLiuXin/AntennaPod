package de.danoeh.antennapod.net.download.service.episode.autodownload

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

import java.util.ArrayList
import java.util.Iterator

import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.net.common.NetworkUtils

/**
 * Implements the automatic download algorithm used by AntennaPod. This class assumes that
 * the client uses the [EpisodeCleanupAlgorithm].
 */
class AutomaticDownloadAlgorithm {
    companion object {
        private const val TAG = "DownloadAlgorithm"

        /**
         * @return true if the device is charging
         */
        @JvmStatic
        fun deviceCharging(context: Context): Boolean {
            // from http://developer.android.com/training/monitoring-device-state/battery-monitoring.html
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)

            val status = batteryStatus!!.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            return (status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL)

        }
    }

    /**
     * Looks for undownloaded episodes in the queue or list of new items and request a download if
     * 1. Network is available
     * 2. The device is charging or the user allows auto download on battery
     * 3. There is free space in the episode cache
     * This method is executed on an internal single thread executor.
     *
     * @param context  Used for accessing the DB.
     * @return A Runnable that will be submitted to an ExecutorService.
     */
    fun autoDownloadUndownloadedItems(context: Context): Runnable {
        return Runnable {

            // true if we should auto download based on network status
            val networkShouldAutoDl = NetworkUtils.isAutoDownloadAllowed()

            // true if we should auto download based on power status
            val powerShouldAutoDl = deviceCharging(context) || UserPreferences.isEnableAutodownloadOnBattery()

            // we should only auto download if both network AND power are happy
            if (networkShouldAutoDl && powerShouldAutoDl) {

                Log.d(TAG, "Performing auto-dl of undownloaded episodes")

                val newItems = DBReader.getEpisodes(0, Int.MAX_VALUE,
                        FeedItemFilter(FeedItemFilter.NEW), SortOrder.DATE_NEW_OLD)
                val candidates = ArrayList<FeedItem>()
                for (newItem in newItems) {
                    val feedPrefs = newItem.getFeed()!!.getPreferences()!!
                    if (feedPrefs.isAutoDownload(UserPreferences.isEnableAutodownloadGlobal())
                            && !candidates.contains(newItem)
                            && feedPrefs.getFilter().shouldAutoDownload(newItem)) {
                        candidates.add(newItem)
                    }
                }

                if (UserPreferences.isEnableAutodownloadQueue()) {
                    val queue = DBReader.getQueue()
                    for (item in queue) {
                        if (!candidates.contains(item)) {
                            candidates.add(item)
                        }
                    }
                }

                // filter items that are not auto downloadable
                val it = candidates.iterator()
                while (it.hasNext()) {
                    val item = it.next()
                    if (!item.isAutoDownloadEnabled()
                            || item.isDownloaded()
                            || !item.hasMedia()
                            || item.getFeed()!!.isLocalFeed()) {
                        it.remove()
                    }
                }

                val autoDownloadableEpisodes = candidates.size
                var downloadedEpisodes = DBReader.getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.DOWNLOADED))
                downloadedEpisodes += DownloadServiceInterface.get()!!.getNumberOfActiveDownloads(context)
                val deletedEpisodes = EpisodeCleanupAlgorithmFactory.build()
                        .makeRoomForEpisodes(context, autoDownloadableEpisodes)
                val cacheIsUnlimited =
                        UserPreferences.getEpisodeCacheSize() == UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED
                val episodeCacheSize = UserPreferences.getEpisodeCacheSize()

                val episodeSpaceLeft: Int
                if (cacheIsUnlimited || episodeCacheSize >= downloadedEpisodes + autoDownloadableEpisodes) {
                    episodeSpaceLeft = autoDownloadableEpisodes
                } else {
                    episodeSpaceLeft = episodeCacheSize - (downloadedEpisodes - deletedEpisodes)
                }

                val itemsToDownload = candidates.subList(0, episodeSpaceLeft)
                if (!itemsToDownload.isEmpty()) {
                    Log.d(TAG, "Enqueueing " + itemsToDownload.size + " items for download")

                    for (episode in itemsToDownload) {
                        DownloadServiceInterface.get()!!.download(context, episode)
                    }
                }
            }
        }
    }
}
