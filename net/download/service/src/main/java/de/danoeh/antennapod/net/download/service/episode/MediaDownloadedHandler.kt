package de.danoeh.antennapod.net.download.service.episode

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log

import de.danoeh.antennapod.model.MediaMetadataRetrieverCompat
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.ui.chapters.ChapterUtils
import org.apache.commons.lang3.StringUtils

import java.io.File
import java.io.InterruptedIOException
import java.util.concurrent.ExecutionException

import de.danoeh.antennapod.model.download.DownloadRequest
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.model.download.DownloadError
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction
import de.danoeh.antennapod.ui.transcript.TranscriptUtils

/**
 * Handles a completed media download.
 */
class MediaDownloadedHandler : Runnable {
    companion object {
        private const val TAG = "MediaDownloadedHandler"
    }

    private val request: DownloadRequest
    private val context: Context
    private var updatedStatus: DownloadResult

    constructor(context: Context, status: DownloadResult,
                request: DownloadRequest) {
        this.request = request
        this.context = context
        this.updatedStatus = status
    }

    override fun run() {
        val media = DBReader.getFeedMedia(request.getFeedfileId())
        if (media == null) {
            Log.e(TAG, "Could not find downloaded media object in database")
            return
        }
        // media.setDownloaded modifies played state
        val broadcastUnreadStateUpdate = media.getItem() != null && media.getItem()!!.isNew()
        media.setDownloaded(true, System.currentTimeMillis())
        media.setLocalFileUrl(request.getDestination())
        media.setSize(File(request.getDestination()).length())
        media.checkEmbeddedPicture() // enforce check

        try {
            // Cache chapters if file has them
            if (media.getItem() != null && !media.getItem()!!.hasChapters()) {
                media.setChapters(ChapterUtils.loadChaptersFromMediaFile(media, context))
            }
            if (media.getItem() != null && media.getItem()!!.getPodcastIndexChapterUrl() != null) {
                ChapterUtils.loadChaptersFromUrl(media.getItem()!!.getPodcastIndexChapterUrl()!!, false)
            }
            val item = media.getItem()
            if (item != null && item.getTranscriptUrl() != null) {
                val transcript = TranscriptUtils.loadTranscriptFromUrl(item.getTranscriptUrl()!!, true)
                if (!StringUtils.isEmpty(transcript)) {
                    TranscriptUtils.storeTranscript(media, transcript)
                }
            }
        } catch (ignore: InterruptedIOException) {
            // Ignore
        }

        // Get duration
        var durationStr: String? = null
        try {
            MediaMetadataRetrieverCompat().use { mmr ->
                mmr.setDataSource(media.getLocalFileUrl())
                durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                media.setDuration(Integer.parseInt(durationStr))
                Log.d(TAG, "Duration of file is " + media.getDuration())
            }
        } catch (e: NumberFormatException) {
            Log.d(TAG, "Invalid file duration: " + durationStr)
        } catch (e: Exception) {
            Log.e(TAG, "Get duration failed", e)
        }

        val item = media.getItem()

        try {
            DBWriter.setFeedMedia(media)!!.get()

            // we've received the media, we don't want to autodownload it again
            if (item != null) {
                item.disableAutoDownload()
                // setFeedItem() signals (via EventBus) that the item has been updated,
                // so we do it after the enclosing media has been updated above,
                // to ensure subscribers will get the updated FeedMedia as well
                DBWriter.setFeedItem(item, broadcastUnreadStateUpdate)!!.get()
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "MediaHandlerThread was interrupted")
        } catch (e: ExecutionException) {
            Log.e(TAG, "ExecutionException in MediaHandlerThread: " + e.message)
            updatedStatus = DownloadResult(media.getEpisodeTitle()!!, media.getId(),
                    FeedMedia.FEEDFILETYPE_FEEDMEDIA, false, DownloadError.ERROR_DB_ACCESS_ERROR, e.message)
        }

        if (item != null && item.getFeed()!!.getState() != Feed.STATE_NOT_SUBSCRIBED) {
            SynchronizationQueue.getInstance()!!.enqueueEpisodeAction(
                    EpisodeAction.Builder(item, EpisodeAction.DOWNLOAD)
                        .currentTimestamp()
                        .build())
        }
    }

    fun getUpdatedStatus(): DownloadResult {
        return updatedStatus
    }
}
