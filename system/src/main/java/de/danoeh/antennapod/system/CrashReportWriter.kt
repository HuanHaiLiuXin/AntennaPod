package de.danoeh.antennapod.system

import android.util.Log
import de.danoeh.antennapod.storage.preferences.UserPreferences
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.util.Date

class CrashReportWriter {
    companion object {
        private const val TAG = "CrashReportWriter"

        @JvmStatic
        fun getFile(): File {
            return File(UserPreferences.getDataFolder(null), "crash-report.log")
        }

        @JvmStatic
        fun write(exception: Throwable) {
            val path = getFile()
            var out: PrintWriter? = null
            try {
                out = PrintWriter(path, "UTF-8")
                exception.printStackTrace(out)
            } catch (e: IOException) {
                Log.e(TAG, Log.getStackTraceString(e))
            } finally {
                IOUtils.closeQuietly(out)
            }
        }

        @JvmStatic
        fun getTimestamp(): Date? {
            var timestamp: Date? = null
            try {
                val file = getFile()
                if (file.exists()) {
                    timestamp = Date(file.lastModified())
                }
            } catch (e: SecurityException) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
            return timestamp
        }

        @JvmStatic
        fun read(): String {
            var content = ""
            try {
                val file = getFile()
                if (file.exists()) {
                    FileInputStream(file).use { fin ->
                        content = IOUtils.toString(fin, StandardCharsets.UTF_8)
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, Log.getStackTraceString(e))
            } catch (e: IOException) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
            return content
        }
    }
}
