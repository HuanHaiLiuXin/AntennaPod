package de.danoeh.antennapod.playback.service

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.view.KeyEvent

import androidx.annotation.RequiresApi

import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter

@RequiresApi(api = Build.VERSION_CODES.N)
class QuickSettingsTileService : TileService() {

    companion object {
        private const val TAG = "QuickSettingsTileSvc"
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        sendBroadcast(MediaButtonStarter.createIntent(this, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }

    // Update the tile status when TileService.requestListeningState() is called elsewhere
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    // Without this, the tile may not be in the correct state after boot
    override fun onBind(intent: Intent): IBinder {
        TileService.requestListeningState(this, ComponentName(this, QuickSettingsTileService::class.java))
        return super.onBind(intent)!!
    }

    fun updateTile() {
        val qsTile = getQsTile()
        if (qsTile == null) {
            Log.d(TAG, "Ignored call to update QS tile: getQsTile() returned null.")
        } else {
            val isPlaying = PlaybackService.isRunning
                    && PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PLAYING
            qsTile.setState(if (isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE)
            qsTile.updateTile()
        }
    }
}
