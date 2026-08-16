package de.danoeh.antennapod.ui.widget

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.playback.base.PlayerStatus
import de.danoeh.antennapod.ui.episodes.PlaybackSpeedUtils

class WidgetUpdaterWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "WidgetUpdaterWorker"

        @JvmStatic
        fun enqueueWork(context: Context) {
            val workRequest = OneTimeWorkRequest.Builder(WidgetUpdaterWorker::class.java).build()
            WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, workRequest)
        }
    }

    override fun doWork(): Result {
        try {
            updateWidget()
        } catch (e: Exception) {
            Log.d(TAG, "Failed to update AntennaPod widget: ", e)
            return Result.failure()
        }
        return Result.success()
    }

    /**
     * Loads the current media from the database and updates the widget in a background job.
     */
    private fun updateWidget() {
        val media: Playable? = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId())
        if (media != null) {
            WidgetUpdater.updateWidget(getApplicationContext(),
                    WidgetUpdater.WidgetState(media, PlayerStatus.STOPPED,
                            media.getPosition(), media.getDuration(),
                            PlaybackSpeedUtils.getCurrentPlaybackSpeed(media)))
        } else {
            WidgetUpdater.updateWidget(getApplicationContext(),
                    WidgetUpdater.WidgetState(PlayerStatus.STOPPED))
        }
    }
}
