package de.danoeh.antennapod.net.download.service.feed

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import de.danoeh.antennapod.net.download.service.episode.EpisodeDownloadWorker
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.storage.preferences.UserPreferences
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers

import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class DownloadServiceInterfaceImpl : DownloadServiceInterface() {
    override fun downloadNow(context: Context, item: FeedItem, ignoreConstraints: Boolean) {
        val workRequest = getRequest(context, item)
        workRequest.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        if (ignoreConstraints) {
            workRequest.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        } else {
            workRequest.setConstraints(getConstraints())
        }
        WorkManager.getInstance(context).enqueueUniqueWork(item.getMedia()!!.getDownloadUrl()!!,
                ExistingWorkPolicy.KEEP, workRequest.build())
    }

    override fun download(context: Context, item: FeedItem) {
        if (item.isDownloaded()) {
            return
        }
        val workRequest = getRequest(context, item)
        workRequest.setConstraints(getConstraints())
        WorkManager.getInstance(context).enqueueUniqueWork(item.getMedia()!!.getDownloadUrl()!!,
                ExistingWorkPolicy.KEEP, workRequest.build())
    }

    companion object {
        private fun getRequest(context: Context, item: FeedItem): OneTimeWorkRequest.Builder {
            val workRequest = OneTimeWorkRequest.Builder(EpisodeDownloadWorker::class.java)
                    .setInitialDelay(0L, TimeUnit.MILLISECONDS)
                    .addTag(DownloadServiceInterface.WORK_TAG)
                    .addTag(DownloadServiceInterface.WORK_TAG_EPISODE_URL + item.getMedia()!!.getDownloadUrl()!!)
            if (!item.isTagged(FeedItem.TAG_QUEUE) && UserPreferences.enqueueDownloadedEpisodes()) {
                DBWriter.addQueueItem(context, item)
                workRequest.addTag(DownloadServiceInterface.WORK_DATA_WAS_QUEUED)
            }
            workRequest.setInputData(Data.Builder()
                    .putLong(DownloadServiceInterface.WORK_DATA_MEDIA_ID, item.getMedia()!!.getId()).build())
            return workRequest
        }

        private fun getConstraints(): Constraints {
            val constraints = Constraints.Builder()
            if (UserPreferences.isAllowMobileEpisodeDownload()) {
                constraints.setRequiredNetworkType(NetworkType.CONNECTED)
            } else {
                constraints.setRequiredNetworkType(NetworkType.UNMETERED)
            }
            return constraints.build()
        }
    }

    override fun cancel(context: Context, media: FeedMedia) {
        // This needs to be done here, not in the worker. Reason: The worker might or might not be running.
        if (media.fileExists()) {
            DBWriter.deleteFeedMediaOfItem(context, media) // Remove partially downloaded file
        }
        val tag = DownloadServiceInterface.WORK_TAG_EPISODE_URL + media.getDownloadUrl()
        val future: Future<List<WorkInfo>> = WorkManager.getInstance(context).getWorkInfosByTag(tag)
        Observable.fromFuture(future)
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.computation())
                .subscribe(
                    { workInfos ->
                        for (info in workInfos) {
                            if (info.tags.contains(DownloadServiceInterface.WORK_DATA_WAS_QUEUED)) {
                                DBWriter.removeQueueItem(context, false, media.getItem()!!)
                            }
                        }
                        WorkManager.getInstance(context).cancelAllWorkByTag(tag)
                    }, { exception ->
                        WorkManager.getInstance(context).cancelAllWorkByTag(tag)
                        exception.printStackTrace()
                    })
    }

    override fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(DownloadServiceInterface.WORK_TAG)
    }

    override fun getNumberOfActiveDownloads(context: Context): Int {
        try {
            val workInfos = WorkManager.getInstance(context)
                    .getWorkInfosByTag(DownloadServiceInterface.WORK_TAG).get()
            var count = 0
            for (info in workInfos) {
                if (info.state == WorkInfo.State.RUNNING
                        || info.state == WorkInfo.State.ENQUEUED
                        || info.state == WorkInfo.State.BLOCKED) {
                    count++
                }
            }
            return count
        } catch (e: ExecutionException) {
            return 0
        } catch (e: InterruptedException) {
            return 0
        }
    }
}
