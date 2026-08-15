package de.danoeh.antennapod.parser.media.id3

import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.model.feed.EmbeddedChapterImage
import de.danoeh.antennapod.parser.media.id3.model.FrameHeader
import org.apache.commons.io.input.CountingInputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.io.ByteArrayInputStream

import org.junit.Assert.assertEquals

@RunWith(RobolectricTestRunner::class)
class ChapterReaderTest {

    @Test
    fun testReadFullTagWithChapter() {
        val chapter = Id3ReaderTest.concat(
                Id3ReaderTest.generateFrameHeader(ChapterReader.FRAME_ID_CHAPTER, CHAPTER_WITHOUT_SUBFRAME.size),
                CHAPTER_WITHOUT_SUBFRAME)
        val data = Id3ReaderTest.concat(
                Id3ReaderTest.generateId3Header(chapter.size),
                chapter)
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val reader = ChapterReader(inputStream)
        reader.readInputStream()
        assertEquals(1, reader.getChapters().size)
        assertEquals(CHAPTER_WITHOUT_SUBFRAME_START_TIME.toLong(), reader.getChapters()[0].getStart())
    }

    @Test
    fun testReadFullTagWithMultipleChapters() {
        val chapter = Id3ReaderTest.concat(
                Id3ReaderTest.generateFrameHeader(ChapterReader.FRAME_ID_CHAPTER, CHAPTER_WITHOUT_SUBFRAME.size),
                CHAPTER_WITHOUT_SUBFRAME)
        val data = Id3ReaderTest.concat(
                Id3ReaderTest.generateId3Header(2 * chapter.size),
                chapter,
                chapter)
        val inputStream = CountingInputStream(ByteArrayInputStream(data))
        val reader = ChapterReader(inputStream)
        reader.readInputStream()
        assertEquals(2, reader.getChapters().size)
        assertEquals(CHAPTER_WITHOUT_SUBFRAME_START_TIME.toLong(), reader.getChapters()[0].getStart())
        assertEquals(CHAPTER_WITHOUT_SUBFRAME_START_TIME.toLong(), reader.getChapters()[1].getStart())
    }

    @Test
    fun testReadChapterWithoutSubframes() {
        val header = FrameHeader(ChapterReader.FRAME_ID_CHAPTER,
                CHAPTER_WITHOUT_SUBFRAME.size, 0.toShort())
        val inputStream = CountingInputStream(ByteArrayInputStream(CHAPTER_WITHOUT_SUBFRAME))
        val chapter = ChapterReader(inputStream).readChapter(header)
        assertEquals(CHAPTER_WITHOUT_SUBFRAME_START_TIME.toLong(), chapter.getStart())
    }

    @Test
    fun testReadChapterWithTitle() {
        val title = byteArrayOf(
            ID3Reader.ENCODING_ISO,
            'H'.code.toByte(), 'e'.code.toByte(), 'l'.code.toByte(), 'l'.code.toByte(), 'o'.code.toByte(), // Title
            0 // Null-terminated
        )
        val chapterData = Id3ReaderTest.concat(
            CHAPTER_WITHOUT_SUBFRAME,
            Id3ReaderTest.generateFrameHeader(ChapterReader.FRAME_ID_TITLE, title.size),
            title)
        val header = FrameHeader(ChapterReader.FRAME_ID_CHAPTER, chapterData.size, 0.toShort())
        val inputStream = CountingInputStream(ByteArrayInputStream(chapterData))
        val reader = ChapterReader(inputStream)
        val chapter = reader.readChapter(header)
        assertEquals(CHAPTER_WITHOUT_SUBFRAME_START_TIME.toLong(), chapter.getStart())
        assertEquals("Hello", chapter.getTitle())
    }

    @Test
    fun testReadTitleWithGarbage() {
        val titleSubframeContent = byteArrayOf(
                ID3Reader.ENCODING_ISO,
                'A'.code.toByte(), // Title
                0, // Null-terminated
                42, 42, 42, 42 // Garbage, should be ignored
        )
        val header = FrameHeader(ChapterReader.FRAME_ID_TITLE, titleSubframeContent.size, 0.toShort())
        val inputStream = CountingInputStream(ByteArrayInputStream(titleSubframeContent))
        val reader = ChapterReader(inputStream)
        val chapter = Chapter()
        reader.readChapterSubFrame(header, chapter)
        assertEquals("A", chapter.getTitle())

        // Should skip the garbage and point to the next frame
        assertEquals(titleSubframeContent.size, reader.getPosition())
    }

