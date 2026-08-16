package de.danoeh.antennapod.parser.media.id3

import org.apache.commons.io.input.CountingInputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import org.junit.Assert.assertEquals

@RunWith(RobolectricTestRunner::class)
class MetadataReaderTest {
    @Test
    fun testRealFileUltraschall() {
        val inputStream = CountingInputStream(javaClass.classLoader!!
                .getResource("ultraschall5.mp3")!!.openStream())
        val reader = Id3MetadataReader(inputStream)
        reader.readInputStream()
        assertEquals("Description", reader.getComment())
    }

    @Test
    fun testRealFileAuphonic() {
        val inputStream = CountingInputStream(javaClass.classLoader!!
                .getResource("auphonic.mp3")!!.openStream())
        val reader = Id3MetadataReader(inputStream)
        reader.readInputStream()
        assertEquals("Summary", reader.getComment())
    }

    @Test
    fun testRealFileHindenburgJournalistPro() {
        val inputStream = CountingInputStream(javaClass.classLoader!!
                .getResource("hindenburg-journalist-pro.mp3")!!.openStream())
        val reader = Id3MetadataReader(inputStream)
        reader.readInputStream()
        assertEquals("This is the summary of this podcast episode. This file was made with"
                + " Hindenburg Journalist Pro version 1.85, build number 2360.", reader.getComment())
    }

    @Test
    fun testRealFileMp3chapsPy() {
        val inputStream = CountingInputStream(javaClass.classLoader!!
                .getResource("mp3chaps-py.mp3")!!.openStream())
        val reader = Id3MetadataReader(inputStream)
        reader.readInputStream()
        assertEquals("2021.08.13", reader.getComment())
    }

    @Test
    fun testRealFileFfmpegComment() {
        // "ffmpeg -i in.mp3 -c copy out.mp3" converts the COMM frame to a TXXX frame with description "comment"
        val inputStream = CountingInputStream(javaClass.classLoader!!
                .getResource("ffmpeg-txxx-comment.mp3")!!.openStream())
        val reader = Id3MetadataReader(inputStream)
        reader.readInputStream()
        assertEquals("This is the comment", reader.getComment())
    }

}
