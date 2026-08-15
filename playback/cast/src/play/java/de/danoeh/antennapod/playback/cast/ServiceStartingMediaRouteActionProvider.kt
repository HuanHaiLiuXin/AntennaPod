package de.danoeh.antennapod.playback.cast

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.mediarouter.app.MediaRouteActionProvider
import androidx.mediarouter.app.MediaRouteButton

/**
 * Shows the currently playing episode directly after connecting to the cast device
 * by starting the playback service.
 */
class ServiceStartingMediaRouteActionProvider : MediaRouteActionProvider {
    companion object {
        private const val TAG = "SrvStartMediaRouteBtn"
    }

    constructor(context: Context) : super(context) {
    }

    override fun onCreateMediaRouteButton(): MediaRouteButton {
        return ServiceStartingMediaRouteButton(getContext())
    }

    private class ServiceStartingMediaRouteButton : MediaRouteButton {
        constructor(context: Context) : super(context) {
        }

        override fun performClick(): Boolean {
            val intent = Intent()
            intent.setComponent(ComponentName(getContext(),
                    "de.danoeh.antennapod.playback.service.Media3PlaybackService"))
            try {
                getContext().startService(intent)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Unable to start playback service", e)
            }
            return super.performClick()
        }
    }
}
