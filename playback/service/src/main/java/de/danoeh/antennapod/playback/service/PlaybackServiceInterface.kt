package de.danoeh.antennapod.playback.service

abstract class PlaybackServiceInterface {
    companion object {
        const val EXTRA_PLAYABLE = "PlaybackService.PlayableExtra"
        const val EXTRA_ALLOW_STREAM_THIS_TIME = "extra.de.danoeh.antennapod.core.service.allowStream"
        const val EXTRA_ALLOW_STREAM_ALWAYS = "extra.de.danoeh.antennapod.core.service.allowStreamAlways"

        const val ACTION_PLAYER_NOTIFICATION
                = "action.de.danoeh.antennapod.core.service.playerNotification"
        const val EXTRA_NOTIFICATION_CODE = "extra.de.danoeh.antennapod.core.service.notificationCode"
        const val EXTRA_NOTIFICATION_TYPE = "extra.de.danoeh.antennapod.core.service.notificationType"
        const val NOTIFICATION_TYPE_PLAYBACK_END = 7
        const val NOTIFICATION_TYPE_RELOAD = 3
        const val EXTRA_CODE_AUDIO = 1 // Used in NOTIFICATION_TYPE_RELOAD
        const val EXTRA_CODE_VIDEO = 2
        const val EXTRA_CODE_CAST = 3

        const val ACTION_SHUTDOWN_PLAYBACK_SERVICE
                = "action.de.danoeh.antennapod.core.service.actionShutdownPlaybackService"
    }
}
