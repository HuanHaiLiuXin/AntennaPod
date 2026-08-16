package de.test.antennapod.util.service.download

import android.util.Base64
import android.util.Log

import fi.iki.elonen.NanoHTTPD
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.net.URLConnection
import java.util.ArrayList
import java.util.Arrays
import java.util.Locale
import java.util.Random
import java.util.zip.GZIPOutputStream

import de.danoeh.antennapod.BuildConfig

/**
 * Http server for testing purposes
 * <p/>
 * Supported features:
 * <p/>
 * /status/code: Returns HTTP response with the given status code
 * /redirect/n:  Redirects n times
 * /delay/n:     Delay response for n seconds
 * /basic-auth/username/password: Basic auth with username and password
 * /gzip/n:      Send gzipped data of size n bytes
 * /files/id:     Accesses the file with the specified ID (this has to be added first via serveFile).
 */
class HTTPBin : NanoHTTPD(0) { // Let system pick a free port
    companion object {
        private const val TAG = "HTTPBin"

        private const val MIME_HTML = "text/html"
        private const val MIME_PLAIN = "text/plain"
    }

    private val servedFiles: MutableList<File> = ArrayList()

    fun getBaseUrl(): String {
        return "http://127.0.0.1:" + getListeningPort()
    }

    /**
     * Adds the given file to the server.
     *
     * @return The ID of the file or -1 if the file could not be added to the server.
     */
    @Synchronized
    fun serveFile(file: File?): Int {
        if (file == null) throw IllegalArgumentException("file = null")
        if (!file.exists()) {
            return -1
        }
        for (i in 0 until servedFiles.size) {
            if (servedFiles.get(i).getAbsolutePath() == file.getAbsolutePath()) {
                return i
            }
        }
        servedFiles.add(file)
        return servedFiles.size - 1
    }

    @Synchronized
    fun accessFile(id: Int): File? {
        if (id < 0 || id >= servedFiles.size) {
            return null
        } else {
            return servedFiles.get(id)
        }
    }

    override fun serve(session: IHTTPSession): Response {

        if (BuildConfig.DEBUG) Log.d(TAG, "Requested url: " + session.getUri())

        val segments = session.getUri().split("/").toTypedArray()
        if (segments.size < 3) {
            Log.w(TAG, String.format(Locale.US, "Invalid number of URI segments: %d %s",
                    segments.size, Arrays.toString(segments)))
            get404Error()
        }

        val func = segments[1]
        val param = segments[2]
        val headers = session.getHeaders()

        if (func.equals("status", ignoreCase = true)) {
            try {
                val code = param.toInt()
                return Response(getStatus(code), MIME_HTML, "")
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                return getInternalError()
            }

        } else if (func.equals("redirect", ignoreCase = true)) {
            try {
                val times = param.toInt()
                if (times < 0) {
                    throw NumberFormatException("times <= 0: " + times)
                }

                return getRedirectResponse(times - 1)
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                return getInternalError()
            }
        } else if (func.equals("delay", ignoreCase = true)) {
            try {
                val sec = param.toInt()
                if (sec <= 0) {
                    throw NumberFormatException("sec <= 0: " + sec)
                }

                Thread.sleep(sec * 1000L)
                return getOKResponse()
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                return getInternalError()
            } catch (e: InterruptedException) {
                e.printStackTrace()
                return getInternalError()
            }
        } else if (func.equals("basic-auth", ignoreCase = true)) {
            if (!headers.containsKey("authorization")) {
                Log.w(TAG, "No credentials provided")
                return getUnauthorizedResponse()
            }
            try {
                val credentials = String(Base64.decode(headers.get("authorization")!!.split(" ")[1], 0), Charsets.UTF_8)
                val credentialParts = credentials.split(":").toTypedArray()
                if (credentialParts.size != 2) {
                    Log.w(TAG, "Unable to split credentials: " + Arrays.toString(credentialParts))
                    return getInternalError()
                }
                if (credentialParts[0] == segments[2]
                        && credentialParts[1] == segments[3]) {
                    Log.i(TAG, "Credentials accepted")
                    return getOKResponse()
                } else {
                    Log.w(TAG, String.format("Invalid credentials. Expected %s, %s, but was %s, %s",
                            segments[2], segments[3], credentialParts[0], credentialParts[1]))
                    return getUnauthorizedResponse()
                }

            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
                return getInternalError()
            }
        } else if (func.equals("gzip", ignoreCase = true)) {
            try {
                val size = param.toInt()
                if (size <= 0) {
                    Log.w(TAG, "Invalid size for gzipped data: " + size)
                    throw NumberFormatException()
                }

                return getGzippedResponse(size)
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                return getInternalError()
            } catch (e: IOException) {
                e.printStackTrace()
                return getInternalError()
            }
        } else if (func.equals("files", ignoreCase = true)) {
            try {
                val id = param.toInt()
                if (id < 0) {
                    Log.w(TAG, "Invalid ID: " + id)
                    throw NumberFormatException()
                }
                return getFileAccessResponse(id, headers)

            } catch (e: NumberFormatException) {
                e.printStackTrace()
                return getInternalError()
            }
        }

        return get404Error()
    }

