package de.danoeh.antennapod.playback.service

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer
import de.danoeh.antennapod.playback.base.PlayerStatus
import de.danoeh.antennapod.playback.service.internal.PlaybackVolumeUpdater
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PlaybackVolumeUpdaterTest {

    companion object {
        private const val FEED_ID = 42L
    }

    private lateinit var mediaPlayer: PlaybackServiceMediaPlayer

    @Before
    fun setUp() {
        mediaPlayer = mock(PlaybackServiceMediaPlayer::class.java)
    }

    @Test
    fun noChangeIfNoFeedMediaPlaying() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        `when`(mediaPlayer.getPlayerStatus()).thenReturn(PlayerStatus.PAUSED)

        val noFeedMedia = mock(Playable::class.java)
        `when`(mediaPlayer.getPlayable()).thenReturn(noFeedMedia)

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer, FEED_ID, VolumeAdaptionSetting.OFF)

        verify(mediaPlayer, never()).pause(anyBoolean(), anyBoolean())
        verify(mediaPlayer, never()).resume()
    }

    @Test
    fun noChangeIfPlayerStatusIsError() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        `when`(mediaPlayer.getPlayerStatus()).thenReturn(PlayerStatus.ERROR)

        val feedMedia = mockFeedMedia()
        `when`(mediaPlayer.getPlayable()).thenReturn(feedMedia)

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer, FEED_ID, VolumeAdaptionSetting.OFF)

        verify(mediaPlayer, never()).pause(anyBoolean(), anyBoolean())
        verify(mediaPlayer, never()).resume()
    }

    @Test
    fun noChangeIfPlayerStatusIsIndeterminate() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        `when`(mediaPlayer.getPlayerStatus()).thenReturn(PlayerStatus.INDETERMINATE)

        val feedMedia = mockFeedMedia()
        `when`(mediaPlayer.getPlayable()).thenReturn(feedMedia)

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer, FEED_ID, VolumeAdaptionSetting.OFF)

        verify(mediaPlayer, never()).pause(anyBoolean(), anyBoolean())
        verify(mediaPlayer, never()).resume()
    }

    @Test
    fun noChangeIfPlayerStatusIsStopped() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        `when`(mediaPlayer.getPlayerStatus()).thenReturn(PlayerStatus.STOPPED)

        val feedMedia = mockFeedMedia()
        `when`(mediaPlayer.getPlayable()).thenReturn(feedMedia)

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer, FEED_ID, VolumeAdaptionSetting.OFF)

        verify(mediaPlayer, never()).pause(anyBoolean(), anyBoolean())
        verify(mediaPlayer, never()).resume()
    }

    @Test
    fun noChangeIfPlayableIsNoItemOfAffectedFeed() {
        `when`(mediaPlayer.getPlayerStatus()).thenReturn(PlayerStatus.PLAYING)

        val feedMedia = mockFeedMedia()
        `when`(mediaPlayer.getPlayable()).thenReturn(feedMedia)
        `when`(feedMedia.getItem()!!.getFeed()!!.getId()).thenReturn(FEED_ID + 1)

        val playbackVolumeUpdater = PlaybackVolumeUpdater()
        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer, FEED_ID, VolumeAdaptionSetting.OFF)

        verify(mediaPlayer, never()).pause(anyBoolean(), anyBoolean())
        verify(mediaPlayer, never()).resume()
    }

    @Test
    fun updatesPreferencesForLoadedFeedMediaIfPlayerStatusIsPaused() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        `when`(mediaPlayer.getPlayerStatus()).thenReturn(PlayerStatus.PAUSED)

        val feedMedia = mockFeedMedia()
        `when`(mediaPlayer.getPlayable()).thenReturn(feedMedia)
        val feedPreferences = feedMedia.getItem()!!.getFeed()!!.getPreferences()!!

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer, FEED_ID, VolumeAdaptionSetting.LIGHT_REDUCTION)

        verify(feedPreferences, times(1)).setVolumeAdaptionSetting(VolumeAdaptionSetting.LIGHT_REDUCTION)

        verify(mediaPlayer, never()).pause(anyBoolean(), anyBoolean())
        verify(mediaPlayer, never()).resume()
    }

    @Test
    fun updatesPreferencesForLoadedFeedMediaIfPlayerStatusIsPrepared() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        `when`(mediaPlayer.getPlayerStatus()).thenReturn(PlayerStatus.PREPARED)

        val feedMedia = mockFeedMedia()
        `when`(mediaPlayer.getPlayable()).thenReturn(feedMedia)
        val feedPreferences = feedMedia.getItem()!!.getFeed()!!.getPreferences()!!

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer, FEED_ID, VolumeAdaptionSetting.LIGHT_REDUCTION)

        verify(feedPreferences, times(1)).setVolumeAdaptionSetting(VolumeAdaptionSetting.LIGHT_REDUCTION)

        verify(mediaPlayer, never()).pause(anyBoolean(), anyBoolean())
        verify(mediaPlayer, never()).resume()
    }

    @Test
    fun updatesPreferencesForLoadedFeedMediaIfPlayerStatusIsInitializing() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        `when`(mediaPlayer.getPlayerStatus()).thenReturn(PlayerStatus.INITIALIZING)

        val feedMedia = mockFeedMedia()
        `when`(mediaPlayer.getPlayable()).thenReturn(feedMedia)
        val feedPreferences = feedMedia.getItem()!!.getFeed()!!.getPreferences()!!

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer, FEED_ID, VolumeAdaptionSetting.LIGHT_REDUCTION)

        verify(feedPreferences, times(1)).setVolumeAdaptionSetting(VolumeAdaptionSetting.LIGHT_REDUCTION)

        verify(mediaPlayer, never()).pause(anyBoolean(), anyBoolean())
        verify(mediaPlayer, never()).resume()
    }

    @Test
    fun updatesPreferencesForLoadedFeedMediaIfPlayerStatusIsPreparing() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        `when`(mediaPlayer.getPlayerStatus()).thenReturn(PlayerStatus.PREPARING)

        val feedMedia = mockFeedMedia()
        `when`(mediaPlayer.getPlayable()).thenReturn(feedMedia)
        val feedPreferences = feedMedia.getItem()!!.getFeed()!!.getPreferences()!!

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer, FEED_ID, VolumeAdaptionSetting.LIGHT_REDUCTION)

        verify(feedPreferences, times(1)).setVolumeAdaptionSetting(VolumeAdaptionSetting.LIGHT_REDUCTION)

        verify(mediaPlayer, never()).pause(anyBoolean(), anyBoolean())
        verify(mediaPlayer, never()).resume()
    }

    @Test
    fun updatesPreferencesForLoadedFeedMediaIfPlayerStatusIsSeeking() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        `when`(mediaPlayer.getPlayerStatus()).thenReturn(PlayerStatus.SEEKING)

        val feedMedia = mockFeedMedia()
        `when`(mediaPlayer.getPlayable()).thenReturn(feedMedia)
        val feedPreferences = feedMedia.getItem()!!.getFeed()!!.getPreferences()!!

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer, FEED_ID, VolumeAdaptionSetting.LIGHT_REDUCTION)

        verify(feedPreferences, times(1)).setVolumeAdaptionSetting(VolumeAdaptionSetting.LIGHT_REDUCTION)

        verify(mediaPlayer, never()).pause(anyBoolean(), anyBoolean())
        verify(mediaPlayer, never()).resume()
    }

    @Test
    fun updatesPreferencesAndForcesVolumeChangeForLoadedFeedMediaIfPlayerStatusIsPlaying() {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()

        `when`(mediaPlayer.getPlayerStatus()).thenReturn(PlayerStatus.PLAYING)

        val feedMedia = mockFeedMedia()
        `when`(mediaPlayer.getPlayable()).thenReturn(feedMedia)
        val feedPreferences = feedMedia.getItem()!!.getFeed()!!.getPreferences()!!

        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer, FEED_ID, VolumeAdaptionSetting.HEAVY_REDUCTION)

        verify(feedPreferences, times(1)).setVolumeAdaptionSetting(VolumeAdaptionSetting.HEAVY_REDUCTION)

        verify(mediaPlayer, times(1)).pause(false, false)
        verify(mediaPlayer, times(1)).resume()
    }

    private fun mockFeedMedia(): FeedMedia {
        val feedMedia = mock(FeedMedia::class.java)
        val feedItem = mock(FeedItem::class.java)
        val feed = mock(Feed::class.java)
        val feedPreferences = mock(FeedPreferences::class.java)

        `when`(feedMedia.getItem()).thenReturn(feedItem)
        `when`(feedItem.getFeed()).thenReturn(feed)
        `when`(feed.getId()).thenReturn(FEED_ID)
        `when`(feed.getPreferences()).thenReturn(feedPreferences)
        return feedMedia
    }
}
