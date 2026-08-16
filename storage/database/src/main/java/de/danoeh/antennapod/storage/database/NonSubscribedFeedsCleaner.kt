package de.danoeh.antennapod.storage.database

import android.content.Context
import android.util.Log
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.SortOrder

import java.util.concurrent.ExecutionException

class NonSubscribedFeedsCleaner {
    companion object {
        private const val TAG = "NonSubscrFeedsCleaner"
        private const val TIME_TO_KEEP_UNTOUCHED = 1000L * 3600 * 24 // 1 day
        private const val TIME_TO_KEEP_PLAYED = 1000L * 3600 * 24 * 30 // 30 days

        @JvmStatic
        fun deleteOldNonSubscribedFeeds(context: Context) {
            val feeds = DBReader.getFeedList()
            for (feed in feeds) {
                if (feed.getState() != Feed.STATE_NOT_SUBSCRIBED) {
                    continue
                }
                DBReader.getFeedItemList(feed, FeedItemFilter(FeedItemFilter.INCLUDE_NOT_SUBSCRIBED),
                        SortOrder.DATE_NEW_OLD, 0, Int.MAX_VALUE)
                DBReader.loadFeedDataOfFeedItemList(feed.getItems()!!)
                if (shouldDelete(feed)) {
                    Log.d(TAG, "Deleting unsubscribed feed " + feed.getTitle())
                    try {
                        DBWriter.deleteFeed(context, feed.getId())!!.get()
                    } catch (e: ExecutionException) {
                        e.printStackTrace()
                        return
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                        return
                    }
                }
                feed.setItems(null) // Let it be garbage collected
            }
        }

        @JvmStatic
        fun shouldDelete(feed: Feed): Boolean {
            if (feed.getState() != Feed.STATE_NOT_SUBSCRIBED) {
                return false
            } else if (feed.getItems() == null) {
                return false
            } else if (feed.hasEpisodeInApp()) {
                return false
            }
            val timeSinceLastRefresh = System.currentTimeMillis() - feed.getLastRefreshAttempt()
            if (!feed.hasInteractedWithEpisode()) {
                return timeSinceLastRefresh > TIME_TO_KEEP_UNTOUCHED
            }
            return timeSinceLastRefresh > TIME_TO_KEEP_PLAYED
        }
    }
}
