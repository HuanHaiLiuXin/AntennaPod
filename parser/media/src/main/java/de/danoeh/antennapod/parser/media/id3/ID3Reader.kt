package de.danoeh.antennapod.parser.media.id3

import android.util.Log
import de.danoeh.antennapod.parser.media.id3.model.FrameHeader
import de.danoeh.antennapod.parser.media.id3.model.TagHeader
import org.apache.commons.io.IOUtils
import org.apache.commons.io.input.CountingInputStream

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.MalformedInputException

/**
 * Reads the ID3 Tag of a given file.
 * See https://id3.org/id3v2.3.0
 */
open class ID3Reader(private val inputStream: CountingInputStream) {
    private var tagHeader: TagHeader? = null

    fun readInputStream() {
        tagHeader = readTagHeader()
        val tagContentStartPosition = getPosition()
        while (getPosition() < tagContentStartPosition + tagHeader!!.getSize()) {
            val frameHeader = readFrameHeader()
            if (frameHeader.getId()[0] < '0' || frameHeader.getId()[0] > 'z') {
                Log.d(TAG, "Stopping because of invalid frame: " + frameHeader.toString())
                return
            }
            readFrame(frameHeader)
        }
    }

    protected open fun readFrame(frameHeader: FrameHeader) {
        Log.d(TAG, "Skipping frame: " + frameHeader.getId() + ", size: " + frameHeader.getSize())
        skipBytes(frameHeader.getSize())
    }

    fun getPosition(): Int {
        return inputStream.count
    }

    /**
     * Skip a certain number of bytes on the given input stream.
     */
    fun skipBytes(number: Int) {
        if (number < 0) {
            throw ID3ReaderException("Trying to read a negative number of bytes")
        }
        IOUtils.skipFully(inputStream, number.toLong())
    }

    fun readByte(): Byte {
        return inputStream.read().toByte()
    }

    fun readShort(): Short {
        val firstByte = inputStream.read().toChar()
        val secondByte = inputStream.read().toChar()
        return ((firstByte.toInt() shl 8) or secondByte.toInt()).toShort()
    }

    fun readInt(): Int {
        val firstByte = inputStream.read().toChar()
        val secondByte = inputStream.read().toChar()
        val thirdByte = inputStream.read().toChar()
        val fourthByte = inputStream.read().toChar()
        return (firstByte.toInt() shl 24) or (secondByte.toInt() shl 16) or (thirdByte.toInt() shl 8) or
                fourthByte.toInt()
    }

    fun expectChar(expected: Char) {
        val read = inputStream.read().toChar()
        if (read != expected) {
            throw ID3ReaderException("Expected " + expected + " and got " + read)
        }
    }

    fun readTagHeader(): TagHeader {
        expectChar('I')
        expectChar('D')
        expectChar('3')
        val version = readShort()
        val flags = readByte()
        val size = unsynchsafe(readInt())
        if ((flags.toInt() and 0b01000000) != 0) {
            val extendedHeaderSize = readInt()
            skipBytes(extendedHeaderSize - 4)
        }
        return TagHeader("ID3", size, version, flags)
    }

    fun readFrameHeader(): FrameHeader {
        val id = readPlainBytesToString(FRAME_ID_LENGTH)
        var size = readInt()
        if (tagHeader != null && tagHeader!!.getVersion().toInt() >= 0x0400) {
            size = unsynchsafe(size)
        }
        val flags = readShort()
        return FrameHeader(id, size, flags)
    }

    private fun unsynchsafe(input: Int): Int {
        var out = 0
        var mask = 0x7F000000

        while (mask != 0) {
            out = out shr 1
            out = out or (input and mask)
            mask = mask shr 8
        }

        return out
    }

    /**
     * Reads a null-terminated string with encoding.
     */
    protected fun readEncodingAndString(max: Int): String {
        val encoding = readByte()
        return readEncodedString(encoding.toInt(), max - 1)
    }

    protected fun readPlainBytesToString(length: Int): String {
        val stringBuilder = StringBuilder()
        var bytesRead = 0
        while (bytesRead < length) {
            stringBuilder.append(readByte().toInt().toChar())
            bytesRead++
        }
        return stringBuilder.toString()
    }

    protected fun readIsoStringNullTerminated(max: Int): String {
        return readEncodedString(ENCODING_ISO.toInt(), max)
    }

    @Suppress("CharsetObjectCanBeUsed")
    fun readEncodedString(encoding: Int, max: Int): String {
        if (encoding == ENCODING_UTF16_WITH_BOM.toInt() || encoding == ENCODING_UTF16_WITHOUT_BOM.toInt()) {
            return readEncodedString2char(Charset.forName("UTF-16"), max)
        } else if (encoding == ENCODING_UTF8.toInt()) {
            return readEncodedString1char(Charset.forName("UTF-8"), max)
        } else {
            return readEncodedString1char(Charset.forName("ISO-8859-1"), max)
        }
    }

    /**
     * Reads chars where the encoding uses 1 char per symbol.
     */
    private fun readEncodedString1char(charset: Charset, max: Int): String {
        val bytes = ByteArrayOutputStream()
        var bytesRead = 0
        while (bytesRead < max) {
            val c = readByte()
            bytesRead++
            if (c == 0.toByte()) {
                break
            }
            bytes.write(c.toInt())
        }
        return charset.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
    }

    /**
     * Reads chars where the encoding uses 2 chars per symbol.
     */
    private fun readEncodedString2char(charset: Charset, max: Int): String {
        val bytes = ByteArrayOutputStream()
        var bytesRead = 0
        var foundEnd = false
        while (bytesRead + 1 < max) {
            val c1 = readByte()
            val c2 = readByte()
            if (c1 == 0.toByte() && c2 == 0.toByte()) {
                foundEnd = true
                break
            }
            bytesRead += 2
            bytes.write(c1.toInt())
            bytes.write(c2.toInt())
        }
        if (!foundEnd && bytesRead < max) {
            // Last character
            val c = readByte()
            if (c != 0.toByte()) {
                bytes.write(c.toInt())
            }
        }
        return try {
            charset.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
        } catch (e: MalformedInputException) {
            ""
        }
    }

    companion object {
        private const val TAG = "ID3Reader"
        private const val FRAME_ID_LENGTH = 4
        const val ENCODING_ISO: Byte = 0
        const val ENCODING_UTF16_WITH_BOM: Byte = 1
        const val ENCODING_UTF16_WITHOUT_BOM: Byte = 2
        const val ENCODING_UTF8: Byte = 3
    }
}
