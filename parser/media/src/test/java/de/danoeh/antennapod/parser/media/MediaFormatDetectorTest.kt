package de.danoeh.antennapod.parser.media

import org.junit.Assert.assertEquals

import java.io.ByteArrayInputStream

import org.junit.Test

class MediaFormatDetectorTest {

    @Test
    fun testMagicId3_detectsId3() {
        assertEquals(MediaFormatDetector.Format.ID3,
                MediaFormatDetector.detect(ByteArrayInputStream(byteArrayOf(0x49.toByte(), 0x44.toByte(), 0x33.toByte()))).format)
    }

    @Test
    fun testMagicOgg_detectsOgg() {
        assertEquals(MediaFormatDetector.Format.OGG,
                MediaFormatDetector.detect(ByteArrayInputStream(byteArrayOf(0x4F.toByte(), 0x67.toByte(), 0x67.toByte(), 0x53.toByte()))).format)
    }

    @Test
    fun testMagicM4a_detectsM4a() {
        assertEquals(MediaFormatDetector.Format.M4A,
                MediaFormatDetector.detect(ByteArrayInputStream(byteArrayOf(0, 0, 0, 0, 0x66.toByte(), 0x74.toByte(), 0x79.toByte(), 0x70.toByte()))).format)
    }

    @Test
    fun testMagicZeroed_detectsUnknown() {
        assertEquals(MediaFormatDetector.Format.UNKNOWN,
                MediaFormatDetector.detect(ByteArrayInputStream(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))).format)
    }

    @Test
    fun testMagicEmpty_detectsUnknown() {
        assertEquals(MediaFormatDetector.Format.UNKNOWN,
                MediaFormatDetector.detect(ByteArrayInputStream(byteArrayOf())).format)
    }

    @Test
    fun testMagicShort_detectsUnknown() {
        assertEquals(MediaFormatDetector.Format.UNKNOWN,
                MediaFormatDetector.detect(ByteArrayInputStream(byteArrayOf(0x66.toByte(), 0x74.toByte(), 0x79.toByte()))).format)
    }

    @Test
    fun testRawMp3FrameMagic_detectsUnknown() {
        assertEquals(MediaFormatDetector.Format.UNKNOWN,
                MediaFormatDetector.detect(ByteArrayInputStream(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x00.toByte(), 0x00.toByte()))).format)
    }
}
