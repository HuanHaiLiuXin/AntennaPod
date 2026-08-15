package de.danoeh.antennapod.event.settings

class SkipIntroEndingChangedEvent {
    private val skipIntro: Int
    private val skipEnding: Int
    private val feedId: Long

    constructor(skipIntro: Int, skipEnding: Int, feedId: Long) {
        this.skipIntro = skipIntro
        this.skipEnding = skipEnding
        this.feedId = feedId
    }

    fun getSkipIntro(): Int {
        return skipIntro
    }

    fun getSkipEnding(): Int {
        return skipEnding
    }

    fun getFeedId(): Long {
        return feedId
    }
}
