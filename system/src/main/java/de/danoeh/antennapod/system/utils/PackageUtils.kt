package de.danoeh.antennapod.system.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.util.Objects

/**
 * Utilities for accessing the package information.
 */
class PackageUtils private constructor() { /* Utility classes should not instantiated */
    companion object {
        @JvmStatic
        fun getApplicationVersion(context: Context): String? {
            val info = getPackageInfo(context)
            return Objects.requireNonNull(info, "Call to getPackageInfo() returned Null.")!!.versionName
        }

        @JvmStatic
        fun getPackageInfo(context: Context): PackageInfo? {
            return try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
}
