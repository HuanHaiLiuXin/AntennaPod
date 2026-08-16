package de.danoeh.antennapod.storage.database

import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.Date
import java.util.HashMap
import java.util.Locale

import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.storage.preferences.UserPreferences

/**
 * Provides method for sorting the a list of [FeedItem] according to rules.
 */
class FeedItemPermutors {

    companion object {
        /**
         * Returns a Permutor that sorts a list appropriate to the given sort order.
         *
         * @return Permutor that sorts a list appropriate to the given sort order.
         */
        @JvmStatic
        fun getPermutor(sortOrder: SortOrder): Permutor<FeedItem> {
            var sortOrder = sortOrder

            var comparator: Comparator<FeedItem>? = null
            var permutor: Permutor<FeedItem>? = null

            if (SortOrder.GLOBAL_DEFAULT == sortOrder) {
                sortOrder = UserPreferences.getPrefGlobalSortedOrder()
            }

            when (sortOrder) {
                SortOrder.EPISODE_TITLE_A_Z -> comparator = Comparator { f1, f2 -> itemTitle(f1).compareTo(itemTitle(f2)) }
                SortOrder.EPISODE_TITLE_Z_A -> comparator = Comparator { f1, f2 -> itemTitle(f2).compareTo(itemTitle(f1)) }
                SortOrder.DATE_OLD_NEW -> comparator = Comparator { f1, f2 -> pubDate(f1).compareTo(pubDate(f2)) }
                SortOrder.DATE_NEW_OLD -> comparator = Comparator { f1, f2 -> pubDate(f2).compareTo(pubDate(f1)) }
                SortOrder.DURATION_SHORT_LONG -> comparator = Comparator { f1, f2 -> Integer.compare(duration(f1), duration(f2)) }
                SortOrder.DURATION_LONG_SHORT -> comparator = Comparator { f1, f2 -> Integer.compare(duration(f2), duration(f1)) }
                SortOrder.EPISODE_FILENAME_A_Z -> comparator = Comparator { f1, f2 -> itemLink(f1).compareTo(itemLink(f2)) }
                SortOrder.EPISODE_FILENAME_Z_A -> comparator = Comparator { f1, f2 -> itemLink(f2).compareTo(itemLink(f1)) }
                SortOrder.FEED_TITLE_A_Z -> comparator = Comparator { f1, f2 -> feedTitle(f1).compareTo(feedTitle(f2)) }
                SortOrder.FEED_TITLE_Z_A -> comparator = Comparator { f1, f2 -> feedTitle(f2).compareTo(feedTitle(f1)) }
                SortOrder.RANDOM -> permutor = object : Permutor<FeedItem> {
                    override fun reorder(queue: MutableList<FeedItem>) {
                        Collections.shuffle(queue)
                    }
                }
                SortOrder.SMART_SHUFFLE_OLD_NEW -> permutor = object : Permutor<FeedItem> {
                    override fun reorder(queue: MutableList<FeedItem>) {
                        smartShuffle(queue, true)
                    }
                }
                SortOrder.SMART_SHUFFLE_NEW_OLD -> permutor = object : Permutor<FeedItem> {
                    override fun reorder(queue: MutableList<FeedItem>) {
                        smartShuffle(queue, false)
                    }
                }
                SortOrder.SIZE_SMALL_LARGE -> comparator = Comparator { f1, f2 -> java.lang.Long.compare(size(f1), size(f2)) }
                SortOrder.SIZE_LARGE_SMALL -> comparator = Comparator { f1, f2 -> java.lang.Long.compare(size(f2), size(f1)) }
                SortOrder.COMPLETION_DATE_NEW_OLD -> comparator = Comparator { f1, f2 -> f2.getMedia()!!.getLastPlayedTimeHistory()!!
                        .compareTo(f1.getMedia()!!.getLastPlayedTimeHistory()) }
                else -> throw IllegalArgumentException("Permutor not implemented")
            }

            if (comparator != null) {
                val comparator2 = comparator
                permutor = object : Permutor<FeedItem> {
                    override fun reorder(queue: MutableList<FeedItem>) {
                        Collections.sort(queue, comparator2)
                    }
                }
            }
            return permutor!!
        }

        // Null-safe accessors

        private fun pubDate(item: FeedItem?): Date {
            return if (item != null && item.getPubDate() != null) item.getPubDate()!! else Date(0)
        }

        private fun itemTitle(item: FeedItem?): String {
            return if (item != null && item.getTitle() != null) item.getTitle()!!.lowercase(Locale.getDefault()) else ""
        }

        private fun duration(item: FeedItem?): Int {
            return if (item != null && item.getMedia() != null) item.getMedia()!!.getDuration() else 0
        }

        private fun size(item: FeedItem?): Long {
            return if (item != null && item.getMedia() != null) item.getMedia()!!.getSize() else 0
        }

        private fun itemLink(item: FeedItem?): String {
            return if (item != null && item.getLink() != null)
                item.getLink()!!.lowercase(Locale.getDefault()) else ""
        }

        private fun feedTitle(item: FeedItem?): String {
            return if (item != null && item.getFeed() != null && item.getFeed()!!.getTitle() != null)
                item.getFeed()!!.getTitle()!!.lowercase(Locale.getDefault()) else ""
        }

        /**
         * Implements a reordering by pubdate that avoids consecutive episodes from the same feed in
         * the queue.
         *
         * A listener might want to hear episodes from any given feed in pubdate order, but would
         * prefer a more balanced ordering that avoids having to listen to clusters of consecutive
         * episodes from the same feed. This is what "Smart Shuffle" tries to accomplish.
         *
         * Assume the queue looks like this: `ABCDDEEEEEEEEEE`.
         * This method first starts with a queue of the final size, where each slot is empty (null).
         * It takes the podcast with most episodes (`E`) and places the episodes spread out in the queue: `EE_E_EE_E_EE_EE`.
         * The podcast with the second-most number of episodes (`D`) is then
         * placed spread-out in the *available* slots: `EE_EDEE_EDEE_EE`.
         * This continues, until we end up with: `EEBEDEECEDEEAEE`.
         *
         * Note that episodes aren't strictly ordered in terms of pubdate, but episodes of each feed are.
         *
         * @param queue A (modifiable) list of FeedItem elements to be reordered.
         * @param ascending `true` to use ascending pubdate in the reordering;
         *                  `false` for descending.
         */
        private fun smartShuffle(queue: MutableList<FeedItem>, ascending: Boolean) {
            // Divide FeedItems into lists by feed
            val map = HashMap<Long, MutableList<FeedItem>>()
            for (item in queue) {
                val id = item.getFeedId()
                if (!map.containsKey(id)) {
                    map.put(id, ArrayList())
                }
                map[id]!!.add(item)
            }

            // Sort each individual list by PubDate (ascending/descending)
            val itemComparator = if (ascending)
                Comparator { f1: FeedItem, f2 -> f1.getPubDate()!!.compareTo(f2.getPubDate()) }
            else
                Comparator { f1: FeedItem, f2 -> f2.getPubDate()!!.compareTo(f1.getPubDate()) }
            val feeds = ArrayList<MutableList<FeedItem>>()
            for (mapEntry in map.entries) {
                Collections.sort(mapEntry.value, itemComparator)
                feeds.add(mapEntry.value)
            }

            val emptySlots = ArrayList<Int>()
            for (i in queue.indices) {
                queue[i] = null as FeedItem
                emptySlots.add(i)
            }

            // Starting with the largest feed, place items spread out through the empty slots in the queue
            Collections.sort(feeds) { f1, f2 -> Integer.compare(f2.size, f1.size) }
            for (feedItems in feeds) {
                val spread = emptySlots.size.toDouble() / (feedItems.size + 1)
                val emptySlotIterator = emptySlots.iterator()
                var skipped = 0
                var placed = 0
                while (emptySlotIterator.hasNext()) {
                    val nextEmptySlot = emptySlotIterator.next()
                    skipped++
                    if (skipped >= spread * (placed + 1)) {
                        if (queue[nextEmptySlot] != null) {
                            throw RuntimeException("Slot to be placed in not empty")
                        }
                        queue[nextEmptySlot] = feedItems[placed]
                        emptySlotIterator.remove()
                        placed++
                        if (placed == feedItems.size) {
                            break
                        }
                    }
                }
            }
        }
    }
}
