package de.danoeh.antennapod.net.sync

import org.apache.commons.lang3.StringUtils

import java.net.IDN
import java.util.regex.Pattern

class HostnameParser(hosturl: String?) {
    @JvmField
    var scheme: String? = null

    @JvmField
    var port: Int = 0

    @JvmField
    var host: String? = null

    @JvmField
    var subfolder: String = ""

    // split into schema, host and port - missing parts are null
    companion object {
        private val URLSPLIT_REGEX = Pattern.compile("(?:(https?)://)?([^:/]+)(?::(\\d+))?(.+)?")
    }

    init {
        val m = URLSPLIT_REGEX.matcher(hosturl)
        if (m.matches()) {
            scheme = m.group(1)
            try {
                host = IDN.toASCII(m.group(2))
            } catch (e: IllegalArgumentException) {
                host = "invalid-hostname"
            }
            if (m.group(3) == null) {
                port = -1
            } else {
                port = Integer.parseInt(m.group(3))    // regex -> can only be digits
            }
            if (m.group(4) == null) {
                subfolder = ""
            } else {
                subfolder = StringUtils.stripEnd(m.group(4), "/")
            }
        } else {
            // URL does not match regex: use it anyway -> this will cause an exception on connect
            scheme = "https"
            try {
                host = IDN.toASCII(hosturl)
            } catch (e: IllegalArgumentException) {
                host = "invalid-hostname"
            }
            port = 443
        }

        if (scheme == null && port == 80) {
            scheme = "http"
        } else if (scheme == null) {
            scheme = "https" // assume https
        }

        if (scheme == "https" && port == -1) {
            port = 443
        } else if (scheme == "http" && port == -1) {
            port = 80
        }
    }
}
