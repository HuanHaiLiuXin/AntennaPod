package de.danoeh.antennapod.storage.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.DatabaseUtils
import android.database.DefaultDatabaseErrorHandler
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CursorFactory
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.text.TextUtils
import android.util.Log

import de.danoeh.antennapod.model.feed.FeedCounter
import de.danoeh.antennapod.model.feed.FeedFunding

import java.io.File
import java.io.IOException
import java.util.ArrayList
import java.util.Date
import java.util.HashMap
import java.util.Locale

import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.storage.database.mapper.FeedItemFilterQuery
import de.danoeh.antennapod.storage.database.mapper.FeedItemSortQuery

import de.danoeh.antennapod.system.utils.ThreadUtils
import org.apache.commons.io.FileUtils

/**
 * Implements methods for accessing the database
 */
class PodDBAdapter {

    companion object {
        private const val TAG = "PodDBAdapter"
        const val DATABASE_NAME = "Antennapod.db"
        const val VERSION = 3110000

        /**
         * Maximum number of arguments for IN-operator.
         */
        private const val IN_OPERATOR_MAXIMUM = 800

        // Key-constants
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_CUSTOM_TITLE = "custom_title"
        const val KEY_LINK = "link"
        const val KEY_DESCRIPTION = "description"
        const val KEY_FILE_URL = "file_url"
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_PUBDATE = "pubDate"
        const val KEY_READ = "read"
        const val KEY_DURATION = "duration"
        const val KEY_POSITION = "position"
        const val KEY_SIZE = "filesize"
        const val KEY_MIME_TYPE = "mime_type"
        const val KEY_IMAGE_URL = "image_url"
        const val KEY_FEED = "feed"
        const val KEY_MEDIA = "media"
        const val KEY_DOWNLOAD_DATE = "downloaded"
        const val KEY_LAST_REFRESH_ATTEMPT = "downloaded"
        const val KEY_LASTUPDATE = "last_update"
        const val KEY_FEEDFILE = "feedfile"
        const val KEY_REASON = "reason"
        const val KEY_SUCCESSFUL = "successful"
        const val KEY_FEEDFILETYPE = "feedfile_type"
        const val KEY_COMPLETION_DATE = "completion_date"
        const val KEY_FEEDITEM = "feeditem"
        const val KEY_PAYMENT_LINK = "payment_link"
        const val KEY_START = "start"
        const val KEY_LANGUAGE = "language"
        const val KEY_AUTHOR = "author"
        const val KEY_HAS_CHAPTERS = "has_simple_chapters"
        const val KEY_TYPE = "type"
        const val KEY_ITEM_IDENTIFIER = "item_identifier"
        const val KEY_FEED_IDENTIFIER = "feed_identifier"
        const val KEY_REASON_DETAILED = "reason_detailed"
        const val KEY_DOWNLOADSTATUS_TITLE = "title"
        const val KEY_AUTO_DOWNLOAD_ENABLED = "auto_download" // Both tables use the same key
        const val KEY_KEEP_UPDATED = "keep_updated"
        const val KEY_AUTO_DELETE_ACTION = "auto_delete_action"
        const val KEY_FEED_VOLUME_ADAPTION = "feed_volume_adaption"
        const val KEY_PLAYED_DURATION = "played_duration"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_IS_PAGED = "is_paged"
        const val KEY_NEXT_PAGE_LINK = "next_page_link"
        const val KEY_HIDE = "hide"
        const val KEY_SORT_ORDER = "sort_order"
        const val KEY_LAST_UPDATE_FAILED = "last_update_failed"
        const val KEY_HAS_EMBEDDED_PICTURE = "has_embedded_picture"
        const val KEY_LAST_PLAYED_TIME_HISTORY = "playback_completion_date"
        const val KEY_LAST_PLAYED_TIME_STATISTICS = "last_played_time"
        const val KEY_INCLUDE_FILTER = "include_filter"
        const val KEY_EXCLUDE_FILTER = "exclude_filter"
        const val KEY_MINIMAL_DURATION_FILTER = "minimal_duration_filter"
        const val KEY_FEED_PLAYBACK_SPEED = "feed_playback_speed"
        const val KEY_FEED_SKIP_SILENCE = "feed_skip_silence"
        const val KEY_FEED_SKIP_INTRO = "feed_skip_intro"
        const val KEY_FEED_SKIP_ENDING = "feed_skip_ending"
        const val KEY_FEED_TAGS = "tags"
        const val KEY_EPISODE_NOTIFICATION = "episode_notification"
        const val KEY_NEW_EPISODES_ACTION = "new_episodes_action"
        const val KEY_PODCASTINDEX_CHAPTER_URL = "podcastindex_chapter_url"
        const val KEY_SOCIAL_INTERACT_URL = "social_interact_url"
        const val KEY_STATE = "state"
        const val KEY_PODCASTINDEX_TRANSCRIPT_URL = "podcastindex_transcript_url"
        const val KEY_PODCASTINDEX_TRANSCRIPT_TYPE = "podcastindex_transcript_type"

        // Table names
        const val TABLE_NAME_FEEDS = "Feeds"
        const val TABLE_NAME_FEED_ITEMS = "FeedItems"
        const val TABLE_NAME_FEED_IMAGES = "FeedImages"
        const val TABLE_NAME_FEED_MEDIA = "FeedMedia"
        const val TABLE_NAME_DOWNLOAD_LOG = "DownloadLog"
        const val TABLE_NAME_QUEUE = "Queue"
        const val TABLE_NAME_SIMPLECHAPTERS = "SimpleChapters"
        const val TABLE_NAME_FAVORITES = "Favorites"

        // SQL Statements for creating new tables
        private const val TABLE_PRIMARY_KEY = KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT ,"

        private val CREATE_TABLE_FEEDS = "CREATE TABLE " + TABLE_NAME_FEEDS + " (" + TABLE_PRIMARY_KEY + KEY_TITLE + " TEXT," + KEY_CUSTOM_TITLE + " TEXT," + KEY_FILE_URL + " TEXT," + KEY_DOWNLOAD_URL + " TEXT," + KEY_LAST_REFRESH_ATTEMPT + " INTEGER," + KEY_LINK + " TEXT," + KEY_DESCRIPTION + " TEXT," + KEY_PAYMENT_LINK + " TEXT," + KEY_LASTUPDATE + " TEXT," + KEY_LANGUAGE + " TEXT," + KEY_AUTHOR + " TEXT," + KEY_IMAGE_URL + " TEXT," + KEY_TYPE + " TEXT," + KEY_FEED_IDENTIFIER + " TEXT," + KEY_AUTO_DOWNLOAD_ENABLED + " INTEGER DEFAULT 1," + KEY_USERNAME + " TEXT," + KEY_PASSWORD + " TEXT," + KEY_INCLUDE_FILTER + " TEXT DEFAULT ''," + KEY_EXCLUDE_FILTER + " TEXT DEFAULT ''," + KEY_MINIMAL_DURATION_FILTER + " INTEGER DEFAULT -1," + KEY_KEEP_UPDATED + " INTEGER DEFAULT 1," + KEY_IS_PAGED + " INTEGER DEFAULT 0," + KEY_NEXT_PAGE_LINK + " TEXT," + KEY_HIDE + " TEXT," + KEY_SORT_ORDER + " TEXT," + KEY_LAST_UPDATE_FAILED + " INTEGER DEFAULT 0," + KEY_AUTO_DELETE_ACTION + " INTEGER DEFAULT 0," + KEY_FEED_PLAYBACK_SPEED + " REAL DEFAULT " + FeedPreferences.SPEED_USE_GLOBAL + "," + KEY_FEED_SKIP_SILENCE + " INTEGER DEFAULT " + FeedPreferences.SkipSilence.GLOBAL.code + "," + KEY_FEED_VOLUME_ADAPTION + " INTEGER DEFAULT 0," + KEY_FEED_TAGS + " TEXT," + KEY_FEED_SKIP_INTRO + " INTEGER DEFAULT 0," + KEY_FEED_SKIP_ENDING + " INTEGER DEFAULT 0," + KEY_EPISODE_NOTIFICATION + " INTEGER DEFAULT 0," + KEY_STATE + " INTEGER DEFAULT " + Feed.STATE_SUBSCRIBED + "," + KEY_NEW_EPISODES_ACTION + " INTEGER DEFAULT 0)"

        private const val CREATE_TABLE_FEED_ITEMS = "CREATE TABLE " + TABLE_NAME_FEED_ITEMS + " (" + TABLE_PRIMARY_KEY + KEY_TITLE + " TEXT," + KEY_PUBDATE + " INTEGER," + KEY_READ + " INTEGER," + KEY_LINK + " TEXT," + KEY_DESCRIPTION + " TEXT," + KEY_PAYMENT_LINK + " TEXT," + KEY_MEDIA + " INTEGER," + KEY_FEED + " INTEGER," + KEY_HAS_CHAPTERS + " INTEGER," + KEY_ITEM_IDENTIFIER + " TEXT," + KEY_IMAGE_URL + " TEXT," + KEY_AUTO_DOWNLOAD_ENABLED + " INTEGER," + KEY_PODCASTINDEX_CHAPTER_URL + " TEXT," + KEY_PODCASTINDEX_TRANSCRIPT_TYPE + " TEXT," + KEY_PODCASTINDEX_TRANSCRIPT_URL + " TEXT," + KEY_SOCIAL_INTERACT_URL + " TEXT)"

        private const val CREATE_TABLE_FEED_MEDIA = "CREATE TABLE " + TABLE_NAME_FEED_MEDIA + " (" + TABLE_PRIMARY_KEY + KEY_DURATION + " INTEGER," + KEY_FILE_URL + " TEXT," + KEY_DOWNLOAD_URL + " TEXT," + KEY_DOWNLOAD_DATE + " INTEGER," + KEY_POSITION + " INTEGER," + KEY_SIZE + " INTEGER," + KEY_MIME_TYPE + " TEXT," + KEY_LAST_PLAYED_TIME_HISTORY + " INTEGER," + KEY_FEEDITEM + " INTEGER," + KEY_PLAYED_DURATION + " INTEGER," + KEY_HAS_EMBEDDED_PICTURE + " INTEGER," + KEY_LAST_PLAYED_TIME_STATISTICS + " INTEGER" + ")"

        private const val CREATE_TABLE_DOWNLOAD_LOG = "CREATE TABLE " + TABLE_NAME_DOWNLOAD_LOG + " (" + TABLE_PRIMARY_KEY + KEY_FEEDFILE + " INTEGER," + KEY_FEEDFILETYPE + " INTEGER," + KEY_REASON + " INTEGER," + KEY_SUCCESSFUL + " INTEGER," + KEY_COMPLETION_DATE + " INTEGER," + KEY_REASON_DETAILED + " TEXT," + KEY_DOWNLOADSTATUS_TITLE + " TEXT)"

        private const val CREATE_TABLE_QUEUE = "CREATE TABLE " + TABLE_NAME_QUEUE + "(" + KEY_ID + " INTEGER PRIMARY KEY," + KEY_FEEDITEM + " INTEGER," + KEY_FEED + " INTEGER)"

        private const val CREATE_TABLE_SIMPLECHAPTERS = "CREATE TABLE " + TABLE_NAME_SIMPLECHAPTERS + " (" + TABLE_PRIMARY_KEY + KEY_TITLE + " TEXT," + KEY_START + " INTEGER," + KEY_FEEDITEM + " INTEGER," + KEY_LINK + " TEXT," + KEY_IMAGE_URL + " TEXT)"

        // SQL Statements for creating indexes
        internal const val CREATE_INDEX_FEEDITEMS_FEED = "CREATE INDEX " +
                TABLE_NAME_FEED_ITEMS + "_" + KEY_FEED + " ON " + TABLE_NAME_FEED_ITEMS + " (" +
                KEY_FEED + ")"

        internal const val CREATE_INDEX_FEEDITEMS_PUBDATE = "CREATE INDEX " +
                TABLE_NAME_FEED_ITEMS + "_" + KEY_PUBDATE + " ON " + TABLE_NAME_FEED_ITEMS + " (" +
                KEY_PUBDATE + ")"

        internal const val CREATE_INDEX_FEEDITEMS_READ = "CREATE INDEX " +
                TABLE_NAME_FEED_ITEMS + "_" + KEY_READ + " ON " + TABLE_NAME_FEED_ITEMS + " (" +
                KEY_READ + ")"

        internal const val CREATE_INDEX_QUEUE_FEEDITEM = "CREATE INDEX " +
                TABLE_NAME_QUEUE + "_" + KEY_FEEDITEM + " ON " + TABLE_NAME_QUEUE + " (" +
                KEY_FEEDITEM + ")"

        internal const val CREATE_INDEX_FEEDMEDIA_FEEDITEM = "CREATE INDEX " +
                TABLE_NAME_FEED_MEDIA + "_" + KEY_FEEDITEM + " ON " + TABLE_NAME_FEED_MEDIA + " (" +
                KEY_FEEDITEM + ")"

        internal const val CREATE_INDEX_SIMPLECHAPTERS_FEEDITEM = "CREATE INDEX " +
                TABLE_NAME_SIMPLECHAPTERS + "_" + KEY_FEEDITEM + " ON " + TABLE_NAME_SIMPLECHAPTERS + " (" +
                KEY_FEEDITEM + ")"

        internal const val CREATE_TABLE_FAVORITES = "CREATE TABLE " +
                TABLE_NAME_FAVORITES + "(" + KEY_ID + " INTEGER PRIMARY KEY," +
                KEY_FEEDITEM + " INTEGER," + KEY_FEED + " INTEGER)"

        /**
         * All the tables in the database
         */
        private val ALL_TABLES = arrayOf(
                TABLE_NAME_FEEDS,
                TABLE_NAME_FEED_ITEMS,
                TABLE_NAME_FEED_MEDIA,
                TABLE_NAME_DOWNLOAD_LOG,
                TABLE_NAME_QUEUE,
                TABLE_NAME_SIMPLECHAPTERS,
                TABLE_NAME_FAVORITES
        )

        const val SELECT_KEY_ITEM_ID = "item_id"
        const val SELECT_KEY_MEDIA_ID = "media_id"
        const val SELECT_KEY_FEED_ID = "feed_id"
        const val SELECT_KEY_IS_FAVORITE = "is_favorite"
        const val SELECT_KEY_IS_IN_QUEUE = "is_in_queue"

        private const val KEYS_FEED_ITEM_WITHOUT_DESCRIPTION =
                TABLE_NAME_FEED_ITEMS + "." + KEY_ID + " AS " + SELECT_KEY_ITEM_ID + ", " + TABLE_NAME_FEED_ITEMS + "." + KEY_TITLE + ", " + TABLE_NAME_FEED_ITEMS + "." + KEY_PUBDATE + ", " + TABLE_NAME_FEED_ITEMS + "." + KEY_READ + ", " + TABLE_NAME_FEED_ITEMS + "." + KEY_LINK + ", " + TABLE_NAME_FEED_ITEMS + "." + KEY_PAYMENT_LINK + ", " + TABLE_NAME_FEED_ITEMS + "." + KEY_MEDIA + ", " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + ", " + TABLE_NAME_FEED_ITEMS + "." + KEY_HAS_CHAPTERS + ", " + TABLE_NAME_FEED_ITEMS + "." + KEY_ITEM_IDENTIFIER + ", " + TABLE_NAME_FEED_ITEMS + "." + KEY_IMAGE_URL + ", " + TABLE_NAME_FEED_ITEMS + "." + KEY_AUTO_DOWNLOAD_ENABLED + ", " + TABLE_NAME_FEED_ITEMS + "." + KEY_PODCASTINDEX_CHAPTER_URL + ", " + TABLE_NAME_FEED_ITEMS + "." + KEY_SOCIAL_INTERACT_URL + ", " + TABLE_NAME_FEED_ITEMS + "." + KEY_PODCASTINDEX_TRANSCRIPT_TYPE + ", " + TABLE_NAME_FEED_ITEMS + "." + KEY_PODCASTINDEX_TRANSCRIPT_URL + ", " + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + " IN (SELECT " + TABLE_NAME_FAVORITES + "." + KEY_FEEDITEM + " FROM " + TABLE_NAME_FAVORITES + ") AS " + SELECT_KEY_IS_FAVORITE + ", " + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + " IN (SELECT " + TABLE_NAME_QUEUE + "." + KEY_FEEDITEM + " FROM " + TABLE_NAME_QUEUE + ") AS " + SELECT_KEY_IS_IN_QUEUE

        private const val KEYS_FEED_MEDIA =
                TABLE_NAME_FEED_MEDIA + "." + KEY_ID + " AS " + SELECT_KEY_MEDIA_ID + ", " + TABLE_NAME_FEED_MEDIA + "." + KEY_DURATION + ", " + TABLE_NAME_FEED_MEDIA + "." + KEY_FILE_URL + ", " + TABLE_NAME_FEED_MEDIA + "." + KEY_DOWNLOAD_URL + ", " + TABLE_NAME_FEED_MEDIA + "." + KEY_DOWNLOAD_DATE + ", " + TABLE_NAME_FEED_MEDIA + "." + KEY_POSITION + ", " + TABLE_NAME_FEED_MEDIA + "." + KEY_SIZE + ", " + TABLE_NAME_FEED_MEDIA + "." + KEY_MIME_TYPE + ", " + TABLE_NAME_FEED_MEDIA + "." + KEY_LAST_PLAYED_TIME_HISTORY + ", " + TABLE_NAME_FEED_MEDIA + "." + KEY_FEEDITEM + ", " + TABLE_NAME_FEED_MEDIA + "." + KEY_PLAYED_DURATION + ", " + TABLE_NAME_FEED_MEDIA + "." + KEY_HAS_EMBEDDED_PICTURE + ", " + TABLE_NAME_FEED_MEDIA + "." + KEY_LAST_PLAYED_TIME_STATISTICS

        private const val KEYS_FEED =
                TABLE_NAME_FEEDS + "." + KEY_ID + " AS " + SELECT_KEY_FEED_ID + ", " + TABLE_NAME_FEEDS + "." + KEY_TITLE + ", " + TABLE_NAME_FEEDS + "." + KEY_CUSTOM_TITLE + ", " + TABLE_NAME_FEEDS + "." + KEY_FILE_URL + ", " + TABLE_NAME_FEEDS + "." + KEY_DOWNLOAD_URL + ", " + TABLE_NAME_FEEDS + "." + KEY_LAST_REFRESH_ATTEMPT + ", " + TABLE_NAME_FEEDS + "." + KEY_LINK + ", " + TABLE_NAME_FEEDS + "." + KEY_DESCRIPTION + ", " + TABLE_NAME_FEEDS + "." + KEY_PAYMENT_LINK + ", " + TABLE_NAME_FEEDS + "." + KEY_LASTUPDATE + ", " + TABLE_NAME_FEEDS + "." + KEY_LANGUAGE + ", " + TABLE_NAME_FEEDS + "." + KEY_AUTHOR + ", " + TABLE_NAME_FEEDS + "." + KEY_IMAGE_URL + ", " + TABLE_NAME_FEEDS + "." + KEY_TYPE + ", " + TABLE_NAME_FEEDS + "." + KEY_FEED_IDENTIFIER + ", " + TABLE_NAME_FEEDS + "." + KEY_IS_PAGED + ", " + TABLE_NAME_FEEDS + "." + KEY_NEXT_PAGE_LINK + ", " + TABLE_NAME_FEEDS + "." + KEY_LAST_UPDATE_FAILED + ", " + TABLE_NAME_FEEDS + "." + KEY_AUTO_DOWNLOAD_ENABLED + ", " + TABLE_NAME_FEEDS + "." + KEY_KEEP_UPDATED + ", " + TABLE_NAME_FEEDS + "." + KEY_USERNAME + ", " + TABLE_NAME_FEEDS + "." + KEY_PASSWORD + ", " + TABLE_NAME_FEEDS + "." + KEY_HIDE + ", " + TABLE_NAME_FEEDS + "." + KEY_SORT_ORDER + ", " + TABLE_NAME_FEEDS + "." + KEY_AUTO_DELETE_ACTION + ", " + TABLE_NAME_FEEDS + "." + KEY_FEED_VOLUME_ADAPTION + ", " + TABLE_NAME_FEEDS + "." + KEY_INCLUDE_FILTER + ", " + TABLE_NAME_FEEDS + "." + KEY_EXCLUDE_FILTER + ", " + TABLE_NAME_FEEDS + "." + KEY_MINIMAL_DURATION_FILTER + ", " + TABLE_NAME_FEEDS + "." + KEY_FEED_PLAYBACK_SPEED + ", " + TABLE_NAME_FEEDS + "." + KEY_FEED_SKIP_SILENCE + ", " + TABLE_NAME_FEEDS + "." + KEY_FEED_TAGS + ", " + TABLE_NAME_FEEDS + "." + KEY_FEED_SKIP_INTRO + ", " + TABLE_NAME_FEEDS + "." + KEY_FEED_SKIP_ENDING + ", " + TABLE_NAME_FEEDS + "." + KEY_EPISODE_NOTIFICATION + ", " + TABLE_NAME_FEEDS + "." + KEY_STATE + ", " + TABLE_NAME_FEEDS + "." + KEY_NEW_EPISODES_ACTION

        private const val JOIN_FEED_ITEM_AND_MEDIA = " LEFT JOIN " + TABLE_NAME_FEED_MEDIA + " ON " + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + "=" + TABLE_NAME_FEED_MEDIA + "." + KEY_FEEDITEM + " "

        private const val SELECT_FEED_ITEMS_AND_MEDIA_WITH_DESCRIPTION =
                "SELECT " + KEYS_FEED_ITEM_WITHOUT_DESCRIPTION + ", " + KEYS_FEED_MEDIA + ", " + TABLE_NAME_FEED_ITEMS + "." + KEY_DESCRIPTION + " FROM " + TABLE_NAME_FEED_ITEMS + JOIN_FEED_ITEM_AND_MEDIA
        private const val SELECT_FEED_ITEMS_AND_MEDIA =
                "SELECT " + KEYS_FEED_ITEM_WITHOUT_DESCRIPTION + ", " + KEYS_FEED_MEDIA + " FROM " + TABLE_NAME_FEED_ITEMS + JOIN_FEED_ITEM_AND_MEDIA
        private const val SELECT_WHERE_FEED_IS_SUBSCRIBED = TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + " IN (SELECT " + KEY_ID + " FROM " + TABLE_NAME_FEEDS + " WHERE " + KEY_STATE + "=" + Feed.STATE_SUBSCRIBED + ")"

        private var context: Context? = null
        private var instance: PodDBAdapter? = null

        @JvmStatic
        fun init(context: Context) {
            PodDBAdapter.context = context.getApplicationContext()
        }

        @JvmStatic
        @Synchronized
        fun getInstance(): PodDBAdapter {
            if (instance == null) {
                instance = PodDBAdapter()
            }
            return instance!!
        }

        @JvmStatic
        fun tearDownTests() {
            getInstance().dbHelper.close()
            instance = null
        }

        @JvmStatic
        fun deleteDatabase(): Boolean {
            val adapter = getInstance()
            adapter.open()
            try {
                for (tableName in ALL_TABLES) {
                    adapter.db.delete(tableName, "1", null)
                }
                return true
            } finally {
                adapter.close()
            }
        }
    }

