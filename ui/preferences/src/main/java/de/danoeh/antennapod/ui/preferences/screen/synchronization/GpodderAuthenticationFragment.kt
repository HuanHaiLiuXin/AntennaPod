package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ViewFlipper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import de.danoeh.antennapod.net.common.AntennapodHttpClient
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationProvider
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.storage.preferences.SynchronizationCredentials
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.net.sync.gpoddernet.GpodnetService
import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetDevice
import de.danoeh.antennapod.ui.common.Keyboard
import de.danoeh.antennapod.ui.preferences.R
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers

import java.util.Locale
import java.util.regex.Pattern

/**
 * Guides the user through the authentication process.
 */
class GpodderAuthenticationFragment : DialogFragment() {
    companion object {
        const val TAG = "GpodnetAuthActivity"

        private const val STEP_DEFAULT = -1
        private const val STEP_HOSTNAME = 0
        private const val STEP_LOGIN = 1
        private const val STEP_DEVICE = 2
        private const val STEP_FINISH = 3
    }

    private var viewFlipper: ViewFlipper? = null

    private var currentStep = -1

    private var service: GpodnetService? = null
    @Volatile
    private var username: String? = null
    @Volatile
    private var password: String? = null
    @Volatile
    private var selectedDevice: GpodnetDevice? = null
    private var devices: List<GpodnetDevice>? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = MaterialAlertDialogBuilder(getContext()!!)
        dialog.setTitle(R.string.gpodnetauth_login_butLabel)
        dialog.setNegativeButton(R.string.cancel_label, null)
        dialog.setCancelable(false)
        this.setCancelable(false)

        val root = View.inflate(getContext(), R.layout.gpodnetauth_dialog, null)
        viewFlipper = root.findViewById(R.id.viewflipper)
        advance()
        dialog.setView(root)

