package de.danoeh.antennapod.net.download.service.feed

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources

import android.graphics.Bitmap
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedCounter
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.net.download.service.R
import de.danoeh.antennapod.storage.database.PodDBAdapter

import de.danoeh.antennapod.ui.notifications.NotificationUtils

class NewEpisodesNotification {
    companion object {
        private const val TAG = "NewEpisodesNotification"
        private const val GROUP_KEY = "de.danoeh.antennapod.EPISODES"

        private fun showNotification(newEpisodes: Int, feed: Feed, context: Context,
                                     notificationManager: NotificationManagerCompat) {
            val res = context.getResources()
            val text = res.getQuantityString(
                    R.plurals.new_episode_notification_message, newEpisodes, newEpisodes, feed.getTitle()
            )
            val title = res.getQuantityString(R.plurals.new_episode_notification_title, newEpisodes)

            val intent = Intent()
            intent.setAction("NewEpisodes" + feed.getId())
            intent.setComponent(ComponentName(context, "de.danoeh.antennapod.activity.MainActivity"))
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.putExtra("fragment_feed_id", feed.getId())
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

            val notification = NotificationCompat.Builder(
                    context, NotificationUtils.CHANNEL_ID_EPISODE_NOTIFICATIONS)
                    .setSmallIcon(R.drawable.ic_notification_new)
                    .setContentTitle(title)
                    .setLargeIcon(loadIcon(context, feed))
                    .setContentText(text)
                    .setContentIntent(pendingIntent)
                    .setGroup(GROUP_KEY)
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .build()
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(NotificationUtils.CHANNEL_ID_EPISODE_NOTIFICATIONS,
                        feed.hashCode(), notification)
            }
            showGroupSummaryNotification(context, notificationManager)
        }

        private fun showGroupSummaryNotification(context: Context, notificationManager: NotificationManagerCompat) {
            val intent = Intent()
            intent.setAction("NewEpisodes")
            intent.setComponent(ComponentName(context, "de.danoeh.antennapod.activity.MainActivity"))
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.putExtra("fragment_tag", "NewEpisodesFragment")
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

            val notificationGroupSummary = NotificationCompat.Builder(
                    context, NotificationUtils.CHANNEL_ID_EPISODE_NOTIFICATIONS)
                    .setSmallIcon(R.drawable.ic_notification_new)
                    .setContentTitle(context.getString(R.string.new_episode_notification_group_text))
                    .setContentIntent(pendingIntent)
                    .setGroup(GROUP_KEY)
                    .setGroupSummary(true)
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .build()
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(NotificationUtils.CHANNEL_ID_EPISODE_NOTIFICATIONS,
                        0, notificationGroupSummary)
            }
        }

        private fun loadIcon(context: Context, feed: Feed): Bitmap? {
            val iconSize = (128 * context.getResources().getDisplayMetrics().density).toInt()
            try {
                return Glide.with(context)
                        .asBitmap()
                        .load(feed.getImageUrl())
                        .apply(RequestOptions().centerCrop())
                        .submit(iconSize, iconSize)
                        .get()
            } catch (tr: Throwable) {
                return null
            }
        }

        private fun getNewEpisodeCount(feedId: Long): Int {
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            val counters = adapter.getFeedCounters(FeedCounter.SHOW_NEW, feedId)
            val episodeCount = if (counters.containsKey(feedId)) counters[feedId]!! else 0
            adapter.close()
            return episodeCount
        }
    }

    private var countersBefore: HashMap<Long, Int>? = null

    constructor() {
    }

    fun loadCountersBeforeRefresh() {
        val adapter = PodDBAdapter.getInstance()
        adapter.open()
        countersBefore = adapter.getFeedCounters(FeedCounter.SHOW_NEW)
        adapter.close()
    }

    fun showIfNeeded(context: Context, feed: Feed) {
        val prefs = feed.getPreferences()!!
        if (!prefs.getKeepUpdated() || !prefs.getShowEpisodeNotification()) {
            return
        }

        val newEpisodesBefore = if (countersBefore!!.containsKey(feed.getId())) countersBefore!![feed.getId()]!! else 0
        val newEpisodesAfter = getNewEpisodeCount(feed.getId())

        Log.d(TAG, "New episodes before: " + newEpisodesBefore + ", after: " + newEpisodesAfter)
        if (newEpisodesAfter > newEpisodesBefore) {
            val notificationManager = NotificationManagerCompat.from(context)
            showNotification(newEpisodesAfter, feed, context, notificationManager)
        }
    }
}
