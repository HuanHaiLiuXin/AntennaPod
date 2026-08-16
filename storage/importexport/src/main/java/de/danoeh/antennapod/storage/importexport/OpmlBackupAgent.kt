package de.danoeh.antennapod.storage.importexport

import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataInputStream
import android.app.backup.BackupDataOutput
import android.app.backup.BackupHelper
import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log

import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter
import org.apache.commons.io.IOUtils
import org.xmlpull.v1.XmlPullParserException

import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Reader
import java.io.Writer
import java.math.BigInteger
import java.nio.charset.Charset
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.storage.database.DBReader

class OpmlBackupAgent : BackupAgentHelper() {
    companion object {
        private const val OPML_BACKUP_KEY = "opml"
    }

    override fun onCreate() {
        addHelper(OPML_BACKUP_KEY, OpmlBackupHelper(this))
    }

    /**
     * Class for backing up and restoring the OPML file.
     */
    private class OpmlBackupHelper : BackupHelper {
        companion object {
            private const val TAG = "OpmlBackupHelper"

            private const val OPML_ENTITY_KEY = "antennapod-feeds.opml"
        }

        private val mContext: Context

        /**
         * Checksum of restored OPML file
         */
        private var mChecksum: ByteArray? = null

        constructor(context: Context) {
            mContext = context
        }

        override fun performBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput, newState: ParcelFileDescriptor) {
            Log.d(TAG, "Performing backup")
            val byteStream = ByteArrayOutputStream()
            var digester: MessageDigest? = null
            val writer: Writer = try {
                digester = MessageDigest.getInstance("MD5")
                OutputStreamWriter(DigestOutputStream(byteStream, digester),
                        Charset.forName("UTF-8"))
            } catch (e: NoSuchAlgorithmException) {
                OutputStreamWriter(byteStream, Charset.forName("UTF-8"))
            }

            try {
                // Write OPML
                OpmlWriter.writeDocument(DBReader.getFeedList(), writer)

                // Compare checksum of new and old file to see if we need to perform a backup at all
                if (digester != null) {
                    val newChecksum = digester.digest()
                    Log.d(TAG, "New checksum: " + BigInteger(1, newChecksum).toString(16))

                    // Get the old checksum
                    if (oldState != null) {
                        FileInputStream(oldState.getFileDescriptor()).use { inState ->
                            val len = inState.read()

                            if (len != -1) {
                                val oldChecksum = ByteArray(len)
                                IOUtils.read(inState, oldChecksum, 0, len)
                                Log.d(TAG, "Old checksum: " + BigInteger(1, oldChecksum).toString(16))

                                if (Arrays.equals(oldChecksum, newChecksum)) {
                                    Log.d(TAG, "Checksums are the same; won't backup")
                                    return
                                }
                            }
                        }
                    }

                    writeNewStateDescription(newState, newChecksum)
                }

                Log.d(TAG, "Backing up OPML")
                val bytes = byteStream.toByteArray()
                data.writeEntityHeader(OPML_ENTITY_KEY, bytes.size)
                data.writeEntityData(bytes, bytes.size)
            } catch (e: IOException) {
                Log.e(TAG, "Error during backup", e)
            } finally {
                IOUtils.closeQuietly(writer)
            }
        }

        override fun restoreEntity(data: BackupDataInputStream) {
            Log.d(TAG, "Backup restore")

            if (OPML_ENTITY_KEY != data.getKey()) {
                Log.d(TAG, "Unknown entity key: " + data.getKey())
                return
            }

            var digester: MessageDigest? = null
            val reader: Reader = try {
                digester = MessageDigest.getInstance("MD5")
                InputStreamReader(DigestInputStream(data, digester),
                        Charset.forName("UTF-8"))
            } catch (e: NoSuchAlgorithmException) {
                InputStreamReader(data, Charset.forName("UTF-8"))
            }

            try {
                val opmlElements = OpmlReader().readDocument(reader)
                mChecksum = if (digester == null) null else digester.digest()
                for (opmlElem in opmlElements) {
                    val feed = Feed(opmlElem.getXmlUrl(), null, opmlElem.getText())
                    feed.setItems(Collections.emptyList())
                    FeedDatabaseWriter.updateFeed(mContext, feed, false)
                }
                FeedUpdateManager.getInstance()!!.runOnce(mContext)
            } catch (e: XmlPullParserException) {
                Log.e(TAG, "Error while parsing the OPML file", e)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to restore OPML backup", e)
            } finally {
                IOUtils.closeQuietly(reader)
            }
        }

        override fun writeNewStateDescription(newState: ParcelFileDescriptor) {
            writeNewStateDescription(newState, mChecksum)
        }

        /**
         * Writes the new state description, which is the checksum of the OPML file.
         *
         * @param newState
         * @param checksum
         */
        private fun writeNewStateDescription(newState: ParcelFileDescriptor, checksum: ByteArray?) {
            if (checksum == null) {
                return
            }

            FileOutputStream(newState.getFileDescriptor()).use { outState ->
                outState.write(checksum.size)
                outState.write(checksum)
                outState.flush()
            }
        }
    }
}
