package de.danoeh.antennapod.playback.service

import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.model.feed.FeedMedia

abstract class PlaybackStatus {
    companion object {
        /**
         * Reads playback preferences to determine whether this FeedMedia object is
         * currently being played and the current player status is playing.
         */
        @JvmStatic
        fun isCurrentlyPlaying(media: FeedMedia): Boolean {
            return isPlaying(media) && PlaybackService.isRunning
                    && ((PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PLAYING))
        }

        @JvmStatic
        fun isPlaying(media: FeedMedia?): Boolean {
            return PlaybackPreferences.getCurrentlyPlayingMediaType() == FeedMedia.PLAYABLE_TYPE_FEEDMEDIA.toLong()
                    && media != null
                    && PlaybackPreferences.getCurrentlyPlayingFeedMediaId() == media.getId()
        }
    }
}
