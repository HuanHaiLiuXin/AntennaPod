package de.danoeh.antennapod.playback.service.internal

import android.app.Notification
import android.util.Log

import androidx.core.app.ServiceCompat
import de.danoeh.antennapod.playback.service.PlaybackService

class PlaybackServiceStateManager {
    companion object {
        private const val TAG = "PlaybackSrvState"
    }

    private val playbackService: PlaybackService

    @Volatile
    private var isInForeground = false
    @Volatile
    private var hasReceivedValidStartCommand = false

    constructor(playbackService: PlaybackService) {
        this.playbackService = playbackService
    }

    fun startForeground(notificationId: Int, notification: Notification) {
        Log.d(TAG, "startForeground")
        playbackService.startForeground(notificationId, notification)
        isInForeground = true
    }

    fun stopService() {
        Log.d(TAG, "stopService")
        stopForeground(true)
        playbackService.stopSelf()
        hasReceivedValidStartCommand = false
    }

    fun stopForeground(removeNotification: Boolean) {
        Log.d(TAG, "stopForeground")
        if (isInForeground) {
            if (removeNotification) {
                ServiceCompat.stopForeground(playbackService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            } else {
                ServiceCompat.stopForeground(playbackService, ServiceCompat.STOP_FOREGROUND_DETACH)
            }
        }
        isInForeground = false
    }

    fun hasReceivedValidStartCommand(): Boolean {
        return hasReceivedValidStartCommand
    }

    fun validStartCommandWasReceived() {
        this.hasReceivedValidStartCommand = true
    }
}
