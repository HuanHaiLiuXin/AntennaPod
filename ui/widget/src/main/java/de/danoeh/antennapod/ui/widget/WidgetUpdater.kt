package de.danoeh.antennapod.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.RemoteViews

import com.bumptech.glide.Glide
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.resource.bitmap.RoundedCorners

import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter
import de.danoeh.antennapod.ui.common.Converter
import de.danoeh.antennapod.storage.preferences.UserPreferences

import java.util.concurrent.TimeUnit

import de.danoeh.antennapod.model.playback.MediaType
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.playback.base.PlayerStatus
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.appstartintent.PlaybackSpeedActivityStarter
import de.danoeh.antennapod.ui.appstartintent.VideoPlayerActivityStarter
import de.danoeh.antennapod.ui.episodes.ImageResourceUtils
import de.danoeh.antennapod.ui.episodes.TimeSpeedConverter
import de.danoeh.antennapod.ui.glide.FastBlurTransformation

/**
 * Updates the state of the player widget.
 */
abstract class WidgetUpdater {
    companion object {
        private const val TAG = "WidgetUpdater"

        /**
         * Update the widgets with the given parameters. Must be called in a background thread.
         */
        @JvmStatic
        fun updateWidget(context: Context, widgetState: WidgetState?) {
            if (!PlayerWidget.isEnabled(context) || widgetState == null) {
                return
            }

            val startMediaPlayer: PendingIntent
            if (widgetState.media != null && widgetState.media.getMediaType() == MediaType.VIDEO) {
                startMediaPlayer = VideoPlayerActivityStarter(context).getPendingIntent()
            } else {
                startMediaPlayer = MainActivityStarter(context)
                        .withOpenPlayer().withClearBackStack().getPendingIntent()
            }

            val startPlaybackSpeedDialog = PlaybackSpeedActivityStarter(context).getPendingIntent()

            val views: RemoteViews
            views = RemoteViews(context.getPackageName(), R.layout.player_widget)

            if (widgetState.media != null) {
                views.setOnClickPendingIntent(R.id.layout_left, startMediaPlayer)
                views.setOnClickPendingIntent(R.id.imgvCover, startMediaPlayer)
                views.setOnClickPendingIntent(R.id.imgvCoverLarge, startMediaPlayer)
                views.setOnClickPendingIntent(R.id.butPlaybackSpeed, startPlaybackSpeedDialog)

                views.setTextViewText(R.id.txtvTitle, widgetState.media.getEpisodeTitle())
                views.setViewVisibility(R.id.txtvTitle, View.VISIBLE)
                views.setViewVisibility(R.id.txtNoPlaying, View.GONE)

                val progressString = getProgressString(widgetState.position,
                        widgetState.duration, widgetState.playbackSpeed)
                if (progressString != null) {
                    views.setViewVisibility(R.id.txtvProgress, View.VISIBLE)
                    views.setTextViewText(R.id.txtvProgress, progressString)
                }

                if (widgetState.status == PlayerStatus.PLAYING) {
                    views.setImageViewResource(R.id.butPlay, R.drawable.ic_widget_pause)
                    views.setContentDescription(R.id.butPlay, context.getString(R.string.pause_label))
                    views.setImageViewResource(R.id.butPlayExtended, R.drawable.ic_widget_pause)
                    views.setContentDescription(R.id.butPlayExtended, context.getString(R.string.pause_label))
                } else {
                    views.setImageViewResource(R.id.butPlay, R.drawable.ic_widget_play)
                    views.setContentDescription(R.id.butPlay, context.getString(R.string.play_label))
                    views.setImageViewResource(R.id.butPlayExtended, R.drawable.ic_widget_play)
                    views.setContentDescription(R.id.butPlayExtended, context.getString(R.string.play_label))
                }
                views.setOnClickPendingIntent(R.id.butPlay,
                        MediaButtonStarter.createPendingIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                views.setOnClickPendingIntent(R.id.butPlayExtended,
                        MediaButtonStarter.createPendingIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                views.setOnClickPendingIntent(R.id.butRew,
                        MediaButtonStarter.createPendingIntent(context, KeyEvent.KEYCODE_MEDIA_REWIND))
                views.setOnClickPendingIntent(R.id.butFastForward,
                        MediaButtonStarter.createPendingIntent(context, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
                views.setOnClickPendingIntent(R.id.butSkip,
                        MediaButtonStarter.createPendingIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT))
            } else {
                // start the app if they click anything
                views.setOnClickPendingIntent(R.id.layout_left, startMediaPlayer)
                views.setOnClickPendingIntent(R.id.butPlay, startMediaPlayer)
                views.setOnClickPendingIntent(R.id.butPlayExtended,
                        MediaButtonStarter.createPendingIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                views.setViewVisibility(R.id.txtvProgress, View.GONE)
                views.setViewVisibility(R.id.txtvTitle, View.GONE)
                views.setViewVisibility(R.id.txtNoPlaying, View.VISIBLE)
                views.setImageViewResource(R.id.imgvCover, R.mipmap.ic_launcher)
                views.setImageViewResource(R.id.butPlay, R.drawable.ic_widget_play)
                views.setImageViewResource(R.id.butPlayExtended, R.drawable.ic_widget_play)
            }

            val playerWidget = ComponentName(context, PlayerWidget::class.java)
            val manager = AppWidgetManager.getInstance(context)
            val widgetIds = manager.getAppWidgetIds(playerWidget)

            for (id in widgetIds) {
                val options = manager.getAppWidgetOptions(id)
                val prefs = context.getSharedPreferences(PlayerWidget.PREFS_NAME, Context.MODE_PRIVATE)
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                val columns = getCellsForSize(minWidth)
                if (columns < 3) {
                    views.setViewVisibility(R.id.layout_center, View.INVISIBLE)
                } else {
                    views.setViewVisibility(R.id.layout_center, View.VISIBLE)
                }
                val showPlaybackSpeed = prefs.getBoolean(PlayerWidget.KEY_WIDGET_PLAYBACK_SPEED + id, false)
                val showRewind = prefs.getBoolean(PlayerWidget.KEY_WIDGET_REWIND + id, false)
                val showFastForward = prefs.getBoolean(PlayerWidget.KEY_WIDGET_FAST_FORWARD + id, false)
                val showSkip = prefs.getBoolean(PlayerWidget.KEY_WIDGET_SKIP + id, false)
                val showCoverAsBcg = prefs.getBoolean(PlayerWidget.KEY_WIDGET_COVER_BACKGROUND + id, false)

                if (showPlaybackSpeed || showRewind || showSkip || showFastForward) {
                    views.setInt(R.id.extendedButtonsContainer, "setVisibility", View.VISIBLE)
                    views.setInt(R.id.butPlay, "setVisibility", View.GONE)
                    views.setInt(R.id.butPlaybackSpeed, "setVisibility", if (showPlaybackSpeed) View.VISIBLE else View.GONE)
                    views.setInt(R.id.butRew, "setVisibility", if (showRewind) View.VISIBLE else View.GONE)
                    views.setInt(R.id.butFastForward, "setVisibility", if (showFastForward) View.VISIBLE else View.GONE)
                    views.setInt(R.id.butSkip, "setVisibility", if (showSkip) View.VISIBLE else View.GONE)
                } else {
                    views.setInt(R.id.extendedButtonsContainer, "setVisibility", View.GONE)
                    views.setInt(R.id.butPlay, "setVisibility", View.VISIBLE)
                }

                if (showCoverAsBcg) {
                    views.setViewVisibility(R.id.imgvCover, View.GONE)
                    views.setViewVisibility(R.id.imgvCoverLarge, View.GONE)
                    views.setViewVisibility(R.id.imgvBackground, View.VISIBLE)
                    val iconSize = 4 * context.getResources().getDimensionPixelSize(android.R.dimen.app_icon_size)
                    var icon: Bitmap? = null
                    if (widgetState.media != null) {
                        icon = loadCover(context, iconSize, widgetState.media, FastBlurTransformation())
                    }
                    if (icon != null) {
                        views.setImageViewBitmap(R.id.imgvBackground, icon)
                    } else {
                        views.setViewVisibility(R.id.imgvBackground, View.GONE)
                    }
                } else {
                    val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                    val largeCover = minHeight >= 100 && columns >= 2
                    views.setViewVisibility(R.id.imgvCover, if (largeCover) View.GONE else View.VISIBLE)
                    views.setViewVisibility(R.id.imgvCoverLarge, if (largeCover) View.VISIBLE else View.GONE)
                    views.setViewVisibility(R.id.imgvBackground, View.GONE)
                    val iconSize = if (largeCover)
                        context.getResources().getDimensionPixelSize(R.dimen.widget_cover_large)
                    else context.getResources().getDimensionPixelSize(android.R.dimen.app_icon_size)
                    val radius = context.getResources().getDimensionPixelSize(R.dimen.widget_inner_radius)
                    var icon: Bitmap? = null
                    if (widgetState.media != null) {
                        icon = loadCover(context, iconSize, widgetState.media, RoundedCorners(radius))
                    }
                    if (icon != null) {
                        views.setImageViewBitmap(if (largeCover) R.id.imgvCoverLarge else R.id.imgvCover, icon)
                    } else {
                        views.setImageViewResource(if (largeCover) R.id.imgvCoverLarge else R.id.imgvCover, R.mipmap.ic_launcher)
                    }
                }
                val backgroundColor = prefs.getInt(PlayerWidget.KEY_WIDGET_COLOR + id, PlayerWidget.DEFAULT_COLOR)
                views.setInt(R.id.widgetLayout, "setBackgroundColor", backgroundColor)

                manager.updateAppWidget(id, views)
            }
        }

        private fun loadCover(context: Context, iconSize: Int, media: Playable, transform: Transformation<Bitmap>): Bitmap? {
            try {
                return Glide.with(context)
                        .asBitmap()
                        .load(media.getImageLocation())
                        .dontAnimate()
                        .transform(transform)
                        .submit(iconSize, iconSize)
                        .get(500, TimeUnit.MILLISECONDS)
            } catch (tr1: Throwable) {
                try {
                    return Glide.with(context)
                            .asBitmap()
                            .load(ImageResourceUtils.getFallbackImageLocation(media))
                            .dontAnimate()
                            .transform(transform)
                            .submit(iconSize, iconSize)
                            .get(500, TimeUnit.MILLISECONDS)
                } catch (tr2: Throwable) {
                    Log.e(TAG, "Error loading the media icon for the widget", tr2)
                    return null
                }
            }
        }

        /**
         * Returns number of cells needed for given size of the widget.
         *
         * @param size Widget size in dp.
         * @return Size in number of cells.
         */
        private fun getCellsForSize(size: Int): Int {
            var n = 2
            while (70 * n - 30 < size) {
                n += 1
            }
            return n - 1
        }

        private fun getProgressString(position: Int, duration: Int, speed: Float): String? {
            if (position < 0 || duration <= 0) {
                return null
            }
            val converter = TimeSpeedConverter(speed)
            if (UserPreferences.shouldShowRemainingTime()) {
                return Converter.getDurationStringLong(converter.convert(position)) + " / -" +
                        Converter.getDurationStringLong(converter.convert(Math.max(0, duration - position)))
            } else {
                return Converter.getDurationStringLong(converter.convert(position)) + " / " +
                        Converter.getDurationStringLong(converter.convert(duration))
            }
        }
    }

    class WidgetState(val media: Playable?, val status: PlayerStatus, val position: Int,
                      val duration: Int, val playbackSpeed: Float) {
        constructor(status: PlayerStatus) : this(null, status, Playable.INVALID_TIME, Playable.INVALID_TIME, 1.0f)
    }
}
