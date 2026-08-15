package de.danoeh.antennapod.net.download.serviceinterface

import android.util.Log
import android.webkit.URLUtil
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.storage.preferences.UserPreferences
import org.apache.commons.io.FilenameUtils

import java.io.File

/**
 * Creates download requests that can be sent to the DownloadService.
 */
class DownloadRequestCreator {
    companion object {
        private const val TAG = "DownloadRequestCreat"
        private const val FEED_DOWNLOADPATH = "cache/"
        private const val MEDIA_DOWNLOADPATH = "media/"

        @JvmStatic
        fun create(feed: Feed): DownloadRequestBuilder {
            val dest = File(getFeedfilePath(), getFeedfileName(feed))
            if (dest.exists()) {
                val deleted = dest.delete()
                Log.d(TAG, "deleted" + dest.getPath() + ": " + deleted)
            }
            Log.d(TAG, "Requesting download of url " + feed.getDownloadUrl())

            val username = if (feed.getPreferences() != null) feed.getPreferences()!!.getUsername() else null
            val password = if (feed.getPreferences() != null) feed.getPreferences()!!.getPassword() else null

            return DownloadRequestBuilder(dest.toString(), feed)
                    .withAuthentication(username, password)
                    .lastModified(feed.getLastModified())
        }

        @JvmStatic
        fun create(media: FeedMedia): DownloadRequestBuilder {
            val partiallyDownloadedFileExists =
                    media.getLocalFileUrl() != null && File(media.getLocalFileUrl()).exists()
            var dest: File
            if (partiallyDownloadedFileExists) {
                dest = File(media.getLocalFileUrl())
            } else {
                dest = File(getMediafilePath(media), getMediafilename(media))
            }

            if (dest.exists() && !partiallyDownloadedFileExists) {
                dest = findUnusedFile(dest)!!
            }
            Log.d(TAG, "Requesting download of url " + media.getDownloadUrl())

            val username = if (media.getItem()!!.getFeed()!!.getPreferences() != null)
                media.getItem()!!.getFeed()!!.getPreferences()!!.getUsername() else null
            val password = if (media.getItem()!!.getFeed()!!.getPreferences() != null)
                media.getItem()!!.getFeed()!!.getPreferences()!!.getPassword() else null

            return DownloadRequestBuilder(dest.toString(), media)
                    .withAuthentication(username, password)
        }

        private fun findUnusedFile(dest: File): File? {
            // find different name
            var newDest: File? = null
            for (i in 1 until Int.MAX_VALUE) {
                val newName = FilenameUtils.getBaseName(dest.getName()) +
                        "-" + i +
                        FilenameUtils.EXTENSION_SEPARATOR +
                        FilenameUtils.getExtension(dest.getName())
                Log.d(TAG, "Testing filename " + newName)
                newDest = File(dest.getParent(), newName)
                if (!newDest!!.exists()) {
                    Log.d(TAG, "File doesn't exist yet. Using " + newName)
                    break
                }
            }
            return newDest
        }

        private fun getFeedfilePath(): String {
            return UserPreferences.getDataFolder(FEED_DOWNLOADPATH).toString() + "/"
        }

        private fun getFeedfileName(feed: Feed): String {
            var filename = feed.getDownloadUrl()
            if (feed.getTitle() != null && !feed.getTitle()!!.isEmpty()) {
                filename = feed.getTitle()
            }
            return "feed-" + FileNameGenerator.generateFileName(filename!!) + feed.getId()
        }

        private fun getMediafilePath(media: FeedMedia): String {
            val mediaPath = MEDIA_DOWNLOADPATH +
                    FileNameGenerator.generateFileName(media.getItem()!!.getFeed()!!.getTitle()!!)
            return UserPreferences.getDataFolder(mediaPath).toString() + "/"
        }

        private fun getMediafilename(media: FeedMedia): String {
            var titleBaseFilename = ""

            // Try to generate the filename by the item title
            if (media.getItem() != null && media.getItem()!!.getTitle() != null) {
                val title = media.getItem()!!.getTitle()
                titleBaseFilename = FileNameGenerator.generateFileName(title!!)
            }

            val urlBaseFilename = URLUtil.guessFileName(media.getDownloadUrl(), null, media.getMimeType())

            val baseFilename: String
            if (titleBaseFilename != "") {
                baseFilename = titleBaseFilename
            } else {
                baseFilename = urlBaseFilename
            }
            val filenameMaxLength = 220
            if (baseFilename.length > filenameMaxLength) {
                return baseFilename.substring(0, filenameMaxLength) +
                        FilenameUtils.EXTENSION_SEPARATOR + media.getId() +
                        FilenameUtils.EXTENSION_SEPARATOR + FilenameUtils.getExtension(urlBaseFilename)
            }
            return baseFilename + FilenameUtils.EXTENSION_SEPARATOR + media.getId() +
                    FilenameUtils.EXTENSION_SEPARATOR + FilenameUtils.getExtension(urlBaseFilename)
        }
    }
}
