package de.danoeh.antennapod.net.download.service.feed

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import de.danoeh.antennapod.net.download.service.R
import de.danoeh.antennapod.net.download.service.feed.local.LocalFeedUpdater
import de.danoeh.antennapod.net.download.service.feed.remote.DefaultDownloaderFactory
import de.danoeh.antennapod.net.download.service.feed.remote.Downloader
import de.danoeh.antennapod.net.download.service.feed.remote.FeedParserTask
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager
import de.danoeh.antennapod.net.download.serviceinterface.DownloadRequestCreator
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.net.common.NetworkUtils
import de.danoeh.antennapod.model.download.DownloadError
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.download.DownloadRequest

import de.danoeh.antennapod.net.download.serviceinterface.DownloadRequestBuilder
import de.danoeh.antennapod.parser.feed.FeedHandlerResult
import de.danoeh.antennapod.storage.database.NonSubscribedFeedsCleaner
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.notifications.NotificationUtils
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FeedUpdateWorker : Worker {
    companion object {
        private const val TAG = "FeedUpdateWorker"
        private val JOB_SCHEDULE_TIME_VARIATION = TimeUnit.MINUTES.toMillis(15)
    }

    private val newEpisodesNotification: NewEpisodesNotification
    private val notificationManager: NotificationManagerCompat

    constructor(context: Context, params: WorkerParameters) : super(context, params) {
        newEpisodesNotification = NewEpisodesNotification()
        notificationManager = NotificationManagerCompat.from(context)
    }

    override fun doWork(): Result {
        newEpisodesNotification.loadCountersBeforeRefresh()

        val toUpdate: ArrayList<Feed>
        val feedId = getInputData().getLong(FeedUpdateManagerImpl.EXTRA_FEED_ID, -1L)
        var allAreLocal = true
        var force = false
        val isAutomaticRefresh = !getInputData().getBoolean(FeedUpdateManagerImpl.EXTRA_MANUAL, false)
        val isAutomaticRefreshEnabled = !UserPreferences.isAutoUpdateDisabled()
        if (feedId == -1L) { // Update all
            toUpdate = ArrayList(DBReader.getFeedList())
            val itr = toUpdate.iterator()
            while (itr.hasNext()) {
                val feed = itr.next()
                if (!feed.getPreferences()!!.getKeepUpdated() || feed.getState() != Feed.STATE_SUBSCRIBED) {
                    itr.remove()
                    continue
                }
                if (isAutomaticRefresh && isAutomaticRefreshEnabled
                        && feed.getLastRefreshAttempt() > System.currentTimeMillis() - JOB_SCHEDULE_TIME_VARIATION
                            - TimeUnit.MINUTES.toMillis(UserPreferences.getUpdateInterval())) {
                    // Recently updated, no need to automatically check again
                    itr.remove()
                    continue
                }
                if (!feed.isLocalFeed()) {
                    allAreLocal = false
                }
            }
            Collections.shuffle(toUpdate) // If the worker gets cancelled early, every feed has a chance to be updated
        } else {
            val feed = DBReader.getFeed(feedId, false, 0, Int.MAX_VALUE)
            if (feed == null) {
                return Result.success()
            }
            if (!feed.isLocalFeed()) {
                allAreLocal = false
            }
            toUpdate = ArrayList()
            toUpdate.add(feed) // Needs to be updatable, so no singletonList
            force = true
        }

        if (!getInputData().getBoolean(FeedUpdateManagerImpl.EXTRA_EVEN_ON_MOBILE, false) && !allAreLocal) {
            if (!NetworkUtils.networkAvailable() || !NetworkUtils.isFeedRefreshAllowed()) {
                Log.d(TAG, "Blocking automatic update")
                return Result.retry()
            }
        }
        refreshFeeds(toUpdate, force)

        NonSubscribedFeedsCleaner.deleteOldNonSubscribedFeeds(getApplicationContext())
        AutoDownloadManager.getInstance()!!.autodownloadUndownloadedItems(getApplicationContext())
        notificationManager.cancel(R.id.notification_updating_feeds)
        SynchronizationQueue.getInstance()!!.syncImmediately()
        return Result.success()
    }

    private fun createNotification(toUpdate: List<Feed>?): Notification {
        val context = getApplicationContext()
        var contentText = ""
        val bigText = StringBuilder()
        if (toUpdate != null) {
            contentText = context.getResources().getQuantityString(R.plurals.downloads_left,
                    toUpdate.size, toUpdate.size)
            for (i in toUpdate.indices) {
                bigText.append("• ").append(toUpdate[i].getTitle())
                if (i != toUpdate.size - 1) {
                    bigText.append("\n")
                }
            }
        }
        return NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID_REFRESHING)
                .setContentTitle(context.getString(R.string.download_notification_title_feeds))
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setSmallIcon(R.drawable.ic_notification_sync)
                .setOngoing(true)
                .addAction(R.drawable.ic_notification_cancel, context.getString(R.string.cancel_label),
                        WorkManager.getInstance(context).createCancelPendingIntent(getId()))
                .build()
    }

    private fun updateNotification(toUpdate: List<Feed>) {
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(R.id.notification_updating_feeds, createNotification(toUpdate))
        }
    }

    override fun getForegroundInfoAsync(): ListenableFuture<ForegroundInfo> {
        return Futures.immediateFuture(ForegroundInfo(R.id.notification_updating_feeds, createNotification(null)))
    }

    private fun refreshFeeds(toUpdate: List<Feed>, force: Boolean) {
        val notificationRemainingFeeds = ArrayList(toUpdate)
        updateNotification(notificationRemainingFeeds)
        val executor = Executors.newFixedThreadPool(4)
        for (feed in toUpdate) {
            executor.submit {
                if (isStopped()) {
                    return@submit
                }
                try {
                    val savedFeed: Feed?
                    if (feed.isLocalFeed()) {
                        savedFeed = LocalFeedUpdater.updateFeed(feed, getApplicationContext(), null)
                    } else {
                        savedFeed = refreshFeed(feed, force)
                    }
                    if (savedFeed != null) {
                        newEpisodesNotification.showIfNeeded(getApplicationContext(), savedFeed)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    DBWriter.setFeedLastUpdateFailed(feed.getId(), true)
                    val status = DownloadResult(feed.getTitle()!!,
                            feed.getId(), Feed.FEEDFILETYPE_FEED, false,
                            DownloadError.ERROR_IO_ERROR, e.message)
                    DBWriter.addDownloadStatus(status)
                }
                synchronized(notificationRemainingFeeds) {
                    notificationRemainingFeeds.remove(feed)
                    if (!notificationRemainingFeeds.isEmpty()) {
                        updateNotification(notificationRemainingFeeds)
                    }
                }
            }
        }
        executor.shutdown()
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
        } catch (e: InterruptedException) {
            //~300 years have elapsed
        }
    }

    fun refreshFeed(feed: Feed, force: Boolean): Feed? {
        val nextPage = getInputData().getBoolean(FeedUpdateManagerImpl.EXTRA_NEXT_PAGE, false)
                && feed.getNextPageLink() != null
        if (nextPage) {
            feed.setPageNr(feed.getPageNr() + 1)
        }
        val builder = DownloadRequestCreator.create(feed)
        builder.setForce(force || feed.hasLastUpdateFailed())
        if (nextPage) {
            builder.setSource(feed.getNextPageLink()!!)
        }
        val request = builder.build()

        val downloader = DefaultDownloaderFactory().create(request)
        if (downloader == null) {
            throw Exception("Unable to create downloader")
        }

        downloader.call()

        if (!downloader.result.isSuccessful()) {
            if (downloader.cancelled || downloader.result.getReason() == DownloadError.ERROR_DOWNLOAD_CANCELLED) {
                return null
            }
            DBWriter.setFeedLastUpdateFailed(request.getFeedfileId(), true)
            DBWriter.addDownloadStatus(downloader.result)
            return null
        }

        val parserTask = FeedParserTask(request)
        val feedHandlerResult = parserTask.call()
        if (!parserTask.isSuccessful()) {
            DBWriter.setFeedLastUpdateFailed(request.getFeedfileId(), true)
            DBWriter.addDownloadStatus(parserTask.getDownloadStatus())
            return null
        }
        feedHandlerResult!!.feed.setLastRefreshAttempt(System.currentTimeMillis())
        val savedFeed = FeedDatabaseWriter.updateFeed(getApplicationContext(), feedHandlerResult.feed, false)

        if (request.getFeedfileId() == 0L) {
            return savedFeed // No download logs for new subscriptions
        }
        // we create a 'successful' download log if the feed's last refresh failed
        val log = DBReader.getFeedDownloadLog(request.getFeedfileId(), 1)
        if (!log.isEmpty() && !log[0].isSuccessful()) {
            DBWriter.addDownloadStatus(parserTask.getDownloadStatus())
        }
        if (downloader.permanentRedirectUrl != null) {
            DBWriter.updateFeedDownloadURL(request.getSource(), downloader.permanentRedirectUrl!!)
        } else if (feedHandlerResult.redirectUrl != null
                && feedHandlerResult.redirectUrl != request.getSource()) {
            DBWriter.updateFeedDownloadURL(request.getSource(), feedHandlerResult.redirectUrl!!)
        }
        return savedFeed
    }
}
