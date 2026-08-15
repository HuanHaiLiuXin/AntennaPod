package de.danoeh.antennapod.net.download.service.feed.remote

import android.text.TextUtils
import android.util.Log
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting
import de.danoeh.antennapod.model.download.DownloadRequest
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.parser.feed.FeedHandler
import de.danoeh.antennapod.parser.feed.FeedHandlerResult
import de.danoeh.antennapod.parser.feed.UnsupportedFeedtypeException
import de.danoeh.antennapod.model.download.DownloadError
import org.xml.sax.SAXException

import javax.xml.parsers.ParserConfigurationException
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.concurrent.Callable

class FeedParserTask : Callable<FeedHandlerResult> {
    companion object {
        private const val TAG = "FeedParserTask"
    }

    private val request: DownloadRequest
    private var downloadResult: DownloadResult
    private var successful = true

    constructor(request: DownloadRequest) {
        this.request = request
        downloadResult = DownloadResult(
        0L, request.getTitle()!!, 0L, request.getFeedfileType(), false,
                DownloadError.ERROR_REQUEST_ERROR, Date(),
                "Unknown error: Status not set")
    }

    override fun call(): FeedHandlerResult? {
        val feed = Feed(request.getSource(), request.getLastModified())
        feed.setLocalFileUrl(request.getDestination())
        feed.setId(request.getFeedfileId())
        feed.setPreferences(FeedPreferences(0L, FeedPreferences.AutoDownloadSetting.GLOBAL,
                FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                FeedPreferences.NewEpisodesAction.GLOBAL, request.getUsername(), request.getPassword()))
        feed.setPageNr(request.getArguments()!!.getInt(DownloadRequest.REQUEST_ARG_PAGE_NR, 0))

        var reason: DownloadError? = null
        var reasonDetailed: String? = null
        val feedHandler = FeedHandler()

        var result: FeedHandlerResult? = null
        try {
            result = feedHandler.parseFeed(feed)
            Log.d(TAG, feed.getTitle() + " parsed")
            checkFeedData(feed)
            if (TextUtils.isEmpty(feed.getImageUrl())) {
                feed.setImageUrl(Feed.PREFIX_GENERATIVE_COVER + feed.getDownloadUrl())
            }
        } catch (e: SAXException) {
            successful = false
            e.printStackTrace()
            reason = DownloadError.ERROR_PARSER_EXCEPTION
            reasonDetailed = e.message
        } catch (e: IOException) {
            successful = false
            e.printStackTrace()
            reason = DownloadError.ERROR_PARSER_EXCEPTION
            reasonDetailed = e.message
        } catch (e: ParserConfigurationException) {
            successful = false
            e.printStackTrace()
            reason = DownloadError.ERROR_PARSER_EXCEPTION
            reasonDetailed = e.message
        } catch (e: UnsupportedFeedtypeException) {
            e.printStackTrace()
            successful = false
            reason = DownloadError.ERROR_UNSUPPORTED_TYPE
            if ("html".equals(e.getRootElement(), true)) {
                reason = DownloadError.ERROR_UNSUPPORTED_TYPE_HTML
            }
            reasonDetailed = e.message
        } catch (e: InvalidFeedException) {
            e.printStackTrace()
            successful = false
            reason = DownloadError.ERROR_PARSER_EXCEPTION
            reasonDetailed = e.message
        } finally {
            val feedFile = File(request.getDestination())
            if (feedFile.exists()) {
                val deleted = feedFile.delete()
                Log.d(TAG, "Deletion of file '" + feedFile.getAbsolutePath() + "' "
                        + if (deleted) "successful" else "FAILED")
            }
        }

        if (successful) {
            downloadResult = DownloadResult(feed.getHumanReadableIdentifier()!!, feed.getId(),
                    Feed.FEEDFILETYPE_FEED, true, DownloadError.SUCCESS, reasonDetailed)
            return result
        } else {
            downloadResult = DownloadResult(feed.getHumanReadableIdentifier()!!, feed.getId(),
                    Feed.FEEDFILETYPE_FEED, false, reason, reasonDetailed)
            return null
        }
    }

    fun isSuccessful(): Boolean {
        return successful
    }

    /**
     * Checks if the feed was parsed correctly.
     */
    @Throws(InvalidFeedException::class)
    private fun checkFeedData(feed: Feed) {
        if (feed.getTitle() == null) {
            throw InvalidFeedException("Feed has no title")
        }
        checkFeedItems(feed)
    }

    @Throws(InvalidFeedException::class)
    private fun checkFeedItems(feed: Feed) {
        for (item in feed.getItems()!!) {
            if (item.getTitle() == null) {
                throw InvalidFeedException("Item has no title: " + item)
            }
        }
    }

    fun getDownloadStatus(): DownloadResult {
        return downloadResult
    }
}
