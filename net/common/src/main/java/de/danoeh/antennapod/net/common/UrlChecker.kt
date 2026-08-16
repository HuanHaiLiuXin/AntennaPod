package de.danoeh.antennapod.net.common

import android.net.Uri
import android.text.TextUtils
import android.util.Log

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.ArrayList
import java.util.Locale

/**
 * Provides methods for checking and editing a URL.
 */
class UrlChecker private constructor() {

    /**
     * Checks if URL is valid and modifies it if necessary.
     *
     * @param url The url which is going to be prepared
     * @return The prepared url
     */
    companion object {

        /**
         * Logging tag.
         */
        private const val TAG = "UrlChecker"

        private const val AP_SUBSCRIBE = "antennapod-subscribe://"
        private const val AP_SUBSCRIBE_DEEPLINK = "antennapod.org/deeplink/subscribe"

        @JvmStatic
        fun prepareUrl(url: String): String {
            var url = url.trim()
            val lowerCaseUrl = url.lowercase(Locale.ROOT) // protocol names are case insensitive
            if (lowerCaseUrl.startsWith("feed://")) {
                Log.d(TAG, "Replacing feed:// with http://")
                return prepareUrl(url.substring("feed://".length))
            } else if (lowerCaseUrl.startsWith("pcast://")) {
                Log.d(TAG, "Removing pcast://")
                return prepareUrl(url.substring("pcast://".length))
            } else if (lowerCaseUrl.startsWith("pcast:")) {
                Log.d(TAG, "Removing pcast:")
                return prepareUrl(url.substring("pcast:".length))
            } else if (lowerCaseUrl.startsWith("itpc")) {
                Log.d(TAG, "Replacing itpc:// with http://")
                return prepareUrl(url.substring("itpc://".length))
            } else if (lowerCaseUrl.startsWith(AP_SUBSCRIBE)) {
                Log.d(TAG, "Removing antennapod-subscribe://")
                return prepareUrl(url.substring(AP_SUBSCRIBE.length))
            } else if (lowerCaseUrl.contains(AP_SUBSCRIBE_DEEPLINK)) {
                Log.d(TAG, "Removing " + AP_SUBSCRIBE_DEEPLINK)
                val query = Uri.parse(url).getQueryParameter("url")
                try {
                    return prepareUrl(URLDecoder.decode(query, "UTF-8"))
                } catch (e: UnsupportedEncodingException) {
                    return prepareUrl(query!!)
                }
            } else if (lowerCaseUrl.contains("subscribeonandroid.com")) {
                return prepareUrl(url.replaceFirst("((www.)?(subscribeonandroid.com/))", ""))
            } else if (!(lowerCaseUrl.startsWith("http://") || lowerCaseUrl.startsWith("https://"))) {
                Log.d(TAG, "Adding http:// at the beginning of the URL")
                return "http://" + url
            } else {
                return url
            }
        }

        @JvmStatic
        fun isDeeplinkWithoutUrl(url: String): Boolean {
            return url.lowercase(Locale.ROOT).contains(AP_SUBSCRIBE_DEEPLINK)
                    && Uri.parse(url).getQueryParameter("url") == null
        }

        /**
         * Checks if URL is valid and modifies it if necessary.
         * This method also handles protocol relative URLs.
         *
         * @param url  The url which is going to be prepared
         * @param base The url against which the (possibly relative) url is applied. If this is null,
         *             the result of prepareURL(url) is returned instead.
         * @return The prepared url
         */
        @JvmStatic
        fun prepareUrl(url: String, base: String?): String {
            if (base == null) {
                return prepareUrl(url)
            }
            var url = url.trim()
            val preparedBase = prepareUrl(base)
            val urlUri = Uri.parse(url)
            val baseUri = Uri.parse(preparedBase)
            return if (urlUri.isRelative && baseUri.isAbsolute) {
                urlUri.buildUpon().scheme(baseUri.scheme).build().toString()
            } else {
                prepareUrl(url)
            }
        }

        @JvmStatic
        fun containsUrl(list: List<String?>, url: String): Boolean {
            for (item in list) {
                if (urlEquals(item, url)) {
                    return true
                }
            }
            return false
        }

        @JvmStatic
        fun urlEquals(string1: String?, string2: String?): Boolean {
            val url1 = Uri.parse(string1)
            val url2 = Uri.parse(string2)
            if (url1 == null || url2 == null || url1.host == null || url2.host == null) {
                return string1!!.equals(string2) // Unable to parse url properly
            }
            if (url1.host!!.lowercase(Locale.ROOT) != url2.host!!.lowercase(Locale.ROOT)) {
                return false
            }
            val pathSegments1 = normalizePathSegments(url1.pathSegments)
            val pathSegments2 = normalizePathSegments(url2.pathSegments)
            if (pathSegments1 != pathSegments2) {
                return false
            }
            if (TextUtils.isEmpty(url1.query)) {
                return TextUtils.isEmpty(url2.query)
            }
            return url1.query!!.equals(url2.query)
        }

        /**
         * Removes empty segments and converts all to lower case.
         * @param input List of path segments
         * @return Normalized list of path segments
         */
        private fun normalizePathSegments(input: List<String>): List<String> {
            val result = ArrayList<String>()
            for (string in input) {
                if (!TextUtils.isEmpty(string)) {
                    result.add(string.lowercase(Locale.ROOT))
                }
            }
            return result
        }
    }
}
