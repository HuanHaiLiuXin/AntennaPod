package de.danoeh.antennapod.net.common

import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL

/**
 * Utility methods for dealing with URL encoding.
 */
abstract class UriUtil private constructor() {
    companion object {
        @JvmStatic
        fun getURIFromRequestUrl(source: String?): URI {
            // try without encoding the URI
            try {
                return URI(source)
            } catch (ignore: URISyntaxException) {
                System.out.println("Source is not encoded, encoding now")
            }
            try {
                val url = URL(source)
                return URI(url.protocol, url.userInfo, url.host, url.port, url.path, url.query, url.ref)
            } catch (e: MalformedURLException) {
                throw IllegalArgumentException(e)
            } catch (e: URISyntaxException) {
                throw IllegalArgumentException(e)
            }
        }
    }
}
