package de.danoeh.antennapod.storage.importexport

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.notifications.NotificationUtils
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.Date
import java.util.Iterator
import java.util.List
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.greenrobot.eventbus.EventBus

class AutomaticDatabaseExportWorker : Worker {
    companion object {
        private const val WORK_ID_AUTOMATIC_DATABASE_EXPORT = "de.danoeh.antennapod.AutomaticDbExport"

        @JvmStatic
        fun enqueueIfNeeded(context: Context, replace: Boolean) {
            if (UserPreferences.getAutomaticExportFolder() == null) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_ID_AUTOMATIC_DATABASE_EXPORT)
            } else {
                val workRequest = PeriodicWorkRequest.Builder(
                            AutomaticDatabaseExportWorker::class.java, 3, TimeUnit.DAYS)
                        .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_ID_AUTOMATIC_DATABASE_EXPORT,
                        if (replace) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP, workRequest)
            }
        }
    }

    constructor(context: Context, params: WorkerParameters) : super(context, params) {
    }

    override fun doWork(): Result {
        val folderUri = UserPreferences.getAutomaticExportFolder()
        if (folderUri == null) {
            return Result.success()
        }
        try {
            export(folderUri)
            return Result.success()
        } catch (e: IOException) {
            showErrorNotification(e)
            return Result.failure()
        }
    }

    @Throws(IOException::class)
    private fun export(folderUri: String) {
        val documentFolder = DocumentFile.fromTreeUri(getApplicationContext(), Uri.parse(folderUri))
        if (documentFolder == null || !documentFolder.exists() || !documentFolder.canWrite()) {
            throw IOException("Unable to open export folder")
        }
        val filename = java.lang.String.format("AntennaPodBackup-%s.db",
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
        val exportFile = documentFolder.createFile("application/x-sqlite3", filename)
        if (exportFile == null || !exportFile.canWrite()) {
            throw IOException("Unable to create export file")
        }
        DatabaseExporter.exportToDocument(exportFile.getUri(), getApplicationContext())
        val files = ArrayList(Arrays.asList(*documentFolder.listFiles()))
        val itr = files.iterator()
        while (itr.hasNext()) {
            val file = itr.next()
            if (!file.getName()!!.matches(Regex("AntennaPodBackup-\\d\\d\\d\\d-\\d\\d-\\d\\d\\.db"))) {
                itr.remove()
            }
        }
        Collections.sort(files) { o1, o2 -> java.lang.Long.compare(o2.lastModified(), o1.lastModified()) }
        var hasDeletionFailed = false
        for (i in 5 until files.size) {
            val isDeleted = files[i].delete()
            if (!hasDeletionFailed && !isDeleted) {
                hasDeletionFailed = true
            }
        }
        if (hasDeletionFailed) {
            throw IOException("Unable to delete some database backup files")
        }
    }

    private fun showErrorNotification(exception: Exception) {
        val description = getApplicationContext().getString(R.string.automatic_database_export_error) +
                " " + exception.message
        if (EventBus.getDefault().hasSubscriberForEvent(MessageEvent::class.java)) {
            EventBus.getDefault().post(MessageEvent(description))
            return
        }

        val intent = getApplicationContext().getPackageManager().getLaunchIntentForPackage(
                getApplicationContext().getPackageName())
        val pendingIntent = PendingIntent.getActivity(getApplicationContext(),
                R.id.pending_intent_backup_error, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(getApplicationContext(),
                        NotificationUtils.CHANNEL_ID_SYNC_ERROR)
                .setContentTitle(getApplicationContext().getString(R.string.automatic_database_export_error))
                .setContentText(exception.message)
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
            nm.notify(R.id.notification_id_backup_error, notification)
        } else {
            Toast.makeText(getApplicationContext(), description, Toast.LENGTH_LONG).show()
        }
    }
}
