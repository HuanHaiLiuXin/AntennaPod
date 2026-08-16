package de.danoeh.antennapod.parser.media.vorbis

import org.apache.commons.io.IOUtils

import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Arrays

class VorbisInputStream internal constructor(input: InputStream) : FilterInputStream(input) {
    private val inputStream = BufferedInputStream(input)
    private var pageRemainBytes: Int = 0

    private fun parsePageHeader(input: InputStream): Int {
        val capturePattern = ByteArray(4)

        IOUtils.readFully(input, capturePattern, 0, 4)
        if (!Arrays.equals(CAPTURE_PATTERN, capturePattern)) {
            throw IOException("Invalid page header")
        }

        IOUtils.skipFully(input, HEADER_SKIP_LENGTH.toLong())

        val pageSegments = input.read()
        val segmentTable = ByteArray(pageSegments)
        var pageLength = 0
        IOUtils.readFully(input, segmentTable)
        for (segment in segmentTable) {
            pageLength += segment.toInt() and 0xff
        }

        return pageLength
    }

    /** check and update remaining bytes **/
    private fun updateRemainBytes() {
        if (pageRemainBytes == 0) {
            pageRemainBytes = parsePageHeader(inputStream)
        } else if (pageRemainBytes < 0) {
            throw IOException("Page remain bytes less than 0")
        }
    }

    override fun read(): Int {
        updateRemainBytes()
        pageRemainBytes--
        return inputStream.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        updateRemainBytes()
        val bytesToRead = Math.min(len, pageRemainBytes)
        IOUtils.readFully(inputStream, b, off, bytesToRead)
        this.pageRemainBytes -= bytesToRead
        return bytesToRead
    }

    companion object {
        private val CAPTURE_PATTERN = byteArrayOf('O'.code.toByte(), 'g'.code.toByte(),
                'g'.code.toByte(), 'S'.code.toByte())
        private const val HEADER_SKIP_LENGTH = 1 + 1 + 8 + 4 + 4 + 4
    }
}
