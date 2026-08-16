package de.danoeh.antennapod.net.sync.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import de.danoeh.antennapod.event.SyncServiceEvent
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.storage.preferences.UserPreferences
import org.greenrobot.eventbus.EventBus

import java.util.concurrent.TimeUnit

class SynchronizationQueueImpl : SynchronizationQueue {
    companion object {
        private const val WORK_ID_SYNC = "SyncServiceWorkId"

        private fun getWorkRequest(): OneTimeWorkRequest.Builder {
            val constraints = Constraints.Builder()
            if (UserPreferences.isAllowMobileSync()) {
                constraints.setRequiredNetworkType(NetworkType.CONNECTED)
            } else {
                constraints.setRequiredNetworkType(NetworkType.UNMETERED)
            }

            val builder = OneTimeWorkRequest.Builder(SyncService::class.java)
                    .setConstraints(constraints.build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)

            if (SyncService.isCurrentlyActive()) {
                // Debounce: don't start sync again immediately after it was finished.
                builder.setInitialDelay(2L, TimeUnit.MINUTES)
            } else {
                // Give it some time, so other possible actions can be queued.
                builder.setInitialDelay(20L, TimeUnit.SECONDS)
                EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_started))
            }
            return builder
        }
    }

    private val context: Context

    constructor(context: Context) {
        this.context = context
    }

    override fun sync() {
        val workRequest = getWorkRequest().build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_ID_SYNC, ExistingWorkPolicy.REPLACE, workRequest)
    }

    override fun syncIfNotSyncedRecently() {
        if (System.currentTimeMillis() - SynchronizationSettings.getLastSyncAttempt() > 1000 * 60 * 10) {
            sync()
        }
    }

    override fun syncImmediately() {
        val workRequest = getWorkRequest()
                .setInitialDelay(0L, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_ID_SYNC, ExistingWorkPolicy.REPLACE, workRequest)
    }

    override fun fullSync() {
        LockingAsyncExecutor.executeLockedAsync {
            SynchronizationSettings.resetTimestamps()
            syncImmediately()
        }
    }

    override fun clear() {
        LockingAsyncExecutor.executeLockedAsync(SynchronizationQueueStorage(context)::clearQueue)
    }

    override fun enqueueFeedAdded(downloadUrl: String?) {
        if (!SynchronizationSettings.isProviderConnected()) {
            return
        }
        LockingAsyncExecutor.executeLockedAsync {
            SynchronizationQueueStorage(context).enqueueFeedAdded(downloadUrl)
            sync()
        }
    }

    override fun enqueueFeedRemoved(downloadUrl: String?) {
        if (!SynchronizationSettings.isProviderConnected()) {
            return
        }
        LockingAsyncExecutor.executeLockedAsync {
            SynchronizationQueueStorage(context).enqueueFeedRemoved(downloadUrl)
            sync()
        }
    }

    override fun enqueueEpisodeAction(action: EpisodeAction) {
        if (!SynchronizationSettings.isProviderConnected()) {
            return
        }
        LockingAsyncExecutor.executeLockedAsync {
            SynchronizationQueueStorage(context).enqueueEpisodeAction(action)
            sync()
        }
    }

    override fun enqueueEpisodePlayed(media: FeedMedia, completed: Boolean) {
        if (!SynchronizationSettings.isProviderConnected()) {
            return
        }
        if (media.getItem() == null || media.getItem()!!.getFeed()!!.isLocalFeed()
                || media.getItem()!!.getFeed()!!.getState() == Feed.STATE_NOT_SUBSCRIBED) {
            return
        }
        if (media.getStartPosition() < 0 || (!completed && media.getStartPosition() >= media.getPosition())) {
            return
        }
        val action = EpisodeAction.Builder(media.getItem(), EpisodeAction.PLAY)
                .currentTimestamp()
                .started(media.getStartPosition() / 1000)
                .position((if (completed) media.getDuration() else media.getPosition()) / 1000)
                .total(media.getDuration() / 1000)
                .build()
        enqueueEpisodeAction(action)
    }
}
