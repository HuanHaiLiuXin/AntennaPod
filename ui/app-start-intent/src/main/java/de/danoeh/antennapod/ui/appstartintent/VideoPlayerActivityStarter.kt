package de.danoeh.antennapod.ui.appstartintent

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Launches the video player activity of the app with specific arguments.
 * Does not require a dependency on the actual implementation of the activity.
 */
class VideoPlayerActivityStarter(context: Context) {
    companion object {
        const val INTENT = "de.danoeh.antennapod.intents.VIDEO_PLAYER"
        const val INTENT_MEDIA3 = "de.danoeh.antennapod.intents.VIDEO_PLAYER_MEDIA3"
    }

    private val intent: Intent
    private val context: Context

    init {
        this.context = context
        intent = Intent(if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) INTENT_MEDIA3 else INTENT)
        intent.setPackage(context.getPackageName())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
    }

    fun getIntent(): Intent {
        return intent
    }

    fun getPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(context, R.id.pending_intent_video_player, getIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun start() {
        context.startActivity(getIntent())
    }
}
