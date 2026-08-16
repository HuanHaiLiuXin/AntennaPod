package de.danoeh.antennapod.playback.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.KeyEvent
import de.danoeh.antennapod.playback.base.BuildConfig

/**
 * Receives media button events.
 */
class MediaButtonReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "MediaButtonReceiver"
        const val EXTRA_KEYCODE = "de.danoeh.antennapod.core.service.extra.MediaButtonReceiver.KEYCODE"
        const val EXTRA_CUSTOM_ACTION =
                "de.danoeh.antennapod.core.service.extra.MediaButtonReceiver.CUSTOM_ACTION"
        const val EXTRA_SOURCE = "de.danoeh.antennapod.core.service.extra.MediaButtonReceiver.SOURCE"
        const val EXTRA_HARDWAREBUTTON
                = "de.danoeh.antennapod.core.service.extra.MediaButtonReceiver.HARDWAREBUTTON"
        const val PLAYBACK_SERVICE_INTENT = "de.danoeh.antennapod.intents.PLAYBACK_SERVICE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent")
        if (intent == null || intent.getExtras() == null || BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
            return
        }
        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
        if (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            val serviceIntent = Intent(PLAYBACK_SERVICE_INTENT)
            serviceIntent.setPackage(context.getPackageName())
            serviceIntent.putExtra(EXTRA_KEYCODE, event.getKeyCode())
            serviceIntent.putExtra(EXTRA_SOURCE, event.getSource())
            serviceIntent.putExtra(EXTRA_HARDWAREBUTTON, event.getEventTime() > 0 || event.getDownTime() > 0)
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

}
