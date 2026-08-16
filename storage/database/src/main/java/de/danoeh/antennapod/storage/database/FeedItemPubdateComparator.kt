package de.danoeh.antennapod.storage.database

import java.util.Comparator

import de.danoeh.antennapod.model.feed.FeedItem

/**
 * Compares the pubDate of two FeedItems for sorting.
 */
class FeedItemPubdateComparator : Comparator<FeedItem> {

    /**
     * Returns a new instance of this comparator in reverse order.
     */
    override fun compare(lhs: FeedItem, rhs: FeedItem): Int {
        if (rhs.getPubDate() == null && lhs.getPubDate() == null) {
            return 0
        } else if (rhs.getPubDate() == null) {
            return 1
        } else if (lhs.getPubDate() == null) {
            return -1
        }
        return rhs.getPubDate()!!.compareTo(lhs.getPubDate())
    }

}
