package de.danoeh.antennapod.storage.database.mapper

import android.database.Cursor
import android.database.CursorWrapper
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.storage.database.PodDBAdapter

import java.util.Date

/**
 * Converts a [Cursor] to a [FeedMedia] object.
 */
class FeedMediaCursor : CursorWrapper {
    private val indexId: Int
    private val indexLastPlayedTimeHistory: Int
    private val indexDuration: Int
    private val indexPosition: Int
    private val indexSize: Int
    private val indexMimeType: Int
    private val indexFileUrl: Int
    private val indexDownloadUrl: Int
    private val indexDownloadDate: Int
    private val indexPlayedDuration: Int
    private val indexLastPlayedTimeStatistics: Int
    private val indexHasEmbeddedPicture: Int

    constructor(cursor: Cursor) : super(cursor) {
        indexId = cursor.getColumnIndexOrThrow(PodDBAdapter.SELECT_KEY_MEDIA_ID)
        indexLastPlayedTimeHistory = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_LAST_PLAYED_TIME_HISTORY)
        indexDuration = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_DURATION)
        indexPosition = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_POSITION)
        indexSize = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_SIZE)
        indexMimeType = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_MIME_TYPE)
        indexFileUrl = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_FILE_URL)
        indexDownloadUrl = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_DOWNLOAD_URL)
        indexDownloadDate = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_DOWNLOAD_DATE)
        indexPlayedDuration = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_PLAYED_DURATION)
        indexLastPlayedTimeStatistics = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_LAST_PLAYED_TIME_STATISTICS)
        indexHasEmbeddedPicture = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_HAS_EMBEDDED_PICTURE)
    }

    /**
     * Create a [FeedMedia] instance from a database row (cursor).
     */
    fun getFeedMedia(): FeedMedia {
        val lastPlayedTimeHistoryTime = getLong(indexLastPlayedTimeHistory)
        val lastPlayedTimeHistory = if (lastPlayedTimeHistoryTime > 0) Date(lastPlayedTimeHistoryTime) else null

        val hasEmbeddedPicture: Boolean?
        when (getInt(indexHasEmbeddedPicture)) {
            1 -> hasEmbeddedPicture = java.lang.Boolean.TRUE
            0 -> hasEmbeddedPicture = java.lang.Boolean.FALSE
            else -> hasEmbeddedPicture = null
        }

        return FeedMedia(
                getLong(indexId),
                null,
                getInt(indexDuration),
                getInt(indexPosition),
                getLong(indexSize),
                getString(indexMimeType),
                getString(indexFileUrl),
                getString(indexDownloadUrl),
                getLong(indexDownloadDate),
                lastPlayedTimeHistory,
                getInt(indexPlayedDuration),
                hasEmbeddedPicture,
                getLong(indexLastPlayedTimeStatistics)
        )
    }
}
