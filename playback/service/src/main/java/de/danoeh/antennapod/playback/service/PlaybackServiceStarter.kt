package de.danoeh.antennapod.playback.service

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.core.content.ContextCompat
import androidx.media3.common.DeviceInfo
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.playback.base.BuildConfig
import de.danoeh.antennapod.playback.base.MediaItemAdapter

class PlaybackServiceStarter {
    private val context: Context
    private val media: Playable?
    private var shouldStreamThisTime = false
    private var callEvenIfRunning = false

    constructor(context: Context, media: Playable?) {
        this.context = context
        this.media = media
    }

    /**
     * Default value: false
     */
    fun callEvenIfRunning(callEvenIfRunning: Boolean): PlaybackServiceStarter {
        this.callEvenIfRunning = callEvenIfRunning
        return this
    }

    fun shouldStreamThisTime(shouldStreamThisTime: Boolean): PlaybackServiceStarter {
        this.shouldStreamThisTime = shouldStreamThisTime
        return this
    }

    fun getIntent(): Intent {
        val launchIntent = Intent(context, PlaybackService::class.java)
        launchIntent.putExtra(PlaybackServiceInterface.EXTRA_PLAYABLE, media as Parcelable?)
        launchIntent.putExtra(PlaybackServiceInterface.EXTRA_ALLOW_STREAM_THIS_TIME, shouldStreamThisTime)
        return launchIntent
    }

    fun start() {
        if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
            PlaybackController.bindToMedia3Service(context) { controller ->
                if (controller.getCurrentMediaItem() != null && media is FeedMedia
                        && ("" + (media as FeedMedia).getId()) == controller.getCurrentMediaItem()!!.mediaId) {
                    controller.play()
                    return@bindToMedia3Service
                }
                if (!controller.isPlaying() && controller.getDeviceInfo().playbackType
                        == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
                    controller.play() // Casting somehow does not play when not quickly starting the old episode
                }
                controller.setMediaItem(MediaItemAdapter.fromMediaIdStub((media as FeedMedia).getId()))
                controller.prepare()
                controller.play()
            }
            return
        }

        if (PlaybackService.isRunning && !callEvenIfRunning) {
            return
        }
        ContextCompat.startForegroundService(context, getIntent())
    }
}
