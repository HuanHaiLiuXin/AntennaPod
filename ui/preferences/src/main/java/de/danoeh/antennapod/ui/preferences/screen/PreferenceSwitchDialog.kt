package de.danoeh.antennapod.ui.preferences.screen

import android.content.Context
import android.view.LayoutInflater
import android.view.View

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

import de.danoeh.antennapod.ui.preferences.R

open class PreferenceSwitchDialog(protected val context: Context, private val title: String?, private val text: String) {
    private var onPreferenceChangedListener: OnPreferenceChangedListener? = null

    interface OnPreferenceChangedListener {
        /**
         * Notified when user confirms preference
         *
         * @param enabled The preference
         */

        fun preferenceChanged(enabled: Boolean)
    }

    fun openDialog() {

        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(title)

        val inflater = LayoutInflater.from(this.context)
        val layout = inflater.inflate(R.layout.dialog_switch_preference, null, false)
        val switchButton = layout.findViewById<MaterialSwitch>(R.id.dialogSwitch)
        switchButton.setText(text)
        builder.setView(layout)

        builder.setPositiveButton(R.string.confirm_label) { dialog, which ->
            if (onPreferenceChangedListener != null) {
                onPreferenceChangedListener!!.preferenceChanged(switchButton.isChecked())
            }
        }
        builder.setNegativeButton(R.string.cancel_label, null)
        builder.create().show()
    }

    fun setOnPreferenceChangedListener(onPreferenceChangedListener: OnPreferenceChangedListener) {
        this.onPreferenceChangedListener = onPreferenceChangedListener
    }
}
