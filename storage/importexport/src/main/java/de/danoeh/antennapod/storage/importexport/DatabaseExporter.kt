package de.danoeh.antennapod.storage.importexport

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.format.Formatter
import android.util.Log
import de.danoeh.antennapod.storage.database.PodDBAdapter
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class DatabaseExporter {
    companion object {
        private const val TAG = "DatabaseExporter"
        private const val TEMP_DB_NAME = PodDBAdapter.DATABASE_NAME + "_tmp"

        @JvmStatic
        @Throws(IOException::class)
        fun exportToDocument(uri: Uri, context: Context) {
            val pfd = context.getContentResolver().openFileDescriptor(uri, "wt")
            var bytesCopied = -1
            var resultingFileSize = 0
            try {
                FileOutputStream(pfd!!.getFileDescriptor()).use { fileOutputStream ->
                    bytesCopied = exportToStream(fileOutputStream, context)
                }
            } catch (e: IOException) {
                Log.e(TAG, Log.getStackTraceString(e))
                throw e
            } finally {
                resultingFileSize = pfd!!.getStatSize().toInt()
                IOUtils.closeQuietly(pfd)
            }
            if (resultingFileSize != bytesCopied) {
                throw IOException(java.lang.String.format(
                        "Unable to write entire database. Expected to write %s, but wrote %s.",
                        Formatter.formatShortFileSize(context, bytesCopied.toLong()),
                        Formatter.formatShortFileSize(context, resultingFileSize.toLong())))
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun exportToStream(outFileStream: FileOutputStream, context: Context): Int {
            val currentDB = context.getDatabasePath(PodDBAdapter.DATABASE_NAME)
            if (!currentDB.exists()) {
                throw IOException("Cannot access current database")
            }
            val tempDB = context.getDatabasePath(TEMP_DB_NAME)
            try {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.walCheckpoint()
                adapter.close()
                FileUtils.copyFile(currentDB, tempDB)
                SQLiteDatabase.openDatabase(
                        tempDB.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY).use { tempDbHandle ->
                    if (tempDbHandle.getVersion() != PodDBAdapter.VERSION) {
                        throw IOException("Database version mismatch. Expected: " + PodDBAdapter.VERSION
                                + ", found: " + tempDbHandle.getVersion())
                    }
                }
                FileInputStream(tempDB).use { src ->
                    return IOUtils.copy(src, outFileStream)
                }
            } catch (e: IOException) {
                Log.e(TAG, Log.getStackTraceString(e))
                throw e
            } catch (e: SQLiteException) {
                Log.e(TAG, Log.getStackTraceString(e))
                throw e
            } finally {
                val deleted = tempDB.delete()
                Log.d(TAG, "Deleted temp database file: " + deleted)
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun importBackup(inputUri: Uri, context: Context) {
            var inputStream: InputStream? = null
            try {
                val tempDB = context.getDatabasePath(TEMP_DB_NAME)
                inputStream = context.getContentResolver().openInputStream(inputUri)
                FileUtils.copyInputStreamToFile(inputStream, tempDB)

                val db = SQLiteDatabase.openDatabase(tempDB.getAbsolutePath(),
                        null, SQLiteDatabase.OPEN_READONLY)
                if (db.getVersion() > PodDBAdapter.VERSION) {
                    throw IOException(context.getString(R.string.import_no_downgrade))
                }
                db.close()

                val currentDB = context.getDatabasePath(PodDBAdapter.DATABASE_NAME)
                if (!currentDB.delete()) {
                    throw IOException("Unable to delete old database")
                }
                for (suffix in arrayOf("-wal", "-shm", "-journal")) {
                    val sidecarFile = File(currentDB.getAbsolutePath() + suffix)
                    val success = sidecarFile.delete()
                    Log.d(TAG, "Deleting sidecar file: " + sidecarFile.getAbsolutePath() + ", success: " + success)
                }
                FileUtils.moveFile(tempDB, currentDB)
            } catch (e: IOException) {
                Log.e(TAG, Log.getStackTraceString(e))
                throw e
            } catch (e: SQLiteException) {
                Log.e(TAG, Log.getStackTraceString(e))
                throw e
            } finally {
                IOUtils.closeQuietly(inputStream)
            }
        }
    }
}