        return dialog.create()
    }

    private fun setupHostView(view: View) {
        val selectHost = view.findViewById<Button>(R.id.chooseHostButton)
        val serverUrlText = view.findViewById<EditText>(R.id.serverUrlText)
        selectHost.setOnClickListener {
            if (serverUrlText.getText().length == 0) {
                return@setOnClickListener
            }
            SynchronizationCredentials.clear()
            SynchronizationQueue.getInstance()!!.clear()
            SynchronizationCredentials.setHosturl(serverUrlText.getText().toString())
            service = GpodnetService(AntennapodHttpClient.getHttpClient(),
                    SynchronizationCredentials.getHosturl()!!, SynchronizationCredentials.getDeviceId()!!,
                    SynchronizationCredentials.getUsername(), SynchronizationCredentials.getPassword())
            getDialog()!!.setTitle(SynchronizationCredentials.getHosturl())
            advance()
        }
    }

    private fun setupLoginView(view: View) {
        val usernameEditText = view.findViewById<EditText>(R.id.etxtUsername)
        val passwordEditText = view.findViewById<EditText>(R.id.etxtPassword)
        val login = view.findViewById<Button>(R.id.butLogin)
        val txtvError = view.findViewById<TextView>(R.id.credentialsError)
        val progressBar = view.findViewById<ProgressBar>(R.id.progBarLogin)
        val createAccountWarning = view.findViewById<TextView>(R.id.createAccountWarning)

        if (SynchronizationCredentials.getHosturl()!!.startsWith("http://")) {
            createAccountWarning.setVisibility(View.VISIBLE)
        }
        passwordEditText.setOnEditorActionListener { v, actionID, event ->
            actionID == EditorInfo.IME_ACTION_GO && login.performClick()
        }

        login.setOnClickListener {
            val usernameStr = usernameEditText.getText().toString()
            val passwordStr = passwordEditText.getText().toString()

            if (usernameHasUnwantedChars(usernameStr)) {
                txtvError.setText(R.string.gpodnetsync_username_characters_error)
                txtvError.setVisibility(View.VISIBLE)
                return@setOnClickListener
            }

            login.setEnabled(false)
            progressBar.setVisibility(View.VISIBLE)
            txtvError.setVisibility(View.GONE)
            Keyboard.hide(getActivity()!!)

            Completable.fromAction {
                service!!.setCredentials(usernameStr, passwordStr)
                service!!.login()
                devices = service!!.getDevices()
                this@GpodderAuthenticationFragment.username = usernameStr
                this@GpodderAuthenticationFragment.password = passwordStr
            }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        login.setEnabled(true)
                        progressBar.setVisibility(View.GONE)
                        advance()
                    }, { error ->
                        login.setEnabled(true)
                        progressBar.setVisibility(View.GONE)
                        txtvError.setText(error.cause!!.message)
                        txtvError.setVisibility(View.VISIBLE)
                    })

        }
    }

    private fun setupDeviceView(view: View) {
        val deviceName = view.findViewById<EditText>(R.id.deviceName)
        val devicesContainer = view.findViewById<LinearLayout>(R.id.devicesContainer)
        deviceName.setText(generateDeviceName())

        val createDeviceButton = view.findViewById<MaterialButton>(R.id.createDeviceButton)
        createDeviceButton.setOnClickListener { createDevice(view) }

        for (device in devices!!) {
            val row = View.inflate(getContext(), R.layout.gpodnetauth_device_row, null)
            val selectDeviceButton = row.findViewById<Button>(R.id.selectDeviceButton)
            selectDeviceButton.setOnClickListener {
                selectedDevice = device
                advance()
            }
            selectDeviceButton.setText(device.getCaption())
            devicesContainer.addView(row)
        }
    }

    private fun createDevice(view: View) {
        val deviceName = view.findViewById<EditText>(R.id.deviceName)
        val txtvError = view.findViewById<TextView>(R.id.deviceSelectError)
        val progBarCreateDevice = view.findViewById<ProgressBar>(R.id.progbarCreateDevice)

        val deviceNameStr = deviceName.getText().toString()
        if (isDeviceInList(deviceNameStr)) {
            return
        }
        progBarCreateDevice.setVisibility(View.VISIBLE)
        txtvError.setVisibility(View.GONE)
        deviceName.setEnabled(false)

        Observable.fromCallable<GpodnetDevice> {
            val deviceId = generateDeviceId(deviceNameStr)
            service!!.configureDevice(deviceId, deviceNameStr, GpodnetDevice.DeviceType.MOBILE)
            GpodnetDevice(deviceId, deviceNameStr, GpodnetDevice.DeviceType.MOBILE.toString(), 0)
        }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ device ->
                    progBarCreateDevice.setVisibility(View.GONE)
                    selectedDevice = device
                    advance()
                }, { error ->
                    deviceName.setEnabled(true)
                    progBarCreateDevice.setVisibility(View.GONE)
                    txtvError.setText(error.message)
                    txtvError.setVisibility(View.VISIBLE)
                })
    }

    private fun generateDeviceName(): String {
        val baseName = getString(R.string.gpodnetauth_device_name_default, Build.MODEL)
        var name = baseName
        var num = 1
        while (isDeviceInList(name)) {
            name = baseName + " (" + num + ")"
            num++
        }
        return name
    }

    private fun generateDeviceId(name: String): String {
        // devices names must be of a certain form:
        // https://gpoddernet.readthedocs.org/en/latest/api/reference/general.html#devices
        return name.replace("[^a-zA-Z0-9]".toRegex(), "_").lowercase(Locale.US)
    }

    private fun isDeviceInList(name: String): Boolean {
        if (devices == null) {
            return false
        }
        val id = generateDeviceId(name)
        for (device in devices!!) {
            if (device.getId() == id || device.getCaption() == name) {
                return true
            }
        }
        return false
    }

    private fun setupFinishView(view: View) {
        val sync = view.findViewById<Button>(R.id.butSyncNow)

        sync.setOnClickListener {
            dismiss()
            SynchronizationQueue.getInstance()!!.syncImmediately()
        }
    }

    private fun advance() {
        if (currentStep < STEP_FINISH) {
            val view = viewFlipper!!.getChildAt(currentStep + 1)
            if (currentStep == STEP_DEFAULT) {
                setupHostView(view)
            } else if (currentStep == STEP_HOSTNAME) {
                setupLoginView(view)
            } else if (currentStep == STEP_LOGIN) {
                if (username == null || password == null) {
                    throw IllegalStateException("Username and password must not be null here")
                } else {
                    setupDeviceView(view)
                }
            } else if (currentStep == STEP_DEVICE) {
                if (selectedDevice == null) {
                    throw IllegalStateException("Device must not be null here")
                } else {
                    SynchronizationSettings.setSelectedSyncProvider(
                            SynchronizationProvider.GPODDER_NET.getIdentifier())
                    SynchronizationCredentials.setUsername(username!!)
                    SynchronizationCredentials.setPassword(password!!)
                    SynchronizationCredentials.setDeviceId(selectedDevice!!.getId())
                    setupFinishView(view)
                }
            }
            if (currentStep != STEP_DEFAULT) {
                viewFlipper!!.showNext()
            }
            currentStep++
        } else {
            dismiss()
        }
    }

    private fun usernameHasUnwantedChars(username: String): Boolean {
        val special = Pattern.compile("[!@#$%&*()+=|<>?{}\\[\\]~]")
        val containsUnwantedChars = special.matcher(username)
        return containsUnwantedChars.find()
    }
}
