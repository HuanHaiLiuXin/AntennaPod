package de.danoeh.antennapod.playback.cast

import androidx.appcompat.app.AppCompatActivity

import android.view.Menu

/**
 * Activity that allows for showing the MediaRouter button whenever there's a cast device in the
 * network.
 */
abstract class CastEnabledActivity : AppCompatActivity() {
    companion object {
        const val TAG = "CastEnabledActivity"
    }

    fun requestCastButton(menu: Menu) {
        // no-op
    }
}
