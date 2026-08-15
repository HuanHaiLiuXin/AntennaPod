package de.danoeh.antennapod.storage.database.mapper

import android.database.Cursor
import android.database.CursorWrapper
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.storage.database.PodDBAdapter

import java.util.Date

/**
 * Converts a [Cursor] to a [FeedItem] object.
 */
class FeedItemCursor : CursorWrapper {
    private val feedMediaCursor: FeedMediaCursor
    private val indexId: Int
    private val indexTitle: Int
    private val indexLink: Int
    private val indexPubDate: Int
    private val indexPaymentLink: Int
    private val indexFeedId: Int
    private val indexHasChapters: Int
    private val indexRead: Int
    private val indexItemIdentifier: Int
    private val indexAutoDownload: Int
    private val indexImageUrl: Int
    private val indexPodcastIndexChapterUrl: Int
    private val indexSocialInteractUrl: Int
    private val indexMediaId: Int
    private val indexPodcastIndexTranscriptType: Int
    private val indexPodcastIndexTranscriptUrl: Int
    private val indexIsFavorite: Int
    private val indexIsInQueue: Int

    constructor(cursor: Cursor) : super(FeedMediaCursor(cursor)) {
        feedMediaCursor = getWrappedCursor() as FeedMediaCursor
        indexId = cursor.getColumnIndexOrThrow(PodDBAdapter.SELECT_KEY_ITEM_ID)
        indexTitle = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_TITLE)
        indexLink = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_LINK)
        indexPubDate = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_PUBDATE)
        indexPaymentLink = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_PAYMENT_LINK)
        indexFeedId = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_FEED)
        indexHasChapters = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_HAS_CHAPTERS)
        indexRead = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_READ)
        indexItemIdentifier = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_ITEM_IDENTIFIER)
        indexAutoDownload = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_AUTO_DOWNLOAD_ENABLED)
        indexImageUrl = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_IMAGE_URL)
        indexPodcastIndexChapterUrl = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_PODCASTINDEX_CHAPTER_URL)
        indexMediaId = cursor.getColumnIndexOrThrow(PodDBAdapter.SELECT_KEY_MEDIA_ID)
        indexSocialInteractUrl = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_SOCIAL_INTERACT_URL)
        indexPodcastIndexTranscriptType = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_PODCASTINDEX_TRANSCRIPT_TYPE)
        indexPodcastIndexTranscriptUrl = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_PODCASTINDEX_TRANSCRIPT_URL)
        indexIsFavorite = cursor.getColumnIndexOrThrow(PodDBAdapter.SELECT_KEY_IS_FAVORITE)
        indexIsInQueue = cursor.getColumnIndexOrThrow(PodDBAdapter.SELECT_KEY_IS_IN_QUEUE)
    }

    /**
     * Create a [FeedItem] instance from a database row (cursor).
     */
    fun getFeedItem(): FeedItem {
        val item = FeedItem(
                getInt(indexId).toLong(),
                getString(indexTitle),
                getString(indexLink),
                Date(getLong(indexPubDate)),
                getString(indexPaymentLink),
                getLong(indexFeedId),
                getInt(indexHasChapters) > 0,
                getString(indexImageUrl),
                getInt(indexRead),
                getString(indexItemIdentifier),
                getLong(indexAutoDownload) > 0,
                getString(indexPodcastIndexChapterUrl),
                getString(indexPodcastIndexTranscriptType),
                getString(indexPodcastIndexTranscriptUrl),
                getString(indexSocialInteractUrl))
        if (!isNull(indexMediaId)) {
            item.setMedia(feedMediaCursor.getFeedMedia())
        }
        if (getInt(indexIsFavorite) > 0) {
            item.addTag(FeedItem.TAG_FAVORITE)
        }
        if (getInt(indexIsInQueue) > 0) {
            item.addTag(FeedItem.TAG_QUEUE)
        }
        return item
    }
}
