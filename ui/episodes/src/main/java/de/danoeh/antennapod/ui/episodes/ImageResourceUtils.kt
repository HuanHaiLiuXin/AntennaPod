package de.danoeh.antennapod.ui.episodes

import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.storage.preferences.UserPreferences

/**
 * Utility class to use the appropriate image resource based on [UserPreferences].
 */
class ImageResourceUtils {
    companion object {
        /**
         * returns the image location, does prefer the episode cover if available and enabled in settings.
         */
        @JvmStatic
        fun getEpisodeListImageLocation(playable: Playable): String? {
            if (UserPreferences.getUseEpisodeCoverSetting()) {
                return playable.getImageLocation()
            } else {
                return getFallbackImageLocation(playable)
            }
        }

        /**
         * returns the image location, does prefer the episode cover if available and enabled in settings.
         */
        @JvmStatic
        fun getEpisodeListImageLocation(feedItem: FeedItem): String? {
            if (UserPreferences.getUseEpisodeCoverSetting()) {
                return feedItem.getImageLocation()
            } else {
                return getFallbackImageLocation(feedItem)
            }
        }

        @JvmStatic
        fun getFallbackImageLocation(playable: Playable): String? {
            if (playable is FeedMedia) {
                val item = playable.getItem()
                if (item != null && item.getFeed() != null) {
                    return item.getFeed()!!.getImageUrl()
                } else {
                    return null
                }
            } else {
                return playable.getImageLocation()
            }
        }

        @JvmStatic
        fun getFallbackImageLocation(feedItem: FeedItem): String? {
            if (feedItem.getFeed() != null) {
                return feedItem.getFeed()!!.getImageUrl()
            } else {
                return null
            }
        }
    }
}
