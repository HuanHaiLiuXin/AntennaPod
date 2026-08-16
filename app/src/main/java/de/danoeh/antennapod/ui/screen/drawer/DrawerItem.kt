package de.danoeh.antennapod.ui.screen.drawer

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.storage.database.NavDrawerData

class DrawerItem {
    private val feed: Feed?
    private val tag: NavDrawerData.TagItem?
    private var counter: Int
    private val layer: Int

    constructor(feed: Feed, counter: Int, layer: Int) {
        this.tag = null
        this.feed = feed
        this.counter = counter
        this.layer = layer
    }

    constructor(tag: NavDrawerData.TagItem) {
        this.feed = null
        this.tag = tag
        this.counter = 0
        this.layer = 0
    }

    fun isFeed(): Boolean {
        return feed != null
    }

    fun asFeed(): Feed? {
        return feed
    }

    fun asTag(): NavDrawerData.TagItem? {
        return tag
    }

    fun setCounter(counter: Int) {
        this.counter = counter
    }

    fun getCounter(): Int {
        return counter
    }

    fun getLayer(): Int {
        return layer
    }

    fun getTitle(): String? {
        return if (isFeed()) feed!!.getTitle() else tag!!.getTitle()
    }

    fun getId(): Long {
        return if (isFeed()) feed!!.getId() else tag!!.getId()
    }
}
