package de.danoeh.antennapod.ui.cleaner

import android.content.Context

import androidx.test.platform.app.InstrumentationRegistry
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test class for [ShownotesCleaner].
 */
@RunWith(RobolectricTestRunner::class)
class ShownotesCleanerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext()
    }

    @Test
    fun testProcessShownotesAddTimecodeHhmmssNoChapters() {
        val timeStr = "10:11:12"
        val time = 3600L * 1000 * 10 + 60L * 1000 * 11 + 12L * 1000

        val shownotes = "<p> Some test text with a timecode " + timeStr + " here.</p>"
        val t = ShownotesCleaner(context, shownotes, Integer.MAX_VALUE)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeHhmmssMoreThen24HoursNoChapters() {
        val timeStr = "25:00:00"
        val time = 25L * 60 * 60 * 1000

        val shownotes = "<p> Some test text with a timecode " + timeStr + " here.</p>"
        val t = ShownotesCleaner(context, shownotes, Integer.MAX_VALUE)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeHhmmNoChapters() {
        val timeStr = "10:11"
        val time = 3600L * 1000 * 10 + 60L * 1000 * 11

        val shownotes = "<p> Some test text with a timecode " + timeStr + " here.</p>"
        val t = ShownotesCleaner(context, shownotes, Integer.MAX_VALUE)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeMmssNoChapters() {
        val timeStr = "10:11"
        val time = 10L * 60 * 1000 + 11L * 1000

        val shownotes = "<p> Some test text with a timecode " + timeStr + " here.</p>"
        val t = ShownotesCleaner(context, shownotes, 11 * 60 * 1000)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeHmmssNoChapters() {
        val timeStr = "2:11:12"
        val time = 2L * 60 * 60 * 1000 + 11L * 60 * 1000 + 12L * 1000

        val shownotes = "<p> Some test text with a timecode " + timeStr + " here.</p>"
        val t = ShownotesCleaner(context, shownotes, Integer.MAX_VALUE)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeMssNoChapters() {
        val timeStr = "1:12"
        val time = 60L * 1000 + 12L * 1000

        val shownotes = "<p> Some test text with a timecode " + timeStr + " here.</p>"
        val t = ShownotesCleaner(context, shownotes, 2 * 60 * 1000)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddNoTimecodeDuration() {
        val timeStr = "2:11:12"
        val time = 2 * 60 * 60 * 1000 + 11 * 60 * 1000 + 12 * 1000

        val shownotes = "<p> Some test text with a timecode " + timeStr + " here.</p>"
        val t = ShownotesCleaner(context, shownotes, time)
        val res = t.processShownotes()
        val d = Jsoup.parse(res)
        assertEquals("Should not parse time codes that equal duration", 0, d.body().getElementsByTag("a").size)
    }

    @Test
    fun testProcessShownotesAddTimecodeMultipleFormatsNoChapters() {
        val timeStrings = arrayOf("10:12", "1:10:12")

        val shownotes = "<p> Some test text with a timecode " + timeStrings[0] +
                " here. Hey look another one " + timeStrings[1] + " here!</p>"
        val t = ShownotesCleaner(context, shownotes, 2 * 60 * 60 * 1000)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(10L * 60 * 1000 + 12L * 1000,
                60L * 60 * 1000 + 10L * 60 * 1000 + 12L * 1000), timeStrings)
    }

    @Test
    fun testProcessShownotesAddTimecodeMultipleShortFormatNoChapters() {

        // One of these timecodes fits as HH:MM and one does not so both should be parsed as MM:SS.
        val timeStrings = arrayOf("10:12", "2:12")

        val shownotes = "<p> Some test text with a timecode " + timeStrings[0] +
                " here. Hey look another one " + timeStrings[1] + " here!</p>"
        val t = ShownotesCleaner(context, shownotes, 3 * 60 * 60 * 1000)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(10L * 60 * 1000 + 12L * 1000, 2L * 60 * 1000 + 12L * 1000), timeStrings)
    }

    @Test
    fun testProcessShownotesAddTimecodeParentheses() {
        val timeStr = "10:11"
        val time = 3600L * 1000 * 10 + 60L * 1000 * 11

        val shownotes = "<p> Some test text with a timecode (" + timeStr + ") here.</p>"
        val t = ShownotesCleaner(context, shownotes, Integer.MAX_VALUE)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeBrackets() {
        val timeStr = "10:11"
        val time = 3600L * 1000 * 10 + 60L * 1000 * 11

        val shownotes = "<p> Some test text with a timecode [" + timeStr + "] here.</p>"
        val t = ShownotesCleaner(context, shownotes, Integer.MAX_VALUE)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAddTimecodeAngleBrackets() {
        val timeStr = "10:11"
        val time = 3600L * 1000 * 10 + 60L * 1000 * 11

        val shownotes = "<p> Some test text with a timecode <" + timeStr + "> here.</p>"
        val t = ShownotesCleaner(context, shownotes, Integer.MAX_VALUE)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(time), arrayOf(timeStr))
    }

    @Test
    fun testProcessShownotesAndInvalidTimecode() {
        val timeStrs = arrayOf("2:1", "0:0", "000", "00", "00:000")

        val shownotes = StringBuilder("<p> Some test text with timecodes ")
        for (timeStr in timeStrs) {
            shownotes.append(timeStr).append(" ")
        }
        shownotes.append("here.</p>")

        val t = ShownotesCleaner(context, shownotes.toString(), Integer.MAX_VALUE)
        val res = t.processShownotes()
        checkLinkCorrect(res, longArrayOf(), arrayOf())
    }

    private fun checkLinkCorrect(res: String?, timecodes: LongArray, timecodeStr: Array<String>) {
        assertNotNull(res)
        val d: Document = Jsoup.parse(res!!)
        val links: Elements = d.body().getElementsByTag("a")
        var countedLinks = 0
        for (link in links) {
            val href = link.attributes().get("href")
            val text = link.text()
            if (href.startsWith("antennapod://")) {
                assertTrue(href.endsWith(timecodes[countedLinks].toString()))
                assertEquals(timecodeStr[countedLinks], text)
                countedLinks++
                assertTrue("Contains too many links: " + countedLinks + " > " +
                        timecodes.size, countedLinks <= timecodes.size)
            }
        }
        assertEquals(timecodes.size.toLong(), countedLinks.toLong())
    }

    @Test
    fun testIsTimecodeLink() {
        assertFalse(ShownotesCleaner.isTimecodeLink(null))
        assertFalse(ShownotesCleaner.isTimecodeLink("http://antennapod/timecode/123123"))
        assertFalse(ShownotesCleaner.isTimecodeLink("antennapod://timecode/"))
        assertFalse(ShownotesCleaner.isTimecodeLink("antennapod://123123"))
        assertFalse(ShownotesCleaner.isTimecodeLink("antennapod://timecode/123123a"))
        assertTrue(ShownotesCleaner.isTimecodeLink("antennapod://timecode/123"))
        assertTrue(ShownotesCleaner.isTimecodeLink("antennapod://timecode/1"))
    }

    @Test
    fun testGetTimecodeLinkTime() {
        assertEquals(-1, ShownotesCleaner.getTimecodeLinkTime("not a link"))
        assertEquals(-1, ShownotesCleaner.getTimecodeLinkTime("http://timecode/123"))
        assertEquals(123, ShownotesCleaner.getTimecodeLinkTime("antennapod://timecode/123"))
    }

    @Test
    fun testCleanupColors() {
        val input = "/* /* */ .foo { text-decoration: underline;color:#f00;font-weight:bold;}" +
                "#bar { text-decoration: underline;color:#f00;font-weight:bold; }" +
                "div {text-decoration: underline; color /* */ : /* */ #f00 /* */; font-weight:bold; }" +
                "#foobar { /* color: */ text-decoration: underline; /* color: */font-weight:bold /* ; */; }" +
                "baz { background-color:#f00;border: solid 2px;border-color:#0f0;text-decoration: underline; }"
        val expected = " .foo { text-decoration: underline;font-weight:bold;}" +
                "#bar { text-decoration: underline;font-weight:bold; }" +
                "div {text-decoration: underline;  font-weight:bold; }" +
                "#foobar {  text-decoration: underline; font-weight:bold ; }" +
                "baz { background-color:#f00;border: solid 2px;border-color:#0f0;text-decoration: underline; }"
        assertEquals(expected, ShownotesCleaner.cleanStyleTag(input))
    }
}
