package de.danoeh.antennapod.ui.chapters

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.webkit.URLUtil

import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.net.common.AntennapodHttpClient
import de.danoeh.antennapod.parser.feed.PodcastIndexChapterParser
import de.danoeh.antennapod.parser.media.MediaFormatDetector
import de.danoeh.antennapod.parser.media.id3.ChapterReader
import de.danoeh.antennapod.parser.media.id3.ID3ReaderException
import de.danoeh.antennapod.parser.media.m4a.M4AChapterReader
import de.danoeh.antennapod.parser.media.vorbis.VorbisCommentChapterReader
import de.danoeh.antennapod.parser.media.vorbis.VorbisCommentReaderException
import de.danoeh.antennapod.storage.database.DBReader
import okhttp3.CacheControl
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.io.input.CountingInputStream

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.SequenceInputStream
import java.util.Collections
import java.util.Comparator
import java.util.Locale

/**
 * Utility class for getting chapter data from media files.
 */
class ChapterUtils private constructor() {
    companion object {
        private const val TAG = "ChapterUtils"

        @JvmStatic
        fun loadChapters(playable: Playable, context: Context, forceRefresh: Boolean) {
            if (playable.getChapters() != null && !forceRefresh) {
                // Already loaded
                return
            }

            try {
                var chaptersFromDatabase: List<Chapter>? = null
                var chaptersFromPodcastIndex: List<Chapter>? = null
                if (playable is FeedMedia) {
                    if (playable.getItem() == null) {
                        playable.setItem(DBReader.getFeedItem(playable.getItemId()))
                    }
                    if (playable.getItem()!!.hasChapters()) {
                        chaptersFromDatabase = DBReader.loadChaptersOfFeedItem(playable.getItem()!!)
                    }

                    if (!TextUtils.isEmpty(playable.getItem()!!.getPodcastIndexChapterUrl())) {
                        chaptersFromPodcastIndex = loadChaptersFromUrl(
                                playable.getItem()!!.getPodcastIndexChapterUrl()!!, forceRefresh)
                    }
                }

                val chaptersFromMediaFile = loadChaptersFromMediaFile(playable, context)
                val chaptersMergePhase1 = ChapterMerger.merge(chaptersFromDatabase, chaptersFromMediaFile)
                val chapters = ChapterMerger.merge(chaptersMergePhase1, chaptersFromPodcastIndex)
                if (chapters == null) {
                    // Do not try loading again. There are no chapters or parsing failed.
                    playable.setChapters(Collections.emptyList())
                } else {
                    playable.setChapters(chapters)
                }
            } catch (e: InterruptedIOException) {
                Log.d(TAG, "Chapter loading interrupted")
                playable.setChapters(null) // Allow later retry
            }
        }

        @Throws(InterruptedIOException::class)
        @JvmStatic
        fun loadChaptersFromMediaFile(playable: Playable, context: Context): List<Chapter>? {
            // Load the first few bytes to detect the format.
            // Then stitch the format back onto the stream and pass it to the chapter reader.
            // If we were unable to detect the format, we stitch and send it to the first reader,
            // then we try again with a fresh stream for the remaining two readers.
            // This reduces the number of times we have to open a new stream as much as possible.
            var format = MediaFormatDetector.Format.UNKNOWN
            var hint = MediaFormatDetector.Format.UNKNOWN
            try {
                openStream(playable, context).use { sniffStream ->
                    val detection = MediaFormatDetector.detect(sniffStream)
                    format = detection.format
                    if (format == MediaFormatDetector.Format.UNKNOWN) {
                        hint = detectHintFromMetadata(
                                (playable as FeedMedia).getMimeType(), playable.getStreamUrl())
                    }
                    val reconstructed: InputStream = SequenceInputStream(
                            ByteArrayInputStream(detection.bytes), sniffStream)
                    if (format != MediaFormatDetector.Format.UNKNOWN) {
                        val input = CountingInputStream(reconstructed)
                        val chapters = readChaptersFromInputStream(input, format)
                        hasLoadedChapters(chapters)
                        return chapters
                    } else if (hint == MediaFormatDetector.Format.ID3
                            || hint == MediaFormatDetector.Format.UNKNOWN) {
                        val chapters = readId3ChaptersFrom(CountingInputStream(reconstructed))
                        if (hasLoadedChapters(chapters)) {
                            return chapters
                        }
                    } else if (hint == MediaFormatDetector.Format.OGG) {
                        val chapters = readOggChaptersFromInputStream(
                                CountingInputStream(reconstructed))
                        if (hasLoadedChapters(chapters)) {
                            return chapters
                        }
                    } else {
                        val chapters = readM4AChaptersFromInputStream(
                                CountingInputStream(reconstructed))
                        if (hasLoadedChapters(chapters)) {
                            return chapters
                        }
                    }
                }
            } catch (e: InterruptedIOException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "Unable to load chapters: " + e.message)
            } catch (e: ID3ReaderException) {
                Log.e(TAG, "Unable to load chapters: " + e.message)
            } catch (e: VorbisCommentReaderException) {
                Log.e(TAG, "Unable to load chapters: " + e.message)
            }

            if (format == MediaFormatDetector.Format.UNKNOWN) {
                for (fallbackFormat in getFallbackOrder(hint)) {
                    val chapters = tryFreshStreamParser(playable, context, fallbackFormat)
                    if (hasLoadedChapters(chapters)) {
                        return chapters
                    }
                }
            }
            return null
        }

