package de.danoeh.antennapod.parser.media.vorbis

import org.apache.commons.io.EndianUtils
import org.apache.commons.io.IOUtils
import android.util.Log

import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.Locale

abstract class VorbisCommentReader {
    private val input: VorbisInputStream

    constructor(input: InputStream) {
        this.input = VorbisInputStream(input)
    }

    @Throws(VorbisCommentReaderException::class)
    fun readInputStream() {
        try {
            findCommentHeader()
            val commentHeader = readCommentHeader()
            Log.d(TAG, commentHeader.toString())
            for (i in 0 until commentHeader.getUserCommentLength()) {
                readUserComment()
            }
        } catch (e: IOException) {
            Log.d(TAG, "Vorbis parser: " + e.message)
        }
    }

    private fun readUserComment() {
        try {
            val vectorLength = EndianUtils.readSwappedUnsignedInteger(input)
            if (vectorLength > 20 * 1024 * 1024) {
                val keyPart = readUtf8String(10)
                throw VorbisCommentReaderException("User comment unrealistically long. "
                        + "key=" + keyPart + ", length=" + vectorLength)
            }
            val key = readContentVectorKey(vectorLength)!!.lowercase(Locale.US)
            val shouldReadValue = handles(key)
            Log.d(TAG, "key=" + key + ", length=" + vectorLength + ", handles=" + shouldReadValue)
            if (shouldReadValue) {
                val value = readUtf8String(vectorLength - key.length.toLong() - 1)
                onContentVectorValue(key, value)
            } else {
                IOUtils.skipFully(input, vectorLength - key.length.toLong() - 1)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun readUtf8String(length: Long): String {
        val buffer = ByteArray(length.toInt())
        IOUtils.readFully(input, buffer)
        val charset = Charset.forName("UTF-8")
        return charset.newDecoder().decode(ByteBuffer.wrap(buffer)).toString()
    }

    private fun findCommentHeader() {
        val buffer = ByteArray(64) // Enough space for some bytes. Used circularly.
        val oggCommentHeader = byteArrayOf(PACKET_TYPE_COMMENT.toByte(),
                'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 'b'.code.toByte(),
                'i'.code.toByte(), 's'.code.toByte())
        for (bytesRead in 0 until SECOND_PAGE_MAX_LENGTH) {
            buffer[bytesRead % buffer.size] = input.read().toByte()
            if (bufferMatches(buffer, oggCommentHeader, bytesRead)) {
                return
            } else if (bufferMatches(buffer, "OpusTags".toByteArray(), bytesRead)) {
                return
            }
        }
        throw IOException("No comment header found")
    }

    /**
     * Reads backwards in haystack, starting at position. Checks if the bytes match needle.
     * Uses haystack circularly, so when reading at (-1), it reads at (length - 1).
     */
    fun bufferMatches(haystack: ByteArray, needle: ByteArray, position: Int): Boolean {
        for (i in needle.indices) {
            var posInHaystack = position - i
            while (posInHaystack < 0) {
                posInHaystack += haystack.size
            }
            posInHaystack = posInHaystack % haystack.size
            if (haystack[posInHaystack] != needle[needle.size - 1 - i]) {
                return false
            }
        }
        return true
    }

    private fun readCommentHeader(): VorbisCommentHeader {
        try {
            val vendorLength = EndianUtils.readSwappedUnsignedInteger(input)
            val vendorName = readUtf8String(vendorLength)
            val userCommentLength = EndianUtils.readSwappedUnsignedInteger(input)
            return VorbisCommentHeader(vendorName, userCommentLength)
        } catch (e: UnsupportedEncodingException) {
            throw VorbisCommentReaderException(e)
        }
    }

    private fun readContentVectorKey(vectorLength: Long): String? {
        val builder = StringBuilder()
        for (i in 0 until vectorLength) {
            val c = input.read().toChar()
            if (c == '=') {
                return builder.toString()
            } else {
                builder.append(c)
            }
        }
        return null // no key found
    }

    /**
     * Is called every time the Reader finds a content vector. The handler
     * should return true if it wants to handle the content vector.
     */
    protected abstract fun handles(key: String): Boolean

    /**
     * Is called if onContentVectorKey returned true for the key.
     */
    protected abstract fun onContentVectorValue(key: String, value: String)

    companion object {
        private const val TAG = "VorbisCommentReader"
        private const val FIRST_OGG_PAGE_LENGTH = 58
        private const val FIRST_OPUS_PAGE_LENGTH = 47
        private const val SECOND_PAGE_MAX_LENGTH = 64 * 1024 * 1024
        private const val PACKET_TYPE_IDENTIFICATION = 1
        private const val PACKET_TYPE_COMMENT = 3
    }
}
