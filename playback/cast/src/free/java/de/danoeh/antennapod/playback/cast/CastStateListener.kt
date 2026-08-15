package de.danoeh.antennapod.playback.cast

import android.content.Context

open class CastStateListener {

    constructor(context: Context) {
    }

    fun destroy() {
    }

    open fun onSessionStartedOrEnded() {
    }
}
