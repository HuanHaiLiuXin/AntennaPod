package de.danoeh.antennapod.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.View
import android.widget.TextView

import androidx.annotation.StringRes

import com.google.android.material.snackbar.Snackbar

/**
 * Utilities for commonly used clipboard functionality.
 */
class ClipboardUtils {
    companion object {
        /**
         * Utility function used to copy the text from the given TextView to the clipboard.
         * @param textView TextView to copy the text from.
         * @param labelId ID of string resource to used as label for the copied text within the clipboard.
         */
        @JvmStatic
        fun copyText(textView: TextView, @StringRes labelId: Int) {
            val context = textView.getContext()
            copyText(textView, context.getString(labelId), context.getString(R.string.copied_to_clipboard),
                    textView.getText().toString())
        }

        @JvmStatic
        fun copyText(textView: TextView) {
            val context = textView.getContext()
            copyText(textView, "AntennaPod", context.getString(R.string.copied_to_clipboard),
                    textView.getText().toString())
        }

        /**
         * Utility function used to copy the given text to the clipboard. The give View is used for context
         * and to display a confirmation Toast to the user on completion where the SDK level is prior to 32.
         * @param view View to copy the text from.
         * @param labelId ID of string resource to use as label for the copied text within the clipboard buffer.
         * @param text Text to copy to the clipboard.
         */
        @JvmStatic
        fun copyText(view: View, @StringRes labelId: Int, text: String) {
            val context = view.getContext()
            copyText(view, context.getString(labelId), context.getString(R.string.copied_to_clipboard), text)
        }

        private fun copyText(view: View, label: String, message: String, text: String) {
            val clipboard = view.getContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))

            if (Build.VERSION.SDK_INT < 32) {
                Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show()
            }
        }
    }
}
