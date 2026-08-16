package de.danoeh.antennapod.net.sync.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.work.Worker
import androidx.work.WorkerParameters
import de.danoeh.antennapod.event.FeedUpdateRunningEvent
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.event.SyncServiceEvent
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.net.common.AntennapodHttpClient
import de.danoeh.antennapod.net.common.RedirectChecker
import de.danoeh.antennapod.net.common.UrlChecker
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager
import de.danoeh.antennapod.net.sync.gpoddernet.GpodnetService
import de.danoeh.antennapod.net.sync.nextcloud.NextcloudSyncService
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeActionChanges
import de.danoeh.antennapod.net.sync.serviceinterface.ISyncService
import de.danoeh.antennapod.net.sync.serviceinterface.SubscriptionChanges
import de.danoeh.antennapod.net.sync.serviceinterface.SyncServiceException
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider
import de.danoeh.antennapod.net.sync.serviceinterface.UploadChangesResponse
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter
import de.danoeh.antennapod.storage.database.LongList
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.notifications.NotificationUtils
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.EventBus

import java.util.ArrayList
import java.util.Collections

class SyncService : Worker {
    companion object {
        const val TAG = "SyncService"

        private var currentlyActive = false

        /* package-private */ @JvmStatic
        fun isCurrentlyActive(): Boolean {
            return currentlyActive
        }
    }

    private val synchronizationQueueStorage: SynchronizationQueueStorage

    constructor(context: Context, params: WorkerParameters) : super(context, params) {
        synchronizationQueueStorage = SynchronizationQueueStorage(context)
    }

