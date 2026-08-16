package de.danoeh.antennapod.ui.common

import de.danoeh.antennapod.ui.common.Converter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Test class for converter
 */
class ConverterTest {

    @Test
    fun testGetDurationStringLong() {
        val expected = "13:05:10"
        val input = 47110000
        assertEquals(expected, Converter.getDurationStringLong(input))
    }

    @Test
    fun testGetDurationStringShort() {
        val expected = "13:05"
        assertEquals(expected, Converter.getDurationStringShort(47110000, true))
        assertEquals(expected, Converter.getDurationStringShort(785000, false))
    }

    @Test
    fun testDurationStringLongToMs() {
        val input = "01:20:30"
        val expected = 4830000L
        assertEquals(expected, Converter.durationStringLongToMs(input).toLong())
    }

    @Test
    fun testDurationStringShortToMs() {
        val input = "8:30"
        assertEquals(30600000, Converter.durationStringShortToMs(input, true))
        assertEquals(510000, Converter.durationStringShortToMs(input, false))
    }
}
