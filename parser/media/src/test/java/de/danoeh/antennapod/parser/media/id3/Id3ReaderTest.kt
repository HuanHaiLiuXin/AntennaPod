package de.danoeh.antennapod.parser.media.id3

import de.danoeh.antennapod.parser.media.id3.model.FrameHeader
import de.danoeh.antennapod.parser.media.id3.model.TagHeader
import org.apache.commons.io.input.CountingInputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

@RunWith(RobolectricTestRunner::class)
class Id3ReaderTest {
    @Test
    fun testReadString() {
        val data = byteArrayOf(
            ID3Reader.ENCODING_ISO,
            'T'.code.toByte(), 'e'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(),
            0 // Null-terminated
        )
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val string = ID3Reader(inputStream).readEncodingAndString(1000)
        assertEquals("Test", string)
    }

    @Test
    fun testReadMultipleStrings() {
        val data = byteArrayOf(
            ID3Reader.ENCODING_ISO,
            'F'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(),
            0, // Null-terminated
            ID3Reader.ENCODING_ISO,
            'B'.code.toByte(), 'a'.code.toByte(), 'r'.code.toByte(),
            0 // Null-terminated
        )
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val reader = ID3Reader(inputStream)
        assertEquals("Foo", reader.readEncodingAndString(1000))
        assertEquals("Bar", reader.readEncodingAndString(1000))
    }

    @Test
    fun testReadingLimit() {
        val data = byteArrayOf(
            ID3Reader.ENCODING_ISO,
            'A'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte(), 'D'.code.toByte()
        )
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val reader = ID3Reader(inputStream)
        assertEquals("ABC", reader.readEncodingAndString(4)) // Includes encoding
        assertEquals('D'.code.toLong(), reader.readByte().toLong())
    }

    @Test
    fun testReadUtf16RespectsBom() {
        val data = byteArrayOf(
            ID3Reader.ENCODING_UTF16_WITH_BOM,
            0xff.toByte(), 0xfe.toByte(), // BOM: Little-endian
            'A'.code.toByte(), 0, 'B'.code.toByte(), 0, 'C'.code.toByte(), 0,
            0, 0, // Null-terminated
            ID3Reader.ENCODING_UTF16_WITH_BOM,
            0xfe.toByte(), 0xff.toByte(), // BOM: Big-endian
            0, 'D'.code.toByte(), 0, 'E'.code.toByte(), 0, 'F'.code.toByte(),
            0, 0, // Null-terminated
        )
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val reader = ID3Reader(inputStream)
        assertEquals("ABC", reader.readEncodingAndString(1000))
        assertEquals("DEF", reader.readEncodingAndString(1000))
    }

    @Test
    fun testReadUtf16NullPrefix() {
        val data = byteArrayOf(
            ID3Reader.ENCODING_UTF16_WITH_BOM,
            0xff.toByte(), 0xfe.toByte(), // BOM
            0x00, 0x01, // Latin Capital Letter A with macron (Ā)
            0, 0, // Null-terminated
        )
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val string = ID3Reader(inputStream).readEncodingAndString(1000)
        assertEquals("Ā", string)
    }

    @Test
    fun testReadingLimitUtf16() {
        val data = byteArrayOf(
            ID3Reader.ENCODING_UTF16_WITHOUT_BOM,
            'A'.code.toByte(), 0, 'B'.code.toByte(), 0, 'C'.code.toByte(), 0, 'D'.code.toByte(), 0
        )
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val reader = ID3Reader(inputStream)
        reader.readEncodingAndString(6) // Includes encoding, produces broken string
        assertTrue("Should respect limit even if it breaks a symbol", reader.getPosition() <= 6)
    }

    @Test
    fun testReadTagHeader() {
        val data = generateId3Header(23)
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val header = ID3Reader(inputStream).readTagHeader()
        assertEquals("ID3", header.getId())
        assertEquals(42, header.getVersion().toInt())
        assertEquals(23, header.getSize())
    }

    @Test
    fun testReadFrameHeader() {
        val data = generateFrameHeader("CHAP", 42)
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val header = ID3Reader(inputStream).readFrameHeader()
        assertEquals("CHAP", header.getId())
        assertEquals(42, header.getSize())
    }

    companion object {
        @JvmStatic
        fun generateFrameHeader(id: String, size: Int): ByteArray {
            return concat(
                id.toByteArray(StandardCharsets.ISO_8859_1), // Frame ID
                byteArrayOf(
                    (size shr 24).toByte(), (size shr 16).toByte(),
                    (size shr 8).toByte(), size.toByte(), // Size
                    0, 0 // Flags
                ))
        }

        @JvmStatic
        fun generateId3Header(size: Int): ByteArray {
            return byteArrayOf(
                    'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), // Identifier
                    0, 42, // Version
                    0, // Flags
                    (size shr 24).toByte(), (size shr 16).toByte(),
                    (size shr 8).toByte(), size.toByte() // Size
            )
        }

        @JvmStatic
        fun concat(vararg arrays: ByteArray): ByteArray {
            val outputStream = ByteArrayOutputStream()
            try {
                for (array in arrays) {
                    outputStream.write(array)
                }
            } catch (e: IOException) {
                fail(e.message)
            }
            return outputStream.toByteArray()
        }
    }
}
