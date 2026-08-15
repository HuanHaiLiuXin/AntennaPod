package de.danoeh.antennapod.playback.service.internal

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.view.KeyEvent
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import de.danoeh.antennapod.playback.service.MediaButtonReceiver
import de.danoeh.antennapod.playback.service.PlaybackService
import de.danoeh.antennapod.playback.service.R
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.common.Converter
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.ui.episodes.ImageResourceUtils
import de.danoeh.antennapod.ui.episodes.TimeSpeedConverter
import de.danoeh.antennapod.ui.notifications.NotificationUtils
import java.util.ArrayList
import java.util.concurrent.ExecutionException

import de.danoeh.antennapod.playback.base.PlayerStatus
import org.apache.commons.lang3.ArrayUtils

class PlaybackServiceNotificationBuilder {
    companion object {
        private const val TAG = "PlaybackSrvNotification"
        private var defaultIcon: Bitmap? = null

        private fun getBitmap(vectorDrawable: VectorDrawable): Bitmap {
            val bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(),
                    vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight())
            vectorDrawable.draw(canvas)
            return bitmap
        }

        private fun getBitmap(context: Context, drawableId: Int): Bitmap? {
            val drawable = AppCompatResources.getDrawable(context, drawableId)
            if (drawable is BitmapDrawable) {
                return drawable.getBitmap()
            } else if (drawable is VectorDrawable) {
                return getBitmap(drawable)
            } else {
                return null
            }
        }
    }

    private val context: Context
    private var playable: Playable? = null
    private var mediaSessionToken: MediaSessionCompat.Token? = null
    private var playerStatus: PlayerStatus? = null
    private var icon: Bitmap? = null
    private var position: String? = null

    constructor(context: Context) {
        this.context = context
    }

    fun setPlayable(playable: Playable?) {
        if (playable !== this.playable) {
            clearCache()
        }
        this.playable = playable
    }

    private fun clearCache() {
        this.icon = null
        this.position = null
    }

    fun updatePosition(position: Int, speed: Float) {
        val converter = TimeSpeedConverter(speed)
        this.position = Converter.getDurationStringLong(converter.convert(position))
    }

    fun isIconCached(): Boolean {
        return icon != null
    }

    fun loadIcon() {
        val iconSize = (128 * context.getResources().getDisplayMetrics().density).toInt()
        val options = RequestOptions().centerCrop()
        try {
            icon = Glide.with(context)
                    .asBitmap()
                    .load(playable!!.getImageLocation())
                    .apply(options)
                    .submit(iconSize, iconSize)
                    .get()
        } catch (e: ExecutionException) {
            try {
                icon = Glide.with(context)
                        .asBitmap()
                        .load(ImageResourceUtils.getFallbackImageLocation(playable!!))
                        .apply(options)
                        .submit(iconSize, iconSize)
                        .get()
            } catch (ignore: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.e(TAG, "Media icon loader was interrupted")
            } catch (tr: Throwable) {
                Log.e(TAG, "Error loading the media icon for the notification", tr)
            }
        } catch (ignore: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.e(TAG, "Media icon loader was interrupted")
        } catch (tr: Throwable) {
            Log.e(TAG, "Error loading the media icon for the notification", tr)
        }
    }

    fun getCachedIcon(): Bitmap? {
        return icon
    }

    private fun getDefaultIcon(): Bitmap {
        if (defaultIcon == null) {
            defaultIcon = getBitmap(context, R.mipmap.ic_launcher)
        }
        return defaultIcon!!
    }

    fun build(): Notification {
        val notification = NotificationCompat.Builder(context,
                NotificationUtils.CHANNEL_ID_PLAYING)

        if (playable != null) {
            notification.setContentTitle(playable!!.getFeedTitle())
            notification.setContentText(playable!!.getEpisodeTitle())
            addActions(notification, mediaSessionToken, playerStatus)

            if (icon != null) {
                notification.setLargeIcon(icon)
            } else {
                notification.setLargeIcon(getDefaultIcon())
            }

            if (Build.VERSION.SDK_INT < 29) {
                notification.setSubText(position)
            }
        } else {
            notification.setContentTitle(context.getString(R.string.app_name))
            notification.setContentText("Loading. If this does not go away, play any episode and contact us.")
        }

        notification.setContentIntent(getPlayerActivityPendingIntent())
        notification.setWhen(0)
        notification.setSmallIcon(R.drawable.ic_notification)
        notification.setOngoing(false)
        notification.setOnlyAlertOnce(true)
        notification.setShowWhen(false)
        notification.setPriority(UserPreferences.getNotifyPriority())
        notification.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        notification.setColor(NotificationCompat.COLOR_DEFAULT)
        return notification.build()
    }

    private fun getPlayerActivityPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(context, R.id.pending_intent_player_activity,
                PlaybackService.getPlayerActivityIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun addActions(notification: NotificationCompat.Builder, mediaSessionToken: MediaSessionCompat.Token?,
                           playerStatus: PlayerStatus?) {
        val compactActionList = ArrayList<Int>()

        var numActions = 0 // we start and 0 and then increment by 1 for each call to addAction

        val rewindButtonPendingIntent = getPendingIntentForMediaAction(
                KeyEvent.KEYCODE_MEDIA_REWIND, numActions)
        notification.addAction(R.drawable.ic_notification_fast_rewind, context.getString(R.string.rewind_label),
                rewindButtonPendingIntent)
        compactActionList.add(numActions)
        numActions++

        if (playerStatus == PlayerStatus.PLAYING) {
            val pauseButtonPendingIntent = getPendingIntentForMediaAction(
                    KeyEvent.KEYCODE_MEDIA_PAUSE, numActions)
            notification.addAction(R.drawable.ic_notification_pause, //pause action
                    context.getString(R.string.pause_label),
                    pauseButtonPendingIntent)
        } else {
            val playButtonPendingIntent = getPendingIntentForMediaAction(
                    KeyEvent.KEYCODE_MEDIA_PLAY, numActions)
            notification.addAction(R.drawable.ic_notification_play, //play action
                    context.getString(R.string.play_label),
                    playButtonPendingIntent)
        }
        compactActionList.add(numActions++)

        // ff follows play, then we have skip (if it's present)
        val ffButtonPendingIntent = getPendingIntentForMediaAction(
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, numActions)
        notification.addAction(R.drawable.ic_notification_fast_forward, context.getString(R.string.fast_forward_label),
                ffButtonPendingIntent)
        compactActionList.add(numActions)
        numActions++

        if (UserPreferences.showNextChapterOnFullNotification() && playable!!.getChapters() != null) {
            val nextChapterPendingIntent = getPendingIntentForCustomMediaAction(
                    PlaybackService.CUSTOM_ACTION_NEXT_CHAPTER, numActions)
            notification.addAction(R.drawable.ic_notification_next_chapter, context.getString(R.string.next_chapter),
                    nextChapterPendingIntent)
            numActions++
        }

        if (UserPreferences.showSkipOnFullNotification()) {
            val skipButtonPendingIntent = getPendingIntentForMediaAction(
                    KeyEvent.KEYCODE_MEDIA_NEXT, numActions)
            notification.addAction(R.drawable.ic_notification_skip, context.getString(R.string.skip_episode_label),
                    skipButtonPendingIntent)
            numActions++
        }

        val stopButtonPendingIntent = getPendingIntentForMediaAction(
                KeyEvent.KEYCODE_MEDIA_STOP, numActions)
        notification.setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSessionToken)
                .setShowActionsInCompactView(*ArrayUtils.toPrimitive(compactActionList.toTypedArray()))
                .setShowCancelButton(true)
                .setCancelButtonIntent(stopButtonPendingIntent))
    }

    private fun getPendingIntentForMediaAction(keycodeValue: Int, requestCode: Int): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java)
        intent.setAction("MediaCode" + keycodeValue)
        intent.putExtra(MediaButtonReceiver.EXTRA_KEYCODE, keycodeValue)

        if (Build.VERSION.SDK_INT >= 26) {
            return PendingIntent.getForegroundService(context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            return PendingIntent.getService(context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }

    private fun getPendingIntentForCustomMediaAction(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java)
        intent.setAction("MediaAction" + action)
        intent.putExtra(MediaButtonReceiver.EXTRA_CUSTOM_ACTION, action)

        if (Build.VERSION.SDK_INT >= 26) {
            return PendingIntent.getForegroundService(context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            return PendingIntent.getService(context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }

    fun setMediaSessionToken(mediaSessionToken: MediaSessionCompat.Token) {
        this.mediaSessionToken = mediaSessionToken
    }

    fun setPlayerStatus(playerStatus: PlayerStatus) {
        this.playerStatus = playerStatus
    }

    fun getPlayerStatus(): PlayerStatus? {
        return playerStatus
    }
}
