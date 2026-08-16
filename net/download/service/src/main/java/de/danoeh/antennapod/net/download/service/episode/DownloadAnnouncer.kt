package de.danoeh.antennapod.net.download.service.episode

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import de.danoeh.antennapod.net.download.service.R

abstract class DownloadAnnouncer {
    companion object {
        @JvmStatic
        fun announceStart(context: Context, episodeTitle: String) {
            announce(context, R.string.download_started_talkback, episodeTitle)
        }

        @JvmStatic
        fun announceCompleted(context: Context, episodeTitle: String) {
            announce(context, R.string.download_completed_talkback, episodeTitle)
        }

        private fun announce(context: Context, messageTemplate: Int, episodeTitle: String) {
            val message = context.getString(messageTemplate, episodeTitle)
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            if (am != null && am.isEnabled()) {
                val event = AccessibilityEvent.obtain()
                event.setEventType(AccessibilityEvent.TYPE_ANNOUNCEMENT)
                event.getText().add(message)
                am.sendAccessibilityEvent(event)
            }
        }
    }
}
