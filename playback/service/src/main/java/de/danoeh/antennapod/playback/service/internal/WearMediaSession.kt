package de.danoeh.antennapod.playback.service.internal

import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

class WearMediaSession {
    companion object {
        /**
         * Take a custom action builder and make sure the custom action shows on Wear OS because this is the Play version
         * of the app.
         */
        @JvmStatic
        fun addWearExtrasToAction(actionBuilder: PlaybackStateCompat.CustomAction.Builder) {
            val actionExtras = Bundle()
            actionExtras.putBoolean("android.support.wearable.media.extra.CUSTOM_ACTION_SHOW_ON_WEAR", true)
            actionBuilder.setExtras(actionExtras)
        }

        @JvmStatic
        fun mediaSessionSetExtraForWear(mediaSession: MediaSessionCompat) {
            val sessionExtras = Bundle()
            sessionExtras.putBoolean("android.support.wearable.media.extra.RESERVE_SLOT_SKIP_TO_PREVIOUS", false)
            sessionExtras.putBoolean("android.support.wearable.media.extra.RESERVE_SLOT_SKIP_TO_NEXT", false)
            mediaSession.setExtras(sessionExtras)
        }
    }
}