    private val db: SQLiteDatabase
    private val dbHelper: PodDBHelper

    private constructor() {
        dbHelper = PodDBHelper(PodDBAdapter.context!!, DATABASE_NAME, null)
        db = openDb()
    }

    private fun openDb(): SQLiteDatabase {
        var newDb: SQLiteDatabase
        try {
            newDb = dbHelper.getWritableDatabase()
        } catch (ex: SQLException) {
            Log.e(TAG, Log.getStackTraceString(ex))
            newDb = dbHelper.getReadableDatabase()
        }
        return newDb
    }

    @Synchronized
    fun open(): PodDBAdapter {
        ThreadUtils.assertNotMainThread()
        // do nothing
        return this
    }

    @Synchronized
    fun close() {
        // do nothing
    }

    /**
     * Resets all database connections to ensure new database connections for
     * the next test case. Call method only for unit tests.
     */
    fun walCheckpoint() {
        if (!db.isOpen() || !db.isWriteAheadLoggingEnabled()) {
            return
        }
        try {
            db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                cursor.moveToFirst()
                Log.d(TAG, "WAL checkpoint result: " + DatabaseUtils.dumpCurrentRowToString(cursor))
            }
        } catch (e: SQLiteException) {
            Log.e(TAG, "wal_checkpoint PRAGMA failed", e)
        }
    }

    /**
     * Inserts or updates a feed entry
     *
     * @return the id of the entry
     */
    private fun setFeed(feed: Feed): Long {
        val values = ContentValues()
        values.put(KEY_TITLE, feed.getFeedTitle())
        values.put(KEY_LINK, feed.getLink())
        values.put(KEY_DESCRIPTION, feed.getDescription())
        values.put(KEY_PAYMENT_LINK, FeedFunding.getPaymentLinksAsString(feed.getPaymentLinks()))
        values.put(KEY_AUTHOR, feed.getAuthor())
        values.put(KEY_LANGUAGE, feed.getLanguage())
        values.put(KEY_IMAGE_URL, feed.getImageUrl())

        values.put(KEY_FILE_URL, feed.getLocalFileUrl())
        values.put(KEY_DOWNLOAD_URL, feed.getDownloadUrl())
        values.put(KEY_LAST_REFRESH_ATTEMPT, feed.getLastRefreshAttempt())
        values.put(KEY_LASTUPDATE, feed.getLastModified())
        values.put(KEY_TYPE, feed.getType())
        values.put(KEY_FEED_IDENTIFIER, feed.getFeedIdentifier())
        values.put(KEY_STATE, feed.getState())

        values.put(KEY_IS_PAGED, feed.isPaged())
        values.put(KEY_NEXT_PAGE_LINK, feed.getNextPageLink())
        if (feed.getItemFilter() != null && feed.getItemFilter()!!.getValues().size > 0) {
            values.put(KEY_HIDE, TextUtils.join(",", feed.getItemFilter()!!.getValues()))
        } else {
            values.put(KEY_HIDE, "")
        }
        values.put(KEY_SORT_ORDER, SortOrder.toCodeString(feed.getSortOrder()))
        values.put(KEY_LAST_UPDATE_FAILED, feed.hasLastUpdateFailed())
        if (feed.getId() == 0L) {
            // Create new entry
            Log.d(this.toString(), "Inserting new Feed into db")
            feed.setId(db.insert(TABLE_NAME_FEEDS, null, values))
        } else {
            Log.d(this.toString(), "Updating existing Feed in db")
            db.update(TABLE_NAME_FEEDS, values, KEY_ID + "=?",
                    arrayOf(feed.getId().toString()))
        }
        return feed.getId()
    }

    fun setFeedPreferences(prefs: FeedPreferences) {
        if (prefs.getFeedID() == 0L) {
            throw IllegalArgumentException("Feed ID of preference must not be null")
        }
        val values = ContentValues()
        values.put(KEY_AUTO_DOWNLOAD_ENABLED, prefs.getAutoDownload().code)
        values.put(KEY_KEEP_UPDATED, prefs.getKeepUpdated())
        values.put(KEY_AUTO_DELETE_ACTION, prefs.getAutoDeleteAction().code)
        values.put(KEY_FEED_VOLUME_ADAPTION, prefs.getVolumeAdaptionSetting().toInteger())
        values.put(KEY_USERNAME, prefs.getUsername())
        values.put(KEY_PASSWORD, prefs.getPassword())
        values.put(KEY_INCLUDE_FILTER, prefs.getFilter().getIncludeFilterRaw())
        values.put(KEY_EXCLUDE_FILTER, prefs.getFilter().getExcludeFilterRaw())
        values.put(KEY_MINIMAL_DURATION_FILTER, prefs.getFilter().getMinimalDurationFilter())
        values.put(KEY_FEED_PLAYBACK_SPEED, prefs.getFeedPlaybackSpeed())
        values.put(KEY_FEED_SKIP_SILENCE, prefs.getFeedSkipSilence().code)
        values.put(KEY_FEED_TAGS, prefs.getTagsAsString())
        values.put(KEY_FEED_SKIP_INTRO, prefs.getFeedSkipIntro())
        values.put(KEY_FEED_SKIP_ENDING, prefs.getFeedSkipEnding())
        values.put(KEY_EPISODE_NOTIFICATION, prefs.getShowEpisodeNotification())
        values.put(KEY_NEW_EPISODES_ACTION, prefs.getNewEpisodesAction().code)
        db.update(TABLE_NAME_FEEDS, values, KEY_ID + "=?", arrayOf(prefs.getFeedID().toString()))
    }

    fun setFeedItemFilter(feedId: Long, filterValues: Set<String>) {
        val valuesList = TextUtils.join(",", filterValues)
        Log.d(TAG, java.lang.String.format(Locale.US,
                "setFeedItemFilter() called with: feedId = [%d], filterValues = [%s]", feedId, valuesList))
        val values = ContentValues()
        values.put(KEY_HIDE, valuesList)
        db.update(TABLE_NAME_FEEDS, values, KEY_ID + "=?", arrayOf(feedId.toString()))
    }

    fun setFeedItemSortOrder(feedId: Long, sortOrder: SortOrder?) {
        val values = ContentValues()
        values.put(KEY_SORT_ORDER, SortOrder.toCodeString(sortOrder))
        db.update(TABLE_NAME_FEEDS, values, KEY_ID + "=?", arrayOf(feedId.toString()))
    }

    /**
     * Inserts or updates a media entry
     * Use carefully to avoid overwriting properties with stale data.
     *
     * @return the id of the entry
     */
    fun setMedia(media: FeedMedia): Long {
        val values = ContentValues()
        values.put(KEY_DURATION, media.getDuration())
        values.put(KEY_POSITION, media.getPosition())
        values.put(KEY_SIZE, media.getSize())
        values.put(KEY_MIME_TYPE, media.getMimeType())
        values.put(KEY_DOWNLOAD_URL, media.getDownloadUrl())
        values.put(KEY_DOWNLOAD_DATE, media.getDownloadDate())
        values.put(KEY_FILE_URL, media.getLocalFileUrl())
        values.put(KEY_HAS_EMBEDDED_PICTURE, media.hasEmbeddedPicture())
        values.put(KEY_LAST_PLAYED_TIME_STATISTICS, media.getLastPlayedTimeStatistics())

        if (media.getLastPlayedTimeHistory() != null) {
            values.put(KEY_LAST_PLAYED_TIME_HISTORY, media.getLastPlayedTimeHistory()!!.getTime())
        } else {
            values.put(KEY_LAST_PLAYED_TIME_HISTORY, 0)
        }
        if (media.getItem() != null) {
            values.put(KEY_FEEDITEM, media.getItem()!!.getId())
        }
        if (media.getId() == 0L) {
            media.setId(db.insert(TABLE_NAME_FEED_MEDIA, null, values))
        } else {
            db.update(TABLE_NAME_FEED_MEDIA, values, KEY_ID + "=?",
                    arrayOf(media.getId().toString()))
        }
        return media.getId()
    }

    /**
     * Update download state related properties of the feed media.
     */
    fun setMediaDownloadInformation(media: FeedMedia) {
        if (media.getId() != 0L) {
            val values = ContentValues()
            values.put(KEY_SIZE, media.getSize())
            values.put(KEY_FILE_URL, media.getLocalFileUrl())
            values.put(KEY_DOWNLOAD_URL, media.getDownloadUrl())
            values.put(KEY_DOWNLOAD_DATE, media.getDownloadDate())
            values.put(KEY_HAS_EMBEDDED_PICTURE, media.hasEmbeddedPicture())
            db.update(TABLE_NAME_FEED_MEDIA, values, KEY_ID + "=?",
                    arrayOf(media.getId().toString()))
        } else {
            Log.e(TAG, "setMediaDownloadInformation: ID of media was 0")
        }
    }

    fun setFeedMediaPlaybackInformation(media: FeedMedia) {
        if (media.getId() != 0L) {
            val values = ContentValues()
            values.put(KEY_POSITION, media.getPosition())
            values.put(KEY_DURATION, media.getDuration())
            values.put(KEY_PLAYED_DURATION, media.getPlayedDuration())
            values.put(KEY_LAST_PLAYED_TIME_STATISTICS, media.getLastPlayedTimeStatistics())
            values.put(KEY_LAST_PLAYED_TIME_HISTORY, media.getLastPlayedTimeHistory()!!.getTime())
            db.update(TABLE_NAME_FEED_MEDIA, values, KEY_ID + "=?",
                    arrayOf(media.getId().toString()))
        } else {
            Log.e(TAG, "setFeedMediaPlaybackInformation: ID of media was 0")
        }
    }

    fun setFeedMediaLastPlayedTimeHistory(media: FeedMedia) {
        if (media.getId() != 0L) {
            val values = ContentValues()
            values.put(KEY_LAST_PLAYED_TIME_HISTORY, media.getLastPlayedTimeHistory()!!.getTime())
            values.put(KEY_PLAYED_DURATION, media.getPlayedDuration())
            db.update(TABLE_NAME_FEED_MEDIA, values, KEY_ID + "=?",
                    arrayOf(media.getId().toString()))
        } else {
            Log.e(TAG, "setFeedMediaLastPlayedTimeHistory: ID of media was 0")
        }
    }

    fun resetAllMediaPlayedDuration() {
        try {
            db.beginTransactionNonExclusive()
            val values = ContentValues()
            values.put(KEY_PLAYED_DURATION, 0)
            db.update(TABLE_NAME_FEED_MEDIA, values, null, arrayOf())
            db.setTransactionSuccessful()
        } catch (e: SQLException) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Insert all FeedItems of a feed and the feed object itself in a single
     * transaction
     */
    fun setCompleteFeed(vararg feeds: Feed) {
        try {
            db.beginTransactionNonExclusive()
            for (feed in feeds) {
                setFeed(feed)
                if (feed.getItems() != null) {
                    for (item in feed.getItems()!!) {
                        updateOrInsertFeedItem(item, false)
                    }
                }
                if (feed.getPreferences() != null) {
                    setFeedPreferences(feed.getPreferences()!!)
                }
            }
            db.setTransactionSuccessful()
        } catch (e: SQLException) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Updates the download URL of a Feed.
     */
    fun setFeedDownloadUrl(original: String, updated: String) {
        val values = ContentValues()
        values.put(KEY_DOWNLOAD_URL, updated)
        db.update(TABLE_NAME_FEEDS, values, KEY_DOWNLOAD_URL + "=?", arrayOf(original))
    }

    fun storeFeedItemlist(items: List<FeedItem>) {
        try {
            db.beginTransactionNonExclusive()
            for (item in items) {
                updateOrInsertFeedItem(item, true)
            }
            db.setTransactionSuccessful()
        } catch (e: SQLException) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
            db.endTransaction()
        }
    }

    fun setSingleFeedItem(item: FeedItem): Long {
        var result = 0L
        try {
            db.beginTransactionNonExclusive()
            result = updateOrInsertFeedItem(item, true)
            db.setTransactionSuccessful()
        } catch (e: SQLException) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
            db.endTransaction()
        }
        return result
    }

    /**
     * Inserts or updates a feeditem entry
     *
     * @param item     The FeedItem
     * @param saveFeed true if the Feed of the item should also be saved. This should be set to
     *                 false if the method is executed on a list of FeedItems of the same Feed.
     * @return the id of the entry
     */
    private fun updateOrInsertFeedItem(item: FeedItem, saveFeed: Boolean): Long {
        if (item.getId() == 0L && item.getPubDate() == null) {
            Log.e(TAG, "Newly saved item has no pubDate. Using current date as pubDate")
            item.setPubDate(Date())
        }

        val values = ContentValues()
        values.put(KEY_TITLE, item.getTitle())
        values.put(KEY_LINK, item.getLink())
        if (item.getDescription() != null) {
            values.put(KEY_DESCRIPTION, item.getDescription())
        }
        values.put(KEY_PUBDATE, item.getPubDate()!!.getTime())
        values.put(KEY_PAYMENT_LINK, item.getPaymentLink())
        if (saveFeed && item.getFeed() != null) {
            setFeed(item.getFeed()!!)
        }
        values.put(KEY_FEED, item.getFeed()!!.getId())
        if (item.isNew()) {
            values.put(KEY_READ, FeedItem.NEW)
        } else if (item.isPlayed()) {
            values.put(KEY_READ, FeedItem.PLAYED)
        } else {
            values.put(KEY_READ, FeedItem.UNPLAYED)
        }
        values.put(KEY_HAS_CHAPTERS, item.getChapters() != null || item.hasChapters())
        values.put(KEY_ITEM_IDENTIFIER, item.getItemIdentifier())
        values.put(KEY_AUTO_DOWNLOAD_ENABLED, item.isAutoDownloadEnabled())
        values.put(KEY_IMAGE_URL, item.getImageUrl())
        values.put(KEY_PODCASTINDEX_CHAPTER_URL, item.getPodcastIndexChapterUrl())
        values.put(KEY_SOCIAL_INTERACT_URL, item.getSocialInteractUrl())

        // We only store one transcript url, we prefer JSON if it exists
        val type = item.getTranscriptType()
        val url = item.getTranscriptUrl()
        if (url != null) {
            values.put(KEY_PODCASTINDEX_TRANSCRIPT_TYPE, type)
            values.put(KEY_PODCASTINDEX_TRANSCRIPT_URL, url)
        }

        if (item.getId() == 0L) {
            item.setId(db.insert(TABLE_NAME_FEED_ITEMS, null, values))
        } else {
            db.update(TABLE_NAME_FEED_ITEMS, values, KEY_ID + "=?",
                    arrayOf(item.getId().toString()))
        }
        if (item.getMedia() != null) {
            setMedia(item.getMedia()!!)
            item.getMedia()!!.setItemId(item.getId())
        }
        if (item.getChapters() != null) {
            setChapters(item)
        }
        return item.getId()
    }

    /**
     * Sets the 'read' attribute of the item.
     *
     * @param played             New read status of items. See @FeedItem
     * @param resetMediaPosition Should the postition of the media item be reset?
     * @param items              List of items to upgrade
     */
    fun setFeedItemsRead(played: Int, resetMediaPosition: Boolean, items: List<FeedItem>) {
        try {
            db.beginTransactionNonExclusive()
            val values = ContentValues()
            for (item in items) {
                values.clear()
                values.put(KEY_READ, played)
                db.update(TABLE_NAME_FEED_ITEMS, values, KEY_ID + "=?", arrayOf(item.getId().toString()))

                if (resetMediaPosition && item.hasMedia()) {
                    values.clear()
                    values.put(KEY_POSITION, 0)
                    db.update(TABLE_NAME_FEED_MEDIA, values, KEY_ID + "=?",
                            arrayOf(item.getMedia()!!.getId().toString()))
                }
            }
            db.setTransactionSuccessful()
        } catch (e: SQLException) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
            db.endTransaction()
        }
    }

    private fun setChapters(item: FeedItem) {
        val values = ContentValues()
        for (chapter in item.getChapters()!!) {
            values.put(KEY_TITLE, chapter.getTitle())
            values.put(KEY_START, chapter.getStart())
            values.put(KEY_FEEDITEM, item.getId())
            values.put(KEY_LINK, chapter.getLink())
            values.put(KEY_IMAGE_URL, chapter.getImageUrl())
            if (chapter.getId() == 0L) {
                chapter.setId(db.insert(TABLE_NAME_SIMPLECHAPTERS, null, values))
            } else {
                db.update(TABLE_NAME_SIMPLECHAPTERS, values, KEY_ID + "=?",
                        arrayOf(chapter.getId().toString()))
            }
        }
    }

    fun resetPagedFeedPage(feed: Feed) {
        val sql = "UPDATE " + TABLE_NAME_FEEDS + " SET " + KEY_NEXT_PAGE_LINK + "=" + KEY_DOWNLOAD_URL + " WHERE " + KEY_ID + "=" + feed.getId()
        db.execSQL(sql)
    }

    fun setFeedLastUpdateFailed(feedId: Long, failed: Boolean) {
        val sql = "UPDATE " + TABLE_NAME_FEEDS + " SET " + KEY_LAST_UPDATE_FAILED + "=" + if (failed) "1" else "0" + "," + KEY_LAST_REFRESH_ATTEMPT + "=" + System.currentTimeMillis() + " WHERE " + KEY_ID + "=" + feedId
        db.execSQL(sql)
    }

    fun setFeedCustomTitle(feedId: Long, customTitle: String?) {
        val values = ContentValues()
        values.put(KEY_CUSTOM_TITLE, customTitle)
        db.update(TABLE_NAME_FEEDS, values, KEY_ID + "=?", arrayOf(feedId.toString()))
    }

    fun setFeedState(feedId: Long, state: Int) {
        val values = ContentValues()
        values.put(KEY_STATE, state)
        db.update(TABLE_NAME_FEEDS, values, KEY_ID + "=?", arrayOf(feedId.toString()))
    }

    /**
     * Inserts or updates a download status.
     */
    fun setDownloadStatus(status: DownloadResult): Long {
        val values = ContentValues()
        values.put(KEY_FEEDFILE, status.getFeedfileId())
        values.put(KEY_FEEDFILETYPE, status.getFeedfileType())
        values.put(KEY_REASON, status.getReason()!!.getCode())
        values.put(KEY_SUCCESSFUL, status.isSuccessful())
        values.put(KEY_COMPLETION_DATE, status.getCompletionDate().getTime())
        values.put(KEY_REASON_DETAILED, status.getReasonDetailed())
        values.put(KEY_DOWNLOADSTATUS_TITLE, status.getTitle())
        if (status.getId() == 0L) {
            status.setId(db.insert(TABLE_NAME_DOWNLOAD_LOG, null, values))
        } else {
            db.update(TABLE_NAME_DOWNLOAD_LOG, values, KEY_ID + "=?",
                    arrayOf(status.getId().toString()))
        }
        return status.getId()
    }

    fun setFavorites(favorites: List<FeedItem>) {
        val values = ContentValues()
        try {
            db.beginTransactionNonExclusive()
            db.delete(TABLE_NAME_FAVORITES, null, null)
            for (i in favorites.indices) {
                val item = favorites[i]
                values.put(KEY_ID, i)
                values.put(KEY_FEEDITEM, item.getId())
                values.put(KEY_FEED, item.getFeed()!!.getId())
                db.insertWithOnConflict(TABLE_NAME_FAVORITES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } catch (e: SQLException) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Adds the item to favorites
     */
    fun addFavoriteItems(items: List<FeedItem>) {
        if (items.isEmpty()) {
            return
        }
        db.execSQL("INSERT INTO " + TABLE_NAME_FAVORITES + " (" + KEY_FEEDITEM + ", " + KEY_FEED + ")" + " SELECT " + KEY_ID + ", " + KEY_FEED + " FROM " + TABLE_NAME_FEED_ITEMS + " WHERE " + KEY_ID + " IN (" + getItemIds(items) + ")" + " AND " + KEY_ID + " NOT IN (SELECT " + KEY_FEEDITEM + " FROM " + TABLE_NAME_FAVORITES + ")")
    }

    fun removeFavoriteItems(items: List<FeedItem>) {
        if (items.isEmpty()) {
            return
        }
        db.execSQL("DELETE FROM " + TABLE_NAME_FAVORITES + " WHERE " + KEY_FEEDITEM + " IN (" + getItemIds(items) + ")")
    }

    fun setQueue(queue: List<FeedItem>) {
        val values = ContentValues()
        try {
            db.beginTransactionNonExclusive()
            db.delete(TABLE_NAME_QUEUE, null, null)
            for (i in queue.indices) {
                val item = queue[i]
                values.put(KEY_ID, i)
                values.put(KEY_FEEDITEM, item.getId())
                values.put(KEY_FEED, item.getFeed()!!.getId())
                db.insertWithOnConflict(TABLE_NAME_QUEUE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } catch (e: SQLException) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
            db.endTransaction()
        }
    }

    fun clearQueue() {
        db.delete(TABLE_NAME_QUEUE, null, null)
    }

    /**
     * Remove the listed items and their FeedMedia entries.
     */
    fun removeFeedItems(items: List<FeedItem>) {
        try {
            val mediaIds = StringBuilder()
            val itemIds = StringBuilder()
            for (item in items) {
                if (item.getMedia() != null) {
                    if (mediaIds.length != 0) {
                        mediaIds.append(",")
                    }
                    mediaIds.append(item.getMedia()!!.getId())
                }
                if (itemIds.length != 0) {
                    itemIds.append(",")
                }
                itemIds.append(item.getId())
            }

            db.beginTransactionNonExclusive()
            db.delete(TABLE_NAME_SIMPLECHAPTERS, KEY_FEEDITEM + " IN (" + itemIds + ")", null)
            db.delete(TABLE_NAME_DOWNLOAD_LOG, KEY_FEEDFILETYPE + "=" + FeedMedia.FEEDFILETYPE_FEEDMEDIA + " AND " + KEY_FEEDFILE + " IN (" + mediaIds + ")", null)
            db.delete(TABLE_NAME_FEED_MEDIA, KEY_ID + " IN (" + mediaIds + ")", null)
            db.delete(TABLE_NAME_FEED_ITEMS, KEY_ID + " IN (" + itemIds + ")", null)
            db.delete(TABLE_NAME_FAVORITES, KEY_FEEDITEM + " IN (" + itemIds + ")", null)
            db.setTransactionSuccessful()
        } catch (e: SQLException) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Remove a feed with all its FeedItems and Media entries.
     */
    fun removeFeed(feed: Feed) {
        try {
            db.beginTransactionNonExclusive()
            if (feed.getItems() != null) {
                removeFeedItems(feed.getItems()!!)
            }
            // delete download log entries for feed
            db.delete(TABLE_NAME_DOWNLOAD_LOG, KEY_FEEDFILE + "=? AND " + KEY_FEEDFILETYPE + "=?",
                    arrayOf(feed.getId().toString(), Feed.FEEDFILETYPE_FEED.toString()))

            db.delete(TABLE_NAME_FEEDS, KEY_ID + "=?",
                    arrayOf(feed.getId().toString()))
            db.setTransactionSuccessful()
        } catch (e: SQLException) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
            db.endTransaction()
        }
    }

    fun clearPlaybackHistory() {
        val values = ContentValues()
        values.put(KEY_LAST_PLAYED_TIME_HISTORY, 0)
        db.update(TABLE_NAME_FEED_MEDIA, values, null, null)
    }

    fun clearDownloadLog() {
        db.delete(TABLE_NAME_DOWNLOAD_LOG, null, null)
    }

    fun clearOldDownloadLog() {
        db.execSQL("DELETE FROM " + PodDBAdapter.TABLE_NAME_DOWNLOAD_LOG + " WHERE " + PodDBAdapter.KEY_COMPLETION_DATE + "<" + (System.currentTimeMillis() - 7L * 24L * 3600L * 1000L))
    }

    /**
     * Get all Feeds from the Feed Table.
     *
     * @return The cursor of the query
     */
    fun getAllFeedsCursor(): Cursor {
        val query = "SELECT " + KEYS_FEED + " FROM " + TABLE_NAME_FEEDS + " ORDER BY " + TABLE_NAME_FEEDS + "." + KEY_TITLE + " COLLATE NOCASE ASC"
        return db.rawQuery(query, null)
    }

    fun getFeedCursorDownloadUrls(subscribedOnly: Boolean): Cursor {
        val selection = if (subscribedOnly) KEY_STATE + "=" + Feed.STATE_SUBSCRIBED else null
        return db.query(TABLE_NAME_FEEDS, arrayOf(KEY_ID, KEY_DOWNLOAD_URL), selection, null, null, null, null)
    }

    /**
     * Returns a cursor with all FeedItems of a Feed. Uses FEEDITEM_SEL_FI_SMALL
     *
     * @param feed The feed you want to get the FeedItems from.
     * @return The cursor of the query
     */
    fun getItemsOfFeedCursor(feed: Feed, filter: FeedItemFilter, sortOrder: SortOrder,
                             offset: Int, limit: Int): Cursor {
        val orderByQuery = FeedItemSortQuery.generateFrom(sortOrder)
        val filter = FeedItemFilter(filter, FeedItemFilter.INCLUDE_ALL_FEED_STATES)
        val filterQuery = FeedItemFilterQuery.generateFrom(filter)
        val whereClauseAnd = if ("" == filterQuery) "" else " AND " + filterQuery
        val query = SELECT_FEED_ITEMS_AND_MEDIA + " WHERE " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + "=" + feed.getId() + whereClauseAnd + " ORDER BY " + orderByQuery + " LIMIT " + offset + ", " + limit
        return db.rawQuery(query, null)
    }

    /**
     * Return the description and content_encoded of item
     */
    fun getDescriptionOfItem(item: FeedItem): Cursor {
        val query = "SELECT " + KEY_DESCRIPTION + " FROM " + TABLE_NAME_FEED_ITEMS + " WHERE " + KEY_ID + "=" + item.getId()
        return db.rawQuery(query, null)
    }

    fun getSimpleChaptersOfFeedItemCursor(item: FeedItem): Cursor {
        return db.query(TABLE_NAME_SIMPLECHAPTERS, null, KEY_FEEDITEM + "=?", arrayOf(item.getId().toString()), null,
                null, null
        )
    }

    fun getDownloadLog(feedFileType: Int, feedFileId: Long, limit: Long): Cursor {
        val query = "SELECT * FROM " + TABLE_NAME_DOWNLOAD_LOG +
                " WHERE " + KEY_FEEDFILE + "=" + feedFileId + " AND " + KEY_FEEDFILETYPE + "=" + feedFileType + " ORDER BY " + KEY_COMPLETION_DATE + " DESC LIMIT " + limit
        return db.rawQuery(query, null)
    }

    fun getDownloadLogCursor(limit: Int): Cursor {
        return db.query(TABLE_NAME_DOWNLOAD_LOG, null, null, null, null,
                null, KEY_COMPLETION_DATE + " DESC LIMIT " + limit)
    }

    /**
     * Returns a cursor which contains all feed items in the queue. The returned
     * cursor uses the FEEDITEM_SEL_FI_SMALL selection.
     * cursor uses the FEEDITEM_SEL_FI_SMALL selection.
     */
    fun getQueueCursor(): Cursor {
        val query = "SELECT " + KEYS_FEED_ITEM_WITHOUT_DESCRIPTION + ", " + KEYS_FEED_MEDIA + " FROM " + TABLE_NAME_QUEUE + " INNER JOIN " + TABLE_NAME_FEED_ITEMS + " ON " + SELECT_KEY_ITEM_ID + " = " + TABLE_NAME_QUEUE + "." + KEY_FEEDITEM +  JOIN_FEED_ITEM_AND_MEDIA + " ORDER BY " + TABLE_NAME_QUEUE + "." + KEY_ID
        return db.rawQuery(query, null)
    }

    fun getQueueIDCursor(): Cursor {
        return db.query(TABLE_NAME_QUEUE, arrayOf(KEY_FEEDITEM), null, null, null, null, KEY_ID + " ASC", null)
    }

    fun getNextInQueue(item: FeedItem): Cursor {
        val query = "SELECT " + KEYS_FEED_ITEM_WITHOUT_DESCRIPTION + ", " + KEYS_FEED_MEDIA + " FROM " + TABLE_NAME_QUEUE + " INNER JOIN " + TABLE_NAME_FEED_ITEMS + " ON " + SELECT_KEY_ITEM_ID + " = " + TABLE_NAME_QUEUE + "." + KEY_FEEDITEM +  JOIN_FEED_ITEM_AND_MEDIA + " WHERE Queue.ID > (SELECT Queue.ID FROM Queue WHERE Queue.FeedItem = " +  item.getId() + ")" + " ORDER BY Queue.ID" + " LIMIT 1"
        return db.rawQuery(query, null)
    }

    fun getPausedQueueCursor(limit: Int): Cursor {
        val hasPositionOrRecentlyPlayed = TABLE_NAME_FEED_MEDIA + "." + KEY_POSITION + " >= 1000" + " OR " + TABLE_NAME_FEED_MEDIA + "." + KEY_LAST_PLAYED_TIME_STATISTICS + " >= " + (System.currentTimeMillis() - 30000)
        val query = "SELECT " + KEYS_FEED_ITEM_WITHOUT_DESCRIPTION + ", " + KEYS_FEED_MEDIA + " FROM " + TABLE_NAME_QUEUE + " INNER JOIN " + TABLE_NAME_FEED_ITEMS + " ON " + SELECT_KEY_ITEM_ID + " = " + TABLE_NAME_QUEUE + "." + KEY_FEEDITEM +  JOIN_FEED_ITEM_AND_MEDIA + " ORDER BY (CASE WHEN " + hasPositionOrRecentlyPlayed + " THEN " + TABLE_NAME_FEED_MEDIA + "." + KEY_LAST_PLAYED_TIME_STATISTICS + " ELSE 0 END) DESC , " + TABLE_NAME_QUEUE + "." + KEY_ID + " LIMIT " + limit
        return db.rawQuery(query, null)
    }

    fun setFeedItems(oldState: Int, newState: Int) {
        setFeedItems(oldState, newState, 0)
    }

    fun setFeedItems(oldState: Int, newState: Int, feedId: Long) {
        var sql = "UPDATE " + TABLE_NAME_FEED_ITEMS + " SET " + KEY_READ + "=" + newState
        if (feedId > 0) {
            sql += " WHERE " + KEY_FEED + "=" + feedId
        }
        if (FeedItem.NEW <= oldState && oldState <= FeedItem.PLAYED) {
            sql += if (feedId > 0) " AND " else " WHERE "
            sql += KEY_READ + "=" + oldState
        }
        db.execSQL(sql)
    }

    fun getEpisodesCursor(offset: Int, limit: Int, filter: FeedItemFilter, sortOrder: SortOrder): Cursor {
        val orderByQuery = FeedItemSortQuery.generateFrom(sortOrder)
        val filterQuery = FeedItemFilterQuery.generateFrom(filter)
        val whereClause = if ("" == filterQuery) "" else " WHERE " + filterQuery
        val query = SELECT_FEED_ITEMS_AND_MEDIA + whereClause + "ORDER BY " + orderByQuery + " LIMIT " + offset + ", " + limit
        return db.rawQuery(query, null)
    }

    fun getEpisodeCountCursor(filter: FeedItemFilter): Cursor {
        val filterQuery = FeedItemFilterQuery.generateFrom(filter)
        val whereClause = if ("" == filterQuery) "" else " WHERE " + filterQuery
        val query = "SELECT count(" + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + ") FROM " + TABLE_NAME_FEED_ITEMS + JOIN_FEED_ITEM_AND_MEDIA + whereClause
        return db.rawQuery(query, null)
    }

    fun getFeedEpisodeCountCursor(feedId: Long, filter: FeedItemFilter): Cursor {
        val filter = FeedItemFilter(filter, FeedItemFilter.INCLUDE_ALL_FEED_STATES)
        val filterQuery = FeedItemFilterQuery.generateFrom(filter)
        val whereAndClause = if ("" == filterQuery) "" else " AND " + filterQuery
        val query = "SELECT count(" + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + ") FROM " + TABLE_NAME_FEED_ITEMS + JOIN_FEED_ITEM_AND_MEDIA + " WHERE " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + "=" + feedId + whereAndClause
        return db.rawQuery(query, null)
    }

    fun getRandomEpisodesCursor(limit: Int, seed: Int): Cursor {
        val oneHourAgo = System.currentTimeMillis() - 1000L * 3600L
        val allItems = SELECT_FEED_ITEMS_AND_MEDIA + " WHERE (" + KEY_READ + " = " + FeedItem.NEW + " OR " + KEY_READ + " = " + FeedItem.UNPLAYED + ") "
                    // Only from the last two years. Older episodes often contain broken covers and stuff like that + " AND " + KEY_PUBDATE + " > " + (System.currentTimeMillis() - 1000L * 3600L * 24L * 356L * 2)
                    // Hide episodes that have been played but not completed + " AND (" + KEY_LAST_PLAYED_TIME_STATISTICS + " == 0" + " OR " + KEY_LAST_PLAYED_TIME_STATISTICS + " > " + oneHourAgo + ")" + " AND " + SELECT_WHERE_FEED_IS_SUBSCRIBED
        val query = "SELECT MAX(" + randomEpisodeNumber(seed) + "), * FROM (" + allItems + ")" + " GROUP BY " + KEY_FEED + " ORDER BY " + randomEpisodeNumber(seed * 3) + " DESC LIMIT " + limit
        return db.rawQuery(query, null)
    }

    /**
     * SQLite does not support random seeds. Create our own "random" number based on that seed and the item ID
     */
    private fun randomEpisodeNumber(seed: Int): String {
        return "((" + SELECT_KEY_ITEM_ID + " * " + seed + ") % 46471)"
    }

    fun getFeedItemFromMediaIdCursor(mediaId: Long): Cursor {
        val query = SELECT_FEED_ITEMS_AND_MEDIA + " WHERE " + SELECT_KEY_MEDIA_ID + " = " + mediaId
        return db.rawQuery(query, null)
    }

    fun getFeedCursor(id: Long): Cursor {
        val query = "SELECT " + KEYS_FEED + " FROM " + TABLE_NAME_FEEDS + " WHERE " + SELECT_KEY_FEED_ID + " = " + id
        return db.rawQuery(query, null)
    }

    fun getFeedItemCursor(id: String): Cursor {
        return getFeedItemCursor(arrayOf(id))
    }

    fun getFeedItemCursor(ids: Array<String>): Cursor {
        if (ids.size > IN_OPERATOR_MAXIMUM) {
            throw IllegalArgumentException("number of IDs must not be larger than " + IN_OPERATOR_MAXIMUM)
        }
        val query = SELECT_FEED_ITEMS_AND_MEDIA + " WHERE " + SELECT_KEY_ITEM_ID + " IN (" + TextUtils.join(",", ids) + ")"
        return db.rawQuery(query, null)
    }

    fun getFeedItemCursorByUrl(urls: List<String>): Cursor {
        if (urls.size > IN_OPERATOR_MAXIMUM) {
            throw IllegalArgumentException("number of IDs must not be larger than " + IN_OPERATOR_MAXIMUM)
        }
        val urlsString = StringBuilder()
        for (i in urls.indices) {
            if (i != 0) {
                urlsString.append(",")
            }
            urlsString.append(DatabaseUtils.sqlEscapeString(urls[i]))
        }
        val query = SELECT_FEED_ITEMS_AND_MEDIA + " WHERE " + KEY_DOWNLOAD_URL + " IN (" + urlsString + ")" + " ORDER BY " + KEY_LAST_PLAYED_TIME_HISTORY + " DESC"
        return db.rawQuery(query, null)
    }

    fun getFeedItemCursor(guid: String?, episodeUrl: String): Cursor {
        val escapedEpisodeUrl = DatabaseUtils.sqlEscapeString(episodeUrl)
        var whereClauseCondition = TABLE_NAME_FEED_MEDIA + "." + KEY_DOWNLOAD_URL + "=" + escapedEpisodeUrl

        if (guid != null) {
            val escapedGuid = DatabaseUtils.sqlEscapeString(guid)
            whereClauseCondition = TABLE_NAME_FEED_ITEMS + "." + KEY_ITEM_IDENTIFIER + "=" + escapedGuid
        }

        val query = SELECT_FEED_ITEMS_AND_MEDIA + " INNER JOIN " + TABLE_NAME_FEEDS + " ON " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + "=" + TABLE_NAME_FEEDS + "." + KEY_ID + " WHERE " + whereClauseCondition
        return db.rawQuery(query, null)
    }

    fun getMonthlyStatisticsCursor(): Cursor {
        val query = "SELECT SUM(" + KEY_PLAYED_DURATION + ") AS total_duration" + ", strftime('%m', datetime(" + KEY_LAST_PLAYED_TIME_STATISTICS + "/1000, 'unixepoch')) AS month" + ", strftime('%Y', datetime(" + KEY_LAST_PLAYED_TIME_STATISTICS + "/1000, 'unixepoch')) AS year" + " FROM " + TABLE_NAME_FEED_MEDIA + " WHERE " + KEY_LAST_PLAYED_TIME_STATISTICS + " > 0 AND " + KEY_PLAYED_DURATION + " > 0" + " GROUP BY year, month" + " ORDER BY year, month"
        return db.rawQuery(query, null)
    }

    fun getFeedStatisticsCursor(includeMarkedAsPlayed: Boolean, timeFilterFrom: Long,
                                timeFilterTo: Long, sixMonthsAgo: Long): Cursor {
        val lastPlayedTimeStatistics = TABLE_NAME_FEED_MEDIA + "." + KEY_LAST_PLAYED_TIME_STATISTICS
        var wasStarted = TABLE_NAME_FEED_MEDIA + "." + KEY_LAST_PLAYED_TIME_HISTORY + " > 0" + " AND " + TABLE_NAME_FEED_MEDIA + "." + KEY_PLAYED_DURATION + " > 0"
        if (includeMarkedAsPlayed) {
            wasStarted = "(" + wasStarted + ") OR " + TABLE_NAME_FEED_ITEMS + "." + KEY_READ + "=" + FeedItem.PLAYED + " OR " + TABLE_NAME_FEED_MEDIA + "." + KEY_POSITION + "> 0"
        }
        val timeFilter = lastPlayedTimeStatistics + ">=" + timeFilterFrom + " AND " + lastPlayedTimeStatistics + "<" + timeFilterTo
        var playedTime = TABLE_NAME_FEED_MEDIA + "." + KEY_PLAYED_DURATION
        if (includeMarkedAsPlayed) {
            playedTime = "(CASE WHEN " + playedTime + " != 0" + " THEN " + playedTime + " ELSE (" + "CASE WHEN " + TABLE_NAME_FEED_ITEMS + "." + KEY_READ + "=" + FeedItem.PLAYED + " THEN " + TABLE_NAME_FEED_MEDIA + "." + KEY_DURATION + " ELSE 0 END" + ") END)"
        }

        val query = "SELECT " + KEYS_FEED + ", " + "COUNT(*) AS num_episodes, " + "MIN(CASE WHEN " + lastPlayedTimeStatistics + " > 0" + " THEN " + lastPlayedTimeStatistics + " ELSE " + Long.MAX_VALUE + " END) AS oldest_date, " + "SUM(CASE WHEN (" + wasStarted + ") THEN 1 ELSE 0 END) AS episodes_started, " + "IFNULL(SUM(CASE WHEN (" + timeFilter + ")" + " THEN (" + playedTime + ") ELSE 0 END), 0) AS played_time, " + "IFNULL(SUM(" + TABLE_NAME_FEED_MEDIA + "." + KEY_DURATION + "), 0) AS total_time, " + "SUM(CASE WHEN " + TABLE_NAME_FEED_MEDIA + "." + KEY_DOWNLOAD_DATE + " > 0" + " OR " + TABLE_NAME_FEEDS + "." + KEY_DOWNLOAD_URL + " LIKE '" + Feed.PREFIX_LOCAL_FOLDER + "%'" + " THEN 1 ELSE 0 END) AS num_downloaded, " + "SUM(CASE WHEN " + TABLE_NAME_FEED_MEDIA + "." + KEY_DOWNLOAD_DATE + " > 0" + " OR " + TABLE_NAME_FEEDS + "." + KEY_DOWNLOAD_URL + " LIKE '" + Feed.PREFIX_LOCAL_FOLDER + "%'" + " THEN " + TABLE_NAME_FEED_MEDIA + "." + KEY_SIZE + " ELSE 0 END) AS download_size, " + "SUM(CASE WHEN " + TABLE_NAME_FEED_ITEMS + "." + KEY_READ + " != " + FeedItem.PLAYED + " AND " + TABLE_NAME_FEED_ITEMS + "." + KEY_PUBDATE + " >= " + sixMonthsAgo + " THEN 1 ELSE 0 END) AS num_recent_unplayed " + " FROM " + TABLE_NAME_FEED_ITEMS + JOIN_FEED_ITEM_AND_MEDIA + " INNER JOIN " + TABLE_NAME_FEEDS + " ON " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + "=" + TABLE_NAME_FEEDS + "." + KEY_ID + " WHERE " + TABLE_NAME_FEEDS + "." + KEY_STATE + "!=" + Feed.STATE_NOT_SUBSCRIBED + " GROUP BY " + TABLE_NAME_FEEDS + "." + KEY_ID
        return db.rawQuery(query, null)
    }

    fun getTimeBetweenReleaseAndPlayback(timeFilterFrom: Long, timeFilterTo: Long): Cursor {
        val from = " FROM " + TABLE_NAME_FEED_ITEMS + JOIN_FEED_ITEM_AND_MEDIA + " WHERE " + TABLE_NAME_FEED_MEDIA + "." + KEY_LAST_PLAYED_TIME_STATISTICS + ">=" + timeFilterFrom + " AND " + TABLE_NAME_FEED_ITEMS + "." + KEY_PUBDATE + ">=" + timeFilterFrom + " AND " + TABLE_NAME_FEED_MEDIA + "." + KEY_LAST_PLAYED_TIME_STATISTICS + "<" + timeFilterTo
        val query = "SELECT " + TABLE_NAME_FEED_MEDIA + "." + KEY_LAST_PLAYED_TIME_STATISTICS + " - " + TABLE_NAME_FEED_ITEMS + "." + KEY_PUBDATE + " AS diff" + from + " ORDER BY diff ASC" + " LIMIT 1" + " OFFSET (SELECT count(*)/2 " + from + ")"
        return db.rawQuery(query, null)
    }

    fun getQueueSize(): Int {
        val query = java.lang.String.format("SELECT COUNT(%s) FROM %s", KEY_ID, TABLE_NAME_QUEUE)
        try {
            db.rawQuery(query, null).use { c ->
                if (c.moveToFirst()) {
                    return c.getInt(0)
                }
                return 0
            }
        } finally {
        }
    }

    fun getFeedCounters(setting: FeedCounter, vararg feedIds: Long): HashMap<Long, Int> {
        val whereRead: String
        val localFeedCondition = KEY_FEED + " IN (SELECT " + KEY_ID + " FROM " + TABLE_NAME_FEEDS + " WHERE " + KEY_DOWNLOAD_URL + " LIKE '" + Feed.PREFIX_LOCAL_FOLDER + "%')"
        when (setting) {
            FeedCounter.SHOW_NEW -> whereRead = KEY_READ + "=" + FeedItem.NEW
            FeedCounter.SHOW_UNPLAYED -> whereRead = "(" + KEY_READ + "=" + FeedItem.NEW + " OR " + KEY_READ + "=" + FeedItem.UNPLAYED + ")"
            FeedCounter.SHOW_DOWNLOADED -> whereRead = "(" + KEY_DOWNLOAD_DATE + ">0 OR " + localFeedCondition + ")"
            FeedCounter.SHOW_DOWNLOADED_UNPLAYED -> whereRead = "(" + KEY_READ + "=" + FeedItem.NEW + " OR " + KEY_READ + "=" + FeedItem.UNPLAYED + ")" + " AND (" + KEY_DOWNLOAD_DATE + ">0 OR " + localFeedCondition + ")"
            FeedCounter.SHOW_NONE -> return HashMap()
        }
        return conditionalFeedCounterRead(whereRead, *feedIds)
    }

    private fun conditionalFeedCounterRead(whereRead: String, vararg feedIds: Long): HashMap<Long, Int> {
        val limitFeeds: String
        if (feedIds.size > 0) {
            // work around TextUtils.join wanting only boxed items
            // and StringUtils.join() causing NoSuchMethodErrors on MIUI
            val builder = StringBuilder()
            for (id in feedIds) {
                builder.append(id)
                builder.append(',')
            }
            // there's an extra ',', get rid of it
            builder.deleteCharAt(builder.length - 1)
            limitFeeds = KEY_FEED + " IN (" + builder.toString() + ") AND "
        } else {
            limitFeeds = SELECT_WHERE_FEED_IS_SUBSCRIBED + " AND "
        }

        val query = "SELECT " + KEY_FEED + ", COUNT(" + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + ") AS count " + " FROM " + TABLE_NAME_FEED_ITEMS + " LEFT JOIN " + TABLE_NAME_FEED_MEDIA + " ON " + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + "=" + TABLE_NAME_FEED_MEDIA + "." + KEY_FEEDITEM + " WHERE " + limitFeeds + " " + whereRead + " GROUP BY " + KEY_FEED

        val result = HashMap<Long, Int>()
        try {
            db.rawQuery(query, null).use { c ->
                if (!c.moveToFirst()) {
                    return result
                }
                do {
                    val feedId = c.getLong(0)
                    val count = c.getInt(1)
                    result.put(feedId, count)
                } while (c.moveToNext())
            }
        } finally {
        }
        return result
    }

    fun getPlayedEpisodesCounters(vararg feedIds: Long): HashMap<Long, Int> {
        val whereRead = KEY_READ + "=" + FeedItem.PLAYED
        return conditionalFeedCounterRead(whereRead, *feedIds)
    }

    fun getMostRecentItemDates(): HashMap<Long, Long> {
        val query = "SELECT " + KEY_FEED + "," + " MAX(" + TABLE_NAME_FEED_ITEMS + "." + KEY_PUBDATE + ") AS most_recent_pubdate" + " FROM " + TABLE_NAME_FEED_ITEMS + " GROUP BY " + KEY_FEED

        val result = HashMap<Long, Long>()
        try {
            db.rawQuery(query, null).use { c ->
                if (!c.moveToFirst()) {
                    return result
                }
                do {
                    val feedId = c.getLong(0)
                    val date = c.getLong(1)
                    result.put(feedId, date)
                } while (c.moveToNext())
            }
        } finally {
        }
        return result
    }

    /**
     * Uses DatabaseUtils to escape a search query and removes ' at the
     * beginning and the end of the string returned by the escape method.
     */
    private fun prepareSearchQuery(query: String): Array<String> {
        val queryWords = query.split(Regex("\\s+")).toTypedArray()
        for (i in queryWords.indices) {
            val builder = StringBuilder()
            DatabaseUtils.appendEscapedSQLString(builder, queryWords[i])
            builder.deleteCharAt(0)
            builder.deleteCharAt(builder.length - 1)
            queryWords[i] = builder.toString()
        }

        return queryWords
    }

    /**
     * Searches for the given query in various values of all items or the items
     * of a specified feed.
     *
     * @return A cursor with all search results in SEL_FI_EXTRA selection.
     */
    fun searchItems(feedID: Long, searchQuery: String, filter: FeedItemFilter): Cursor {
        val queryWords = prepareSearchQuery(searchQuery)

        val queryFeedId: String
        if (feedID != 0L) {
            // search items in specific feed
            queryFeedId = KEY_FEED + " = " + feedID
        } else {
            // search through all items
            queryFeedId = "1 = 1"
        }

        var queryStart = SELECT_FEED_ITEMS_AND_MEDIA_WITH_DESCRIPTION + " WHERE " + queryFeedId
        val effectiveFilter = if (feedID != 0L)
            FeedItemFilter(filter, FeedItemFilter.INCLUDE_ALL_FEED_STATES)
        else
            filter
        val filterQuery = FeedItemFilterQuery.generateFrom(effectiveFilter)
        if (!filterQuery.isEmpty()) {
            queryStart += " AND " + filterQuery
        }
        queryStart += " AND ("
        val sb = StringBuilder(queryStart)

        for (i in queryWords.indices) {
            sb
                    .append("(")
                    .append(KEY_DESCRIPTION + " LIKE '%").append(queryWords[i])
                    .append("%' OR ")
                    .append(KEY_TITLE).append(" LIKE '%").append(queryWords[i])
                    .append("%') ")

            if (i != queryWords.size - 1) {
                sb.append("AND ")
            }
        }

        sb.append(") ORDER BY " + KEY_PUBDATE + " DESC LIMIT 300")

        return db.rawQuery(sb.toString(), null)
    }

    /**
     * Searches for the given query in various values of all feeds.
     *
     * @return A cursor with all search results in SEL_FI_EXTRA selection.
     */
    fun searchFeeds(searchQuery: String, filter: FeedItemFilter): Cursor {
        val queryWords = prepareSearchQuery(searchQuery)
        val allowedStates = ArrayList<String>()
        if (filter.includeSubscribed) {
            allowedStates.add(Feed.STATE_SUBSCRIBED.toString())
        }
        if (filter.includeArchived) {
            allowedStates.add(Feed.STATE_ARCHIVED.toString())
        }
        if (filter.includeNotSubscribed) {
            allowedStates.add(Feed.STATE_NOT_SUBSCRIBED.toString())
        }
        if (allowedStates.isEmpty()) {
            allowedStates.add(Feed.STATE_SUBSCRIBED.toString())
        }
        val queryStart = "SELECT " + KEYS_FEED + " FROM " + TABLE_NAME_FEEDS + " WHERE " + KEY_STATE + " IN (" + TextUtils.join(",", allowedStates) + ")"
        val sb = StringBuilder(queryStart)

        for (i in queryWords.indices) {
            sb
                    .append(" AND (")
                    .append(KEY_TITLE).append(" LIKE '%").append(queryWords[i])
                    .append("%' OR ")
                    .append(KEY_CUSTOM_TITLE).append(" LIKE '%").append(queryWords[i])
                    .append("%' OR ")
                    .append(KEY_AUTHOR).append(" LIKE '%").append(queryWords[i])
                    .append("%' OR ")
                    .append(KEY_DESCRIPTION).append(" LIKE '%").append(queryWords[i])
                    .append("%') ")
        }

        sb.append(" ORDER BY " + KEY_TITLE + " ASC LIMIT 300")

        return db.rawQuery(sb.toString(), null)
    }

    private fun getItemIds(items: List<FeedItem>): String {
        val itemIds = StringBuilder()
        for (item in items) {
            if (itemIds.length != 0) {
                itemIds.append(",")
            }
            itemIds.append(item.getId())
        }
        return itemIds.toString()
    }

    /**
     * Insert raw data to the database.
     * Call method only for unit tests.
     */
    fun insertTestData(table: String, values: ContentValues) {
        db.insert(table, null, values)
    }

    /**
     * Called when a database corruption happens.
     */
    class PodDbErrorHandler : DatabaseErrorHandler {
        override fun onCorruption(db: SQLiteDatabase) {
            Log.e(TAG, "Database corrupted: " + db.getPath())

            val dbPath = File(db.getPath())
            val backupFolder = PodDBAdapter.context!!.getExternalFilesDir(null)
            val backupFile = File(backupFolder, "CorruptedDatabaseBackup.db")
            try {
                FileUtils.copyFile(dbPath, backupFile)
                Log.d(TAG, "Dumped database to " + backupFile.getPath())
            } catch (e: IOException) {
                Log.d(TAG, Log.getStackTraceString(e))
            }

            DefaultDatabaseErrorHandler().onCorruption(db) // This deletes the database
        }
    }

    /**
     * Helper class for opening the Antennapod database.
     */
    private class PodDBHelper : SQLiteOpenHelper {
        /**
         * Constructor.
         *
         * @param context Context to use
         * @param name    Name of the database
         * @param factory to use for creating cursor objects
         */
        constructor(context: Context, name: String, factory: CursorFactory?) : super(context, name, factory, VERSION, PodDbErrorHandler()) {
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(CREATE_TABLE_FEEDS)
            db.execSQL(CREATE_TABLE_FEED_ITEMS)
            db.execSQL(CREATE_TABLE_FEED_MEDIA)
            db.execSQL(CREATE_TABLE_DOWNLOAD_LOG)
            db.execSQL(CREATE_TABLE_QUEUE)
            db.execSQL(CREATE_TABLE_SIMPLECHAPTERS)
            db.execSQL(CREATE_TABLE_FAVORITES)

            db.execSQL(CREATE_INDEX_FEEDITEMS_FEED)
            db.execSQL(CREATE_INDEX_FEEDITEMS_PUBDATE)
            db.execSQL(CREATE_INDEX_FEEDITEMS_READ)
            db.execSQL(CREATE_INDEX_FEEDMEDIA_FEEDITEM)
            db.execSQL(CREATE_INDEX_QUEUE_FEEDITEM)
            db.execSQL(CREATE_INDEX_SIMPLECHAPTERS_FEEDITEM)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            Log.w("DBAdapter", "Upgrading from version " + oldVersion + " to " + newVersion + ".")
            DBUpgrader.upgrade(db, oldVersion, newVersion)
        }
    }
}
