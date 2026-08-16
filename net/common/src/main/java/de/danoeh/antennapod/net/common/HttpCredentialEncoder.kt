package de.danoeh.antennapod.net.common

import android.util.Base64

import java.io.UnsupportedEncodingException

abstract class HttpCredentialEncoder private constructor() {
    companion object {
        @JvmStatic
        fun encode(username: String?, password: String?, charset: String): String {
            try {
                val credentials = username + ":" + password
                val bytes = credentials.toByteArray(java.nio.charset.Charset.forName(charset))
                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                return "Basic " + encoded
            } catch (e: UnsupportedEncodingException) {
                throw AssertionError(e)
            }
        }
    }
}
