package de.danoeh.antennapod.storage.database

import de.danoeh.antennapod.model.feed.Feed

import java.util.ArrayList

class NavDrawerData {
    @JvmField
    val feeds: List<Feed>
    @JvmField
    val tags: List<TagItem>
    @JvmField
    val queueSize: Int
    @JvmField
    val numNewItems: Int
    @JvmField
    val numDownloadedItems: Int
    @JvmField
    val feedCounters: Map<Long, Int>

    constructor(feeds: List<Feed>,
                tags: List<TagItem>,
                queueSize: Int,
                numNewItems: Int,
                numDownloadedItems: Int,
                feedIndicatorValues: Map<Long, Int>) {
        this.feeds = feeds
        this.tags = tags
        this.queueSize = queueSize
        this.numNewItems = numNewItems
        this.numDownloadedItems = numDownloadedItems
        this.feedCounters = feedIndicatorValues
    }

    class TagItem {
        private val name: String
        private var feeds = ArrayList<Feed>()
        private var counter = 0
        private var isOpen = false
        private val id: Long

        constructor(name: String) {
            this.name = name
            // Keep IDs >0 but make room for many feeds
            this.id = (Math.abs(name.hashCode().toLong()) shl 20)
        }

        fun getTitle(): String {
            return name
        }

        fun isOpen(): Boolean {
            return isOpen
        }

        fun setOpen(open: Boolean) {
            isOpen = open
        }

        fun getFeeds(): List<Feed> {
            return feeds
        }

        fun getCounter(): Int {
            return counter
        }

        fun addFeed(feed: Feed, feedCounter: Int) {
            counter += feedCounter
            feeds.add(feed)
        }

        fun getId(): Long {
            return id
        }
    }
}
