package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.DialogFragment
import de.danoeh.antennapod.net.common.AntennapodHttpClient
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.net.sync.nextcloud.NextcloudLoginFlow
import de.danoeh.antennapod.ui.preferences.R
import de.danoeh.antennapod.ui.preferences.databinding.NextcloudAuthDialogBinding

/**
 * Guides the user through the authentication process.
 */
class NextcloudAuthenticationFragment : DialogFragment(),
        NextcloudLoginFlow.AuthenticationCallback {
    companion object {
        const val TAG = "NextcloudAuthenticationFragment"
        private const val EXTRA_LOGIN_FLOW = "LoginFlow"
    }

    private var viewBinding: NextcloudAuthDialogBinding? = null
    private var nextcloudLoginFlow: NextcloudLoginFlow? = null
    private var shouldDismiss = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = MaterialAlertDialogBuilder(requireContext())
        dialog.setTitle(R.string.gpodnetauth_login_butLabel)
        dialog.setNegativeButton(R.string.cancel_label, null)
        dialog.setCancelable(false)
        this.setCancelable(false)

        viewBinding = NextcloudAuthDialogBinding.inflate(getLayoutInflater())
        dialog.setView(viewBinding!!.getRoot())

        viewBinding!!.chooseHostButton.setOnClickListener {
            nextcloudLoginFlow = NextcloudLoginFlow(AntennapodHttpClient.getHttpClient(),
                    viewBinding!!.serverUrlText.getText().toString(), requireContext(), this)
            startLoginFlow()
        }
        if (savedInstanceState != null && savedInstanceState.getStringArrayList(EXTRA_LOGIN_FLOW) != null) {
            nextcloudLoginFlow = NextcloudLoginFlow.fromInstanceState(AntennapodHttpClient.getHttpClient(),
                    requireContext(), this, savedInstanceState.getStringArrayList(EXTRA_LOGIN_FLOW)!!)
            startLoginFlow()
        }
        return dialog.create()
    }

    private fun startLoginFlow() {
        viewBinding!!.chooseHostButton.setVisibility(View.GONE)
        viewBinding!!.loginProgressContainer.setVisibility(View.VISIBLE)
        viewBinding!!.serverUrlText.setEnabled(false)
        nextcloudLoginFlow!!.start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (nextcloudLoginFlow != null) {
            outState.putStringArrayList(EXTRA_LOGIN_FLOW, nextcloudLoginFlow!!.saveInstanceState())
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (nextcloudLoginFlow != null) {
            nextcloudLoginFlow!!.cancel()
        }
    }

    override fun onResume() {
        super.onResume()
        if (shouldDismiss) {
            dismiss()
        }
    }

    override fun onNextcloudAuthenticated(server: String, username: String, password: String) {
        SynchronizationSettings.setSelectedSyncProvider(
                SynchronizationProvider.NEXTCLOUD_GPODDER.getIdentifier())
        SynchronizationCredentials.clear()
        SynchronizationQueue.getInstance()!!.clear()
        SynchronizationCredentials.setPassword(password)
        SynchronizationCredentials.setHosturl(server)
        SynchronizationCredentials.setUsername(username)
        SynchronizationQueue.getInstance()!!.fullSync()
        if (isResumed()) {
            dismiss()
        } else {
            shouldDismiss = true
        }
    }

    override fun onNextcloudAuthError(errorMessage: String?) {
        viewBinding!!.loginProgressContainer.setVisibility(View.GONE)
        viewBinding!!.chooseHostButton.setVisibility(View.VISIBLE)
        viewBinding!!.serverUrlText.setEnabled(true)

        val errorDialog = MaterialAlertDialogBuilder(requireContext())
        errorDialog.setTitle(R.string.error_label)
        val genericMessage = getString(R.string.nextcloud_login_error_generic)
        val combinedMessage = SpannableString(genericMessage + "\n\n" + errorMessage)
        combinedMessage.setSpan(ForegroundColorSpan(0x88888888.toInt()),
                genericMessage.length, combinedMessage.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        errorDialog.setMessage(combinedMessage)
        errorDialog.setPositiveButton(android.R.string.ok, null)
        errorDialog.show()
    }
}
