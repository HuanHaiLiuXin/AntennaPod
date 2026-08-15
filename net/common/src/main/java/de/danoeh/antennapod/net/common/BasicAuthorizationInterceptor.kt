package de.danoeh.antennapod.net.common

import android.text.TextUtils
import android.util.Log
import de.danoeh.antennapod.model.download.DownloadRequest
import java.net.HttpURLConnection

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class BasicAuthorizationInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        var response = chain.proceed(request)

        if (response.code != HttpURLConnection.HTTP_UNAUTHORIZED) {
            return response
        }

        val newRequest = request.newBuilder()
        if (!TextUtils.equals(response.request.url.toString(), request.url.toString())) {
            // Redirect detected. OkHTTP does not re-add the headers on redirect, so calling the new location directly.
            newRequest.url(response.request.url)

            val authorizationHeaders = request.headers.values(HEADER_AUTHORIZATION)
            if (!authorizationHeaders.isEmpty() && !TextUtils.isEmpty(authorizationHeaders[0])) {
                // Call already had authorization headers. Try again with the same credentials.
                newRequest.header(HEADER_AUTHORIZATION, authorizationHeaders[0])
                return chain.proceed(newRequest.build())
            }
        }

        var userInfo: String? = null
        if (request.tag() is DownloadRequest) {
            val downloadRequest = request.tag() as DownloadRequest
            userInfo = UriUtil.getURIFromRequestUrl(downloadRequest.getSource()).userInfo
            if (TextUtils.isEmpty(userInfo)
                    && (!TextUtils.isEmpty(downloadRequest.getUsername())
                        || !TextUtils.isEmpty(downloadRequest.getPassword()))) {
                userInfo = downloadRequest.getUsername() + ":" + downloadRequest.getPassword()
            }
        }

        if (TextUtils.isEmpty(userInfo)) {
            Log.d(TAG, "no credentials for '" + request.url + "'")
            return response
        }

        if (!userInfo!!.contains(":")) {
            Log.d(TAG, "Invalid credentials for '" + request.url + "'")
            return response
        }
        val username = userInfo.substring(0, userInfo.indexOf(':'))
        val password = userInfo.substring(userInfo.indexOf(':') + 1)

        Log.d(TAG, "Authorization failed, re-trying with ISO-8859-1 encoded credentials")
        newRequest.header(HEADER_AUTHORIZATION, HttpCredentialEncoder.encode(username, password, "ISO-8859-1"))
        response.close()
        response = chain.proceed(newRequest.build())

        if (response.code != HttpURLConnection.HTTP_UNAUTHORIZED) {
            return response
        }

        Log.d(TAG, "Authorization failed, re-trying with UTF-8 encoded credentials")
        newRequest.header(HEADER_AUTHORIZATION, HttpCredentialEncoder.encode(username, password, "UTF-8"))
        response.close()
        return chain.proceed(newRequest.build())
    }

    companion object {
        private const val TAG = "BasicAuthInterceptor"
        private const val HEADER_AUTHORIZATION = "Authorization"
    }
}
