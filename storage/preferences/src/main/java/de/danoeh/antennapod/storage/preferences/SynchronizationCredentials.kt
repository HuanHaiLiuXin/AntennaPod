package de.danoeh.antennapod.storage.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages preferences for accessing gpodder.net service and other sync providers
 */
abstract class SynchronizationCredentials private constructor() {
    companion object {
        private const val PREF_NAME = "gpodder.net"
        private const val PREF_USERNAME = "de.danoeh.antennapod.preferences.gpoddernet.username"
        private const val PREF_PASSWORD = "de.danoeh.antennapod.preferences.gpoddernet.password"
        private const val PREF_DEVICEID = "de.danoeh.antennapod.preferences.gpoddernet.deviceID"
        private const val PREF_HOSTNAME = "prefGpodnetHostname"

        private var prefs: SharedPreferences? = null

        @JvmStatic
        fun init(context: Context) {
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }

        @JvmStatic
        fun getUsername(): String? {
            return prefs!!.getString(PREF_USERNAME, null)
        }

        @JvmStatic
        fun setUsername(username: String?) {
            prefs!!.edit().putString(PREF_USERNAME, username).apply()
        }

        @JvmStatic
        fun getPassword(): String? {
            return prefs!!.getString(PREF_PASSWORD, null)
        }

        @JvmStatic
        fun setPassword(password: String?) {
            prefs!!.edit().putString(PREF_PASSWORD, password).apply()
        }

        @JvmStatic
        fun getDeviceId(): String? {
            return prefs!!.getString(PREF_DEVICEID, null)
        }

        @JvmStatic
        fun setDeviceId(deviceId: String?) {
            prefs!!.edit().putString(PREF_DEVICEID, deviceId).apply()
        }

        @JvmStatic
        fun getHosturl(): String? {
            return prefs!!.getString(PREF_HOSTNAME, null)
        }

        @JvmStatic
        fun setHosturl(value: String?) {
            prefs!!.edit().putString(PREF_HOSTNAME, value).apply()
        }

        @JvmStatic
        @Synchronized
        fun clear() {
            setUsername(null)
            setPassword(null)
            setDeviceId(null)
            UserPreferences.setGpodnetNotificationsEnabled()
        }
    }
}
