package de.danoeh.antennapod.event.settings

import de.danoeh.antennapod.model.feed.FeedPreferences

class SpeedPresetChangedEvent {
    private val speed: Float
    private val skipSilence: FeedPreferences.SkipSilence
    private val feedId: Long

    constructor(speed: Float, feedId: Long, skipSilence: FeedPreferences.SkipSilence) {
        this.speed = speed
        this.feedId = feedId
        this.skipSilence = skipSilence
    }

    fun getSpeed(): Float {
        return speed
    }

    fun getSkipSilence(): FeedPreferences.SkipSilence {
        return skipSilence
    }

    fun getFeedId(): Long {
        return feedId
    }
}
