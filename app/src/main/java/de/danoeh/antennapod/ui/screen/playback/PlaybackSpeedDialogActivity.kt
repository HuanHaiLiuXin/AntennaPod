package de.danoeh.antennapod.ui.screen.playback

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import de.danoeh.antennapod.ui.common.ThemeSwitcher

class PlaybackSpeedDialogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeSwitcher.getTranslucentTheme(this))
        super.onCreate(savedInstanceState)
        val speedDialog = InnerVariableSpeedDialog()
        speedDialog.show(getSupportFragmentManager(), null)
    }

    class InnerVariableSpeedDialog : VariableSpeedDialog() {
        override fun onDismiss(dialog: DialogInterface) {
            super.onDismiss(dialog)
            getActivity()!!.finish()
        }
    }
}
