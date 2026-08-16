package de.danoeh.antennapod.net.download.service.episode

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import de.danoeh.antennapod.net.download.service.R
import de.danoeh.antennapod.net.download.service.feed.remote.DefaultDownloaderFactory
import de.danoeh.antennapod.net.download.service.feed.remote.Downloader
import de.danoeh.antennapod.net.download.serviceinterface.DownloadRequestCreator
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.model.download.DownloadError
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.download.DownloadRequest
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.notifications.NotificationUtils
import org.apache.commons.io.FileUtils
import org.greenrobot.eventbus.EventBus

import java.io.File
import java.io.IOException
import java.util.HashMap
import java.util.Locale
import java.util.concurrent.ExecutionException

class EpisodeDownloadWorker : Worker {
    companion object {
        private const val TAG = "EpisodeDownloadWorker"
        private val notificationProgress: HashMap<String, Int> = HashMap()
    }

    private var downloader: Downloader? = null

    constructor(context: Context, params: WorkerParameters) : super(context, params) {
    }

    override fun doWork(): Result {
        val mediaId = getInputData().getLong(DownloadServiceInterface.WORK_DATA_MEDIA_ID, 0L)
        val media = DBReader.getFeedMedia(mediaId)
        if (media == null) {
            return Result.failure()
        }

        val request = DownloadRequestCreator.create(media).build()
        val progressUpdaterThread = object : Thread() {
            override fun run() {
                while (true) {
                    try {
                        synchronized(notificationProgress) {
                            if (isInterrupted()) {
                                return
                            }
                            notificationProgress.put(media.getEpisodeTitle()!!, request.getProgressPercent())
                        }
                        setProgressAsync(
                                Data.Builder()
                                    .putInt(DownloadServiceInterface.WORK_DATA_PROGRESS, request.getProgressPercent())
                                    .build())
                                .get()
                        val nm = getApplicationContext()
                                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        if (ContextCompat.checkSelfPermission(getApplicationContext(),
                                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                            nm.notify(R.id.notification_downloading, generateProgressNotification())
                        }
                        Thread.sleep(1000)
                    } catch (e: InterruptedException) {
                        return
                    } catch (e: ExecutionException) {
                        return
                    }
                }
            }
        }
        progressUpdaterThread.start()
        var result: Result
        try {
            result = performDownload(media, request)
        } catch (e: Exception) {
            e.printStackTrace()
            result = Result.failure()
        }
        if (result == Result.failure() && downloader != null) {
            FileUtils.deleteQuietly(File(downloader!!.getDownloadRequest().getDestination()))
        }
        progressUpdaterThread.interrupt()
        try {
            progressUpdaterThread.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        synchronized(notificationProgress) {
            notificationProgress.remove(media.getEpisodeTitle())
            if (notificationProgress.isEmpty()) {
                val nm = getApplicationContext()
                        .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(R.id.notification_downloading)
            }
        }
        Log.d(TAG, "Worker for " + media.getDownloadUrl() + " returned.")
        return result
    }

    override fun onStopped() {
        super.onStopped()
        if (downloader != null) {
            downloader!!.cancel()
        }
    }

    override fun getForegroundInfoAsync(): ListenableFuture<ForegroundInfo> {
        return Futures.immediateFuture(
                ForegroundInfo(R.id.notification_downloading, generateProgressNotification()))
    }

    private fun performDownload(media: FeedMedia, request: DownloadRequest): Result {
        val dest = File(request.getDestination())
        if (!dest.exists()) {
            try {
                dest.createNewFile()
            } catch (e: IOException) {
                Log.e(TAG, "Unable to create file")
            }
        }

        if (dest.exists()) {
            media.setLocalFileUrl(request.getDestination())
            try {
                DBWriter.setMediaDownloadInformation(media)!!.get()
            } catch (e: Exception) {
                Log.e(TAG, "ExecutionException in writeFileUrl: " + e.message)
            }
        }

        downloader = DefaultDownloaderFactory().create(request)
        if (downloader == null) {
            Log.d(TAG, "Unable to create downloader")
            return Result.failure()
        }

        val wifiManager = getApplicationContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
        var wifiLock: WifiManager.WifiLock? = null
        if (wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(TAG)
            wifiLock.acquire()
        }

        DownloadAnnouncer.announceStart(getApplicationContext(), request.getTitle())
        try {
            downloader!!.call()
        } catch (e: Exception) {
            DBWriter.addDownloadStatus(downloader!!.result)
            sendErrorNotification(request.getTitle())
            return Result.failure()
        } finally {
            if (wifiLock != null) {
                wifiLock.release()
            }
        }

        if (downloader!!.cancelled) {
            // This also happens when the worker was preempted, not just when the user cancelled it
            return Result.success()
        }

        val status = downloader!!.result
        if (status.isSuccessful()) {
            val handler = MediaDownloadedHandler(
                    getApplicationContext(), downloader!!.result, request)
            handler.run()
            DBWriter.addDownloadStatus(handler.getUpdatedStatus())
            DownloadAnnouncer.announceCompleted(getApplicationContext(), request.getTitle())
            return Result.success()
        }

        if (status.getReason() == DownloadError.ERROR_HTTP_DATA_ERROR
                && Integer.parseInt(status.getReasonDetailed()!!) == 416) {
            Log.d(TAG, "Requested invalid range, restarting download from the beginning")
            FileUtils.deleteQuietly(File(downloader!!.getDownloadRequest().getDestination()))
            sendMessage(request.getTitle(), false)
            return retry3times()
        }

        Log.e(TAG, "Download failed")
        DBWriter.addDownloadStatus(status)
        if (status.getReason() == DownloadError.ERROR_FORBIDDEN
                || status.getReason() == DownloadError.ERROR_NOT_FOUND
                || status.getReason() == DownloadError.ERROR_UNAUTHORIZED
                || status.getReason() == DownloadError.ERROR_IO_BLOCKED) {
            // Fail fast, these are probably unrecoverable
            sendErrorNotification(request.getTitle())
            return Result.failure()
        }
        sendMessage(request.getTitle(), false)
        return retry3times()
    }

    private fun retry3times(): Result {
        if (isLastRunAttempt()) {
            sendErrorNotification(downloader!!.getDownloadRequest().getTitle())
            return Result.failure()
        } else {
            return Result.retry()
        }
    }

    private fun isLastRunAttempt(): Boolean {
        return getRunAttemptCount() >= 2
    }

    private fun sendMessage(episodeTitle: String, isImmediateFail: Boolean) {
        val retrying = !isLastRunAttempt() && !isImmediateFail
        var episodeTitle = episodeTitle
        if (episodeTitle.length > 20) {
            episodeTitle = episodeTitle.substring(0, 19) + "…"
        }
        EventBus.getDefault().post(MessageEvent(
                    getApplicationContext().getString(
                            if (retrying) R.string.download_error_retrying else R.string.download_error_not_retrying,
                            episodeTitle), { ctx -> MainActivityStarter(ctx).withDownloadLogsOpen().start() },
                getApplicationContext().getString(R.string.download_error_details)))
    }

    private fun getDownloadLogsIntent(context: Context): PendingIntent {
        val intent = MainActivityStarter(context).withDownloadLogsOpen().getIntent()
        return PendingIntent.getActivity(context, R.id.pending_intent_download_service_report, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun getDownloadsIntent(context: Context): PendingIntent {
        val intent = MainActivityStarter(context).withFragmentLoaded("DownloadsFragment").getIntent()
        return PendingIntent.getActivity(context, R.id.pending_intent_download_service_notification, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun sendErrorNotification(title: String) {
        if (EventBus.getDefault().hasSubscriberForEvent(MessageEvent::class.java)) {
            sendMessage(title, false)
            return
        }

        val builder = NotificationCompat.Builder(getApplicationContext(),
                NotificationUtils.CHANNEL_ID_DOWNLOAD_ERROR)
        builder.setTicker(getApplicationContext().getString(R.string.episode_download_failed))
                .setContentTitle(getApplicationContext().getString(R.string.episode_download_failed))
                .setContentText(getApplicationContext().getString(R.string.download_error_tap_for_details))
                .setSmallIcon(R.drawable.ic_notification_sync_error)
                .setContentIntent(getDownloadLogsIntent(getApplicationContext()))
                .setAutoCancel(true)
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        val nm = getApplicationContext()
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            nm.notify(R.id.notification_download_report, builder.build())
        }
    }

    private fun generateProgressNotification(): Notification {
        val bigTextB = StringBuilder()
        val progressCopy: HashMap<String, Int>
        synchronized(notificationProgress) {
            progressCopy = HashMap(notificationProgress)
        }
        for (entry in progressCopy.entries) {
            bigTextB.append(java.lang.String.format(Locale.getDefault(), "%s (%d%%)\n", entry.key, entry.value))
        }
        val bigText = bigTextB.toString().trim()
        val contentText: String
        if (progressCopy.size == 1) {
            contentText = bigText
        } else {
            contentText = getApplicationContext().getResources().getQuantityString(R.plurals.downloads_left,
                    progressCopy.size, progressCopy.size)
        }
        val builder = NotificationCompat.Builder(getApplicationContext(),
                NotificationUtils.CHANNEL_ID_DOWNLOADING)
        builder.setTicker(getApplicationContext().getString(R.string.download_notification_title_episodes))
                .setContentTitle(getApplicationContext().getString(R.string.download_notification_title_episodes))
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setContentIntent(getDownloadsIntent(getApplicationContext()))
                .setAutoCancel(false)
                .setOngoing(true)
                .setWhen(0)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setSmallIcon(R.drawable.ic_notification_sync)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        return builder.build()
    }
}
