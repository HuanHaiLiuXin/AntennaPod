package de.danoeh.antennapod.ui.screen.feed.preferences

import android.content.Context
import android.view.View
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R

/**
 * Displays a dialog with a username and password text field and an optional checkbox to save username and preferences.
 */
abstract class FeedPreferenceSkipDialog(context: Context, skipIntroInitialValue: Int,
                                        skipEndInitialValue: Int) : MaterialAlertDialogBuilder(context) {

    init {
        setTitle(R.string.pref_feed_skip)
        val rootView = View.inflate(context, R.layout.feed_pref_skip_dialog, null)
        setView(rootView)

        val etxtSkipIntro = rootView.findViewById<EditText>(R.id.etxtSkipIntro)
        val etxtSkipEnd = rootView.findViewById<EditText>(R.id.etxtSkipEnd)

        etxtSkipIntro.setText(skipIntroInitialValue.toString())
        etxtSkipEnd.setText(skipEndInitialValue.toString())

        setNegativeButton(R.string.cancel_label, null)
        setPositiveButton(R.string.confirm_label) { dialog, which ->
            var skipIntro: Int
            var skipEnding: Int
            try {
                skipIntro = Integer.parseInt(etxtSkipIntro.getText().toString())
            } catch (e: NumberFormatException) {
                skipIntro = 0
            }

            try {
                skipEnding = Integer.parseInt(etxtSkipEnd.getText().toString())
            } catch (e: NumberFormatException) {
                skipEnding = 0
            }
            onConfirmed(skipIntro, skipEnding)
        }
    }

    protected abstract fun onConfirmed(skipIntro: Int, skipEndig: Int)
}
