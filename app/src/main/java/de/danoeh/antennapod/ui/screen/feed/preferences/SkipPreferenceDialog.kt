package de.danoeh.antennapod.ui.screen.feed.preferences

import android.content.Context
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import java.text.NumberFormat
import java.util.Locale

import de.danoeh.antennapod.R
import de.danoeh.antennapod.storage.preferences.UserPreferences

/**
 * Shows the dialog that allows setting the skip time.
 */
class SkipPreferenceDialog {
    companion object {
        @JvmStatic
        fun showSkipPreference(context: Context, direction: SkipDirection, textView: TextView?) {
            var checked = 0

            val skipSecs: Int
            if (direction == SkipDirection.SKIP_FORWARD) {
                skipSecs = UserPreferences.getFastForwardSecs()
            } else {
                skipSecs = UserPreferences.getRewindSecs()
            }

            val values = context.getResources().getIntArray(R.array.seek_delta_values)
            val choices = arrayOfNulls<String>(values.size)
            for (i in values.indices) {
                if (skipSecs == values[i]) {
                    checked = i
                }
                choices[i] = String.format(Locale.getDefault(),
                        "%d %s", values[i], context.getString(R.string.time_seconds))
            }

            val builder = MaterialAlertDialogBuilder(context)
            builder.setTitle(if (direction == SkipDirection.SKIP_FORWARD) R.string.pref_fast_forward else R.string.pref_rewind)
            builder.setSingleChoiceItems(choices, checked) { dialog, which ->
                val choice = (dialog as AlertDialog).getListView().getCheckedItemPosition()
                if (choice < 0 || choice >= values.size) {
                    System.err.printf("Choice in showSkipPreference is out of bounds %d", choice)
                } else {
                    val seconds = values[choice]
                    if (direction == SkipDirection.SKIP_FORWARD) {
                        UserPreferences.setFastForwardSecs(seconds)
                    } else {
                        UserPreferences.setRewindSecs(seconds)
                    }
                    if (textView != null) {
                        textView.setText(NumberFormat.getInstance().format(seconds))
                    }
                    dialog.dismiss()
                }
            }
            builder.setNegativeButton(R.string.cancel_label, null)
            builder.show()
        }
    }

    enum class SkipDirection {
        SKIP_FORWARD, SKIP_REWIND
    }
}
