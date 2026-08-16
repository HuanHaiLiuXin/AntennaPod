package de.danoeh.antennapod.playback.service.internal

import android.content.Context

import org.greenrobot.eventbus.EventBus

import java.util.concurrent.TimeUnit

import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.event.playback.SleepTimerUpdatedEvent
import de.danoeh.antennapod.model.playback.TimerValue

class EpisodeSleepTimer : ClockSleepTimer {

    constructor(context: Context) : super(context) {
    }

    override fun isEndingThisEpisode(episodeRemainingMillis: Long): Boolean {
        return getTimeLeft().getDisplayValue() == 1L
    }

    override fun getTimeLeft(): TimerValue {
        val x = super.getTimeLeft()
        return TimerValue(x.getDisplayValue(), TimeUnit.DAYS.toMillis(x.getDisplayValue()))
    }

    override fun playbackPositionUpdate(playbackPositionEvent: PlaybackPositionEvent) {
        val currentEpisodeTimeLeft = (playbackPositionEvent.getDuration() - playbackPositionEvent.getPosition()).toLong()

        val current = getTimeLeft()

        if (isEndingThisEpisode(playbackPositionEvent.getPosition().toLong())) {
            // if we're ending this episode send the "correct" remaining time
            // this ensures that the last 10 seconds the playback volume will be reduced
            EventBus.getDefault().post(SleepTimerUpdatedEvent.updated(TimerValue(
                    current.getDisplayValue(), currentEpisodeTimeLeft)))

            if (currentEpisodeTimeLeft < SleepTimer.NOTIFICATION_THRESHOLD) {
                notifyAboutExpiry()
            }
        } else {
            // if we have more than 1 episode left then just report the current values
            EventBus.getDefault().post(SleepTimerUpdatedEvent.updated(current))
        }
    }

    override fun episodeFinishedPlayback() {
        // episode has finished, decrease the number of episodes left
        updateRemainingTime(getTimeLeft().getDisplayValue() - 1)
    }

    override fun shouldContinueToNextEpisode(): Boolean {
        val cont = getTimeLeft().getDisplayValue() > 0 // number of episodes left
        // stop ourselves too if we're blocking playback
        if (!cont) {
            stop()
        }

        return cont
    }
}
