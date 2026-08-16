package de.danoeh.antennapod.storage.database

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.model.feed.SubscriptionsFilter
import de.danoeh.antennapod.storage.preferences.UserPreferences

import java.util.ArrayList

abstract class SubscriptionsFilterExecutor {
    companion object {
        @JvmStatic
        fun filter(items: List<Feed>, feedCounters: Map<Long, Int>, filter: SubscriptionsFilter): List<Feed> {
            val result = ArrayList<Feed>()

            for (item in items) {
                val itemPreferences = item.getPreferences()!!

                val globalAutodownload = UserPreferences.isEnableAutodownloadGlobal()
                val shouldItemAutoDownload = itemPreferences.isAutoDownload(globalAutodownload)
                // If the item does not meet a requirement, skip it.
                if (filter.showAutoDownloadEnabled && !shouldItemAutoDownload) {
                    continue
                } else if (filter.showAutoDownloadDisabled && shouldItemAutoDownload) {
                    continue
                }

                if (filter.showUpdatedEnabled && !itemPreferences.getKeepUpdated()) {
                    continue
                } else if (filter.showUpdatedDisabled && itemPreferences.getKeepUpdated()) {
                    continue
                }

                if (filter.showEpisodeNotificationEnabled && !itemPreferences.getShowEpisodeNotification()) {
                    continue
                } else if (filter.showEpisodeNotificationDisabled && itemPreferences.getShowEpisodeNotification()) {
                    continue
                }

                if (filter.hideNonSubscribedFeeds && item.getState() == Feed.STATE_NOT_SUBSCRIBED) {
                    continue
                }

                // If the item reaches here, it meets all criteria (except counter > 0)
                result.add(item)
            }

            if (filter.showIfCounterGreaterZero) {
                for (i in result.size - 1 downTo 0) {
                    if (!feedCounters.containsKey(result[i].getId()) || feedCounters[result[i].getId()]!! <= 0) {
                        result.removeAt(i)
                    }
                }
            }

            return result
        }
    }
}
