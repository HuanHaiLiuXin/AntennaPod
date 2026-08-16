package de.danoeh.antennapod.parser.media.m4a

import de.danoeh.antennapod.model.feed.Chapter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import org.junit.Assert.assertEquals

@RunWith(RobolectricTestRunner::class)
class M4AChapterReaderTest {

    @Test
    fun testFiles() {
        testFile()
    }

    fun testFile() {
        val inputStream = javaClass.classLoader!!
                .getResource("nero-chapters.m4a")!!.openStream()
        val reader = M4AChapterReader(inputStream)
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
    }
}
