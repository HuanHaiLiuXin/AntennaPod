package de.danoeh.antennapod.ui.notifications

import android.content.Context

import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationManagerCompat

import java.util.Arrays

import de.danoeh.antennapod.storage.preferences.UserPreferences

class NotificationUtils {
    companion object {
        const val CHANNEL_ID_USER_ACTION = "user_action"
        const val CHANNEL_ID_DOWNLOADING = "downloading"
        const val CHANNEL_ID_REFRESHING = "refreshing"
        const val CHANNEL_ID_PLAYING = "playing"
        const val CHANNEL_ID_DOWNLOAD_ERROR = "error"
        const val CHANNEL_ID_SYNC_ERROR = "sync_error"
        const val CHANNEL_ID_EPISODE_NOTIFICATIONS = "episode_notifications"

        const val GROUP_ID_ERRORS = "group_errors"
        const val GROUP_ID_NEWS = "group_news"

        @JvmStatic
        fun createChannels(context: Context) {
            val mNotificationManager = NotificationManagerCompat.from(context)

            val channelGroups: List<NotificationChannelGroupCompat> = Arrays.asList(
                    createGroupErrors(context),
                    createGroupNews(context))
            mNotificationManager.createNotificationChannelGroupsCompat(channelGroups)

            val channels: List<NotificationChannelCompat> = Arrays.asList(
                    createChannelUserAction(context),
                    createChannelDownloading(context),
                    createChannelRefreshing(context),
                    createChannelPlaying(context),
                    createChannelError(context),
                    createChannelSyncError(context),
                    createChannelEpisodeNotification(context))
            mNotificationManager.createNotificationChannelsCompat(channels)
        }

        private fun createChannelUserAction(c: Context): NotificationChannelCompat {
            return NotificationChannelCompat.Builder(
                            CHANNEL_ID_USER_ACTION, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(c.getString(R.string.notification_channel_user_action))
                    .setDescription(c.getString(R.string.notification_channel_user_action_description))
                    .setGroup(GROUP_ID_ERRORS)
                    .build()
        }

        private fun createChannelDownloading(c: Context): NotificationChannelCompat {
            return NotificationChannelCompat.Builder(
                            CHANNEL_ID_DOWNLOADING, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(c.getString(R.string.notification_channel_downloading))
                    .setDescription(c.getString(R.string.notification_channel_downloading_description))
                    .setShowBadge(false)
                    .build()
        }

        private fun createChannelRefreshing(c: Context): NotificationChannelCompat {
            return NotificationChannelCompat.Builder(
                            CHANNEL_ID_REFRESHING, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(c.getString(R.string.notification_channel_refreshing))
                    .setDescription(c.getString(R.string.notification_channel_refreshing_description))
                    .setShowBadge(false)
                    .build()
        }

        private fun createChannelPlaying(c: Context): NotificationChannelCompat {
            return NotificationChannelCompat.Builder(
                            CHANNEL_ID_PLAYING, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(c.getString(R.string.notification_channel_playing))
                    .setDescription(c.getString(R.string.notification_channel_playing_description))
                    .setShowBadge(false)
                    .build()
        }

        private fun createChannelError(c: Context): NotificationChannelCompat {
            val notificationChannel = NotificationChannelCompat.Builder(
                            CHANNEL_ID_DOWNLOAD_ERROR, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(c.getString(R.string.notification_channel_download_error))
                    .setDescription(c.getString(R.string.notification_channel_download_error_description))
                    .setGroup(GROUP_ID_ERRORS)

            if (!UserPreferences.getShowDownloadReportRaw()) {
                // Migration from app managed setting: disable notification
                notificationChannel.setImportance(NotificationManagerCompat.IMPORTANCE_NONE)
            }
            return notificationChannel.build()
        }

        private fun createChannelSyncError(c: Context): NotificationChannelCompat {
            val notificationChannel = NotificationChannelCompat.Builder(
                            CHANNEL_ID_SYNC_ERROR, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(c.getString(R.string.notification_channel_sync_error))
                    .setDescription(c.getString(R.string.notification_channel_sync_error_description))
                    .setGroup(GROUP_ID_ERRORS)

            if (!UserPreferences.getGpodnetNotificationsEnabledRaw()) {
                // Migration from app managed setting: disable notification
                notificationChannel.setImportance(NotificationManagerCompat.IMPORTANCE_NONE)
            }
            return notificationChannel.build()
        }

        private fun createChannelEpisodeNotification(c: Context): NotificationChannelCompat {
            return NotificationChannelCompat.Builder(
                            CHANNEL_ID_EPISODE_NOTIFICATIONS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(c.getString(R.string.notification_channel_new_episode))
                    .setDescription(c.getString(R.string.notification_channel_new_episode_description))
                    .setGroup(GROUP_ID_NEWS)
                    .build()
        }

        private fun createGroupErrors(c: Context): NotificationChannelGroupCompat {
            return NotificationChannelGroupCompat.Builder(GROUP_ID_ERRORS)
                    .setName(c.getString(R.string.notification_group_errors))
                    .build()
        }

        private fun createGroupNews(c: Context): NotificationChannelGroupCompat {
            return NotificationChannelGroupCompat.Builder(GROUP_ID_NEWS)
                    .setName(c.getString(R.string.notification_group_news))
                    .build()
        }
    }
}
