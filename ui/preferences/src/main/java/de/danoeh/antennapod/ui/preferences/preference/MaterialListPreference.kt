package de.danoeh.antennapod.ui.preferences.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder

open class MaterialListPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ListPreference(context, attrs) {

    override fun onClick() {
        if (getOnPreferenceClickListener() != null && getOnPreferenceClickListener()!!.onPreferenceClick(this)) {
            return
        }
        val builder = MaterialAlertDialogBuilder(getContext())
        builder.setTitle(getTitle())
        builder.setIcon(getDialogIcon())
        builder.setNegativeButton(getNegativeButtonText(), null)

        val values = getEntryValues()
        var selected = -1
        for (i in values.indices) {
            if (values[i].toString() == getValue()) {
                selected = i
            }
        }
        builder.setSingleChoiceItems(getEntries(), selected) { dialog, which ->
            dialog.dismiss()
            if (which >= 0 && getEntryValues() != null) {
                val value = getEntryValues()!![which].toString()
                if (callChangeListener(value)) {
                    setValue(value)
                }
            }
        }
        builder.show()
    }
}
