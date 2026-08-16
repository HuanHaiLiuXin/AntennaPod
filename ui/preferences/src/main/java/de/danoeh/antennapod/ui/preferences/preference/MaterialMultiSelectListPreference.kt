package de.danoeh.antennapod.ui.preferences.preference

import android.content.Context
import android.content.DialogInterface
import android.util.AttributeSet
import androidx.preference.MultiSelectListPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import java.util.HashSet

class MaterialMultiSelectListPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        MultiSelectListPreference(context, attrs) {

    override fun onClick() {
        val builder = MaterialAlertDialogBuilder(getContext())
        builder.setTitle(getTitle())
        builder.setIcon(getDialogIcon())
        builder.setNegativeButton(getNegativeButtonText(), null)

        val selected = BooleanArray(getEntries().size)
        val values = getEntryValues()
        for (i in values.indices) {
            selected[i] = getValues().contains(values[i].toString())
        }
        builder.setMultiChoiceItems(getEntries(), selected) { dialog: DialogInterface, which: Int, isChecked: Boolean ->
            selected[which] = isChecked }
        builder.setPositiveButton(android.R.string.ok) { dialog, which ->
            val selectedValues = HashSet<String>()
            for (i in values.indices) {
                if (selected[i]) {
                    selectedValues.add(getEntryValues()!![i].toString())
                }
            }
            setValues(selectedValues)
        }
        builder.show()
    }
}