    override fun doWork(): Result {
        val activeSyncProvider = getActiveSyncProvider()
        if (activeSyncProvider == null) {
            return Result.success()
        }

        if (currentlyActive) {
            return Result.success()
        }
        currentlyActive = true
        SynchronizationSettings.updateLastSynchronizationAttempt()
        try {
            activeSyncProvider.login()
            syncSubscriptions(activeSyncProvider)
            waitForDownloadServiceCompleted()
            if (someFeedWasNotRefreshedYet()) {
                // Note that this service might get called several times before the FeedUpdate completes
                Log.d(TAG, "Found new subscriptions. Need to refresh them before syncing episode actions")
                EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_wait_for_downloads))
                FeedUpdateManager.getInstance()!!.runOnce(getApplicationContext())
                return Result.success()
            }
            syncEpisodeActions(activeSyncProvider)
            activeSyncProvider.logout()
            clearErrorNotifications()
            EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_success))
            SynchronizationSettings.setLastSynchronizationAttemptSuccess(true)
            return Result.success()
        } catch (e: Exception) {
            EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_error))
            SynchronizationSettings.setLastSynchronizationAttemptSuccess(false)
            Log.e(TAG, Log.getStackTraceString(e))

            if (e is SyncServiceException) {
                if (getRunAttemptCount() % 3 == 2) {
                    // Do not spam users with notification and retry before notifying
                    updateErrorNotification(e)
                }
                return Result.retry()
            } else {
                updateErrorNotification(e)
                return Result.failure()
            }
        } finally {
            currentlyActive = false
        }
    }

    private fun waitForDownloadServiceCompleted() {
        EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_wait_for_downloads))
        try {
            while (true) {
                val event = EventBus.getDefault().getStickyEvent(FeedUpdateRunningEvent::class.java)
                if (event == null || !event.isFeedUpdateRunning) {
                    return
                }
                //noinspection BusyWait
                Thread.sleep(1000)
            }
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun someFeedWasNotRefreshedYet(): Boolean {
        for (feed in DBReader.getFeedList()) {
            if (feed.getPreferences()!!.getKeepUpdated() && feed.getLastRefreshAttempt() == 0L) {
                return true
            }
        }
        return false
    }

    @Throws(SyncServiceException::class)
    private fun syncSubscriptions(syncServiceImpl: ISyncService) {
        val lastSync = SynchronizationSettings.getLastSubscriptionSynchronizationTimestamp()
        EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_subscriptions))
        val localSubscriptions = DBReader.getFeedListDownloadUrls(true)
        val subscriptionChanges = syncServiceImpl.getSubscriptionChanges(lastSync)
        var newTimeStamp = subscriptionChanges!!.getTimestamp()

        val queuedRemovedFeeds = synchronizationQueueStorage.getQueuedRemovedFeeds()
        var queuedAddedFeeds: MutableList<String> = synchronizationQueueStorage.getQueuedAddedFeeds()

        Log.d(TAG, "Downloaded subscription changes: " + subscriptionChanges)
        for (downloadUrl in subscriptionChanges.getAdded()) {
            if (!downloadUrl.startsWith("http")) { // Also matches https
                Log.d(TAG, "Skipping url: " + downloadUrl)
                continue
            } else if (UrlChecker.containsUrl(localSubscriptions, downloadUrl)
                    || queuedRemovedFeeds.contains(downloadUrl)) {
                continue
            }
            val redirectedUrl = RedirectChecker.getNewUrlIfPermanentRedirect(downloadUrl)
            if (redirectedUrl != null
                    && (UrlChecker.containsUrl(localSubscriptions, redirectedUrl)
                        || queuedRemovedFeeds.contains(redirectedUrl))) {
                continue
            }
            // If the creator redirected their feed incorrectly, we still try to avoid duplicated subscriptions
            val finalUrl = RedirectChecker.getFinalUrl(downloadUrl)
            if (UrlChecker.containsUrl(localSubscriptions, finalUrl)
                    || queuedRemovedFeeds.contains(finalUrl)) {
                continue
            }

            val feed = Feed(downloadUrl, null, "Unknown podcast")
            feed.setItems(Collections.emptyList())
            FeedDatabaseWriter.updateFeed(getApplicationContext(), feed, false)
        }

        // remove subscription if not just subscribed (again)
        for (downloadUrl in subscriptionChanges.getRemoved()) {
            if (!queuedAddedFeeds.contains(downloadUrl)) {
                DBWriter.removeFeedWithDownloadUrl(getApplicationContext(), downloadUrl)
            }
        }

        if (lastSync == 0L) {
            Log.d(TAG, "First sync. Adding all local subscriptions.")
            queuedAddedFeeds = localSubscriptions
        }

        queuedAddedFeeds.removeAll(subscriptionChanges.getAdded())
        queuedRemovedFeeds.removeAll(subscriptionChanges.getRemoved())

        if (queuedAddedFeeds.isEmpty() && queuedRemovedFeeds.isEmpty()) {
            Log.d(TAG, "No feeds to add or remove from server")
            synchronizationQueueStorage.clearFeedQueues()
        } else {
            Log.d(TAG, "Added: " + StringUtils.join(queuedAddedFeeds, ", "))
            Log.d(TAG, "Removed: " + StringUtils.join(queuedRemovedFeeds, ", "))

            LockingAsyncExecutor.lock()
            try {
                val uploadResponse = syncServiceImpl
                        .uploadSubscriptionChanges(queuedAddedFeeds, queuedRemovedFeeds)
                synchronizationQueueStorage.clearFeedQueues()
                newTimeStamp = uploadResponse!!.timestamp
            } catch (exception: SyncServiceException) {
                synchronizationQueueStorage.removeLegacyConflictingFeedEntries(localSubscriptions)
                throw exception
            } finally {
                LockingAsyncExecutor.unlock()
            }
        }
        SynchronizationSettings.setLastSubscriptionSynchronizationAttemptTimestamp(newTimeStamp)
    }

    @Throws(SyncServiceException::class)
    private fun syncEpisodeActions(syncServiceImpl: ISyncService) {
        val lastSync = SynchronizationSettings.getLastEpisodeActionSynchronizationTimestamp()
        EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_episodes_download))
        val getResponse = syncServiceImpl.getEpisodeActionChanges(lastSync)
        var newTimeStamp = getResponse!!.getTimestamp()
        val remoteActions = getResponse.getEpisodeActions()
        processEpisodeActions(remoteActions)

        // upload local actions
        EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_episodes_upload))
        val queuedEpisodeActions = synchronizationQueueStorage.getQueuedEpisodeActions()
        if (lastSync == 0L) {
            EventBus.getDefault().postSticky(SyncServiceEvent(R.string.sync_status_upload_played))
            val readItems = DBReader.getEpisodes(0, Int.MAX_VALUE,
                    FeedItemFilter(FeedItemFilter.PLAYED), SortOrder.DATE_NEW_OLD)
            Log.d(TAG, "First sync. Upload state for all " + readItems.size + " played episodes")
            for (item in readItems) {
                val media = item.getMedia()
                if (media == null) {
                    continue
                }
                val played = EpisodeAction.Builder(item, EpisodeAction.PLAY)
                        .currentTimestamp()
                        .started(media.getDuration() / 1000)
                        .position(media.getDuration() / 1000)
                        .total(media.getDuration() / 1000)
                        .build()
                queuedEpisodeActions.add(played)
            }
        }
        if (!queuedEpisodeActions.isEmpty()) {
            LockingAsyncExecutor.lock()
            try {
                Log.d(TAG, "Uploading " + queuedEpisodeActions.size + " actions: "
                        + StringUtils.join(queuedEpisodeActions, ", "))
                val postResponse = syncServiceImpl.uploadEpisodeActions(queuedEpisodeActions)
                newTimeStamp = postResponse!!.timestamp
                Log.d(TAG, "Upload episode response: " + postResponse)
                synchronizationQueueStorage.clearEpisodeActionQueue()
            } finally {
                LockingAsyncExecutor.unlock()
            }
        }
        SynchronizationSettings.setLastEpisodeActionSynchronizationAttemptTimestamp(newTimeStamp)
    }

    @Synchronized
    private fun processEpisodeActions(remoteActions: List<EpisodeAction>) {
        Log.d(TAG, "Processing " + remoteActions.size + " actions")
        if (remoteActions.isEmpty()) {
            return
        }

        val playActionsToUpdate = EpisodeActionFilter
                .getRemoteActionsOverridingLocalActions(remoteActions,
                        synchronizationQueueStorage.getQueuedEpisodeActions())
        val queueToBeRemoved = LongList()
        val updatedItems = ArrayList<FeedItem>()
        for (action in playActionsToUpdate.values) {
            val guid = if (GuidValidator.isValidGuid(action.getGuid())) action.getGuid() else null
            val feedItem = DBReader.getFeedItemByGuidOrEpisodeUrl(guid!!, action.getEpisode())
            if (feedItem == null) {
                Log.i(TAG, "Unknown feed item: " + action)
                continue
            }
            if (feedItem.getMedia() == null) {
                Log.i(TAG, "Feed item has no media: " + action)
                continue
            }
            val media = feedItem.getMedia()!!
            media.setPosition(action.getPosition() * 1000)
            val smartMarkAsPlayedSecs = UserPreferences.getSmartMarkAsPlayedSecs()
            val almostEnded = media.getDuration() > 0
                    && media.getPosition() >= media.getDuration() - smartMarkAsPlayedSecs * 1000
            if (almostEnded) {
                Log.d(TAG, "Marking as played: " + action)
                feedItem.setPlayed(true)
                media.setPosition(0)
                queueToBeRemoved.add(feedItem.getId())
            } else {
                Log.d(TAG, "Setting position: " + action)
            }
            updatedItems.add(feedItem)
        }
        DBWriter.removeQueueItem(getApplicationContext(), false, *queueToBeRemoved.toArray())
        DBReader.loadFeedDataOfFeedItemList(updatedItems)
        DBWriter.setItemList(updatedItems)
    }

    private fun clearErrorNotifications() {
        val nm = getApplicationContext()
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(R.id.notification_gpodnet_sync_error)
        nm.cancel(R.id.notification_gpodnet_sync_autherror)
    }

    private fun updateErrorNotification(exception: Exception) {
        Log.d(TAG, "Posting sync error notification")
        val description = getApplicationContext().getString(R.string.gpodnetsync_error_descr) +
                exception.message

        if (!UserPreferences.gpodnetNotificationsEnabled()) {
            Log.d(TAG, "Skipping sync error notification because of user setting")
            return
        }
        if (EventBus.getDefault().hasSubscriberForEvent(MessageEvent::class.java)) {
            EventBus.getDefault().post(MessageEvent(description))
            return
        }

        val intent = getApplicationContext().getPackageManager().getLaunchIntentForPackage(
                getApplicationContext().getPackageName())
        val pendingIntent = PendingIntent.getActivity(getApplicationContext(),
                R.id.pending_intent_sync_error, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(getApplicationContext(),
                NotificationUtils.CHANNEL_ID_SYNC_ERROR)
                .setContentTitle(getApplicationContext().getString(R.string.gpodnetsync_error_title))
                .setContentText(description)
                .setStyle(NotificationCompat.BigTextStyle().bigText(description))
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.ic_notification_sync_error)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
        val nm = getApplicationContext()
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            nm.notify(R.id.notification_gpodnet_sync_error, notification)
        }
    }

    private fun getActiveSyncProvider(): ISyncService? {
        val selectedSyncProviderKey = SynchronizationSettings.getSelectedSyncProviderKey()
        val selectedService = SynchronizationProvider
                .fromIdentifier(selectedSyncProviderKey)
        if (selectedService == null) {
            return null
        }
        when (selectedService) {
            SynchronizationProvider.GPODDER_NET -> return GpodnetService(AntennapodHttpClient.getHttpClient(),
                    SynchronizationCredentials.getHosturl()!!, SynchronizationCredentials.getDeviceId()!!,
                    SynchronizationCredentials.getUsername()!!, SynchronizationCredentials.getPassword()!!)
            SynchronizationProvider.NEXTCLOUD_GPODDER -> return NextcloudSyncService(AntennapodHttpClient.getHttpClient(),
                    SynchronizationCredentials.getHosturl()!!, SynchronizationCredentials.getUsername()!!,
                    SynchronizationCredentials.getPassword()!!)
            else -> return null
        }
    }
}
