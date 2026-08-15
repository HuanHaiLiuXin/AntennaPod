package de.danoeh.antennapod.net.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import java.util.regex.Pattern

import de.danoeh.antennapod.storage.preferences.UserPreferences

abstract class NetworkUtils private constructor() {
    companion object {
        private const val REGEX_PATTERN_IP_ADDRESS = "([0-9]{1,3}[\\.]){3}[0-9]{1,3}"

        private var context: Context? = null

        @JvmStatic
        fun init(context: Context) {
            NetworkUtils.context = context
        }

        @JvmStatic
        fun isAutoDownloadAllowed(): Boolean {
            val cm = context!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = cm.activeNetworkInfo
            if (networkInfo == null) {
                return false
            }
            if (networkInfo.type == ConnectivityManager.TYPE_WIFI) {
                return !isNetworkMetered()
            } else if (networkInfo.type == ConnectivityManager.TYPE_ETHERNET) {
                return true
            } else {
                return UserPreferences.isAllowMobileAutoDownload() || !NetworkUtils.isNetworkRestricted()
            }
        }

        @JvmStatic
        fun networkAvailable(): Boolean {
            val cm = context!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val info = cm.activeNetworkInfo
            return info != null && info.isConnected
        }

        @JvmStatic
        fun isEpisodeDownloadAllowed(): Boolean {
            return UserPreferences.isAllowMobileEpisodeDownload() || !NetworkUtils.isNetworkRestricted()
        }

        @JvmStatic
        fun isEpisodeHeadDownloadAllowed(): Boolean {
            // It is not an image but it is a similarly tiny request
            // that is probably not even considered a download by most users
            return isImageAllowed()
        }

        @JvmStatic
        fun isImageAllowed(): Boolean {
            return UserPreferences.isAllowMobileImages() || !NetworkUtils.isNetworkRestricted()
        }

        @JvmStatic
        fun isStreamingAllowed(): Boolean {
            return UserPreferences.isAllowMobileStreaming() || !NetworkUtils.isNetworkRestricted()
        }

        @JvmStatic
        fun isFeedRefreshAllowed(): Boolean {
            return UserPreferences.isAllowMobileFeedRefresh() || !NetworkUtils.isNetworkRestricted()
        }

        @JvmStatic
        fun isNetworkRestricted(): Boolean {
            return isNetworkMetered() || isNetworkCellular()
        }

        private fun isNetworkMetered(): Boolean {
            val connManager = context!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return connManager.isActiveNetworkMetered
        }

        @JvmStatic
        fun isVpnOverWifi(): Boolean {
            val connManager = context!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = connManager.getNetworkCapabilities(connManager.activeNetwork)
            return capabilities != null
                    && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }

        private fun isNetworkCellular(): Boolean {
            val connManager = context!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connManager.activeNetwork
            if (network == null) {
                return false // Nothing connected
            }
            val info = connManager.getNetworkInfo(network)
            if (info == null) {
                return true // Better be safe than sorry
            }
            val capabilities = connManager.getNetworkCapabilities(network)
            if (capabilities == null) {
                return true // Better be safe than sorry
            }
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }

        @JvmStatic
        fun wasDownloadBlocked(throwable: Throwable): Boolean {
            val message = throwable.message
            if (message != null) {
                val pattern = Pattern.compile(REGEX_PATTERN_IP_ADDRESS)
                val matcher = pattern.matcher(message)
                if (matcher.find()) {
                    val ip = matcher.group()
                    return ip.startsWith("127.") || ip.startsWith("0.")
                }
            }
            if (throwable.cause != null) {
                return wasDownloadBlocked(throwable.cause!!)
            }
            return false
        }
    }
}
