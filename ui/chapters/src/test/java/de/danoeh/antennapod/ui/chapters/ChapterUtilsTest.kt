package de.danoeh.antennapod.ui.chapters

import android.content.Context

import androidx.test.core.app.ApplicationProvider

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.io.File

import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.parser.media.MediaFormatDetector

@RunWith(RobolectricTestRunner::class)
class ChapterUtilsTest {

    @Test
    fun testMimeMpeg_selectsId3() {
        assertEquals(MediaFormatDetector.Format.ID3,
                ChapterUtils.detectHintFromMetadata("audio/mpeg", "http://example.com/file?token=abc"))
    }

    @Test
    fun testMimeMp3_selectsId3() {
        assertEquals(MediaFormatDetector.Format.ID3,
                ChapterUtils.detectHintFromMetadata("audio/mp3", "http://example.com/file?token=abc"))
    }

    @Test
    fun testMimeXMp3_selectsId3() {
        assertEquals(MediaFormatDetector.Format.ID3,
                ChapterUtils.detectHintFromMetadata("audio/x-mp3", "http://example.com/file?token=abc"))
    }

    @Test
    fun testMimeOgg_selectsOgg() {
        assertEquals(MediaFormatDetector.Format.OGG,
                ChapterUtils.detectHintFromMetadata("audio/ogg", "http://example.com/file?token=abc"))
    }

    @Test
    fun testMimeApplicationOgg_selectsOgg() {
        assertEquals(MediaFormatDetector.Format.OGG,
                ChapterUtils.detectHintFromMetadata("application/ogg", "http://example.com/file?token=abc"))
    }

    @Test
    fun testMimeOpus_selectsOgg() {
        assertEquals(MediaFormatDetector.Format.OGG,
                ChapterUtils.detectHintFromMetadata("audio/opus", "http://example.com/file?token=abc"))
    }

    @Test
    fun testMimeApplicationOpus_selectsOgg() {
        assertEquals(MediaFormatDetector.Format.OGG,
                ChapterUtils.detectHintFromMetadata("application/opus", "http://example.com/file?token=abc"))
    }

    @Test
    fun testMimeM4a_selectsM4a() {
        assertEquals(MediaFormatDetector.Format.M4A,
                ChapterUtils.detectHintFromMetadata("audio/mp4", "http://example.com/file?token=abc"))
    }

    @Test
    fun testMimeVideoMp4_selectsM4a() {
        assertEquals(MediaFormatDetector.Format.M4A,
                ChapterUtils.detectHintFromMetadata("video/mp4", "http://example.com/file?token=abc"))
    }

    @Test
    fun testMimeM4b_selectsM4a() {
        assertEquals(MediaFormatDetector.Format.M4A,
                ChapterUtils.detectHintFromMetadata("audio/m4b", "http://example.com/file?token=abc"))
    }

    @Test
    fun testMimeXM4b_selectsM4a() {
        assertEquals(MediaFormatDetector.Format.M4A,
                ChapterUtils.detectHintFromMetadata("audio/x-m4b", "http://example.com/file?token=abc"))
    }

    @Test
    fun testMimePreferredOverExtension() {
        assertEquals(MediaFormatDetector.Format.ID3,
                ChapterUtils.detectHintFromMetadata("audio/mpeg", "http://example.com/file.m4a?token=abc"))
    }

    @Test
    fun testExtensionMp3_selectsId3() {
        assertEquals(MediaFormatDetector.Format.ID3,
                ChapterUtils.detectHintFromMetadata(null, "http://example.com/file.mp3?token=abc"))
    }

    @Test
    fun testExtensionOgg_selectsOgg() {
        assertEquals(MediaFormatDetector.Format.OGG,
                ChapterUtils.detectHintFromMetadata(null, "http://example.com/file.ogg?token=abc"))
    }

    @Test
    fun testExtensionOpus_selectsOgg() {
        assertEquals(MediaFormatDetector.Format.OGG,
                ChapterUtils.detectHintFromMetadata(null, "http://example.com/file.opus?token=abc"))
    }

    @Test
    fun testExtensionM4a_selectsM4a() {
        assertEquals(MediaFormatDetector.Format.M4A,
                ChapterUtils.detectHintFromMetadata(null, "http://example.com/file.m4a?token=abc"))
    }

