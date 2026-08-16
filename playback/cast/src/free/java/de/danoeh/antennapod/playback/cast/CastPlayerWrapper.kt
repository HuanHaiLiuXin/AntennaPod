package de.danoeh.antennapod.playback.cast

import android.content.Context
import androidx.media3.common.Player

class CastPlayerWrapper {
    companion object {
        @JvmStatic
        fun wrap(player: Player, context: Context): Player {
            return player
        }

        @JvmStatic
        fun hasPlaybackJustFinished(context: Context): Boolean {
            return false
        }
    }
}
