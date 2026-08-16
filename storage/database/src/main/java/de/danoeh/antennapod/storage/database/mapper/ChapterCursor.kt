package de.danoeh.antennapod.storage.database.mapper

import android.database.Cursor
import android.database.CursorWrapper
import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.storage.database.PodDBAdapter

/**
 * Converts a [Cursor] to a [Chapter] object.
 */
class ChapterCursor : CursorWrapper {
    private val indexId: Int
    private val indexTitle: Int
    private val indexStart: Int
    private val indexLink: Int
    private val indexImage: Int

    constructor(cursor: Cursor) : super(cursor) {
        indexId = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_ID)
        indexTitle = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_TITLE)
        indexStart = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_START)
        indexLink = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_LINK)
        indexImage = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_IMAGE_URL)
    }

    /**
     * Create a [Chapter] instance from a database row (cursor).
     */
    fun getChapter(): Chapter {
        val chapter = Chapter(
                getLong(indexStart),
                getString(indexTitle),
                getString(indexLink),
                getString(indexImage))
        chapter.setId(getLong(indexId))
        return chapter
    }
}
