package de.danoeh.antennapod.ui.transcript

import android.util.Log
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.charset.Charset
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.net.common.AntennapodHttpClient
import de.danoeh.antennapod.model.feed.Transcript
import de.danoeh.antennapod.parser.transcript.TranscriptParser
import okhttp3.CacheControl
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils

class TranscriptUtils {
    companion object {
        private const val TAG = "Transcript"

        @Throws(InterruptedIOException::class)
        @JvmStatic
        fun loadTranscriptFromUrl(url: String, forceRefresh: Boolean): String? {
            if (forceRefresh) {
                return loadTranscriptFromUrl(url, CacheControl.FORCE_NETWORK)
            }
            val str = loadTranscriptFromUrl(url, CacheControl.FORCE_CACHE)
            if (str == null || str.length <= 1) {
                // Some publishers use one dummy transcript before actual transcript are available
                return loadTranscriptFromUrl(url, CacheControl.FORCE_NETWORK)
            }
            return str
        }

        @Throws(InterruptedIOException::class)
        private fun loadTranscriptFromUrl(url: String, cacheControl: CacheControl): String? {
            val str = StringBuilder()
            var response: Response? = null

            try {
                Log.d(TAG, "Downloading transcript URL " + url)
                val request = Request.Builder().url(url).cacheControl(cacheControl).build()
                response = AntennapodHttpClient.getHttpClient().newCall(request).execute()
                val r = response!!
                if (r.isSuccessful && r.body != null) {
                    Log.d(TAG, "Done Downloading transcript URL " + url)
                    str.append(r.body!!.string())
                } else {
                    Log.d(TAG, "Error Downloading transcript URL " + url + ": " + r.message)
                }
            } catch (e: InterruptedIOException) {
                Log.d(TAG, "InterruptedIOException while downloading transcript URL " + url)
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            } finally {
                if (response != null) {
                    response.close()
                }
            }
            return str.toString()
        }

        @Throws(InterruptedIOException::class)
        @JvmStatic
        fun loadTranscript(media: FeedMedia, forceRefresh: Boolean): Transcript? {
            val transcriptType = media.getItem()!!.getTranscriptType()

            if (!forceRefresh && media.getItem()!!.getTranscript() != null) {
                return media.getTranscript()
            }

            if (!forceRefresh && media.getTranscriptFileUrl() != null) {
                val transcriptFile = File(media.getTranscriptFileUrl())
                try {
                    if (transcriptFile.exists()) {
                        val t = FileUtils.readFileToString(transcriptFile, null as String?)
                        if (StringUtils.isNotEmpty(t)) {
                            media.setTranscript(TranscriptParser.parse(t, transcriptType))
                            return media.getTranscript()
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            val transcriptUrl = media.getItem()!!.getTranscriptUrl()
            val t = loadTranscriptFromUrl(transcriptUrl!!, forceRefresh)
            if (StringUtils.isNotEmpty(t)) {
                return TranscriptParser.parse(t, transcriptType)
            }
            return null
        }

        @JvmStatic
        fun storeTranscript(media: FeedMedia, transcript: String) {
            val transcriptFile = File(media.getTranscriptFileUrl())
            var ostream: FileOutputStream? = null
            try {
                if (transcriptFile.exists() && !transcriptFile.delete()) {
                    Log.e(TAG, "Failed to delete existing transcript file " + transcriptFile.getAbsolutePath())
                }
                if (transcriptFile.createNewFile()) {
                    ostream = FileOutputStream(transcriptFile)
                    ostream.write(transcript.toByteArray(Charset.forName("UTF-8")))
                    ostream.close()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                IOUtils.closeQuietly(ostream)
            }
        }
    }
}
