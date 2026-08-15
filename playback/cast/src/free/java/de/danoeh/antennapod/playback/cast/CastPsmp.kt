package de.danoeh.antennapod.playback.cast

import android.content.Context
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer

/**
 * Stub implementation of CastPsmp for Free build flavour
 */
class CastPsmp {
    companion object {
        @JvmStatic
        fun getInstanceIfConnected(context: Context,
                                   callback: PlaybackServiceMediaPlayer.PSMPCallback): PlaybackServiceMediaPlayer? {
            return null
        }
    }
}
