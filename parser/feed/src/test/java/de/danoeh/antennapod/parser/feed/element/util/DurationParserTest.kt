package de.danoeh.antennapod.parser.feed.element.util

import de.danoeh.antennapod.parser.feed.util.DurationParser
import org.junit.Assert.assertEquals
import org.junit.Test

class DurationParserTest {
    private val milliseconds = 1
    private val seconds = 1000 * milliseconds
    private val minutes = 60 * seconds
    private val hours = 60 * minutes

    @Test
    fun testSecondDurationInMillis() {
        val duration = DurationParser.inMillis("00:45")
        assertEquals(45L * seconds, duration)
    }

    @Test
    fun testSingleNumberDurationInMillis() {
        val twoHoursInSeconds = 2 * 60 * 60
        val duration = DurationParser.inMillis(twoHoursInSeconds.toString())
        assertEquals(2L * hours, duration)
    }

    @Test
    fun testMinuteSecondDurationInMillis() {
        val duration = DurationParser.inMillis("05:10")
        assertEquals(5L * minutes + 10L * seconds, duration)
    }

    @Test
    fun testHourMinuteSecondDurationInMillis() {
        val duration = DurationParser.inMillis("02:15:45")
        assertEquals(2L * hours + 15L * minutes + 45L * seconds, duration)
    }

    @Test
    fun testSecondsWithMillisecondsInMillis() {
        val duration = DurationParser.inMillis("00:00:00.123")
        assertEquals(123L, duration)
    }
}