        @JvmStatic
        internal fun detectHintFromMetadata(mime: String?, url: String?): MediaFormatDetector.Format {
            var format = MediaFormatDetector.Format.UNKNOWN
            if (mime != null) {
                format = when (mime.trim().lowercase(Locale.US)) {
                    "audio/mpeg", "audio/mp3", "audio/x-mp3"
                    -> MediaFormatDetector.Format.ID3
                    "audio/ogg", "application/ogg", "audio/opus", "application/opus"
                    -> MediaFormatDetector.Format.OGG
                    "audio/mp4", "audio/x-m4a", "audio/m4a", "video/mp4", "audio/x-m4b", "audio/m4b"
                    -> MediaFormatDetector.Format.M4A
                    else -> format
                }
            }

            if (format == MediaFormatDetector.Format.UNKNOWN && url != null) {
                val filename = URLUtil.guessFileName(url, null, mime)
                if (!TextUtils.isEmpty(filename)) {
                    val dot = filename.lastIndexOf('.')
                    if (dot != -1 && dot < filename.length - 1) {
                        val ext = filename.substring(dot + 1).lowercase(Locale.US)
                        format = when (ext) {
                            "mp3" -> MediaFormatDetector.Format.ID3
                            "ogg", "opus" -> MediaFormatDetector.Format.OGG
                            "m4a", "mp4", "m4b" -> MediaFormatDetector.Format.M4A
                            else -> format
                        }
                    }
                }
            }
            return format
        }

        @JvmStatic
        internal fun getFallbackOrder(hint: MediaFormatDetector.Format): Array<MediaFormatDetector.Format> {
            return when (hint) {
                MediaFormatDetector.Format.OGG -> arrayOf(
                        MediaFormatDetector.Format.ID3, MediaFormatDetector.Format.M4A)
                MediaFormatDetector.Format.M4A -> arrayOf(
                        MediaFormatDetector.Format.ID3, MediaFormatDetector.Format.OGG)
                else -> arrayOf(
                        MediaFormatDetector.Format.OGG, MediaFormatDetector.Format.M4A)
            }
        }

        @Throws(IOException::class, ID3ReaderException::class, VorbisCommentReaderException::class)
        private fun readChaptersFromInputStream(
                input: CountingInputStream, format: MediaFormatDetector.Format): List<Chapter> {
            return when (format) {
                MediaFormatDetector.Format.ID3 -> readId3ChaptersFrom(input)
                MediaFormatDetector.Format.OGG -> readOggChaptersFromInputStream(input)
                MediaFormatDetector.Format.M4A -> readM4AChaptersFromInputStream(input)
                else -> Collections.emptyList()
            }
        }

        private fun hasLoadedChapters(chapters: List<Chapter>?): Boolean {
            if (chapters != null && !chapters.isEmpty()) {
                Log.i(TAG, "Chapters loaded")
                return true
            }
            return false
        }

