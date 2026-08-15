package de.danoeh.antennapod.net.download.service.episode.autodownload

import android.content.Context
import android.util.Log

import java.util.ArrayList
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutionException

import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.preferences.UserPreferences

/**
 * A cleanup algorithm that removes any item that isn't in the queue and isn't a favorite
 * but only if space is needed.
 */
class APQueueCleanupAlgorithm : EpisodeCleanupAlgorithm() {

    companion object {
        private const val TAG = "APQueueCleanupAlgorithm"
    }

    /**
     * @return the number of episodes that *could* be cleaned up, if needed
     */
    override fun getReclaimableItems(): Int {
        return getCandidates().size
    }

    override fun performCleanup(context: Context, numberOfEpisodesToDelete: Int): Int {
        val candidates = getCandidates()
        val delete: List<FeedItem>

        // in the absence of better data, we'll sort by item publication date
        Collections.sort(candidates) { lhs, rhs ->
            var l = lhs.getPubDate()
            var r = rhs.getPubDate()

            if (l == null) {
                l = Date()
            }
            if (r == null) {
                r = Date()
            }
            l.compareTo(r)
        }

        if (candidates.size > numberOfEpisodesToDelete) {
            delete = candidates.subList(0, numberOfEpisodesToDelete)
        } else {
            delete = candidates
        }

        for (item in delete) {
            try {
                DBWriter.deleteFeedMediaOfItem(context, item.getMedia()!!)!!.get()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } catch (e: ExecutionException) {
                e.printStackTrace()
            }
        }

        val counter = delete.size


        Log.i(TAG, java.lang.String.format(Locale.US,
                "Auto-delete deleted %d episodes (%d requested)", counter,
                numberOfEpisodesToDelete))

        return counter
    }

    private fun getCandidates(): List<FeedItem> {
        val candidates = ArrayList<FeedItem>()
        val downloadedItems = DBReader.getEpisodes(0, Int.MAX_VALUE,
                FeedItemFilter(FeedItemFilter.DOWNLOADED), SortOrder.DATE_NEW_OLD)
        for (item in downloadedItems) {
            if (item.hasMedia()
                    && item.getMedia()!!.isDownloaded()
                    && (!item.getFeed()!!.isLocalFeed() || UserPreferences.isAutoDeleteLocal())
                    && !item.isTagged(FeedItem.TAG_QUEUE)
                    && !item.isTagged(FeedItem.TAG_FAVORITE)) {
                candidates.add(item)
            }
        }
        return candidates
    }

    override fun getDefaultCleanupParameter(): Int {
        return getNumEpisodesToCleanup(0)
    }
}
