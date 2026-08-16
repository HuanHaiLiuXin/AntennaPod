package de.danoeh.antennapod.ui.appstartintent

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Launches the playback speed dialog activity of the app with specific arguments.
 * Does not require a dependency on the actual implementation of the activity.
 */
class PlaybackSpeedActivityStarter(context: Context) {
    companion object {
        const val INTENT = "de.danoeh.antennapod.intents.PLAYBACK_SPEED"
    }

    private val intent: Intent
    private val context: Context

    init {
        this.context = context
        intent = Intent(INTENT)
        intent.setPackage(context.getPackageName())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
    }

    fun getIntent(): Intent {
        return intent
    }

    fun getPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(context, R.id.pending_intent_playback_speed, getIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun start() {
        context.startActivity(getIntent())
    }
}
