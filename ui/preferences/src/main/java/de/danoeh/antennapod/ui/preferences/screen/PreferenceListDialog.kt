package de.danoeh.antennapod.ui.preferences.screen

import android.content.Context

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import de.danoeh.antennapod.ui.preferences.R

open class PreferenceListDialog(protected val context: Context, private val title: String?) {
    private var onPreferenceChangedListener: OnPreferenceChangedListener? = null
    private var selectedPos = 0

    interface OnPreferenceChangedListener {
        /**
         * Notified when user confirms preference
         *
         * @param pos The index of the item that was selected
         */

        fun preferenceChanged(pos: Int)
    }

    fun openDialog(items: Array<String>) {

        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(title)
        builder.setSingleChoiceItems(items, selectedPos) { dialog, which ->
            selectedPos = which
        }
        builder.setPositiveButton(R.string.confirm_label) { dialog, which ->
            if (onPreferenceChangedListener != null && selectedPos >= 0) {
                onPreferenceChangedListener!!.preferenceChanged(selectedPos)
            }
        }
        builder.setNegativeButton(R.string.cancel_label, null)
        builder.create().show()
    }

    fun setOnPreferenceChangedListener(onPreferenceChangedListener: OnPreferenceChangedListener) {
        this.onPreferenceChangedListener = onPreferenceChangedListener
    }
}
