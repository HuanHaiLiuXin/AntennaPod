package de.danoeh.antennapod.playback.service.internal

import de.danoeh.antennapod.model.playback.TimerValue

interface SleepTimer {

    /**
     * @return Returns time left for this sleep timer, both display value and in milis
     */
    fun getTimeLeft(): TimerValue

    /**
     * Starts the sleep timer.
     * @param initialWaitingTime The waiting time for the sleep timer, either episodes or duration
     */
    fun start(initialWaitingTime: Long)

    /**
     * Cancels (stops) current sleep timer forever, cannot be restarted.
     */
    fun stop()

    /**
     * Update sleep timer with new waiting time
     * @param waitingTimeOrEpisodes Waiting time in millis or episode count
     */
    fun updateRemainingTime(waitingTimeOrEpisodes: Long)

    /**
     * Resets sleep timer to original duration.
     */
    fun reset()

    /**
     * @return True if sleep timer is active, false otherwise
     */
    fun isActive(): Boolean

    /**
     * @param episodeRemainingMillis Remaining milliseconds of current episode
     * @return Returns true if the sleep timer will terminate sometime during this episode, false otherwise
     */
    fun isEndingThisEpisode(episodeRemainingMillis: Long): Boolean

    /**
     * Called when sleep timer is asked if playback is allowed to proceed to next episode.
     * Should take into account the time left, episodes left, etc.
     * @return True if playback is allowed to continue to next episode, false otherwise
     */
    fun shouldContinueToNextEpisode(): Boolean

    fun episodeFinishedPlayback()

    companion object {
        const val NOTIFICATION_THRESHOLD = 10000L
    }
}
