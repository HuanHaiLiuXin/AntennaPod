package de.danoeh.antennapod.storage.databasemaintenanceservice

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import de.danoeh.antennapod.storage.database.PodDBAdapter
import de.danoeh.antennapod.ui.notifications.NotificationUtils

import java.util.concurrent.TimeUnit

class DatabaseMaintenanceWorker : Worker {
    companion object {
        private const val WORK_ID_DATABASE_MAINTENANCE = "DatabaseMaintenanceWorker"

        @JvmStatic
        fun enqueueIfNeeded(context: Context) {
            val workRequest = PeriodicWorkRequest.Builder(
                    DatabaseMaintenanceWorker::class.java, 3, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_ID_DATABASE_MAINTENANCE,
                            ExistingPeriodicWorkPolicy.KEEP, workRequest)
        }
    }

    constructor(context: Context, params: WorkerParameters) : super(context, params) {
    }

    override fun doWork(): Result {
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        adapter.clearOldDownloadLog()
        adapter.close()
        return Result.success()
    }

    override fun getForegroundInfoAsync(): ListenableFuture<ForegroundInfo> {
        return Futures.immediateFuture(ForegroundInfo(R.id.notification_db_maintenance,
                NotificationCompat.Builder(getApplicationContext(), NotificationUtils.CHANNEL_ID_REFRESHING)
                    .setContentTitle(getApplicationContext().getString(R.string.download_notification_title_feeds))
                    .setSmallIcon(R.drawable.ic_notification_sync)
                    .setOngoing(true)
                    .build()))
    }
}
