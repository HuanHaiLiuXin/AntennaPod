package de.danoeh.antennapod.storage.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

import android.util.Log
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.model.playback.MediaType
import de.danoeh.antennapod.model.playback.Playable

/**
 * Provides access to preferences set by the playback service. A private
 * instance of this class must first be instantiated via createInstance() or
 * otherwise every public method will throw an Exception when called.
 */
abstract class PlaybackPreferences private constructor() {

    companion object {

        private const val TAG = "PlaybackPreferences"

        /**
         * Contains the feed id of the currently playing item if it is a FeedMedia
         * object.
         */
        private const val PREF_CURRENTLY_PLAYING_FEED_ID = "de.danoeh.antennapod.preferences.lastPlayedFeedId"

        /**
         * Contains the id of the currently playing FeedMedia object or
         * NO_MEDIA_PLAYING if the currently playing media is no FeedMedia object.
         */
        private const val PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID
                = "de.danoeh.antennapod.preferences.lastPlayedFeedMediaId"

        /**
         * Type of the media object that is currently being played. This preference
         * is set to NO_MEDIA_PLAYING after playback has been completed and is set
         * as soon as the 'play' button is pressed.
         */
        private const val PREF_CURRENTLY_PLAYING_MEDIA_TYPE
                = "de.danoeh.antennapod.preferences.currentlyPlayingMedia"

        /**
         * True if last played media was a video.
         */
        private const val PREF_CURRENT_EPISODE_IS_VIDEO = "de.danoeh.antennapod.preferences.lastIsVideo"

        /**
         * The current player status as int.
         */
        private const val PREF_CURRENT_PLAYER_STATUS = "de.danoeh.antennapod.preferences.currentPlayerStatus"

        /**
         * A temporary playback speed which overrides the per-feed playback speed for the currently playing
         * media. Considered unset if set to SPEED_USE_GLOBAL;
         */
        private const val PREF_CURRENTLY_PLAYING_TEMPORARY_PLAYBACK_SPEED
                = "de.danoeh.antennapod.preferences.temporaryPlaybackSpeed"

        /**
         * A temporary skip silence preference which overrides the per-feed skip silence for the currently playing
         * media. Considered unset if set to null;
         */
        private const val PREF_CURRENTLY_PLAYING_TEMPORARY_SKIP_SILENCE
                = "de.danoeh.antennapod.preferences.temporarySkipSilence"

        /**
         * Value of PREF_CURRENTLY_PLAYING_MEDIA if no media is playing.
         */
        const val NO_MEDIA_PLAYING = -1L

        /**
         * Value of PREF_CURRENT_PLAYER_STATUS if media player status is playing.
         */
        const val PLAYER_STATUS_PLAYING = 1

        /**
         * Value of PREF_CURRENT_PLAYER_STATUS if media player status is paused.
         */
        const val PLAYER_STATUS_PAUSED = 2

        /**
         * Value of PREF_CURRENT_PLAYER_STATUS if media player status is neither playing nor paused.
         */
        const val PLAYER_STATUS_OTHER = 3

        private var prefs: SharedPreferences? = null

        @JvmStatic
        fun init(context: Context) {
            prefs = PreferenceManager.getDefaultSharedPreferences(context)
        }

        @JvmStatic
        fun getCurrentlyPlayingMediaType(): Long {
            return prefs!!.getLong(PREF_CURRENTLY_PLAYING_MEDIA_TYPE, NO_MEDIA_PLAYING)
        }

        @JvmStatic
        fun getCurrentlyPlayingFeedMediaId(): Long {
            if (PlaybackPreferences.getCurrentlyPlayingMediaType() == NO_MEDIA_PLAYING) {
                return NO_MEDIA_PLAYING
            }
            return prefs!!.getLong(PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID, NO_MEDIA_PLAYING)
        }

        @JvmStatic
        fun getCurrentEpisodeIsVideo(): Boolean {
            return prefs!!.getBoolean(PREF_CURRENT_EPISODE_IS_VIDEO, false)
        }

        @JvmStatic
        fun getCurrentPlayerStatus(): Int {
            return prefs!!.getInt(PREF_CURRENT_PLAYER_STATUS, PLAYER_STATUS_OTHER)
        }

        @JvmStatic
        fun setCurrentPlayerStatus(playerStatus: Int) {
            prefs!!.edit().putInt(PREF_CURRENT_PLAYER_STATUS, playerStatus).apply()
        }

        @JvmStatic
        fun getCurrentlyPlayingTemporaryPlaybackSpeed(): Float {
            return prefs!!.getFloat(PREF_CURRENTLY_PLAYING_TEMPORARY_PLAYBACK_SPEED, FeedPreferences.SPEED_USE_GLOBAL)
        }

        @JvmStatic
        fun getCurrentlyPlayingTemporarySkipSilence(): FeedPreferences.SkipSilence {
            return FeedPreferences.SkipSilence.fromCode(prefs!!.getInt(
                    PREF_CURRENTLY_PLAYING_TEMPORARY_SKIP_SILENCE, FeedPreferences.SkipSilence.GLOBAL.code))
        }

        @JvmStatic
        fun writeNoMediaPlaying() {
            val editor = prefs!!.edit()
            editor.putLong(PREF_CURRENTLY_PLAYING_MEDIA_TYPE, NO_MEDIA_PLAYING)
            editor.putLong(PREF_CURRENTLY_PLAYING_FEED_ID, NO_MEDIA_PLAYING)
            editor.putLong(PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID, NO_MEDIA_PLAYING)
            editor.putInt(PREF_CURRENT_PLAYER_STATUS, PLAYER_STATUS_OTHER)
            editor.apply()
        }

        @JvmStatic
        fun writeMediaPlaying(playable: Playable?) {
            Log.d(TAG, "Writing playback preferences")
            val editor = prefs!!.edit()

            if (playable == null) {
                writeNoMediaPlaying()
            } else {
                editor.putLong(PREF_CURRENTLY_PLAYING_MEDIA_TYPE, playable.getPlayableType().toLong())
                editor.putBoolean(PREF_CURRENT_EPISODE_IS_VIDEO, playable.getMediaType() == MediaType.VIDEO)
                if (playable is FeedMedia) {
                    val feedMedia = playable as FeedMedia
                    editor.putLong(PREF_CURRENTLY_PLAYING_FEED_ID, feedMedia.getItem()!!.getFeed()!!.getId())
                    editor.putLong(PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID, feedMedia.getId())
                } else {
                    editor.putLong(PREF_CURRENTLY_PLAYING_FEED_ID, NO_MEDIA_PLAYING)
                    editor.putLong(PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID, NO_MEDIA_PLAYING)
                }
            }
            editor.apply()
        }

        @JvmStatic
        fun setCurrentlyPlayingTemporaryPlaybackSpeed(speed: Float) {
            val editor = prefs!!.edit()
            editor.putFloat(PREF_CURRENTLY_PLAYING_TEMPORARY_PLAYBACK_SPEED, speed)
            editor.apply()
        }

        @JvmStatic
        fun setCurrentlyPlayingTemporarySkipSilence(skipSilence: Boolean) {
            val editor = prefs!!.edit()
            editor.putInt(PREF_CURRENTLY_PLAYING_TEMPORARY_SKIP_SILENCE, if (skipSilence)
                FeedPreferences.SkipSilence.AGGRESSIVE.code else FeedPreferences.SkipSilence.OFF.code)
            editor.apply()
        }

        @JvmStatic
        fun clearCurrentlyPlayingTemporaryPlaybackSettings() {
            val editor = prefs!!.edit()
            editor.remove(PREF_CURRENTLY_PLAYING_TEMPORARY_PLAYBACK_SPEED)
            editor.remove(PREF_CURRENTLY_PLAYING_TEMPORARY_SKIP_SILENCE)
            editor.apply()
        }
    }
}