        @Throws(InterruptedIOException::class)
        private fun tryFreshStreamParser(playable: Playable, context: Context,
                                         format: MediaFormatDetector.Format): List<Chapter>? {
            try {
                openStream(playable, context).use { input ->
                    return readChaptersFromInputStream(input, format)
                }
            } catch (e: InterruptedIOException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "Unable to load chapters (" + format + "): " + e.message)
                return null
            } catch (e: ID3ReaderException) {
                Log.e(TAG, "Unable to load chapters (" + format + "): " + e.message)
                return null
            } catch (e: VorbisCommentReaderException) {
                Log.e(TAG, "Unable to load chapters (" + format + "): " + e.message)
                return null
            }
        }

        @Throws(IOException::class)
        private fun openStream(playable: Playable, context: Context): CountingInputStream {
            if (playable.localFileAvailable()) {
                if (playable.getLocalFileUrl() == null) {
                    throw IOException("No local url")
                }
                val source = File(playable.getLocalFileUrl())
                if (!source.exists()) {
                    throw IOException("Local file does not exist")
                }
                return CountingInputStream(BufferedInputStream(FileInputStream(source)))
            } else if (playable.getStreamUrl()!!.startsWith(ContentResolver.SCHEME_CONTENT)) {
                val uri = Uri.parse(playable.getStreamUrl())
                return CountingInputStream(BufferedInputStream(context.getContentResolver().openInputStream(uri)))
            } else {
                val request = Request.Builder().url(playable.getStreamUrl()!!).build()
                val response = AntennapodHttpClient.getHttpClient().newCall(request).execute()
                if (response.body == null) {
                    throw IOException("Body is null")
                }
                return CountingInputStream(BufferedInputStream(response.body!!.byteStream()))
            }
        }

        @Throws(InterruptedIOException::class)
        @JvmStatic
        fun loadChaptersFromUrl(url: String, forceRefresh: Boolean): List<Chapter>? {
            if (forceRefresh) {
                return loadChaptersFromUrl(url, CacheControl.FORCE_NETWORK)
            }
            val cachedChapters = loadChaptersFromUrl(url, CacheControl.FORCE_CACHE)
            if (cachedChapters == null || cachedChapters.size <= 1) {
                // Some publishers use one dummy chapter before actual chapters are available
                return loadChaptersFromUrl(url, CacheControl.FORCE_NETWORK)
            }
            return cachedChapters
        }

        @Throws(InterruptedIOException::class)
        private fun loadChaptersFromUrl(url: String, cacheControl: CacheControl): List<Chapter>? {
            var response: Response? = null
            try {
                val request = Request.Builder().url(url).cacheControl(cacheControl).build()
                response = AntennapodHttpClient.getHttpClient().newCall(request).execute()
                val r = response!!
                if (r.isSuccessful && r.body != null) {
                    return PodcastIndexChapterParser.parse(r.body!!.string())
                }
            } catch (e: InterruptedIOException) {
                throw e
            } catch (e: IOException) {
                Log.d(TAG, "Failed to load chapters from URL: " + url, e)
            } finally {
                if (response != null) {
                    response.close()
                }
            }
            return null
        }

        @Throws(IOException::class, ID3ReaderException::class)
        private fun readId3ChaptersFrom(input: CountingInputStream): List<Chapter> {
            val reader = ChapterReader(input)
            reader.readInputStream()
            val chapters = reader.getChapters()
            return processChapters(chapters)
        }

        @Throws(VorbisCommentReaderException::class)
        private fun readOggChaptersFromInputStream(input: InputStream): List<Chapter> {
            val reader = VorbisCommentChapterReader(BufferedInputStream(input))
            reader.readInputStream()
            val chapters = reader.getChapters()
            return processChapters(chapters)
        }

        private fun readM4AChaptersFromInputStream(input: InputStream): List<Chapter> {
            val reader = M4AChapterReader(BufferedInputStream(input))
            reader.readInputStream()
            val chapters = reader.getChapters()
            return processChapters(chapters)
        }

        private fun processChapters(chapters: List<Chapter>): List<Chapter> {
            Collections.sort(chapters, ChapterStartTimeComparator())
            enumerateEmptyChapterTitles(chapters)
            if (chaptersValid(chapters)) {
                return chapters
            }
            Log.e(TAG, "Chapter data was invalid")
            return Collections.emptyList()
        }

        /**
         * Makes sure that chapter does a title and an item attribute.
         */
        private fun enumerateEmptyChapterTitles(chapters: List<Chapter>) {
            for (i in chapters.indices) {
                val c = chapters[i]
                if (c.getTitle() == null) {
                    c.setTitle(i.toString())
                }
            }
        }

        private fun chaptersValid(chapters: List<Chapter>): Boolean {
            if (chapters.isEmpty()) {
                return false
            }
            for (c in chapters) {
                if (c.getStart() < 0) {
                    return false
                }
            }
            return true
        }
    }

    class ChapterStartTimeComparator : Comparator<Chapter> {
        override fun compare(lhs: Chapter, rhs: Chapter): Int {
            return java.lang.Long.compare(lhs.getStart(), rhs.getStart())
        }
    }
}
