package de.danoeh.antennapod.ui.preferences.screen

import android.content.Context
import android.text.InputType
import android.view.LayoutInflater

import androidx.appcompat.app.AlertDialog

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import de.danoeh.antennapod.ui.preferences.R
import de.danoeh.antennapod.ui.common.Keyboard
import de.danoeh.antennapod.ui.common.databinding.EditTextDialogBinding
import de.danoeh.antennapod.storage.preferences.UserPreferences

class ParentalControlDialog {
    companion object {
        @JvmStatic
        fun show(context: Context, onSuccess: Runnable) {
            show(context, onSuccess, null)
        }

        @JvmStatic
        fun show(context: Context, onSuccess: Runnable, onCancel: Runnable?) {
            val builder = MaterialAlertDialogBuilder(context)
            builder.setTitle(R.string.pref_parental_control_title)
            val dialogBinding = EditTextDialogBinding.inflate(LayoutInflater.from(context))
            dialogBinding.textInput.setHint(R.string.password_label)
            dialogBinding.textInput.setInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            builder.setView(dialogBinding.getRoot())
            builder.setPositiveButton(R.string.confirm_label, null)
            if (onCancel != null) {
                builder.setNegativeButton(R.string.cancel_label) { d, w -> onCancel.run() }
                builder.setOnCancelListener { onCancel.run() }
            } else {
                builder.setNegativeButton(R.string.cancel_label, null)
            }
            val alertDialog = builder.create()
            alertDialog.show()
            dialogBinding.textInput.requestFocus()
            Keyboard.show(context, dialogBinding.textInput)

            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entered = dialogBinding.textInput.getText().toString()
                if (UserPreferences.verifyParentalControlPassword(entered)) {
                    alertDialog.dismiss()
                    onSuccess.run()
                } else {
                    dialogBinding.textInputLayout.setError(context.getString(R.string.wrong_password))
                }
            }
        }
    }
}
