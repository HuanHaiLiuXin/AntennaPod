package de.danoeh.antennapod.event

import de.danoeh.antennapod.model.feed.Feed

class FeedListUpdateEvent {
    private val feeds = ArrayList<Long>()

    constructor(feeds: List<Feed>) {
        for (feed in feeds) {
            this.feeds.add(feed.getId())
        }
    }

    constructor(feed: Feed) {
        feeds.add(feed.getId())
    }

    constructor(feedId: Long) {
        feeds.add(feedId)
    }

    fun contains(feed: Feed): Boolean {
        return feeds.contains(feed.getId())
    }
}