    @Synchronized
    private fun getFileAccessResponse(id: Int, header: Map<String, String>): Response {
        val file = accessFile(id)
        if (file == null || !file.exists()) {
            Log.w(TAG, "File not found: " + id)
            return get404Error()
        }
        var inputStream: InputStream? = null
        var contentRange: String? = null
        var status: Response.Status
        var successful = false
        try {
            inputStream = FileInputStream(file)
            if (header.containsKey("range")) {
                // read range header field
                val value = header.get("range")!!
                val segments = value.split("=").toTypedArray()
                if (segments.size != 2) {
                    Log.w(TAG, "Invalid segment length: " + Arrays.toString(segments))
                    return getInternalError()
                }
                val type = StringUtils.substringBefore(value, "=")
                if (!type.equals("bytes", ignoreCase = true)) {
                    Log.w(TAG, "Range is not specified in bytes: " + value)
                    return getInternalError()
                }
                try {
                    val start = StringUtils.substringBefore(segments[1], "-").toLong()
                    if (start >= file.length()) {
                        return getRangeNotSatisfiable()
                    }

                    // skip 'start' bytes
                    IOUtils.skipFully(inputStream, start)
                    contentRange = "bytes " + start + (file.length() - 1) + "/" + file.length()

                } catch (e: NumberFormatException) {
                    e.printStackTrace()
                    return getInternalError()
                } catch (e: IOException) {
                    e.printStackTrace()
                    return getInternalError()
                }

                status = Response.Status.PARTIAL_CONTENT

            } else {
                // request did not contain range header field
                status = Response.Status.OK
            }
            successful = true
        } catch (e: FileNotFoundException) {
            e.printStackTrace()

            return getInternalError()
        } finally {
            if (!successful && inputStream != null) {
                IOUtils.closeQuietly(inputStream)
            }
        }

        val response = Response(status, URLConnection.guessContentTypeFromName(file.getAbsolutePath()), inputStream)

        response.addHeader("Accept-Ranges", "bytes")
        if (contentRange != null) {
            response.addHeader("Content-Range", contentRange)
        }
        response.addHeader("Content-Length", file.length().toString())
        return response
    }

    @Throws(IOException::class)
    private fun getGzippedResponse(size: Int): Response {
        try {
            Thread.sleep(200L)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        val buffer = ByteArray(size)
        val random = Random(System.currentTimeMillis())
        random.nextBytes(buffer)

        val compressed = ByteArrayOutputStream(buffer.size)
        val gzipOutputStream = GZIPOutputStream(compressed)
        gzipOutputStream.write(buffer)
        gzipOutputStream.close()

        val inputStream: InputStream = ByteArrayInputStream(compressed.toByteArray())
        val response = Response(Response.Status.OK, MIME_PLAIN, inputStream)
        response.addHeader("Content-Encoding", "gzip")
        response.addHeader("Content-Length", compressed.size().toString())
        return response
    }

    private fun getStatus(code: Int): Response.IStatus {
        return when (code) {
            200 -> Response.Status.OK
            201 -> Response.Status.CREATED
            206 -> Response.Status.PARTIAL_CONTENT
            301 -> Response.Status.REDIRECT
            304 -> Response.Status.NOT_MODIFIED
            400 -> Response.Status.BAD_REQUEST
            401 -> Response.Status.UNAUTHORIZED
            403 -> Response.Status.FORBIDDEN
            404 -> Response.Status.NOT_FOUND
            405 -> Response.Status.METHOD_NOT_ALLOWED
            416 -> Response.Status.RANGE_NOT_SATISFIABLE
            500 -> Response.Status.INTERNAL_ERROR
            else -> object : Response.IStatus {
                override fun getRequestStatus(): Int {
                    return code
                }

                override fun getDescription(): String {
                    return "Unknown"
                }
            }
        }
    }

    private fun getRedirectResponse(times: Int): Response {
        if (times > 0) {
            val response = Response(Response.Status.REDIRECT, MIME_HTML, "This resource has been moved permanently")
            response.addHeader("Location", "/redirect/" + times)
            return response
        } else if (times == 0) {
            return getOKResponse()
        } else {
            return getInternalError()
        }
    }

    private fun getUnauthorizedResponse(): Response {
        val response = Response(Response.Status.UNAUTHORIZED, MIME_HTML, "")
        response.addHeader("WWW-Authenticate", "Basic realm=\"Test Realm\"")
        return response
    }

    private fun getOKResponse(): Response {
        return Response(Response.Status.OK, MIME_HTML, "")
    }

    private fun getInternalError(): Response {
        return Response(Response.Status.INTERNAL_ERROR, MIME_HTML, "The server encountered an internal error")
    }

    private fun getRangeNotSatisfiable(): Response {
        return Response(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAIN, "")
    }

    private fun get404Error(): Response {
        return Response(Response.Status.NOT_FOUND, MIME_HTML, "The requested URL was not found on this server")
    }
}
