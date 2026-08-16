package de.danoeh.antennapod.net.download.service.episode.autodownload

import android.content.Context
import android.util.Log

import java.util.ArrayList
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutionException

import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.preferences.UserPreferences

/**
 * Implementation of the EpisodeCleanupAlgorithm interface used by AntennaPod.
 */
class APCleanupAlgorithm : EpisodeCleanupAlgorithm {

    companion object {
        private const val TAG = "APCleanupAlgorithm"

        private fun minusHours(baseDate: Date, numberOfHours: Int): Date {
            val cal = Calendar.getInstance()
            cal.setTime(baseDate)

            cal.add(Calendar.HOUR_OF_DAY, -1 * numberOfHours)

            return cal.getTime()
        }
    }

    /** the number of days after playback to wait before an item is eligible to be cleaned up.
        Fractional for number of hours, e.g., 0.5 = 12 hours, 0.0416 = 1 hour.  */
    private val numberOfHoursAfterPlayback: Int

    constructor(numberOfHoursAfterPlayback: Int) {
        this.numberOfHoursAfterPlayback = numberOfHoursAfterPlayback
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

        Collections.sort(candidates) { lhs, rhs ->
            var l = lhs.getMedia()!!.getLastPlayedTimeHistory()
            var r = rhs.getMedia()!!.getLastPlayedTimeHistory()

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

    fun calcMostRecentDateForDeletion(currentDate: Date): Date {
        return minusHours(currentDate, numberOfHoursAfterPlayback)
    }

    private fun getCandidates(): List<FeedItem> {
        val candidates = ArrayList<FeedItem>()
        val downloadedItems = DBReader.getEpisodes(0, Int.MAX_VALUE,
                FeedItemFilter(FeedItemFilter.DOWNLOADED), SortOrder.DATE_NEW_OLD)

        val mostRecentDateForDeletion = calcMostRecentDateForDeletion(Date())
        for (item in downloadedItems) {
            if (item.hasMedia()
                    && item.getMedia()!!.isDownloaded()
                    && (!item.getFeed()!!.isLocalFeed() || UserPreferences.isAutoDeleteLocal())
                    && !item.isTagged(FeedItem.TAG_QUEUE)
                    && item.isPlayed()
                    && !item.isTagged(FeedItem.TAG_FAVORITE)) {
                val media = item.getMedia()
                // make sure this candidate was played at least the proper amount of days prior
                // to now
                if (media != null
                        && media.getLastPlayedTimeHistory() != null
                        && media.getLastPlayedTimeHistory()!!.before(mostRecentDateForDeletion)) {
                    candidates.add(item)
                }
            }
        }
        return candidates
    }

    override fun getDefaultCleanupParameter(): Int {
        return getNumEpisodesToCleanup(0)
    }

    fun getNumberOfHoursAfterPlayback(): Int {
        return numberOfHoursAfterPlayback
    }
}
