package de.danoeh.antennapod.net.download.service.feed.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.TextUtils
import android.util.Log

import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.input.CountingInputStream

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.HashSet
import java.util.Locale
import java.util.UUID

import androidx.documentfile.provider.DocumentFile
import de.danoeh.antennapod.model.MediaMetadataRetrieverCompat
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.net.download.service.R
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.parser.feed.util.DateUtils
import de.danoeh.antennapod.model.download.DownloadError
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.playback.MediaType
import de.danoeh.antennapod.parser.feed.util.MimeTypeUtils
import de.danoeh.antennapod.parser.media.id3.ID3ReaderException
import de.danoeh.antennapod.parser.media.id3.Id3MetadataReader
import de.danoeh.antennapod.parser.media.vorbis.VorbisCommentMetadataReader
import de.danoeh.antennapod.parser.media.vorbis.VorbisCommentReaderException

class LocalFeedUpdater {
    companion object {
        private const val TAG = "LocalFeedUpdater"

        internal val PREFERRED_FEED_IMAGE_FILENAMES = arrayOf("folder.jpg", "Folder.jpg", "folder.png", "Folder.png")

        @JvmStatic
        fun updateFeed(feed: Feed, context: Context,
                       updaterProgressListener: UpdaterProgressListener?): Feed? {
            try {
                val uriString = feed.getDownloadUrl()!!.replace(Feed.PREFIX_LOCAL_FOLDER, "")
                val documentFolder = DocumentFile.fromTreeUri(context, Uri.parse(uriString))
                if (documentFolder == null) {
                    throw IOException("Unable to retrieve document tree. " +
                            "Try re-connecting the folder on the podcast info page.")
                }
                if (!documentFolder.exists() || !documentFolder.canRead()) {
                    throw IOException("Cannot read local directory. " +
                            "Try re-connecting the folder on the podcast info page.")
                }
                val updatedFeed = tryUpdateFeed(feed, context, documentFolder.getUri(), updaterProgressListener)

                val downloadResults = DBReader.getFeedDownloadLog(feed.getId(), 1)
                if (downloadResults.isEmpty() || !downloadResults[0].isSuccessful()) {
                    reportSuccess(feed)
                }
                return updatedFeed
            } catch (e: Exception) {
                e.printStackTrace()
                reportError(feed, e.message)
            }
            return null
        }

        @JvmStatic
        @Throws(IOException::class)
        internal fun tryUpdateFeed(feed: Feed, context: Context, folderUri: Uri,
                                   updaterProgressListener: UpdaterProgressListener?): Feed {
            var feed = feed
            if (feed.getItems() == null) {
                feed.setItems(ArrayList())
            }
            // make sure it is the latest 'version' of this feed from the db (all items etc)
            // and for new feeds, settings etc are set up properly.
            feed = FeedDatabaseWriter.updateFeed(context, feed, false)

            // list files in feed folder
            val allFiles = FastDocumentFile.list(context, folderUri)
            val mediaFiles = ArrayList<FastDocumentFile>()
            val mediaFileNames = HashSet<String>()
            for (file in allFiles) {
                val mimeType = MimeTypeUtils.getMimeType(file.getType(), file.getUri().toString())
                val mediaType = MediaType.fromMimeType(mimeType)
                if (mediaType == MediaType.AUDIO || mediaType == MediaType.VIDEO) {
                    mediaFiles.add(file)
                    mediaFileNames.add(file.getName())
                }
            }

            // add new files to feed and update item data
            val newItems = feed.getItems() as ArrayList<FeedItem>
            for (i in mediaFiles.indices) {
                val oldItem = feedContainsFile(feed, mediaFiles[i].getName())
                val newItem = createFeedItem(feed, mediaFiles[i], context)
                if (oldItem == null) {
                    newItems.add(newItem)
                } else {
                    oldItem.updateFromOther(newItem)
                }
                if (updaterProgressListener != null) {
                    updaterProgressListener.onLocalFileScanned(i, mediaFiles.size)
                }
            }

            // remove feed items without corresponding file
            val it = newItems.iterator()
            while (it.hasNext()) {
                val feedItem = it.next()
                if (!mediaFileNames.contains(feedItem.getLink())) {
                    it.remove()
                }
            }

            feed.setImageUrl(getImageUrl(allFiles, folderUri))

            feed.getPreferences()!!.setAutoDownload(FeedPreferences.AutoDownloadSetting.DISABLED)
            feed.setDescription(context.getString(R.string.local_feed_description))
            feed.setAuthor(context.getString(R.string.local_folder))

            FeedDatabaseWriter.updateFeed(context, feed, true)

            return feed
        }

        /**
         * Returns the image URL for the local feed.
         */
        @JvmStatic
        internal fun getImageUrl(files: List<FastDocumentFile>, folderUri: Uri): String {
            // look for special file names
            for (iconLocation in PREFERRED_FEED_IMAGE_FILENAMES) {
                for (file in files) {
                    if (iconLocation == file.getName()) {
                        return file.getUri().toString()
                    }
                }
            }

            // use the first image in the folder if existing
            for (file in files) {
                val mime = file.getType()
                if (mime != null && (mime.startsWith("image/jpeg") || mime.startsWith("image/png"))) {
                    return file.getUri().toString()
                }
            }

            // use default icon as fallback
            return Feed.PREFIX_GENERATIVE_COVER + folderUri
        }

        private fun feedContainsFile(feed: Feed, filename: String): FeedItem? {
            val items = feed.getItems()!!
            for (i in items) {
                if (i.getMedia() != null && i.getLink() == filename) {
                    return i
                }
            }
            return null
        }

        private fun createFeedItem(feed: Feed, file: FastDocumentFile, context: Context): FeedItem {
            val title = FilenameUtils.removeExtension(file.getName())
            val item = FeedItem(0L, title, UUID.randomUUID().toString(),
                    file.getName(), Date(file.getLastModified()), FeedItem.UNPLAYED, feed)
            item.disableAutoDownload()

            val size = file.getLength()
            val media = FeedMedia(0L, item, 0, 0, size, file.getType(),
                    file.getUri().toString(), file.getUri().toString(), 0L, null, 0, 0L)
            item.setMedia(media)

            for (existingItem in feed.getItems()!!) {
                if (existingItem.getMedia() != null
                        && existingItem.getMedia()!!.getDownloadUrl() == file.getUri().toString()
                        && file.getLength() == existingItem.getMedia()!!.getSize()) {
                    // We found an old file that we already scanned. Re-use metadata.
                    item.updateFromOther(existingItem)
                    return item
                }
            }

            // Did not find existing item. Scan metadata.
            try {
                loadMetadata(item, file, context)
            } catch (e: Exception) {
                e.printStackTrace()
                item.setDescriptionIfLonger(e.message)
            }
            return item
        }

        private fun loadMetadata(item: FeedItem, file: FastDocumentFile, context: Context) {
            try {
                MediaMetadataRetrieverCompat().use { mediaMetadataRetriever ->
                    mediaMetadataRetriever.setDataSource(context, file.getUri())

                    val dateStr = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                    if (!TextUtils.isEmpty(dateStr) && "19040101T000000.000Z" != dateStr) {
                        try {
                            val simpleDateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.getDefault())
                            item.setPubDate(simpleDateFormat.parse(dateStr))
                        } catch (parseException: ParseException) {
                            val date = DateUtils.parse(dateStr)
                            if (date != null) {
                                item.setPubDate(date)
                            }
                        }
                    }

                    val title = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    if (!TextUtils.isEmpty(title)) {
                        item.setTitle(title)
                    }

                    val durationStr = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    if (durationStr != null && durationStr != "null") {
                        item.getMedia()!!.setDuration(java.lang.Long.parseLong(durationStr).toInt())
                    }

                    item.getMedia()!!.setHasEmbeddedPicture(mediaMetadataRetriever.getEmbeddedPicture() != null)

                    try {
                        context.getContentResolver().openInputStream(file.getUri())!!.use { inputStream ->
                            val reader = Id3MetadataReader(
                                    CountingInputStream(BufferedInputStream(inputStream)))
                            reader.readInputStream()
                            item.setDescriptionIfLonger(reader.getComment())
                        }
                    } catch (e: IOException) {
                        Log.d(TAG, "Unable to parse ID3 of " + file.getUri() + ": " + e.message)

                        try {
                            context.getContentResolver().openInputStream(file.getUri())!!.use { inputStream ->
                                val reader = VorbisCommentMetadataReader(inputStream)
                                reader.readInputStream()
                                item.setDescriptionIfLonger(reader.getDescription())
                            }
                        } catch (e2: IOException) {
                            Log.d(TAG, "Unable to parse vorbis comments of " + file.getUri() + ": " + e2.message)
                        } catch (e2: VorbisCommentReaderException) {
                            Log.d(TAG, "Unable to parse vorbis comments of " + file.getUri() + ": " + e2.message)
                        }
                    } catch (e: ID3ReaderException) {
                        Log.d(TAG, "Unable to parse ID3 of " + file.getUri() + ": " + e.message)

                        try {
                            context.getContentResolver().openInputStream(file.getUri())!!.use { inputStream ->
                                val reader = VorbisCommentMetadataReader(inputStream)
                                reader.readInputStream()
                                item.setDescriptionIfLonger(reader.getDescription())
                            }
                        } catch (e2: IOException) {
                            Log.d(TAG, "Unable to parse vorbis comments of " + file.getUri() + ": " + e2.message)
                        } catch (e2: VorbisCommentReaderException) {
                            Log.d(TAG, "Unable to parse vorbis comments of " + file.getUri() + ": " + e2.message)
                        }
                    }
                }
            } catch (e: Exception) {
                throw e
            }
        }

        private fun reportError(feed: Feed, reasonDetailed: String?) {
            val status = DownloadResult(feed.getTitle()!!, feed.getId(),
                    Feed.FEEDFILETYPE_FEED, false, DownloadError.ERROR_IO_ERROR, reasonDetailed)
            DBWriter.addDownloadStatus(status)
            DBWriter.setFeedLastUpdateFailed(feed.getId(), true)
        }

        /**
         * Reports a successful download status.
         */
        private fun reportSuccess(feed: Feed) {
            val status = DownloadResult(feed.getTitle()!!, feed.getId(),
                    Feed.FEEDFILETYPE_FEED, true, DownloadError.SUCCESS, null)
            DBWriter.addDownloadStatus(status)
            DBWriter.setFeedLastUpdateFailed(feed.getId(), false)
        }
    }

    @FunctionalInterface
    interface UpdaterProgressListener {
        fun onLocalFileScanned(scanned: Int, totalFiles: Int)
    }
}
