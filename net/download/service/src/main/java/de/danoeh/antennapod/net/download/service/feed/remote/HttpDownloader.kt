package de.danoeh.antennapod.net.download.service.feed.remote

import android.os.StatFs
import android.text.TextUtils
import android.util.Log

import de.danoeh.antennapod.net.common.NetworkUtils
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.model.download.DownloadRequest
import de.danoeh.antennapod.net.common.AntennapodHttpClient
import de.danoeh.antennapod.net.common.RedirectChecker
import de.danoeh.antennapod.net.download.service.R
import de.danoeh.antennapod.storage.preferences.UserPreferences
import okhttp3.CacheControl
import org.apache.commons.io.IOUtils

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.Collections
import java.util.Date
import java.util.Locale

import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.parser.feed.util.DateUtils
import de.danoeh.antennapod.model.download.DownloadError
import de.danoeh.antennapod.net.common.UriUtil
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

class HttpDownloader : Downloader {
    companion object {
        private const val TAG = "HttpDownloader"
        private const val BUFFER_SIZE = 8 * 1024

        private fun getFreeSpaceAvailable(): Long {
            val dataFolder = UserPreferences.getDataFolder(null)
            if (dataFolder != null) {
                val stat = StatFs(dataFolder.getAbsolutePath())
                val availableBlocks = stat.getAvailableBlocksLong()
                val blockSize = stat.getBlockSizeLong()
                return availableBlocks * blockSize
            } else {
                return 0
            }
        }
    }

    constructor(request: DownloadRequest) : super(request) {
    }

    override fun download() {
        val destination = File(request.getDestination())
        val fileExists = destination.exists()

        var out: RandomAccessFile? = null
        var connection: InputStream? = null
        var responseBody: ResponseBody? = null

        try {
            val uri: URI = UriUtil.getURIFromRequestUrl(request.getSource())
            val httpReq = Request.Builder().url(uri.toURL())
            httpReq.tag(request)
            httpReq.cacheControl(CacheControl.Builder().noStore().build())

            if (request.getFeedfileType() == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
                // set header explicitly so that okhttp doesn't do transparent gzip
                Log.d(TAG, "addHeader(\"Accept-Encoding\", \"identity\")")
                httpReq.addHeader("Accept-Encoding", "identity")
                httpReq.cacheControl(CacheControl.Builder().noCache().build()) // noStore breaks CDNs
            }

            if (uri.getScheme() == "http") {
                httpReq.addHeader("Upgrade-Insecure-Requests", "1")
            }

            if (!TextUtils.isEmpty(request.getLastModified())) {
                val lastModified = request.getLastModified()
                val lastModifiedDate = DateUtils.parse(lastModified)
                if (lastModifiedDate != null) {
                    val threeDaysAgo = System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 3
                    if (lastModifiedDate.getTime() > threeDaysAgo) {
                        Log.d(TAG, "addHeader(\"If-Modified-Since\", \"" + lastModified + "\")")
                        httpReq.addHeader("If-Modified-Since", lastModified!!)
                    }
                } else {
                    Log.d(TAG, "addHeader(\"If-None-Match\", \"" + lastModified + "\")")
                    httpReq.addHeader("If-None-Match", lastModified!!)
                }
            }

            // add range header if necessary
            if (fileExists && destination.length() > 0) {
                request.setSoFar(destination.length())
                httpReq.addHeader("Range", "bytes=" + request.getSoFar() + "-")
                Log.d(TAG, "Adding range header: " + request.getSoFar())
                // Only continue if the etag matches, otherwise the file might have changed
                if (!TextUtils.isEmpty(request.getLastModified())) {
                    httpReq.addHeader("If-Range", request.getLastModified()!!)
                    Log.d(TAG, "Adding If-Range header: " + request.getLastModified())
                }
            }

            val response = newCall(httpReq)
            responseBody = response.body
            val contentEncodingHeader = response.header("Content-Encoding")
            var isGzip = false
            if (!TextUtils.isEmpty(contentEncodingHeader)) {
                isGzip = TextUtils.equals(contentEncodingHeader!!.lowercase(Locale.US), "gzip")
            }

            Log.d(TAG, "Response code is " + response.code)
            if (!response.isSuccessful && response.code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                Log.d(TAG, "Feed '" + request.getSource() + "' not modified since last update, Download canceled")
                onCancelled()
                return
            } else if (!response.isSuccessful || response.body == null) {
                callOnFailByResponseCode(response)
                return
            } else if (request.getFeedfileType() == FeedMedia.FEEDFILETYPE_FEEDMEDIA
                    && isContentTypeTextAndSmallerThan100kb(response)) {
                onFail(DownloadError.ERROR_FILE_TYPE, null)
                return
            }
            val redirect = RedirectChecker.getNewUrlIfPermanentRedirect(response)
            if (redirect != null) {
                permanentRedirectUrl = redirect
            }

            connection = BufferedInputStream(responseBody!!.byteStream())

            val contentRangeHeader = if (fileExists) response.header("Content-Range") else null
            if (fileExists && response.code == HttpURLConnection.HTTP_PARTIAL
                    && !TextUtils.isEmpty(contentRangeHeader)) {
                val start = contentRangeHeader!!.substring("bytes ".length,
                        contentRangeHeader.indexOf("-"))
                request.setSoFar(java.lang.Long.parseLong(start))
                Log.d(TAG, "Starting download at position " + request.getSoFar())

                out = RandomAccessFile(destination, "rw")
                out.seek(request.getSoFar())
            } else {
                var success = destination.delete()
                success = success or destination.createNewFile()
                if (!success) {
                    throw IOException("Unable to recreate partially downloaded file")
                }
                out = RandomAccessFile(destination, "rw")
            }

            val buffer = ByteArray(BUFFER_SIZE)
            var count: Int
            request.setStatusMsg(R.string.download_running)
            Log.d(TAG, "Getting size of download")
            request.setSize(responseBody!!.contentLength() + request.getSoFar())
            Log.d(TAG, "Size is " + request.getSize())
            if (request.getSize() < 0) {
                request.setSize(DownloadResult.SIZE_UNKNOWN.toLong())
            }

            val freeSpace = getFreeSpaceAvailable()
            Log.d(TAG, "Free space is " + freeSpace)
            if (request.getSize() != DownloadResult.SIZE_UNKNOWN.toLong() && request.getSize() > freeSpace) {
                onFail(DownloadError.ERROR_NOT_ENOUGH_SPACE, null)
                return
            }

            Log.d(TAG, "Starting download")
            try {
                while (!cancelled) {
                    count = connection!!.read(buffer)
                    if (count == -1) {
                        break
                    }
                    out!!.write(buffer, 0, count)
                    request.setSoFar(request.getSoFar() + count)
                    val progressPercent = (100.0 * request.getSoFar() / request.getSize()).toInt()
                    request.setProgressPercent(progressPercent)
                }
            } catch (e: IOException) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
            if (cancelled) {
                onCancelled()
            } else {
                // check if size specified in the response header is the same as the size of the
                // written file. This check cannot be made if compression was used
                if (!isGzip && request.getSize() != DownloadResult.SIZE_UNKNOWN.toLong()
                        && request.getSoFar() != request.getSize()) {
                    onFail(DownloadError.ERROR_IO_WRONG_SIZE, "Download completed but size: "
                            + request.getSoFar() + " does not equal expected size " + request.getSize())
                    return
                } else if (request.getSize() > 0 && request.getSoFar() == 0L) {
                    onFail(DownloadError.ERROR_IO_ERROR, "Download completed, but nothing was read")
                    return
                }
                val lastModified = response.header("Last-Modified")
                if (lastModified != null) {
                    request.setLastModified(lastModified)
                } else {
                    request.setLastModified(response.header("ETag"))
                }
                onSuccess()
            }

        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            onFail(DownloadError.ERROR_MALFORMED_URL, e.message)
        } catch (e: SocketTimeoutException) {
            e.printStackTrace()
            onFail(DownloadError.ERROR_CONNECTION_ERROR, e.message)
        } catch (e: UnknownHostException) {
            e.printStackTrace()
            onFail(DownloadError.ERROR_UNKNOWN_HOST, e.message)
        } catch (e: IOException) {
            e.printStackTrace()
            if (NetworkUtils.wasDownloadBlocked(e)) {
                onFail(DownloadError.ERROR_IO_BLOCKED, e.message)
                return
            }
            val message = e.message
            if (message != null && message.contains("Trust anchor for certification path not found")) {
                onFail(DownloadError.ERROR_CERTIFICATE, e.message)
                return
            }
            onFail(DownloadError.ERROR_IO_ERROR, e.message)
        } catch (e: NullPointerException) {
            // might be thrown by connection.getInputStream()
            e.printStackTrace()
            onFail(DownloadError.ERROR_CONNECTION_ERROR, request.getSource())
        } finally {
            IOUtils.closeQuietly(out)
            IOUtils.closeQuietly(connection)
            IOUtils.closeQuietly(responseBody)
        }
    }

