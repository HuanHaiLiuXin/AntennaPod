package de.danoeh.antennapod.parser.transcript

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import de.danoeh.antennapod.model.feed.Transcript

@RunWith(RobolectricTestRunner::class)
class SrtTranscriptParserTest {

    @Test
    fun testParseSrt() {
        val result = SrtTranscriptParser.parse(srtStr)

        assertEquals(result!!.getSegmentAtTime(0L).getWords(), "Promoting your podcast in a new")
        assertEquals(result.getSegmentAtTime(0L).getSpeaker(), "John Doe")
        assertEquals(result.getSegmentAtTime(0L).getStartTime(), 0L)
        assertEquals(result.getSegmentAtTime(0L).getEndTime(), 50730L)
        assertEquals(result.getSegmentAtTime(90740L).getStartTime(), 90740L)
        assertEquals("way. The latest from PogNews. We bring your favorite podcast.",
                result.getSegmentAtTime(90740L).getWords())
    }

    @Test
    fun testParse() {
        var type = "application/srr"
        var result: Transcript?

        result = TranscriptParser.parse(srtStr, type)
        // There isn't a segment at 800L, so go backwards and get the segment at 0L
        assertEquals(result!!.getSegmentAtTime(800L).getWords(), "Promoting your podcast in a new")

        result = TranscriptParser.parse(null, type)
        assertEquals(result, null)

        // blank string
        val blankStr = ""
        result = TranscriptParser.parse(blankStr, type)
        assertNull(result)

        // All empty lines
        val allNewlinesStr = "\r\n\r\n\r\n\r\n"
        result = TranscriptParser.parse(allNewlinesStr, type)
        assertEquals(result, null)

        // first segment has invalid time formatting, so the entire segment will be thrown out
        val srtStrBad1 = "00:0000,000 --> 00:00:02,730\n"
                + "John Doe: Promoting your podcast in a new\n\n"
                + "2\n"
                + "00:00:02,730 --> 00:00:04,600\n"
                + "way. The latest from PogNews."
        result = TranscriptParser.parse(srtStrBad1, type)
        assertEquals(result!!.getSegmentAtTime(2730L).getWords(), "way. The latest from PogNews.")

        // first segment has invalid time in end time, 2nd segment has invalid time in both start time and end time
        val srtStrBad2 = "00:00:00,000 --> 00:0002,730\n"
                + "Jane Doe: Promoting your podcast in a new\n\n"
                + "2\n"
                + "badstarttime --> badendtime\n"
                + "way. The latest from PogNews.\n"
                + "badstarttime -->\n"
                + "Jane Doe says something\n"
                + "00:00:00,000 --> 00:00:02,730\n"
                + "Jane Doe:"
        result = TranscriptParser.parse(srtStrBad2, type)
        assertNull(result)

        // Just plain text
        val strBad3 = "John Doe: Promoting your podcast in a new\n\n"
                + "way. The latest from PogNews."
        result = TranscriptParser.parse(strBad3, type)
        assertNull(result)

        // passing the wrong type
        type = "application/json"
        result = TranscriptParser.parse(srtStr, type)
        assertEquals(result, null)

        type = "unknown"
        result = TranscriptParser.parse(srtStr, type)
        assertEquals(result, null)
    }

    companion object {
        private val srtStr = "1\n"
                + "00:00:00,000 --> 00:00:50,730\n"
                + "John Doe: Promoting your podcast in a new\n\n"
                + "2\n"
                + "00:00:90,740 --> 00:00:91,600\n"
                + "way. The latest from PogNews.\n\n"
                + "00:00:91,730 --> 00:00:93,600\n"
                + "We bring your favorite podcast."
    }
}
