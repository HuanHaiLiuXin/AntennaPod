package de.danoeh.antennapod.system.utils

import android.os.Build
import android.os.Looper
import de.danoeh.antennapod.system.BuildConfig

class ThreadUtils private constructor() { // Private constructor to prevent instantiation
    companion object {
        /**
         * Assert to notify developers that they are not supposed to call a function on the main thread.
         * If you get an exception in this method, you should move your calls to background threads.
         */
        @JvmStatic
        fun assertNotMainThread() {
            if (BuildConfig.DEBUG) {
                if (Looper.myLooper() === Looper.getMainLooper() && !isTest()) {
                    throw RuntimeException("I/O on main thread")
                }
            }
        }

        private fun isTest(): Boolean {
            if ("robolectric" == Build.FINGERPRINT) {
                return true
            }
            return try {
                Class.forName("org.junit.Test")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }
    }
}
