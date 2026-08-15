package de.danoeh.antennapod.playback.service.internal

import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer
import de.danoeh.antennapod.playback.base.PlayerStatus

class PlaybackVolumeUpdater {

    fun updateVolumeIfNecessary(mediaPlayer: PlaybackServiceMediaPlayer, feedId: Long,
                                volumeAdaptionSetting: VolumeAdaptionSetting) {
        val playable = mediaPlayer.getPlayable()

        if (playable is FeedMedia) {
            updateFeedMediaVolumeIfNecessary(mediaPlayer, feedId, volumeAdaptionSetting, playable)
        }
    }

    private fun updateFeedMediaVolumeIfNecessary(mediaPlayer: PlaybackServiceMediaPlayer, feedId: Long,
                                                 volumeAdaptionSetting: VolumeAdaptionSetting, feedMedia: FeedMedia) {
        if (feedMedia.getItem()!!.getFeed()!!.getId() == feedId) {
            val preferences = feedMedia.getItem()!!.getFeed()!!.getPreferences()!!
            preferences.setVolumeAdaptionSetting(volumeAdaptionSetting)

            if (mediaPlayer.getPlayerStatus() == PlayerStatus.PLAYING) {
                forceUpdateVolume(mediaPlayer)
            }
        }
    }

    private fun forceUpdateVolume(mediaPlayer: PlaybackServiceMediaPlayer) {
        mediaPlayer.pause(false, false)
        mediaPlayer.resume()
    }

}
