package de.danoeh.antennapod.ui.screen.playback

import android.app.Activity
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.event.PlayerErrorEvent

class MediaPlayerErrorDialog {
    companion object {
        @JvmStatic
        fun show(activity: Activity, event: PlayerErrorEvent) {
            val errorDialog = MaterialAlertDialogBuilder(activity)
            errorDialog.setTitle(R.string.error_label)

            val genericMessage = activity.getString(R.string.playback_error_generic)
            val errorMessage = SpannableString(genericMessage + "\n\n" + event.getMessage())
            errorMessage.setSpan(ForegroundColorSpan(0x88888888.toInt()),
                    genericMessage.length, errorMessage.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            errorDialog.setMessage(errorMessage)
            errorDialog.setPositiveButton(android.R.string.ok) { dialog, which ->
                if (activity is MainActivity) {
                    activity.getBottomSheet().setState(BottomSheetBehavior.STATE_COLLAPSED)
                }
            }
            errorDialog.create().show()
        }
    }
}
