package de.danoeh.antennapod.storage.database

import android.database.Cursor
import android.util.Log

import androidx.collection.ArrayMap

import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.HashMap

import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedCounter
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.FeedOrder
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.model.feed.SubscriptionsFilter
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.storage.database.mapper.ChapterCursor
import de.danoeh.antennapod.storage.database.mapper.DownloadResultCursor
import de.danoeh.antennapod.storage.database.mapper.FeedCursor
import de.danoeh.antennapod.storage.database.mapper.FeedItemCursor

/**
 * Provides methods for reading data from the AntennaPod database.
 * In general, all database calls in DBReader-methods are executed on the caller's thread.
 * This means that the caller should make sure that DBReader-methods are not executed on the GUI-thread.
 */
class DBReader private constructor() {

    companion object {
        private const val TAG = "DBReader"

        /**
         * Maximum size of the list returned by [.getDownloadLog].
         */
        private const val DOWNLOAD_LOG_SIZE = 200

        /**
         * Returns a list of Feeds, sorted alphabetically by their title.
         *
         * @return A list of Feeds, sorted alphabetically by their title.
         *      A Feed-object of the returned list does NOT have its list of FeedItems yet.
         *      The FeedItem-list can be loaded separately with getFeedItemList().
         */
        @JvmStatic
        @Synchronized
        fun getFeedList(): List<Feed> {
            Log.d(TAG, "Extracting Feedlist")

            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                FeedCursor(adapter.getAllFeedsCursor()).use { cursor ->
                    val feeds = ArrayList<Feed>(cursor.getCount())
                    while (cursor.moveToNext()) {
                        feeds.add(cursor.getFeed())
                    }
                    return feeds
                }
            } finally {
                adapter.close()
            }
        }

