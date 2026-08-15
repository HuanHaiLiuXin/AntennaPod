package de.danoeh.antennapod.playback.service.internal

import android.content.Context
import android.util.Log
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.playback.service.R
import org.greenrobot.eventbus.EventBus

class SkipUtils private constructor() {
    companion object {
        private const val TAG = "SkipUtils"

        /**
         * Returns the position to start playback at, taking into account the configured skip intro time.
         * Uses media's saved position if > 0.
         */
        @JvmStatic
        fun skipIntroIfNecessary(context: Context, media: FeedMedia): Long {
            if (media.getItem() == null || media.getItem()!!.getFeed() == null
                    || media.getItem()!!.getFeed()!!.getPreferences() == null) {
                return media.getPosition().toLong()
            }
            val skipIntro = media.getItem()!!.getFeed()!!.getPreferences()!!.getFeedSkipIntro()
            val duration = media.getDuration().toLong()
            var startPosition = media.getPosition().toLong()
            if (skipIntro > 0 && media.getPosition() < skipIntro * 1000L
                    && (skipIntro * 1000L < duration || duration <= 0)) {
                startPosition = skipIntro * 1000L
            }
            if (startPosition != media.getPosition().toLong()) {
                Log.d(TAG, "skipIntro " + media.getEpisodeTitle())
                EventBus.getDefault().post(MessageEvent(
                        context.getResources().getQuantityString(R.plurals.pref_feed_skip_intro_snackbar,
                                (startPosition / 1000).toInt(), (startPosition / 1000).toInt())))
            }
            return startPosition
        }

        /**
         * Returns true and notifies the user if the ending should be
         * skipped given the current position, duration and playback speed.
         */
        @JvmStatic
        fun skipEndingIfNecessary(context: Context, media: FeedMedia,
                                  position: Long, duration: Long, speed: Float): Boolean {
            if (media.getItem() == null || media.getItem()!!.getFeed() == null
                    || media.getItem()!!.getFeed()!!.getPreferences() == null) {
                return false
            }
            val preferences = media.getItem()!!.getFeed()!!.getPreferences()!!
            val skipEnd = preferences.getFeedSkipEnding()
            val remainingTime = duration - position
            if (skipEnd > 0
                    && skipEnd * 1000L < duration
                    && (remainingTime - (skipEnd * 1000L) > 0)
                    && ((remainingTime - skipEnd * 1000L) < (speed * 1000))) {
                Log.d(TAG, "skipEndingIfNecessary: Skipping remaining " + (duration - position))
                EventBus.getDefault().post(
                        MessageEvent(
                                context.getResources().getQuantityString(
                                        R.plurals.pref_feed_skip_ending_snackbar, skipEnd, skipEnd)))
                return true
            }
            return false
        }
    }
}