    @Throws(IOException::class)
    private fun newCall(httpReq: Request.Builder): Response {
        var httpClient: OkHttpClient = AntennapodHttpClient.getHttpClient()
        try {
            return httpClient.newCall(httpReq.build()).execute()
        } catch (e: IOException) {
            Log.e(TAG, e.toString())
            if (e.message != null && e.message!!.contains("PROTOCOL_ERROR")) {
                // Apparently some servers announce they support SPDY but then actually don't.
                httpClient = httpClient.newBuilder()
                        .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                        .build()
                return httpClient.newCall(httpReq.build()).execute()
            } else {
                throw e
            }
        }
    }

    private fun isContentTypeTextAndSmallerThan100kb(response: Response): Boolean {
        var contentLength = -1
        val contentLen = response.header("Content-Length")
        if (contentLen != null) {
            try {
                contentLength = Integer.parseInt(contentLen)
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
        }
        Log.d(TAG, "content length: " + contentLength)
        val contentType = response.header("Content-Type")
        Log.d(TAG, "content type: " + contentType)
        return contentType != null && contentType.startsWith("text/") && contentLength < 100 * 1024
    }

    private fun callOnFailByResponseCode(response: Response) {
        val error: DownloadError
        val details: String
        if (response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            error = DownloadError.ERROR_UNAUTHORIZED
            details = response.code.toString()
        } else if (response.code == HttpURLConnection.HTTP_FORBIDDEN) {
            error = DownloadError.ERROR_FORBIDDEN
            details = response.code.toString()
        } else if (response.code == HttpURLConnection.HTTP_NOT_FOUND
                || response.code == HttpURLConnection.HTTP_GONE) {
            error = DownloadError.ERROR_NOT_FOUND
            details = response.code.toString()
        } else {
            error = DownloadError.ERROR_HTTP_DATA_ERROR
            details = response.code.toString()
        }
        onFail(error, details)
    }

    private fun onSuccess() {
        Log.d(TAG, "Download was successful")
        result.setSuccessful()
    }

    private fun onFail(reason: DownloadError, reasonDetailed: String?) {
        Log.d(TAG, "onFail() called with: " + "reason = [" + reason + "], reasonDetailed = [" + reasonDetailed + "]")
        result.setFailed(reason, reasonDetailed)
    }

    private fun onCancelled() {
        Log.d(TAG, "Download was cancelled")
        result.setCancelled()
        cancelled = true
    }
}