    @Test
    fun testExtensionMp4_selectsM4a() {
        assertEquals(MediaFormatDetector.Format.M4A,
                ChapterUtils.detectHintFromMetadata(null, "http://example.com/file.mp4?token=abc"))
    }

    @Test
    fun testExtensionM4b_selectsM4a() {
        assertEquals(MediaFormatDetector.Format.M4A,
                ChapterUtils.detectHintFromMetadata(null, "http://example.com/file.m4b?token=abc"))
    }

    @Test
    fun testUnknownMetadata_detectsUnknown() {
        assertEquals(MediaFormatDetector.Format.UNKNOWN,
                ChapterUtils.detectHintFromMetadata(null, "http://example.com/file.unknown?token=abc"))
    }

    @Test
    fun testFallbackOrderOggHint_skipsOgg() {
        assertEquals(MediaFormatDetector.Format.ID3,
                ChapterUtils.getFallbackOrder(MediaFormatDetector.Format.OGG)[0])
        assertEquals(MediaFormatDetector.Format.M4A,
                ChapterUtils.getFallbackOrder(MediaFormatDetector.Format.OGG)[1])
    }

    @Test
    fun testFallbackOrderM4aHint_skipsM4a() {
        assertEquals(MediaFormatDetector.Format.ID3,
                ChapterUtils.getFallbackOrder(MediaFormatDetector.Format.M4A)[0])
        assertEquals(MediaFormatDetector.Format.OGG,
                ChapterUtils.getFallbackOrder(MediaFormatDetector.Format.M4A)[1])
    }

    @Test
    fun testFallbackOrderUnknownHint_skipsId3BecauseAlreadyTried() {
        assertEquals(MediaFormatDetector.Format.OGG,
                ChapterUtils.getFallbackOrder(MediaFormatDetector.Format.UNKNOWN)[0])
        assertEquals(MediaFormatDetector.Format.M4A,
                ChapterUtils.getFallbackOrder(MediaFormatDetector.Format.UNKNOWN)[1])
    }

    @Test
    @Throws(Exception::class)
    fun testLoadChaptersFromMediaFile_stitchedMp3_readsChapters() {
        val chapters = loadFixtureChapters("auphonic.mp3", "audio/mpeg")

        assertEquals(4, chapters.size)
        assertEquals(0L, chapters.get(0).getStart())
        assertEquals("Chapter 1 - ❤️😊", chapters.get(0).getTitle())
    }

    @Test
    @Throws(Exception::class)
    fun testLoadChaptersFromMediaFile_magicBytesOverrideMetadataHint() {
        val chapters = loadFixtureChapters("auphonic.mp3", "audio/ogg")

        assertEquals(4, chapters.size)
        assertEquals(0L, chapters.get(0).getStart())
        assertEquals("Chapter 1 - ❤️😊", chapters.get(0).getTitle())
    }

    @Test
    @Throws(Exception::class)
    fun testLoadChaptersFromMediaFile_stitchedOgg_readsChapters() {
        val chapters = loadFixtureChapters("auphonic.ogg", "audio/ogg")

        assertEquals(4, chapters.size)
        assertEquals(0L, chapters.get(0).getStart())
        assertEquals("Chapter 1 - ❤️😊", chapters.get(0).getTitle())
    }

    @Test
    @Throws(Exception::class)
    fun testLoadChaptersFromMediaFile_stitchedM4a_readsChapters() {
        val chapters = loadFixtureChapters("nero-chapters.m4a", "audio/mp4")

        assertEquals(4, chapters.size)
        assertEquals(0L, chapters.get(0).getStart())
        assertEquals("Chapter 1 - ❤️😊", chapters.get(0).getTitle())
    }

    @Throws(Exception::class)
    private fun loadFixtureChapters(filename: String, mimeType: String): List<Chapter> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val path = File(javaClass.classLoader!!.getResource(filename)!!.toURI()).path

        val media = FeedMedia(1L, null, 0, 0, 0L, mimeType,
                path, "http://example.com/" + filename, 1L, null, 0, 0L)
        return ChapterUtils.loadChaptersFromMediaFile(media, context)!!
    }
}
