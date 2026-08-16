package de.danoeh.antennapod.ui.preferences.screen

import android.app.Dialog
import android.content.Context
import android.content.res.TypedArray
import android.os.Build
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketAddress
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.TimeUnit

import de.danoeh.antennapod.ui.preferences.R
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.net.common.AntennapodHttpClient
import de.danoeh.antennapod.model.download.ProxyConfig
import de.danoeh.antennapod.ui.common.ThemeUtils
import de.danoeh.antennapod.ui.preferences.databinding.ProxySettingsBinding
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class ProxyDialog(private val context: Context) {
    private var dialog: AlertDialog? = null
    private var testSuccessful = false
    private var disposable: Disposable? = null
    private var viewBinding: ProxySettingsBinding? = null

    fun show(): Dialog {
        viewBinding = ProxySettingsBinding.bind(View.inflate(context, R.layout.proxy_settings, null))

        dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pref_proxy_title)
                .setView(viewBinding!!.getRoot())
                .setNegativeButton(R.string.cancel_label, null)
                .setPositiveButton(R.string.proxy_test_label, null)
                .setNeutralButton(R.string.reset, null)
                .show()
        // To prevent cancelling the dialog on button click
        dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!testSuccessful) {
                dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false)
                test()
                return@setOnClickListener
            }
            setProxyConfig()
            AntennapodHttpClient.reinit()
            dialog!!.dismiss()
        }

        dialog!!.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            viewBinding!!.hostText.getText().clear()
            viewBinding!!.portText.getText().clear()
            viewBinding!!.usernameText.getText().clear()
            viewBinding!!.passwordText.getText().clear()
            setProxyConfig()
        }

        val proxyConfig = UserPreferences.getProxyConfig()

        viewBinding!!.hostText.setText(proxyConfig.host)
        viewBinding!!.hostText.addTextChangedListener(requireTestOnChange)
        viewBinding!!.portText.setText(if (proxyConfig.port > 0) proxyConfig.port.toString() else "")
        viewBinding!!.portText.addTextChangedListener(requireTestOnChange)
        viewBinding!!.usernameText.setText(proxyConfig.username)
        viewBinding!!.usernameText.addTextChangedListener(requireTestOnChange)
        viewBinding!!.passwordText.setText(proxyConfig.password)
        viewBinding!!.passwordText.addTextChangedListener(requireTestOnChange)
        if (proxyConfig.type == Proxy.Type.DIRECT) {
            enableSettings(false)
            setTestRequired(false)
        }

        val types: MutableList<String> = ArrayList()
        types.add(Proxy.Type.DIRECT.name)
        types.add(Proxy.Type.HTTP.name)
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            types.add(Proxy.Type.SOCKS.name)
        }

        val adapter = ArrayAdapter(context,
                android.R.layout.simple_list_item_1, types)
        viewBinding!!.proxyTypeSpinner.setAdapter(adapter)
        viewBinding!!.proxyTypeSpinner.setThreshold(999)
        viewBinding!!.proxyTypeSpinner.setText(proxyConfig.type.name)
        viewBinding!!.proxyTypeSpinner.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                val isDirect = Proxy.Type.DIRECT.name == viewBinding!!.proxyTypeSpinner.getText().toString()
                if (isDirect) {
                    dialog!!.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE)
                } else {
                    dialog!!.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.VISIBLE)
                }
                enableSettings(!isDirect)
                setTestRequired(!isDirect)
            }
        })
        checkValidity()
        return dialog!!
    }

    private fun setProxyConfig() {
        val type = viewBinding!!.proxyTypeSpinner.getText().toString()
        val typeEnum = Proxy.Type.valueOf(type)
        val host = viewBinding!!.hostText.getText().toString()
        val port = viewBinding!!.portText.getText().toString()

        var username: String? = viewBinding!!.usernameText.getText().toString()
        if (TextUtils.isEmpty(username)) {
            username = null
        }
        var password: String? = viewBinding!!.passwordText.getText().toString()
        if (TextUtils.isEmpty(password)) {
            password = null
        }
        var portValue = 0
        if (!TextUtils.isEmpty(port)) {
            portValue = Integer.parseInt(port)
        }
        val config = ProxyConfig(typeEnum, host, portValue, username, password)
        UserPreferences.setProxyConfig(config)
        AntennapodHttpClient.setProxyConfig(config)
    }

    private val requireTestOnChange = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        }

        override fun afterTextChanged(s: Editable) {
            setTestRequired(true)
        }
    }

    private fun enableSettings(enable: Boolean) {
        viewBinding!!.hostText.setEnabled(enable)
        viewBinding!!.portText.setEnabled(enable)
        viewBinding!!.usernameText.setEnabled(enable)
        viewBinding!!.passwordText.setEnabled(enable)
    }

    private fun checkValidity(): Boolean {
        var valid = true
        if (Proxy.Type.DIRECT.name != viewBinding!!.proxyTypeSpinner.getText().toString()) {
            valid = checkHost()
        }
        valid = valid and checkPort()
        return valid
    }

    private fun checkHost(): Boolean {
        val host = viewBinding!!.hostText.getText().toString()
        if (host.isEmpty()) {
            viewBinding!!.hostText.setError(context.getString(R.string.proxy_host_empty_error))
            return false
        }
        if ("localhost" != host && !Patterns.DOMAIN_NAME.matcher(host).matches()) {
            viewBinding!!.hostText.setError(context.getString(R.string.proxy_host_invalid_error))
            return false
        }
        return true
    }

    private fun checkPort(): Boolean {
        val port = getPort()
        if (port < 0 || port > 65535) {
            viewBinding!!.portText.setError(context.getString(R.string.proxy_port_invalid_error))
            return false
        }
        return true
    }

    private fun getPort(): Int {
        val port = viewBinding!!.portText.getText().toString()
        if (!port.isEmpty()) {
            try {
                return Integer.parseInt(port)
            } catch (e: NumberFormatException) {
                // ignore
            }
        }
        return 0
    }

    private fun setTestRequired(required: Boolean) {
        if (required) {
            testSuccessful = false
            dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.proxy_test_label)
        } else {
            testSuccessful = true
            dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).setText(android.R.string.ok)
        }
        dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true)
    }

    private fun test() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        if (!checkValidity()) {
            setTestRequired(true)
            return
        }
        val res: TypedArray = context.getTheme().obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
        val textColorPrimary = res.getColor(0, 0)
        res.recycle()
        viewBinding!!.infoLabel.setTextColor(textColorPrimary)
        viewBinding!!.infoLabel.setText(R.string.proxy_checking)
        viewBinding!!.infoLabel.setVisibility(View.VISIBLE)
        disposable = Completable.create { emitter ->
            val type = viewBinding!!.proxyTypeSpinner.getText().toString()
            val host = viewBinding!!.hostText.getText().toString()
            val port = viewBinding!!.portText.getText().toString()
            val username = viewBinding!!.usernameText.getText().toString()
            val password = viewBinding!!.passwordText.getText().toString()
            var portValue = 8080
            if (!TextUtils.isEmpty(port)) {
                portValue = Integer.parseInt(port)
            }
            val address = InetSocketAddress.createUnresolved(host, portValue)
            val proxyType = Proxy.Type.valueOf(type.uppercase(Locale.US))
            val builder = AntennapodHttpClient.newBuilder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .proxy(Proxy(proxyType, address))
            if (!TextUtils.isEmpty(username)) {
                builder.proxyAuthenticator { route, response ->
                    val credentials = Credentials.basic(username!!, password)
                    response.request.newBuilder()
                            .header("Proxy-Authorization", credentials)
                            .build()
                }
            }
            val client = builder.build()
            val request = Request.Builder().url("https://www.example.com").head().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    emitter.onComplete()
                } else {
                    emitter.onError(IOException(response.message))
                }
            }
        }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            viewBinding!!.infoLabel.setTextColor(
                                    ThemeUtils.getColorFromAttr(context, R.attr.icon_green))
                            viewBinding!!.infoLabel.setText(R.string.proxy_test_successful)
                            setTestRequired(false)
                        },
                        { error ->
                            error.printStackTrace()
                            viewBinding!!.infoLabel.setTextColor(
                                    ThemeUtils.getColorFromAttr(context, R.attr.icon_red))
                            val message = String.format("%s: %s",
                                    context.getString(R.string.proxy_test_failed), error.message)
                            viewBinding!!.infoLabel.setText(message)
                            setTestRequired(true)
                        }
                )
    }

}
