package de.danoeh.antennapod.ui.screen.preferences

import android.os.Bundle
import android.text.InputType

import androidx.appcompat.app.AlertDialog
import androidx.preference.SwitchPreferenceCompat

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import de.danoeh.antennapod.R
import de.danoeh.antennapod.databinding.DialogSetPasswordBinding
import de.danoeh.antennapod.ui.common.Keyboard
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.preferences.screen.AnimatedPreferenceFragment


class ParentalControlPreferencesFragment : AnimatedPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_parental_control)
        setupPreferences()
    }

    override fun onStart() {
        super.onStart()
        (getActivity() as PreferenceActivity).getSupportActionBar()!!.setTitle(R.string.pref_parental_control_title)
    }

    private fun setupPreferences() {
        val enabledPref = findPreference<SwitchPreferenceCompat>(UserPreferences.PREF_PARENTAL_CONTROL_ENABLED)!!
        enabledPref.setChecked(UserPreferences.isParentalControlPasswordSet())
        enabledPref.setOnPreferenceChangeListener { pref, newValue ->
            if (newValue as Boolean) {
                showSetNewPasswordDialog()
            } else {
                UserPreferences.clearParentalControlPassword()
                enabledPref.setChecked(false)
                updateRequireSubscribeEnabled()
            }
            false
        }
        updateRequireSubscribeEnabled()
    }

    private fun updateRequireSubscribeEnabled() {
        val requireSubscribePref =
                findPreference<SwitchPreferenceCompat>(UserPreferences.PREF_PARENTAL_CONTROL_REQUIRE_SUBSCRIBE)!!
        requireSubscribePref.setEnabled(UserPreferences.isParentalControlPasswordSet())
    }

    private fun showSetNewPasswordDialog() {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(R.string.pref_parental_control_set_password)
        val dialogBinding = DialogSetPasswordBinding.inflate(getLayoutInflater())
        dialogBinding.textInput.setHint(R.string.password_label)
        dialogBinding.textInput.setInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        dialogBinding.textInput2.setHint(R.string.pref_parental_control_confirm_password)
        dialogBinding.textInput2.setInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        builder.setView(dialogBinding.getRoot())
        builder.setPositiveButton(R.string.confirm_label, null)
        builder.setNegativeButton(R.string.cancel_label, null)
        val alertDialog = builder.create()
        alertDialog.show()
        dialogBinding.textInput.requestFocus()
        Keyboard.show(requireContext(), dialogBinding.textInput)

        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newPassword = dialogBinding.textInput.getText().toString()
            val confirmPassword = dialogBinding.textInput2.getText().toString()
            if (newPassword.isEmpty()) {
                dialogBinding.textInputLayout.setError(getString(R.string.pref_parental_control_password_empty))
                return@setOnClickListener
            }
            if (newPassword != confirmPassword) {
                dialogBinding.textInputLayout2.setError(getString(R.string.pref_parental_control_passwords_dont_match))
                return@setOnClickListener
            }
            UserPreferences.setParentalControlPassword(newPassword)
            alertDialog.dismiss()
            val enabledPref = findPreference<SwitchPreferenceCompat>(UserPreferences.PREF_PARENTAL_CONTROL_ENABLED)!!
            enabledPref.setChecked(true)
            updateRequireSubscribeEnabled()
        }
    }
}
