package de.danoeh.antennapod.net.common

import android.text.TextUtils
import android.util.Log
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.http.StatusLine

import java.io.IOException
import java.net.HttpURLConnection
import java.util.ArrayList
import java.util.Collections

abstract class RedirectChecker private constructor() {
    companion object {
        private const val TAG = "RedirectChecker"

        @JvmStatic
        fun getNewUrlIfPermanentRedirect(response: Response?): String? {
            // detect 301 Moved permanently and 308 Permanent Redirect
            val responses = ArrayList<Response>()
            var current = response
            while (current != null) {
                responses.add(current)
                current = current.priorResponse
            }
            if (responses.size < 2) {
                return null
            }
            Collections.reverse(responses)
            val firstCode = responses[0].code
            val firstUrl = responses[0].request.url.toString()
            val secondUrl = responses[1].request.url.toString()
            if (firstCode == HttpURLConnection.HTTP_MOVED_PERM || firstCode == StatusLine.HTTP_PERM_REDIRECT) {
                Log.d(TAG, "Detected permanent redirect from " + firstUrl + " to " + secondUrl)
                return secondUrl
            } else if (secondUrl == firstUrl.replace("http://", "https://")) {
                Log.d(TAG, "Treating http->https non-permanent redirect as permanent: " + firstUrl)
                return secondUrl
            }
            return null
        }

        @JvmStatic
        fun getNewUrlIfPermanentRedirect(downloadUrl: String?): String? {
            try {
                val httpReq = Request.Builder().url(downloadUrl!!).head().build()
                val response = AntennapodHttpClient.getHttpClient().newCall(httpReq).execute()
                return RedirectChecker.getNewUrlIfPermanentRedirect(response)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return null
        }

        @JvmStatic
        fun getFinalUrl(url: String): String {
            if (TextUtils.isEmpty(url) || !url.startsWith("http")) {
                return url
            }
            try {
                val httpReq = Request.Builder().url(url).head().build()
                val response = AntennapodHttpClient.getHttpClient().newCall(httpReq).execute()
                response.close()
                return response.request.url.toString()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to follow redirects for " + url + ": " + e.message)
                return url
            }
        }
    }
}