        /**
         * Returns a list with the download URLs of feeds.
         *
         * @param subscribedOnly If true, only return feeds with [Feed.STATE_SUBSCRIBED].
         * @return A list of Strings with the download URLs of matching feeds.
         */
        @JvmStatic
        @Synchronized
        fun getFeedListDownloadUrls(subscribedOnly: Boolean): List<String> {
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                adapter.getFeedCursorDownloadUrls(subscribedOnly).use { cursor ->
                    val result = ArrayList<String>(cursor.getCount())
                    while (cursor.moveToNext()) {
                        val url = cursor.getString(1)
                        if (url != null && !url.startsWith(Feed.PREFIX_LOCAL_FOLDER)) {
                            result.add(url)
                        }
                    }
                    return result
                }
            } finally {
                adapter.close()
            }
        }

        /**
         * Takes a list of FeedItems and loads their corresponding Feed-objects from the database.
         * The feedID-attribute of a FeedItem must be set to the ID of its feed or the method will
         * not find the correct feed of an item.
         *
         * @param items The FeedItems whose Feed-objects should be loaded.
         */
        @JvmStatic
        @Synchronized
        fun loadFeedDataOfFeedItemList(items: List<FeedItem>) {
            val feeds = getFeedList()

            val feedIndex = ArrayMap<Long, Feed>(feeds.size)
            for (feed in feeds) {
                feedIndex.put(feed.getId(), feed)
            }
            for (item in items) {
                var feed = feedIndex[item.getFeedId()]
                if (feed == null) {
                    Log.w(TAG, "No match found for item with ID " + item.getId() + ". Feed ID was " + item.getFeedId())
                    feed = Feed("", "", "Error: Item without feed")
                }
                item.setFeed(feed)
            }
        }

        /**
         * Loads the list of FeedItems for a certain Feed-object.
         * This method should NOT be used if the FeedItems are not used.
         *
         * @param feed The Feed whose items should be loaded
         * @return A list with the FeedItems of the Feed. The Feed-attribute of the FeedItems will already be set correctly.
         */
        @JvmStatic
        @Synchronized
        fun getFeedItemList(feed: Feed,
                            filter: FeedItemFilter, sortOrder: SortOrder, offset: Int, limit: Int): List<FeedItem> {
            Log.d(TAG, "getFeedItemList() called with: " + "feed = [" + feed + "]")

            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                FeedItemCursor(adapter.getItemsOfFeedCursor(
                        feed, filter, sortOrder, offset, limit)).use { cursor ->
                    val items = extractItemlistFromCursor(cursor)
                    feed.setItems(items)
                    for (item in items) {
                        item.setFeed(feed)
                    }
                    return items
                }
            } finally {
                adapter.close()
            }
        }

        private fun extractItemlistFromCursor(cursor: FeedItemCursor): ArrayList<FeedItem> {
            val result = ArrayList<FeedItem>(cursor.getCount())
            while (cursor.moveToNext()) {
                result.add(cursor.getFeedItem())
            }
            return result
        }

        /**
         * Loads the IDs of the FeedItems in the queue. This method should be preferred over
         * [.getQueue] if the FeedItems of the queue are not needed.
         *
         * @return A list of IDs sorted by the same order as the queue.
         */
        @JvmStatic
        @Synchronized
        fun getQueueIDList(): LongList {
            Log.d(TAG, "getQueueIDList() called")
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                adapter.getQueueIDCursor().use { cursor ->
                    val queueIds = LongList(cursor.getCount())
                    while (cursor.moveToNext()) {
                        queueIds.add(cursor.getLong(0))
                    }
                    return queueIds
                }
            } finally {
                adapter.close()
            }
        }

        /**
         * Gets the remaining queue size, given a current item, including the current item.
         * If the current item is not found it will return 0.
         */
        @JvmStatic
        @Synchronized
        fun getRemainingQueueSize(existingId: Long): Int {
            val wholeQueue = getQueueIDList()

            // now try to find the id
            for (i in 0 until wholeQueue.size()) {
                if (wholeQueue.get(i) == existingId) {
                    return wholeQueue.size() - i // return however many are left, including us
                }
            }

            return 0
        }

        /**
         * Loads a list of the FeedItems in the queue. If the FeedItems of the queue are not used directly, consider using
         * [.getQueueIDList] instead.
         *
         * @return A list of FeedItems sorted by the same order as the queue.
         */
        @JvmStatic
        @Synchronized
        fun getQueue(): ArrayList<FeedItem> {
            Log.d(TAG, "getQueue() called")

            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                FeedItemCursor(adapter.getQueueCursor()).use { cursor ->
                    val items = extractItemlistFromCursor(cursor)
                    loadFeedDataOfFeedItemList(items)
                    return items
                }
            } finally {
                adapter.close()
            }
        }

        /**
         *
         * @param offset The first episode that should be loaded.
         * @param limit The maximum number of episodes that should be loaded.
         * @param filter The filter describing which episodes to filter out.
         */
        @JvmStatic
        @Synchronized
        fun getEpisodes(offset: Int, limit: Int,
                        filter: FeedItemFilter, sortOrder: SortOrder): List<FeedItem> {
            Log.d(TAG, "getRecentlyPublishedEpisodes() called with: offset=" + offset + ", limit=" + limit)
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                FeedItemCursor(adapter.getEpisodesCursor(offset, limit, filter, sortOrder)).use { cursor ->
                    val items = extractItemlistFromCursor(cursor)
                    loadFeedDataOfFeedItemList(items)
                    return items
                }
            } finally {
                adapter.close()
            }
        }

        @JvmStatic
        @Synchronized
        fun getTotalEpisodeCount(filter: FeedItemFilter): Int {
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                adapter.getEpisodeCountCursor(filter).use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getInt(0)
                    }
                    return -1
                }
            } finally {
                adapter.close()
            }
        }

        @JvmStatic
        @Synchronized
        fun getFeedEpisodeCount(feedId: Long, filter: FeedItemFilter): Int {
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                adapter.getFeedEpisodeCountCursor(feedId, filter).use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getInt(0)
                    }
                    return -1
                }
            } finally {
                adapter.close()
            }
        }

        @JvmStatic
        @Synchronized
        fun getRandomEpisodes(limit: Int, seed: Int): List<FeedItem> {
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                FeedItemCursor(adapter.getRandomEpisodesCursor(limit, seed)).use { cursor ->
                    val items = extractItemlistFromCursor(cursor)
                    loadFeedDataOfFeedItemList(items)
                    return items
                }
            } finally {
                adapter.close()
            }
        }

        /**
         * Loads the download log from the database.
         *
         * @return A list with DownloadStatus objects that represent the download log.
         * The size of the returned list is limited by [.DOWNLOAD_LOG_SIZE].
         */
        @JvmStatic
        @Synchronized
        fun getDownloadLog(): List<DownloadResult> {
            Log.d(TAG, "getDownloadLog() called")

            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                DownloadResultCursor(adapter.getDownloadLogCursor(DOWNLOAD_LOG_SIZE)).use { cursor ->
                    val downloadLog = ArrayList<DownloadResult>(cursor.getCount())
                    while (cursor.moveToNext()) {
                        downloadLog.add(cursor.getDownloadResult())
                    }
                    return downloadLog
                }
            } finally {
                adapter.close()
            }
        }

        /**
         * Loads the download log for a particular feed from the database.
         *
         * @param feedId Feed id for which the download log is loaded
         * @return A list with DownloadStatus objects that represent the feed's download log,
         * newest events first.
         */
        @JvmStatic
        @Synchronized
        fun getFeedDownloadLog(feedId: Long, limit: Long): List<DownloadResult> {
            Log.d(TAG, "getFeedDownloadLog() called with: " + "feed = [" + feedId + "]")

            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                DownloadResultCursor(
                        adapter.getDownloadLog(Feed.FEEDFILETYPE_FEED, feedId, limit)).use { cursor ->
                    val downloadLog = ArrayList<DownloadResult>(cursor.getCount())
                    while (cursor.moveToNext()) {
                        downloadLog.add(cursor.getDownloadResult())
                    }
                    return downloadLog
                }
            } finally {
                adapter.close()
            }
        }

        /**
         * Loads a specific Feed from the database.
         *
         * @param feedId The ID of the Feed
         * @param filtered `true` if only the visible items should be loaded according to the feed filter.
         * @return The Feed or null if the Feed could not be found. The Feeds FeedItems will also be loaded from the
         *         database and the items-attribute will be set correctly.
         */
        @JvmStatic
        @Synchronized
        fun getFeed(feedId: Long, filtered: Boolean, offset: Int, limit: Int): Feed? {
            Log.d(TAG, "getFeed() called with: " + "feedId = [" + feedId + "]")
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            var feed: Feed? = null
            try {
                FeedCursor(adapter.getFeedCursor(feedId)).use { cursor ->
                    if (cursor.moveToNext()) {
                        feed = cursor.getFeed()
                        var filter = if (filtered && feed!!.getItemFilter() != null)
                            feed!!.getItemFilter()!! else FeedItemFilter.unfiltered()
                        filter = FeedItemFilter(filter, FeedItemFilter.INCLUDE_ALL_FEED_STATES)
                        val items = getFeedItemList(feed!!, filter, feed!!.getSortOrder()!!, offset, limit)
                        for (item in items) {
                            item.setFeed(feed)
                        }
                        feed!!.setItems(items)
                    } else {
                        Log.e(TAG, "getFeed could not find feed with id " + feedId)
                    }
                    return feed
                }
            } finally {
                adapter.close()
            }
        }

        /**
         * Loads a specific FeedItem from the database. This method should not be used for loading more
         * than one FeedItem because this method might query the database several times for each item.
         *
         * @param itemId The ID of the FeedItem
         * @return The FeedItem or null if the FeedItem could not be found.
         */
        @JvmStatic
        @Synchronized
        fun getFeedItem(itemId: Long): FeedItem? {
            Log.d(TAG, "getFeedItem() called with: " + "itemId = [" + itemId + "]")

            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                FeedItemCursor(adapter.getFeedItemCursor(itemId.toString())).use { cursor ->
                    val list = extractItemlistFromCursor(cursor)
                    if (!list.isEmpty()) {
                        val item = list[0]
                        loadFeedDataOfFeedItemList(list)
                        return item
                    }
                }
            } finally {
                adapter.close()
            }
            return null
        }

        /**
         * Get next feed item in queue following a particular feeditem
         *
         * @param item The FeedItem
         * @return The FeedItem next in queue or null if the FeedItem could not be found.
         */
        @JvmStatic
        @Synchronized
        fun getNextInQueue(item: FeedItem): FeedItem? {
            Log.d(TAG, "getNextInQueue() called with: " + "itemId = [" + item.getId() + "]")
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                FeedItemCursor(adapter.getNextInQueue(item)).use { cursor ->
                    val list = extractItemlistFromCursor(cursor)
                    if (!list.isEmpty()) {
                        val nextItem = list[0]
                        loadFeedDataOfFeedItemList(list)
                        return nextItem
                    }
                    return null
                }
            } catch (e: Exception) {
                return null
            } finally {
                adapter.close()
            }
        }

        @JvmStatic
        @Synchronized
        fun getPausedQueue(limit: Int): List<FeedItem> {
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                FeedItemCursor(adapter.getPausedQueueCursor(limit)).use { cursor ->
                    val items = extractItemlistFromCursor(cursor)
                    loadFeedDataOfFeedItemList(items)
                    return items
                }
            } finally {
                adapter.close()
            }
        }

        /**
         * Loads a specific FeedItem from the database.
         *
         * @param guid feed item guid
         * @param episodeUrl the feed item's url
         * @return The FeedItem or null if the FeedItem could not be found.
         *          Does NOT load additional attributes like feed.
         */
        @JvmStatic
        @Synchronized
        fun getFeedItemByGuidOrEpisodeUrl(guid: String, episodeUrl: String): FeedItem? {
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                FeedItemCursor(adapter.getFeedItemCursor(guid, episodeUrl)).use { cursor ->
                    val list = extractItemlistFromCursor(cursor)
                    if (!list.isEmpty()) {
                        return list[0]
                    }
                    return null
                }
            } finally {
                adapter.close()
            }
        }

        /**
         * Loads shownotes information about a FeedItem.
         *
         * @param item The FeedItem
         */
        @JvmStatic
        @Synchronized
        fun loadDescriptionOfFeedItem(item: FeedItem) {
            Log.d(TAG, "loadDescriptionOfFeedItem() called with: " + "item = [" + item + "]")
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                adapter.getDescriptionOfItem(item).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val indexDescription = cursor.getColumnIndex(PodDBAdapter.KEY_DESCRIPTION)
                        val description = cursor.getString(indexDescription)
                        item.setDescriptionIfLonger(description)
                    }
                }
            } finally {
                adapter.close()
            }
        }

        /**
         * Loads the list of chapters that belongs to this FeedItem if available. This method overwrites
         * any chapters that this FeedItem has. If no chapters were found in the database, the chapters
         * reference of the FeedItem will be set to null.
         *
         * @param item The FeedItem
         */
        @JvmStatic
        @Synchronized
        fun loadChaptersOfFeedItem(item: FeedItem): List<Chapter>? {
            Log.d(TAG, "loadChaptersOfFeedItem() called with: " + "item = [" + item + "]")

            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                ChapterCursor(adapter.getSimpleChaptersOfFeedItemCursor(item)).use { cursor ->
                    val chaptersCount = cursor.getCount()
                    if (chaptersCount == 0) {
                        item.setChapters(null)
                        return null
                    }
                    val chapters = ArrayList<Chapter>()
                    while (cursor.moveToNext()) {
                        chapters.add(cursor.getChapter())
                    }
                    return chapters
                }
            } finally {
                adapter.close()
            }
        }

        /**
         * Searches the DB for a FeedMedia of the given id.
         *
         * @param mediaId The id of the object
         * @return The found object, or null if it does not exist
         */
        @JvmStatic
        @Synchronized
        fun getFeedMedia(mediaId: Long): FeedMedia? {
            val adapter = PodDBAdapter.getInstance()
            adapter.open()

            try {
                FeedItemCursor(adapter.getFeedItemFromMediaIdCursor(mediaId)).use { itemCursor ->
                    if (!itemCursor.moveToFirst()) {
                        return null
                    }
                    val item = itemCursor.getFeedItem()
                    loadFeedDataOfFeedItemList(Collections.singletonList(item))
                    return item.getMedia()
                }
            } finally {
                adapter.close()
            }
        }

        @JvmStatic
        @Synchronized
        fun getFeedItemsWithUrl(urls: List<String>): List<FeedItem> {
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                FeedItemCursor(adapter.getFeedItemCursorByUrl(urls)).use { itemCursor ->
                    val items = extractItemlistFromCursor(itemCursor)
                    loadFeedDataOfFeedItemList(items)
                    return items
                }
            } finally {
                adapter.close()
            }
        }

        class MonthlyStatisticsItem {
            private var year = 0
            private var month = 0
            private var timePlayed = 0L

            fun getYear(): Int {
                return year
            }

            fun setYear(year: Int) {
                this.year = year
            }

            fun getMonth(): Int {
                return month
            }

            fun setMonth(month: Int) {
                this.month = month
            }

            fun getTimePlayed(): Long {
                return timePlayed
            }

            fun setTimePlayed(timePlayed: Long) {
                this.timePlayed = timePlayed
            }
        }

        @JvmStatic
        @Synchronized
        fun getMonthlyTimeStatistics(): List<MonthlyStatisticsItem> {
            val months = ArrayList<MonthlyStatisticsItem>()
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                adapter.getMonthlyStatisticsCursor().use { cursor ->
                    val indexMonth = cursor.getColumnIndexOrThrow("month")
                    val indexYear = cursor.getColumnIndexOrThrow("year")
                    val indexTotalDuration = cursor.getColumnIndexOrThrow("total_duration")
                    while (cursor.moveToNext()) {
                        val item = MonthlyStatisticsItem()
                        item.setMonth(Integer.parseInt(cursor.getString(indexMonth)))
                        item.setYear(Integer.parseInt(cursor.getString(indexYear)))
                        item.setTimePlayed(cursor.getLong(indexTotalDuration))
                        months.add(item)
                    }
                }
            } finally {
                adapter.close()
            }
            return months
        }

        class StatisticsResult {
            @JvmField
            val feedTime: MutableList<StatisticsItem> = ArrayList()
            @JvmField
            var oldestDate: Long = System.currentTimeMillis()
        }

        /**
         * Searches the DB for statistics.
         *
         * @return The list of statistics objects
         */
        @JvmStatic
        @Synchronized
        fun getStatistics(includeMarkedAsPlayed: Boolean,
                          timeFilterFrom: Long, timeFilterTo: Long): StatisticsResult {
            val adapter = PodDBAdapter.getInstance()
            adapter.open()

            val result = StatisticsResult()
            val sixMonthsAgo = System.currentTimeMillis() - (1000L * 3600 * 24 * 30.44 * 6).toLong()
            try {
                FeedCursor(adapter.getFeedStatisticsCursor(
                        includeMarkedAsPlayed, timeFilterFrom, timeFilterTo, sixMonthsAgo)).use { cursor ->
                    val indexOldestDate = cursor.getColumnIndexOrThrow("oldest_date")
                    val indexNumEpisodes = cursor.getColumnIndexOrThrow("num_episodes")
                    val indexEpisodesStarted = cursor.getColumnIndexOrThrow("episodes_started")
                    val indexTotalTime = cursor.getColumnIndexOrThrow("total_time")
                    val indexPlayedTime = cursor.getColumnIndexOrThrow("played_time")
                    val indexNumDownloaded = cursor.getColumnIndexOrThrow("num_downloaded")
                    val indexDownloadSize = cursor.getColumnIndexOrThrow("download_size")
                    val indexNumRecentUnplayed = cursor.getColumnIndexOrThrow("num_recent_unplayed")

                    while (cursor.moveToNext()) {
                        val feed = cursor.getFeed()

                        val feedPlayedTime = cursor.getLong(indexPlayedTime) / 1000
                        val feedTotalTime = cursor.getLong(indexTotalTime) / 1000
                        val episodes = cursor.getLong(indexNumEpisodes)
                        val episodesStarted = cursor.getLong(indexEpisodesStarted)
                        val totalDownloadSize = cursor.getLong(indexDownloadSize)
                        val episodesDownloadCount = cursor.getLong(indexNumDownloaded)
                        val oldestDate = cursor.getLong(indexOldestDate)
                        val hasRecentUnplayed = cursor.getLong(indexNumRecentUnplayed) > 0

                        if (episodes > 0 && oldestDate < Long.MAX_VALUE) {
                            result.oldestDate = Math.min(result.oldestDate, oldestDate)
                        }

                        result.feedTime.add(StatisticsItem(feed, feedTotalTime, feedPlayedTime, episodes,
                                episodesStarted, totalDownloadSize, episodesDownloadCount, hasRecentUnplayed))
                    }
                }
            } finally {
                adapter.close()
            }
            return result
        }

        @JvmStatic
        @Synchronized
        fun getTimeBetweenReleaseAndPlayback(timeFilterFrom: Long, timeFilterTo: Long): Long {
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                adapter.getTimeBetweenReleaseAndPlayback(timeFilterFrom, timeFilterTo).use { cursor ->
                    cursor.moveToFirst()
                    val result = cursor.getLong(0)
                    adapter.close()
                    return result
                }
            } finally {
                adapter.close()
            }
        }

        /**
         * Returns data necessary for displaying the navigation drawer. This includes
         * the list of subscriptions, the number of items in the queue and the number of unread
         * items.
         */
        @JvmStatic
        @Synchronized
        fun getNavDrawerData(subscriptionsFilter: SubscriptionsFilter?,
                             feedOrder: FeedOrder, feedCounter: FeedCounter, feedState: Int): NavDrawerData {
            val adapter = PodDBAdapter.getInstance()
            adapter.open()

            val feedCounters = adapter.getFeedCounters(feedCounter)
            val allFeeds = getFeedList()
            val typeFilteredFeeds = ArrayList<Feed>()
            for (feed in allFeeds) {
                if (feed.getState() == feedState) {
                    typeFilteredFeeds.add(feed)
                }
            }
            var subscriptionsFilter = subscriptionsFilter
            if (subscriptionsFilter == null) {
                subscriptionsFilter = SubscriptionsFilter("")
            }
            val feeds = SubscriptionsFilterExecutor.filter(typeFilteredFeeds, feedCounters, subscriptionsFilter)

            val comparator: Comparator<Feed>
            when (feedOrder) {
                FeedOrder.COUNTER -> comparator = Comparator { lhs, rhs ->
                    val counterLhs = if (feedCounters.containsKey(lhs.getId())) feedCounters[lhs.getId()]!! else 0
                    val counterRhs = if (feedCounters.containsKey(rhs.getId())) feedCounters[rhs.getId()]!! else 0
                    if (counterLhs > counterRhs) {
                        // reverse natural order: podcast with most unplayed episodes first
                        -1
                    } else if (counterLhs == counterRhs) {
                        lhs.getTitle()!!.compareTo(rhs.getTitle()!!, true)
                    } else {
                        1
                    }
                }
                FeedOrder.ALPHABETICAL -> comparator = Comparator { lhs, rhs ->
                    val t1 = lhs.getTitle()
                    val t2 = rhs.getTitle()
                    if (t1 == null) {
                        1
                    } else if (t2 == null) {
                        -1
                    } else {
                        t1.compareTo(t2, true)
                    }
                }
                FeedOrder.MOST_PLAYED -> {
                    val playedCounters = adapter.getPlayedEpisodesCounters()
                    comparator = Comparator { lhs, rhs ->
                        val counterLhs = if (playedCounters.containsKey(lhs.getId())) playedCounters[lhs.getId()]!! else 0
                        val counterRhs = if (playedCounters.containsKey(rhs.getId())) playedCounters[rhs.getId()]!! else 0
                        if (counterLhs > counterRhs) {
                            // podcast with most played episodes first
                            -1
                        } else if (counterLhs == counterRhs) {
                            lhs.getTitle()!!.compareTo(rhs.getTitle()!!, true)
                        } else {
                            1
                        }
                    }
                }
                else -> {
                    val recentPubDates = adapter.getMostRecentItemDates()
                    comparator = Comparator { lhs, rhs ->
                        val dateLhs = if (recentPubDates.containsKey(lhs.getId())) recentPubDates[lhs.getId()]!! else 0
                        val dateRhs = if (recentPubDates.containsKey(rhs.getId())) recentPubDates[rhs.getId()]!! else 0
                        java.lang.Long.compare(dateRhs, dateLhs)
                    }
                }
            }

            Collections.sort(feeds, comparator)
            val queueSize = adapter.getQueueSize()
            val numNewItems = getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.NEW))
            val numDownloadedItems = getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.DOWNLOADED))

            val untaggedTag = NavDrawerData.TagItem(FeedPreferences.TAG_UNTAGGED)
            val tags = HashMap<String, NavDrawerData.TagItem>()
            for (feed in feeds) {
                if (feed.getPreferences()!!.getTags().isEmpty() || (feed.getPreferences()!!.getTags().size) == 1
                        && feed.getPreferences()!!.getTags().contains(FeedPreferences.TAG_ROOT)) {
                    untaggedTag.addFeed(feed, 0)
                }
                for (tag in feed.getPreferences()!!.getTags()) {
                    if (!tags.containsKey(tag)) {
                        tags.put(tag, NavDrawerData.TagItem(tag))
                    }
                    val counter = if (feedCounters.containsKey(feed.getId())) feedCounters[feed.getId()]!! else 0
                    tags[tag]!!.addFeed(feed, counter)
                }
            }
            val tagsSorted = ArrayList(tags.values)
            Collections.sort(tagsSorted) { o1, o2 -> o1.getTitle().compareTo(o2.getTitle(), true) }

            if (!untaggedTag.getFeeds().isEmpty()) {
                tagsSorted.add(0, untaggedTag)
            }

            val result = NavDrawerData(feeds, tagsSorted,
                    queueSize, numNewItems, numDownloadedItems, feedCounters)
            adapter.close()
            return result
        }

        @JvmStatic
        @Synchronized
        fun getAllTags(feedState: Int): List<NavDrawerData.TagItem> {
            val tags = HashMap<String, NavDrawerData.TagItem>()
            val allFeeds = getFeedList()
            val feeds = ArrayList<Feed>()
            for (feed in allFeeds) {
                if (feed.getState() == feedState) {
                    feeds.add(feed)
                }
            }
            val untaggedTag = NavDrawerData.TagItem(FeedPreferences.TAG_UNTAGGED)
            for (feed in feeds) {
                if (feed.getPreferences()!!.getTags().isEmpty() || (feed.getPreferences()!!.getTags().size) == 1
                        && feed.getPreferences()!!.getTags().contains(FeedPreferences.TAG_ROOT)) {
                    untaggedTag.addFeed(feed, 0)
                }
                for (tag in feed.getPreferences()!!.getTags()) {
                    if (FeedPreferences.TAG_ROOT == tag) {
                        continue
                    }
                    if (!tags.containsKey(tag)) {
                        tags.put(tag, NavDrawerData.TagItem(tag))
                    }
                    tags[tag]!!.addFeed(feed, 0)
                }
            }
            val tagsSorted = ArrayList(tags.values)
            Collections.sort(tagsSorted) { o1, o2 -> o1.getTitle().compareTo(o2.getTitle(), true) }
            // Root tag here means "all feeds", this is different from the nav drawer.
            val rootTag = NavDrawerData.TagItem(FeedPreferences.TAG_ROOT)
            for (feed in feeds) {
                rootTag.addFeed(feed, 0)
            }
            tagsSorted.add(0, rootTag)
            if (!untaggedTag.getFeeds().isEmpty()) {
                tagsSorted.add(untaggedTag)
            }
            return tagsSorted
        }

        @JvmStatic
        @Synchronized
        fun searchFeedItems(feedId: Long, query: String,
                            filter: FeedItemFilter): List<FeedItem> {
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                FeedItemCursor(adapter.searchItems(feedId, query, filter)).use { searchResult ->
                    val items = extractItemlistFromCursor(searchResult)
                    loadFeedDataOfFeedItemList(items)
                    return items
                }
            } finally {
                adapter.close()
            }
        }

        @JvmStatic
        @Synchronized
        fun searchFeeds(query: String, filter: FeedItemFilter): List<Feed> {
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            try {
                FeedCursor(adapter.searchFeeds(query, filter)).use { cursor ->
                    val items = ArrayList<Feed>()
                    while (cursor.moveToNext()) {
                        items.add(cursor.getFeed())
                    }
                    return items
                }
            } finally {
                adapter.close()
            }
        }
    }
}
