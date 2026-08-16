package de.danoeh.antennapod.event

import de.danoeh.antennapod.model.feed.FeedItem

class FeedItemEvent(@JvmField val items: List<FeedItem>, @JvmField val unreadStatusChanged: Boolean) {

    companion object {
        @JvmStatic
        fun indexOfItemWithId(items: List<FeedItem>, id: Long): Int {
            for (i in items.indices) {
                val item = items[i]
                if (item != null && item.getId() == id) {
                    return i
                }
            }
            return -1
        }
    }
}
