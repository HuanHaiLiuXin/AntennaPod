package de.danoeh.antennapod.storage.database

import java.util.Random

import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.storage.preferences.UserPreferences.EnqueueLocation
import de.danoeh.antennapod.model.playback.Playable

/**
 * Determine the positions of the new [FeedItem] in the queue.
 */
class ItemEnqueuePositionCalculator {

    private val enqueueLocation: EnqueueLocation

    constructor(enqueueLocation: EnqueueLocation) {
        this.enqueueLocation = enqueueLocation
    }

    /**
     * Determine the position (0-based) that the item(s) should be inserted to the named queue.
     *
     * @param curQueue           the queue to which the item is to be inserted
     * @param currentPlaying     the currently playing media
     */
    fun calcPosition(curQueue: List<FeedItem>, currentPlaying: Playable?): Int {
        when (enqueueLocation) {
            EnqueueLocation.BACK -> return curQueue.size
            EnqueueLocation.FRONT ->
                // Return not necessarily 0, so that when a list of items are downloaded and enqueued
                // in succession of calls (e.g., users manually tapping download one by one),
                // the items enqueued are kept the same order.
                // Simply returning 0 will reverse the order.
                return getPositionOfFirstNonDownloadingItem(0, curQueue)
            EnqueueLocation.AFTER_CURRENTLY_PLAYING -> {
                val currentlyPlayingPosition = getCurrentlyPlayingPosition(curQueue, currentPlaying)
                return getPositionOfFirstNonDownloadingItem(
                        currentlyPlayingPosition + 1, curQueue)
            }
            EnqueueLocation.RANDOM -> {
                val random = Random()
                return random.nextInt(curQueue.size + 1)
            }
            else -> throw AssertionError("calcPosition() : unrecognized enqueueLocation option: " + enqueueLocation)
        }
    }

    private fun getPositionOfFirstNonDownloadingItem(startPosition: Int, curQueue: List<FeedItem>): Int {
        val curQueueSize = curQueue.size
        for (i in startPosition until curQueueSize) {
            if (!isItemAtPositionDownloading(i, curQueue)) {
                return i
            } // else continue to search;
        }
        return curQueueSize
    }

    private fun isItemAtPositionDownloading(position: Int, curQueue: List<FeedItem>): Boolean {
        val curItem: FeedItem? = try {
            curQueue[position]
        } catch (e: IndexOutOfBoundsException) {
            null
        }
        return curItem != null
                && curItem.getMedia() != null
                && DownloadServiceInterface.get()!!.isDownloadingEpisode(curItem.getMedia()!!.getDownloadUrl()!!)
    }

    companion object {
        private fun getCurrentlyPlayingPosition(curQueue: List<FeedItem>,
                                                currentPlaying: Playable?): Int {
            if (currentPlaying !is FeedMedia) {
                return -1
            }
            val curPlayingItemId = currentPlaying.getItem()!!.getId()
            for (i in curQueue.indices) {
                if (curPlayingItemId == curQueue[i].getId()) {
                    return i
                }
            }
            return -1
        }
    }
}
