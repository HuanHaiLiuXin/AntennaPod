package de.danoeh.antennapod.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager

import java.util.Arrays
import java.util.concurrent.TimeUnit

class PlayerWidget : AppWidgetProvider() {
    companion object {
        private const val TAG = "PlayerWidget"
        const val PREFS_NAME = "PlayerWidgetPrefs"
        private const val KEY_WORKAROUND_ENABLED = "WorkaroundEnabled"
        private const val KEY_ENABLED = "WidgetEnabled"
        const val KEY_WIDGET_COLOR = "widget_color"
        const val KEY_WIDGET_PLAYBACK_SPEED = "widget_playback_speed"
        const val KEY_WIDGET_SKIP = "widget_skip"
        const val KEY_WIDGET_FAST_FORWARD = "widget_fast_forward"
        const val KEY_WIDGET_REWIND = "widget_rewind"
        const val KEY_WIDGET_COVER_BACKGROUND = "widget_cover_background"
        @JvmField
        val DEFAULT_COLOR = 0xff262C31.toInt()
        private const val WORKAROUND_WORK_NAME = "WidgetUpdaterWorkaround"

        private fun scheduleWorkaround(context: Context) {
            // Enqueueing work enables a BOOT_COMPLETED receiver, which in turn makes Android refresh widgets.
            // This creates an endless loop with a flickering widget.
            // Workaround: When there is a widget, schedule a dummy task in the far future, so that the receiver stays.
            val workRequest = OneTimeWorkRequest.Builder(WidgetUpdaterWorker::class.java)
                    .setInitialDelay(100 * 356, TimeUnit.DAYS)
                    .build()
            WorkManager.getInstance(context)
                    .enqueueUniqueWork(WORKAROUND_WORK_NAME, ExistingWorkPolicy.REPLACE, workRequest)
        }

        @JvmStatic
        fun isEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_ENABLED, false)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "Widget enabled")
        setEnabled(context, true)
        WidgetUpdaterWorker.enqueueWork(context)
        scheduleWorkaround(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate() called with: " + "context = [" + context + "], appWidgetManager = ["
                + appWidgetManager + "], appWidgetIds = [" + Arrays.toString(appWidgetIds) + "]")
        WidgetUpdaterWorker.enqueueWork(context)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_WORKAROUND_ENABLED, false)) {
            scheduleWorkaround(context)
            prefs.edit().putBoolean(KEY_WORKAROUND_ENABLED, true).apply()
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager,
                                           appWidgetId: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        WidgetUpdaterWorker.enqueueWork(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Widget disabled")
        setEnabled(context, false)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Log.d(TAG, "OnDeleted")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        for (appWidgetId in appWidgetIds) {
            prefs.edit().remove(KEY_WIDGET_COLOR + appWidgetId).apply()
            prefs.edit().remove(KEY_WIDGET_PLAYBACK_SPEED + appWidgetId).apply()
            prefs.edit().remove(KEY_WIDGET_REWIND + appWidgetId).apply()
            prefs.edit().remove(KEY_WIDGET_FAST_FORWARD + appWidgetId).apply()
            prefs.edit().remove(KEY_WIDGET_SKIP + appWidgetId).apply()
            prefs.edit().remove(KEY_WIDGET_COVER_BACKGROUND + appWidgetId).apply()
        }
        val manager = AppWidgetManager.getInstance(context)
        val widgetIds = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
        if (widgetIds.isEmpty()) {
            prefs.edit().putBoolean(KEY_WORKAROUND_ENABLED, false).apply()
            WorkManager.getInstance(context).cancelUniqueWork(WORKAROUND_WORK_NAME)
        }
        super.onDeleted(context, appWidgetIds)
    }

    private fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
