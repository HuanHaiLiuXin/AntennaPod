package de.danoeh.antennapod.net.download.service.feed

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.event.FeedUpdateRunningEvent
import de.danoeh.antennapod.net.common.NetworkUtils
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.net.download.service.R
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager
import de.danoeh.antennapod.storage.preferences.UserPreferences
import org.greenrobot.eventbus.EventBus

import java.util.concurrent.TimeUnit

class FeedUpdateManagerImpl : FeedUpdateManager() {
    companion object {
        const val WORK_TAG_FEED_UPDATE = "feedUpdate"
        private const val WORK_ID_FEED_UPDATE = "de.danoeh.antennapod.core.service.FeedUpdateWorker"
        private const val WORK_ID_FEED_UPDATE_MANUAL = "feedUpdateManual"
        const val EXTRA_FEED_ID = "feed_id"
        const val EXTRA_NEXT_PAGE = "next_page"
        const val EXTRA_EVEN_ON_MOBILE = "even_on_mobile"
        const val EXTRA_MANUAL = "manual"
        private const val TAG = "AutoUpdateManager"
        private var lastManualRefreshTime = 0L
        private var lastManualRefreshFeedId = -1L
        private const val REFRESH_COOLDOWN_MS = 20_000L
    }

    /**
     * Start / restart periodic auto feed refresh
     * @param context Context
     */
    override fun restartUpdateAlarm(context: Context, replace: Boolean) {
        if (UserPreferences.isAutoUpdateDisabled()) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_ID_FEED_UPDATE)
        } else {
            val workRequest = PeriodicWorkRequest.Builder(
                    FeedUpdateWorker::class.java, 1, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder()
                        .setRequiredNetworkType(if (UserPreferences.isAllowMobileFeedRefresh())
                            NetworkType.CONNECTED else NetworkType.UNMETERED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_ID_FEED_UPDATE,
                    if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                            else ExistingPeriodicWorkPolicy.KEEP, workRequest)
        }
    }

    override fun runOnce(context: Context) {
        runOnce(context, null, false)
    }

    override fun runOnce(context: Context, feed: Feed) {
        runOnce(context, feed, false)
    }

    override fun runOnce(context: Context, feed: Feed?, nextPage: Boolean) {
        lastManualRefreshTime = System.currentTimeMillis()
        lastManualRefreshFeedId = if (feed != null) feed.getId() else -1
        val workRequest = OneTimeWorkRequest.Builder(FeedUpdateWorker::class.java)
                .setInitialDelay(0L, TimeUnit.MILLISECONDS)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(WORK_TAG_FEED_UPDATE)
        if (feed == null || !feed.isLocalFeed()) {
            workRequest.setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build())
        }
        val builder = Data.Builder()
        builder.putBoolean(EXTRA_EVEN_ON_MOBILE, true)
        builder.putBoolean(EXTRA_MANUAL, true)
        if (feed != null) {
            builder.putLong(EXTRA_FEED_ID, feed.getId())
            builder.putBoolean(EXTRA_NEXT_PAGE, nextPage)
        }
        workRequest.setInputData(builder.build())
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_ID_FEED_UPDATE_MANUAL,
                ExistingWorkPolicy.REPLACE, workRequest.build())
    }

    override fun runOnceOrAsk(context: Context) {
        runOnceOrAsk(context, null)
    }

    override fun runOnceOrAsk(context: Context, feed: Feed?) {
        val feedId = if (feed != null) feed.getId() else -1
        if (System.currentTimeMillis() - lastManualRefreshTime < REFRESH_COOLDOWN_MS
                && lastManualRefreshFeedId == feedId) {
            EventBus.getDefault().post(MessageEvent(context.getString(R.string.please_wait_before_refreshing)))
            EventBus.getDefault().postSticky(FeedUpdateRunningEvent(false))
            return
        }
        Log.d(TAG, "Run auto update immediately in background.")
        if (feed != null && feed.isLocalFeed()) {
            runOnce(context, feed)
        } else if (!NetworkUtils.networkAvailable()) {
            EventBus.getDefault().post(MessageEvent(context.getString(R.string.download_error_no_connection)))
            EventBus.getDefault().postSticky(FeedUpdateRunningEvent(false))
        } else if (NetworkUtils.isFeedRefreshAllowed()) {
            runOnce(context, feed!!)
        } else {
            confirmMobileRefresh(context, feed)
        }
    }

    private fun confirmMobileRefresh(context: Context, feed: Feed?) {
        val builder = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.feed_refresh_title)
                .setPositiveButton(R.string.confirm_mobile_streaming_button_once
                ) { _, _ -> runOnce(context, feed!!) }
                .setNeutralButton(R.string.confirm_mobile_streaming_button_always) { _, _ ->
                    UserPreferences.setAllowMobileFeedRefresh(true)
                    runOnce(context, feed!!)
                }
                .setOnCancelListener {
                    EventBus.getDefault().postSticky(FeedUpdateRunningEvent(false))
                }
                .setNegativeButton(R.string.no) { _, _ ->
                    EventBus.getDefault().postSticky(FeedUpdateRunningEvent(false))
                }
        if (NetworkUtils.isNetworkRestricted() && NetworkUtils.isVpnOverWifi()) {
            builder.setMessage(R.string.confirm_mobile_feed_refresh_dialog_message_vpn)
        } else {
            builder.setMessage(R.string.confirm_mobile_feed_refresh_dialog_message)
        }
        builder.show()
    }
}
