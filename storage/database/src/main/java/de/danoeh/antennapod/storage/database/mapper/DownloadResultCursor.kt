package de.danoeh.antennapod.storage.database.mapper

import android.database.Cursor
import android.database.CursorWrapper
import de.danoeh.antennapod.model.download.DownloadError
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.storage.database.PodDBAdapter

import java.util.Date

/**
 * Converts a [Cursor] to a [DownloadResult] object.
 */
class DownloadResultCursor : CursorWrapper {
    private val indexId: Int
    private val indexTitle: Int
    private val indexFeedFile: Int
    private val indexFileFileType: Int
    private val indexSuccessful: Int
    private val indexReason: Int
    private val indexCompletionDate: Int
    private val indexReasonDetailed: Int

    constructor(cursor: Cursor) : super(cursor) {
        indexId = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_ID)
        indexTitle = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_DOWNLOADSTATUS_TITLE)
        indexFeedFile = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_FEEDFILE)
        indexFileFileType = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_FEEDFILETYPE)
        indexSuccessful = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_SUCCESSFUL)
        indexReason = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_REASON)
        indexCompletionDate = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_COMPLETION_DATE)
        indexReasonDetailed = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_REASON_DETAILED)
    }

    /**
     * Create a [DownloadResult] instance from a database row (cursor).
     */
    fun getDownloadResult(): DownloadResult {
        return DownloadResult(
                getLong(indexId),
                getString(indexTitle),
                getLong(indexFeedFile),
                getInt(indexFileFileType),
                getInt(indexSuccessful) > 0,
                DownloadError.fromCode(getInt(indexReason)),
                Date(getLong(indexCompletionDate)),
                getString(indexReasonDetailed))
    }
}