    @Test
    fun testRealFileUltraschall() {
        val inputStream = CountingInputStream(javaClass.classLoader!!
                .getResource("ultraschall5.mp3")!!.openStream())
        val reader = ChapterReader(inputStream)
        reader.readInputStream()
        val chapters = reader.getChapters()

        assertEquals(3, chapters.size)

        assertEquals(0L, chapters[0].getStart())
        assertEquals(4004L, chapters[1].getStart())
        assertEquals(7999L, chapters[2].getStart())

        assertEquals("Marke 1", chapters[0].getTitle())
        assertEquals("Marke 2", chapters[1].getTitle())
        assertEquals("Marke 3", chapters[2].getTitle())

        assertEquals("https://example.com", chapters[0].getLink())
        assertEquals("https://example.com", chapters[1].getLink())
        assertEquals("https://example.com", chapters[2].getLink())

        assertEquals(EmbeddedChapterImage.makeUrl(16073, 2750569), chapters[0].getImageUrl())
        assertEquals(EmbeddedChapterImage.makeUrl(2766765, 15740), chapters[1].getImageUrl())
        assertEquals(EmbeddedChapterImage.makeUrl(2782628, 2750569), chapters[2].getImageUrl())
    }

    @Test
    fun testRealFileAuphonic() {
        val inputStream = CountingInputStream(javaClass.classLoader!!
                .getResource("auphonic.mp3")!!.openStream())
        val reader = ChapterReader(inputStream)
        reader.readInputStream()
        val chapters = reader.getChapters()

        assertEquals(4, chapters.size)

        assertEquals(0L, chapters[0].getStart())
        assertEquals(3000L, chapters[1].getStart())
        assertEquals(6000L, chapters[2].getStart())
        assertEquals(9000L, chapters[3].getStart())

        assertEquals("Chapter 1 - ❤️😊", chapters[0].getTitle())
        assertEquals("Chapter 2 - ßöÄ", chapters[1].getTitle())
        assertEquals("Chapter 3 - 爱", chapters[2].getTitle())
        assertEquals("Chapter 4", chapters[3].getTitle())

        assertEquals("https://example.com", chapters[0].getLink())
        assertEquals("https://example.com", chapters[1].getLink())
        assertEquals("https://example.com", chapters[2].getLink())
        assertEquals("https://example.com", chapters[3].getLink())

        assertEquals(EmbeddedChapterImage.makeUrl(765, 308), chapters[0].getImageUrl())
        assertEquals(EmbeddedChapterImage.makeUrl(1271, 308), chapters[1].getImageUrl())
        assertEquals(EmbeddedChapterImage.makeUrl(1771, 308), chapters[2].getImageUrl())
        assertEquals(EmbeddedChapterImage.makeUrl(2259, 308), chapters[3].getImageUrl())
    }

    @Test
    fun testRealFileHindenburgJournalistPro() {
        val inputStream = CountingInputStream(javaClass.classLoader!!
                .getResource("hindenburg-journalist-pro.mp3")!!.openStream())
        val reader = ChapterReader(inputStream)
        reader.readInputStream()
        val chapters = reader.getChapters()

        assertEquals(2, chapters.size)

        assertEquals(0L, chapters[0].getStart())
        assertEquals(5006L, chapters[1].getStart())

        assertEquals("Chapter Marker 1", chapters[0].getTitle())
        assertEquals("Chapter Marker 2", chapters[1].getTitle())

        assertEquals("https://example.com/chapter1url", chapters[0].getLink())
        assertEquals("https://example.com/chapter2url", chapters[1].getLink())

        assertEquals(EmbeddedChapterImage.makeUrl(5330, 4015), chapters[0].getImageUrl())
        assertEquals(EmbeddedChapterImage.makeUrl(9498, 4364), chapters[1].getImageUrl())
    }

    @Test
    fun testRealFileMp3chapsPy() {
        val inputStream = CountingInputStream(javaClass.classLoader!!
                .getResource("mp3chaps-py.mp3")!!.openStream())
        val reader = ChapterReader(inputStream)
        reader.readInputStream()
        val chapters = reader.getChapters()

        assertEquals(4, chapters.size)

        assertEquals(0L, chapters[0].getStart())
        assertEquals(7000L, chapters[1].getStart())
        assertEquals(9000L, chapters[2].getStart())
        assertEquals(11000L, chapters[3].getStart())

        assertEquals("Start", chapters[0].getTitle())
        assertEquals("Chapter 1", chapters[1].getTitle())
        assertEquals("Chapter 2", chapters[2].getTitle())
        assertEquals("Chapter 3", chapters[3].getTitle())
    }

    companion object {
        private val CHAPTER_WITHOUT_SUBFRAME_START_TIME: Byte = 23
        private val CHAPTER_WITHOUT_SUBFRAME = byteArrayOf(
                'C'.code.toByte(), 'H'.code.toByte(), '1'.code.toByte(), 0, // String ID for mapping to CTOC
                0, 0, 0, CHAPTER_WITHOUT_SUBFRAME_START_TIME, // Start time
                0, 0, 0, 0, // End time
                0, 0, 0, 0, // Start offset
                0, 0, 0, 0 // End offset
        )
    }
}
