package de.danoeh.antennapod.parser.feed.element.util

import de.danoeh.antennapod.parser.feed.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Test

import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * Unit test for [DateUtils].
 */
class DateUtilsTest {

    @Test
    fun testParseDateWithMicroseconds() {
        val exp = GregorianCalendar(2015, 2, 28, 13, 31, 4)
        exp.setTimeZone(TimeZone.getTimeZone("UTC"))
        val expected = Date(exp.getTimeInMillis() + 963)
        val actual = DateUtils.parse("2015-03-28T13:31:04.963870")
        assertEquals(expected, actual)
    }

    @Test
    fun testParseDateWithCentiseconds() {
        val exp = GregorianCalendar(2015, 2, 28, 13, 31, 4)
        exp.setTimeZone(TimeZone.getTimeZone("UTC"))
        val expected = Date(exp.getTimeInMillis() + 960)
        val actual = DateUtils.parse("2015-03-28T13:31:04.96")
        assertEquals(expected, actual)
    }

    @Test
    fun testParseDateWithZuluTimezone() {
        val exp = GregorianCalendar(2024, 4, 8, 22, 18, 52)
        exp.setTimeZone(TimeZone.getTimeZone("UTC"))
        val expected = Date(exp.getTimeInMillis() + 485)
        val actual = DateUtils.parse("2024-05-08T22:18:52.485Z")
        assertEquals(expected, actual)
    }

    @Test
    fun testParseDateWithDeciseconds() {
        val exp = GregorianCalendar(2015, 2, 28, 13, 31, 4)
        exp.setTimeZone(TimeZone.getTimeZone("UTC"))
        val expected = Date(exp.getTimeInMillis() + 900)
        val actual = DateUtils.parse("2015-03-28T13:31:04.9")
        assertEquals(expected!!.getTime() / 1000, actual!!.getTime() / 1000)
        assertEquals(900, actual.getTime() % 1000)
    }

    @Test
    fun testParseDateWithMicrosecondsAndTimezone() {
        val exp = GregorianCalendar(2015, 2, 28, 6, 31, 4)
        exp.setTimeZone(TimeZone.getTimeZone("UTC"))
        val expected = Date(exp.getTimeInMillis() + 963)
        val actual = DateUtils.parse("2015-03-28T13:31:04.963870 +0700")
        assertEquals(expected, actual)
    }

    @Test
    fun testParseDateWithCentisecondsAndTimezone() {
        val exp = GregorianCalendar(2015, 2, 28, 6, 31, 4)
        exp.setTimeZone(TimeZone.getTimeZone("UTC"))
        val expected = Date(exp.getTimeInMillis() + 960)
        val actual = DateUtils.parse("2015-03-28T13:31:04.96 +0700")
        assertEquals(expected, actual)
    }

    @Test
    fun testParseDateWithDecisecondsAndTimezone() {
        val exp = GregorianCalendar(2015, 2, 28, 6, 31, 4)
        exp.setTimeZone(TimeZone.getTimeZone("UTC"))
        val expected = Date(exp.getTimeInMillis() + 900)
        val actual = DateUtils.parse("2015-03-28T13:31:04.9 +0700")
        assertEquals(expected!!.getTime() / 1000, actual!!.getTime() / 1000)
        assertEquals(900, actual.getTime() % 1000)
    }

    @Test
    fun testParseDateWithTimezoneName() {
        val exp = GregorianCalendar(2015, 2, 28, 6, 31, 4)
        exp.setTimeZone(TimeZone.getTimeZone("UTC"))
        val expected = Date(exp.getTimeInMillis())
        val actual = DateUtils.parse("Sat, 28 Mar 2015 01:31:04 EST")
        assertEquals(expected, actual)
    }

    @Test
    fun testParseDateWithTimezoneName2() {
        val exp = GregorianCalendar(2015, 2, 28, 6, 31, 0)
        exp.setTimeZone(TimeZone.getTimeZone("UTC"))
        val expected = Date(exp.getTimeInMillis())
        val actual = DateUtils.parse("Sat, 28 Mar 2015 01:31 EST")
        assertEquals(expected, actual)
    }

