package de.danoeh.antennapod.ui.appstartintent

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent

abstract class MediaButtonStarter {
    companion object {
        private const val INTENT = "de.danoeh.antennapod.NOTIFY_BUTTON_RECEIVER"
        private const val MEDIA3_PLAYBACK_SERVICE =
                "de.danoeh.antennapod.playback.service.Media3PlaybackService"
        const val EXTRA_MEDIA_BUTTON_SOURCE = "media_button_source"
        const val MEDIA_BUTTON_SOURCE_WIDGET = "widget"

        @JvmStatic
        fun createIntent(context: Context, eventCode: Int): Intent {
            val event = KeyEvent(KeyEvent.ACTION_DOWN, eventCode)
            val startingIntent = Intent(if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE)
                Intent.ACTION_MEDIA_BUTTON else INTENT)
            startingIntent.setPackage(context.getPackageName())
            startingIntent.putExtra(Intent.EXTRA_KEY_EVENT, event)
            return startingIntent
        }

        @JvmStatic
        fun createPendingIntent(context: Context, eventCode: Int): PendingIntent {
            if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
                val intent = createIntent(context, eventCode)
                        .setComponent(ComponentName(context, MEDIA3_PLAYBACK_SERVICE))
                        .putExtra(EXTRA_MEDIA_BUTTON_SOURCE, MEDIA_BUTTON_SOURCE_WIDGET)
                return PendingIntent.getService(context, eventCode, intent, PendingIntent.FLAG_IMMUTABLE)
            }
            return PendingIntent.getBroadcast(context, eventCode, createIntent(context, eventCode),
                    PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
