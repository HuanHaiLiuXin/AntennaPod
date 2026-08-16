package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.content.Context
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.ui.preferences.R
import de.danoeh.antennapod.ui.preferences.databinding.AuthenticationDialogBinding

/**
 * Displays a dialog with a username and password text field and an optional checkbox to save username and preferences.
 */
abstract class AuthenticationDialog(context: Context, titleRes: Int, enableUsernameField: Boolean,
                                    usernameInitialValue: String?, passwordInitialValue: String?) :
        MaterialAlertDialogBuilder(context) {
    internal var passwordHidden = true

    init {
        setTitle(titleRes)
        val viewBinding = AuthenticationDialogBinding.inflate(LayoutInflater.from(context))
        setView(viewBinding.getRoot())

        viewBinding.usernameEditText.setEnabled(enableUsernameField)
        if (usernameInitialValue != null) {
            viewBinding.usernameEditText.setText(usernameInitialValue)
        }
        if (passwordInitialValue != null) {
            viewBinding.passwordEditText.setText(passwordInitialValue)
        }
        viewBinding.showPasswordButton.setOnClickListener {
            if (passwordHidden) {
                viewBinding.passwordEditText.setTransformationMethod(HideReturnsTransformationMethod.getInstance())
                viewBinding.showPasswordButton.setAlpha(1.0f)
            } else {
                viewBinding.passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance())
                viewBinding.showPasswordButton.setAlpha(0.6f)
            }
            passwordHidden = !passwordHidden
        }

        setOnCancelListener { onCancelled() }
        setNegativeButton(R.string.cancel_label) { dialog, which -> onCancelled() }
        setPositiveButton(R.string.confirm_label) { dialog, which ->
            onConfirmed(viewBinding.usernameEditText.getText().toString(),
                    viewBinding.passwordEditText.getText().toString()) }
    }

    protected open fun onCancelled() {

    }

    protected abstract fun onConfirmed(username: String, password: String)
}