    @Test
    fun testParseDateWithTimeZoneOffset() {
        val exp = GregorianCalendar(2015, 2, 28, 12, 16, 12)
        exp.setTimeZone(TimeZone.getTimeZone("UTC"))
        val expected = Date(exp.getTimeInMillis())
        val actual = DateUtils.parse("Sat, 28 March 2015 08:16:12 -0400")
        assertEquals(expected, actual)
    }

    @Test
    fun testAsctime() {
        val exp = GregorianCalendar(2011, 4, 25, 12, 33, 0)
        exp.setTimeZone(TimeZone.getTimeZone("UTC"))
        val expected = Date(exp.getTimeInMillis())
        val actual = DateUtils.parse("Wed, 25 May 2011 12:33:00")
        assertEquals(expected, actual)
    }

    @Test
    fun testMultipleConsecutiveSpaces() {
        val exp = GregorianCalendar(2010, 2, 23, 6, 6, 26)
        exp.setTimeZone(TimeZone.getTimeZone("UTC"))
        val expected = Date(exp.getTimeInMillis())
        val actual = DateUtils.parse("Tue,  23 Mar   2010 01:06:26 -0500")
        assertEquals(expected, actual)
    }

    @Test
    fun testParseDateWithNoTimezonePadding() {
        val exp = GregorianCalendar(2017, 1, 22, 22, 28, 0)
        exp.setTimeZone(TimeZone.getTimeZone("UTC"))
        val expected = Date(exp.getTimeInMillis() + 2)
        val actual = DateUtils.parse("2017-02-22T14:28:00.002-08:00")
        assertEquals(expected, actual)
    }

    /**
     * Requires Android platform. Root cause: [DateUtils] implementation makes
     * use of ISO 8601 time zone, which does not work on standard JDK.
     *
     * @see .testParseDateWithNoTimezonePadding
     */
    @Test
    fun testParseDateWithForCest() {
        val exp1 = GregorianCalendar(2017, 0, 28, 22, 0, 0)
        exp1.setTimeZone(TimeZone.getTimeZone("UTC"))
        val expected1 = Date(exp1.getTimeInMillis())
        val actual1 = DateUtils.parse("Sun, 29 Jan 2017 00:00:00 CEST")
        assertEquals(expected1, actual1)

        val exp2 = GregorianCalendar(2017, 0, 28, 23, 0, 0)
        exp2.setTimeZone(TimeZone.getTimeZone("UTC"))
        val expected2 = Date(exp2.getTimeInMillis())
        val actual2 = DateUtils.parse("Sun, 29 Jan 2017 00:00:00 CET")
        assertEquals(expected2, actual2)
    }

    @Test
    fun testParseDateWithIncorrectWeekday() {
        val exp1 = GregorianCalendar(2014, 9, 8, 9, 0, 0)
        exp1.setTimeZone(TimeZone.getTimeZone("GMT"))
        val expected = Date(exp1.getTimeInMillis())
        val actual = DateUtils.parse("Thu, 8 Oct 2014 09:00:00 GMT") // actually a Wednesday
        assertEquals(expected, actual)
    }

    @Test
    fun testParseDateWithBadAbbreviation() {
        val exp1 = GregorianCalendar(2014, 8, 8, 0, 0, 0)
        exp1.setTimeZone(TimeZone.getTimeZone("GMT"))
        val expected = Date(exp1.getTimeInMillis())
        val actual = DateUtils.parse("Mon, 8 Sept 2014 00:00:00 GMT") // should be Sep
        assertEquals(expected, actual)
    }

    @Test
    fun testParseDateWithTwoTimezones() {
        val exp1 = GregorianCalendar(2015, Calendar.MARCH, 1, 1, 0, 0)
        exp1.setTimeZone(TimeZone.getTimeZone("GMT-4"))
        val expected = Date(exp1.getTimeInMillis())
        val actual = DateUtils.parse("Sun 01 Mar 2015 01:00:00 GMT-0400 (EDT)")
        assertEquals(expected, actual)
    }
}
