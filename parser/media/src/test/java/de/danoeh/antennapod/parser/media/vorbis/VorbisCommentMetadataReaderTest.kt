package de.danoeh.antennapod.parser.media.vorbis

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import org.junit.Assert.assertEquals

@RunWith(RobolectricTestRunner::class)
class VorbisCommentMetadataReaderTest {

    @Test
    fun testRealFilesAuphonic() {
        testRealFileAuphonic("auphonic.ogg")
        testRealFileAuphonic("auphonic.opus")
        testRealFileAuphonic("opus-comment.opus")
    }

    fun testRealFileAuphonic(filename: String) {
        val inputStream = javaClass.classLoader!!
                .getResource(filename)!!.openStream()
        val reader = VorbisCommentMetadataReader(inputStream)
        reader.readInputStream()
        assertEquals("Summary", reader.getDescription())
    }
}
