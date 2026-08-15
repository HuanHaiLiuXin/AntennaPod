package de.danoeh.antennapod.net.sync.nextcloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import de.danoeh.antennapod.net.sync.HostnameParser
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.util.Log

import java.io.IOException
import java.net.URI
import java.net.URL
import java.util.ArrayList
import java.util.concurrent.TimeUnit

class NextcloudLoginFlow(
    private val httpClient: OkHttpClient,
    private val rawHostUrl: String,
    private val context: Context,
    private val callback: AuthenticationCallback
) {
    private val hostname = HostnameParser(rawHostUrl)
    private var token: String? = null
    private var endpoint: String? = null
    private var startDisposable: Disposable? = null
    private var pollDisposable: Disposable? = null

    fun saveInstanceState(): ArrayList<String?> {
        val state = ArrayList<String?>()
        state.add(rawHostUrl)
        state.add(token)
        state.add(endpoint)
        return state
    }

    fun start() {
        if (token != null) {
            poll()
            return
        }
        startDisposable = Observable.fromCallable {
            val url = URI(hostname.scheme, null, hostname.host, hostname.port,
                    hostname.subfolder + "/index.php/login/v2", null, null).toURL()
            val result = doRequest(url, "")
            val loginUrl = result.getString("login")
            this.token = result.getJSONObject("poll").getString("token")
            this.endpoint = result.getJSONObject("poll").getString("endpoint")
            loginUrl
        }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { result ->
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(result))
                        context.startActivity(browserIntent)
                        poll()
                    }, { error ->
                        Log.e(TAG, Log.getStackTraceString(error))
                        this.token = null
                        this.endpoint = null
                        callback.onNextcloudAuthError(error.localizedMessage)
                    })
    }

    private fun poll() {
        pollDisposable = Observable.fromCallable { doRequest(URI.create(endpoint).toURL(), "token=" + token) }
                .retryWhen { t -> t.delay(1, TimeUnit.SECONDS) }
                .timeout(5, TimeUnit.MINUTES)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result -> callback.onNextcloudAuthenticated(
                        result.getString("server"), result.getString("loginName"), result.getString("appPassword")) },
                    { error ->
                        this.token = null
                        this.endpoint = null
                        callback.onNextcloudAuthError(error.localizedMessage)
                    })
    }

    fun cancel() {
        if (startDisposable != null) {
            startDisposable!!.dispose()
        }
        if (pollDisposable != null) {
            pollDisposable!!.dispose()
        }
    }

    private fun doRequest(url: URL, bodyContent: String): JSONObject {
        val requestBody = bodyContent.toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val request = Request.Builder().url(url).method("POST", requestBody).build()
        val response = httpClient.newCall(request).execute()
        if (response.code != 200) {
            response.close()
            throw IOException("Return code " + response.code)
        }
        val body = response.body
        if (body == null) {
            throw IOException("Empty response")
        }
        return JSONObject(body.string())
    }

    interface AuthenticationCallback {
        fun onNextcloudAuthenticated(server: String, username: String, password: String)

        fun onNextcloudAuthError(errorMessage: String?)
    }

    companion object {
        private const val TAG = "NextcloudLoginFlow"

        @JvmStatic
        fun fromInstanceState(httpClient: OkHttpClient, context: Context,
                              callback: AuthenticationCallback, instanceState: ArrayList<String>): NextcloudLoginFlow {
            val flow = NextcloudLoginFlow(httpClient, instanceState[0], context, callback)
            flow.token = instanceState[1]
            flow.endpoint = instanceState[2]
            return flow
        }
    }
}
