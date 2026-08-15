package de.danoeh.antennapod.storage.database.mapper

import android.database.Cursor

import android.database.CursorWrapper

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.storage.database.PodDBAdapter

/**
 * Converts a [Cursor] to a [Feed] object.
 */
class FeedCursor : CursorWrapper {
    private val preferencesCursor: FeedPreferencesCursor
    private val indexId: Int
    private val indexLastUpdate: Int
    private val indexTitle: Int
    private val indexCustomTitle: Int
    private val indexLink: Int
    private val indexDescription: Int
    private val indexPaymentLink: Int
    private val indexAuthor: Int
    private val indexLanguage: Int
    private val indexType: Int
    private val indexFeedIdentifier: Int
    private val indexFileUrl: Int
    private val indexDownloadUrl: Int
    private val indexLastRefreshed: Int
    private val indexIsPaged: Int
    private val indexNextPageLink: Int
    private val indexHide: Int
    private val indexSortOrder: Int
    private val indexLastUpdateFailed: Int
    private val indexImageUrl: Int
    private val indexState: Int

    constructor(cursor: Cursor) : super(FeedPreferencesCursor(cursor)) {
        preferencesCursor = getWrappedCursor() as FeedPreferencesCursor
        indexId = cursor.getColumnIndexOrThrow(PodDBAdapter.SELECT_KEY_FEED_ID)
        indexLastUpdate = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_LASTUPDATE)
        indexTitle = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_TITLE)
        indexCustomTitle = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_CUSTOM_TITLE)
        indexLink = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_LINK)
        indexDescription = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_DESCRIPTION)
        indexPaymentLink = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_PAYMENT_LINK)
        indexAuthor = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_AUTHOR)
        indexLanguage = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_LANGUAGE)
        indexType = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_TYPE)
        indexFeedIdentifier = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_FEED_IDENTIFIER)
        indexFileUrl = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_FILE_URL)
        indexDownloadUrl = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_DOWNLOAD_URL)
        indexLastRefreshed = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_LAST_REFRESH_ATTEMPT)
        indexIsPaged = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_IS_PAGED)
        indexNextPageLink = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_NEXT_PAGE_LINK)
        indexHide = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_HIDE)
        indexSortOrder = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_SORT_ORDER)
        indexLastUpdateFailed = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_LAST_UPDATE_FAILED)
        indexImageUrl = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_IMAGE_URL)
        indexState = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_STATE)
    }

    /**
     * Create a [Feed] instance from the current database row.
     */
    fun getFeed(): Feed {
        val feed = Feed(
                getLong(indexId),
                getString(indexLastUpdate),
                getString(indexTitle),
                getString(indexCustomTitle),
                getString(indexLink),
                getString(indexDescription),
                getString(indexPaymentLink),
                getString(indexAuthor),
                getString(indexLanguage),
                getString(indexType),
                getString(indexFeedIdentifier),
                getString(indexImageUrl),
                getString(indexFileUrl),
                getString(indexDownloadUrl),
                getLong(indexLastRefreshed),
                getInt(indexIsPaged) > 0,
                getString(indexNextPageLink),
                getString(indexHide),
                SortOrder.fromCodeString(getString(indexSortOrder)),
                getInt(indexLastUpdateFailed) > 0,
                getInt(indexState))
        feed.setPreferences(preferencesCursor.getFeedPreferences())
        return feed
    }
}
