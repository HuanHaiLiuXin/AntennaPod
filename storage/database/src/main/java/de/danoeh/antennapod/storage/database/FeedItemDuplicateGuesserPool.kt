package de.danoeh.antennapod.storage.database

import de.danoeh.antennapod.model.feed.FeedItem
import org.apache.commons.lang3.StringUtils

import java.util.ArrayList
import java.util.HashMap

class FeedItemDuplicateGuesserPool {
    private val normalizedTitles: HashMap<String, MutableList<FeedItem>> = HashMap()
    private val downloadUrls: HashMap<String, FeedItem> = HashMap()
    private val identifiers: HashMap<String, FeedItem> = HashMap()

    constructor(itemsList: List<FeedItem>) {
        for (item in itemsList) {
            add(item)
        }
    }

    fun add(item: FeedItem) {
        val normalizedTitle = FeedItemDuplicateGuesser.canonicalizeTitle(item.getTitle())
        if (!normalizedTitles.containsKey(normalizedTitle)) {
            normalizedTitles.put(normalizedTitle, ArrayList())
        }
        normalizedTitles[normalizedTitle]!!.add(item)
        if (item.getMedia() != null && !StringUtils.isEmpty(item.getMedia()!!.getStreamUrl())
                && !downloadUrls.containsKey(item.getMedia()!!.getStreamUrl())) {
            downloadUrls.put(item.getMedia()!!.getStreamUrl()!!, item)
        }
        if (item.getIdentifyingValue() != null && !identifiers.containsKey(item.getIdentifyingValue())) {
            identifiers.put(item.getIdentifyingValue()!!, item)
        }
    }

    fun guessDuplicate(searchItem: FeedItem): FeedItem? {
        if (searchItem.getMedia() != null && !StringUtils.isEmpty(searchItem.getMedia()!!.getStreamUrl())
                && downloadUrls.containsKey(searchItem.getMedia()!!.getStreamUrl())) {
            return downloadUrls[searchItem.getMedia()!!.getStreamUrl()]
        }
        val normalizedTitle = FeedItemDuplicateGuesser.canonicalizeTitle(searchItem.getTitle())
        val candidates = normalizedTitles[normalizedTitle]
        if (candidates == null) {
            return null
        }
        for (item in candidates) {
            if (FeedItemDuplicateGuesser.seemDuplicates(item, searchItem)) {
                return item
            }
        }
        return null
    }

    fun findById(item: FeedItem): FeedItem? {
        if (identifiers.containsKey(item.getIdentifyingValue())) {
            return identifiers[item.getIdentifyingValue()]
        }
        return null
    }
}
