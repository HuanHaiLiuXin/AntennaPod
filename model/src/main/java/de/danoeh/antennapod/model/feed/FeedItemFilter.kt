package de.danoeh.antennapod.model.feed

import java.io.Serializable
import java.util.ArrayList
import java.util.Arrays

class FeedItemFilter : Serializable {

    private var properties: Array<String?> = emptyArray()

    @JvmField
    var showPlayed: Boolean = false

    @JvmField
    var showUnplayed: Boolean = false

    @JvmField
    var showPaused: Boolean = false

    @JvmField
    var showNotPaused: Boolean = false

    @JvmField
    var showNew: Boolean = false

    @JvmField
    var showQueued: Boolean = false

    @JvmField
    var showNotQueued: Boolean = false

    @JvmField
    var showDownloaded: Boolean = false

    @JvmField
    var showNotDownloaded: Boolean = false

    @JvmField
    var showHasMedia: Boolean = false

    @JvmField
    var showNoMedia: Boolean = false

    @JvmField
    var showIsFavorite: Boolean = false

    @JvmField
    var showNotFavorite: Boolean = false

    @JvmField
    var showInHistory: Boolean = false

    @JvmField
    var includeSubscribed: Boolean = false

    @JvmField
    var includeArchived: Boolean = false

    @JvmField
    var includeNotSubscribed: Boolean = false

    constructor(properties: String?) : this(*(if (properties!!.isEmpty()) emptyArray() else properties.split(",").toTypedArray())) {
    }

    constructor(filter: FeedItemFilter, vararg additionalProperties: String) : this(
            filter.getValues().joinToString(",") + "," + additionalProperties.joinToString(",")) {
    }

    constructor(vararg properties: String?) {
        val joined = properties.joinToString(",")
        this.properties = if (joined.isEmpty()) emptyArray() else joined.split(",").toTypedArray()

        // see R.arrays.feed_filter_values
        showUnplayed = hasProperty(UNPLAYED)
        showPaused = hasProperty(PAUSED)
        showNotPaused = hasProperty(NOT_PAUSED)
        showPlayed = hasProperty(PLAYED)
        showQueued = hasProperty(QUEUED)
        showNotQueued = hasProperty(NOT_QUEUED)
        showDownloaded = hasProperty(DOWNLOADED)
        showNotDownloaded = hasProperty(NOT_DOWNLOADED)
        showHasMedia = hasProperty(HAS_MEDIA)
        showNoMedia = hasProperty(NO_MEDIA)
        showIsFavorite = hasProperty(IS_FAVORITE)
        showNotFavorite = hasProperty(NOT_FAVORITE)
        showNew = hasProperty(NEW)
        showInHistory = hasProperty(IS_IN_HISTORY)
        includeSubscribed = hasProperty(INCLUDE_SUBSCRIBED)
        includeArchived = hasProperty(INCLUDE_ARCHIVED)
        includeNotSubscribed = hasProperty(INCLUDE_NOT_SUBSCRIBED)
    }

    private fun hasProperty(property: String?): Boolean {
        return Arrays.asList(*properties).contains(property)
    }

    fun getValues(): Array<String?> {
        return properties.clone()
    }

    fun getValuesList(): List<String?> {
        return Arrays.asList(*properties)
    }

    fun without(property: String?): FeedItemFilter {
        val newValues = ArrayList(Arrays.asList(*properties))
        newValues.remove(property)
        return FeedItemFilter(*newValues.toTypedArray())
    }

    fun matches(item: FeedItem): Boolean {
        if (showNew && !item.isNew()) {
            return false
        } else if (showPlayed && !item.isPlayed()) {
            return false
        } else if (showUnplayed && item.isPlayed()) {
            return false
        } else if (showPaused && !item.isInProgress()) {
            return false
        } else if (showNotPaused && item.isInProgress()) {
            return false
        } else if (showNew && !item.isNew()) {
            return false
        } else if (showQueued && !item.isTagged(FeedItem.TAG_QUEUE)) {
            return false
        } else if (showNotQueued && item.isTagged(FeedItem.TAG_QUEUE)) {
            return false
        } else if (showDownloaded && !item.isDownloaded()) {
            return false
        } else if (showNotDownloaded && item.isDownloaded()) {
            return false
        } else if (showHasMedia && !item.hasMedia()) {
            return false
        } else if (showNoMedia && item.hasMedia()) {
            return false
        } else if (showIsFavorite && !item.isTagged(FeedItem.TAG_FAVORITE)) {
            return false
        } else if (showNotFavorite && item.isTagged(FeedItem.TAG_FAVORITE)) {
            return false
        } else if (showInHistory && item.getMedia() != null
                && item.getMedia()!!.getLastPlayedTimeHistory()!!.time == 0L) {
            return false
        }
        if (item.getFeed() != null) {
            val state = item.getFeed()!!.getState()
            if (includeSubscribed || includeArchived || includeNotSubscribed) {
                if (state == Feed.STATE_SUBSCRIBED && !includeSubscribed) {
                    return false
                } else if (state == Feed.STATE_ARCHIVED && !includeArchived) {
                    return false
                } else if (state == Feed.STATE_NOT_SUBSCRIBED && !includeNotSubscribed) {
                    return false
                }
            } else if (state != Feed.STATE_SUBSCRIBED) {
                return false
            }
        }
        return true
    }

    companion object {
        const val PLAYED = "played"
        const val UNPLAYED = "unplayed"
        const val NEW = "new"
        const val PAUSED = "paused"
        const val NOT_PAUSED = "not_paused"
        const val IS_FAVORITE = "is_favorite"
        const val NOT_FAVORITE = "not_favorite"
        const val HAS_MEDIA = "has_media"
        const val NO_MEDIA = "no_media"
        const val QUEUED = "queued"
        const val NOT_QUEUED = "not_queued"
        const val DOWNLOADED = "downloaded"
        const val NOT_DOWNLOADED = "not_downloaded"
        const val IS_IN_HISTORY = "is_in_history"
        const val INCLUDE_SUBSCRIBED = "include_subscribed"
        const val INCLUDE_ARCHIVED = "include_archived"
        const val INCLUDE_NOT_SUBSCRIBED = "include_not_subscribed"
        const val INCLUDE_ALL_FEED_STATES =
                INCLUDE_SUBSCRIBED + "," + INCLUDE_ARCHIVED + "," + INCLUDE_NOT_SUBSCRIBED

        @JvmStatic
        fun unfiltered(): FeedItemFilter {
            return FeedItemFilter()
        }
    }
}
