package de.danoeh.antennapod.storage.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

class ReleaseScheduleGuesserTest {
    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
    }

    private fun makeDate(dateStr: String): Date {
        try {
            return DATE_FORMAT.parse(dateStr)!!
        } catch (e: ParseException) {
            throw RuntimeException(e)
        }
    }

    private fun assertClose(expected: Date, actual: Date?, tolerance: Long) {
        assertTrue("Date should differ at most " + tolerance / 60000 + " minutes from "
                + DATE_FORMAT.format(expected) + ", but is " + DATE_FORMAT.format(actual),
                Math.abs(expected.getTime() - actual!!.getTime()) < tolerance)
    }

    @Test
    fun testEdgeCases() {
        val releaseDates = ArrayList<Date>()
        assertEquals(ReleaseScheduleGuesser.Schedule.UNKNOWN, ReleaseScheduleGuesser.performGuess(releaseDates).schedule)
        releaseDates.add(makeDate("2024-01-01 16:30"))
        assertEquals(ReleaseScheduleGuesser.Schedule.UNKNOWN, ReleaseScheduleGuesser.performGuess(releaseDates).schedule)
    }

    @Test
    fun testMultipleTimesPerDayEveryDay() {
        val releaseDates = ArrayList<Date>()
        releaseDates.add(makeDate("2024-01-01 12:00"))
        releaseDates.add(makeDate("2024-01-01 16:00"))
        releaseDates.add(makeDate("2024-01-02 12:00"))
        releaseDates.add(makeDate("2024-01-02 16:00"))
        releaseDates.add(makeDate("2024-01-03 12:00"))
        releaseDates.add(makeDate("2024-01-03 16:00"))
        releaseDates.add(makeDate("2024-01-04 12:00"))
        releaseDates.add(makeDate("2024-01-04 16:00"))
        releaseDates.add(makeDate("2024-01-06 12:00"))
        releaseDates.add(makeDate("2024-01-06 16:00"))
        releaseDates.add(makeDate("2024-01-07 12:00"))
        releaseDates.add(makeDate("2024-01-07 16:00"))
        val guess = ReleaseScheduleGuesser.performGuess(releaseDates)
        assertEquals(ReleaseScheduleGuesser.Schedule.SPECIFIC_DAYS, guess.schedule)
        assertTrue(guess.multipleReleasesPerDay)
        val expectedDays = ArrayList<Int>()
        expectedDays.add(1)
        expectedDays.add(2)
        expectedDays.add(3)
        expectedDays.add(4)
        expectedDays.add(5)
        expectedDays.add(7)
        assertEquals(expectedDays, guess.days)
        assertClose(makeDate("2024-01-08 12:00"), guess.nextExpectedDate, ReleaseScheduleGuesser.ONE_DAY)
    }

    @Test
    fun testMultipleTimesPerDayWeekdays() {
        val releaseDates = ArrayList<Date>()
        releaseDates.add(makeDate("2024-01-01 00:00"))
        releaseDates.add(makeDate("2024-01-01 12:00"))
        releaseDates.add(makeDate("2024-01-02 00:00"))
        releaseDates.add(makeDate("2024-01-02 12:00"))
        releaseDates.add(makeDate("2024-01-03 00:00"))
        releaseDates.add(makeDate("2024-01-03 12:00"))
        releaseDates.add(makeDate("2024-01-04 00:00"))
        releaseDates.add(makeDate("2024-01-04 12:00"))
        releaseDates.add(makeDate("2024-01-05 00:00"))
        releaseDates.add(makeDate("2024-01-05 12:00"))
        val guess = ReleaseScheduleGuesser.performGuess(releaseDates)
        assertEquals(ReleaseScheduleGuesser.Schedule.WEEKDAYS, guess.schedule)
        assertTrue(guess.multipleReleasesPerDay)
        assertClose(makeDate("2024-01-08 12:00"), guess.nextExpectedDate, ReleaseScheduleGuesser.ONE_DAY)
    }

    @Test
    fun testMultipleTimesPerDaySpecificDays() {
        val releaseDates = ArrayList<Date>()
        releaseDates.add(makeDate("2024-01-01 00:00"))
        releaseDates.add(makeDate("2024-01-01 12:00"))
        releaseDates.add(makeDate("2024-01-03 00:00"))
        releaseDates.add(makeDate("2024-01-03 12:00"))
        releaseDates.add(makeDate("2024-01-08 00:00"))
        releaseDates.add(makeDate("2024-01-08 12:00"))
        releaseDates.add(makeDate("2024-01-10 00:00"))
        releaseDates.add(makeDate("2024-01-10 12:00"))
        val guess = ReleaseScheduleGuesser.performGuess(releaseDates)
        assertEquals(ReleaseScheduleGuesser.Schedule.SPECIFIC_DAYS, guess.schedule)
        assertTrue(guess.multipleReleasesPerDay)
        assertClose(makeDate("2024-01-15 12:00"), guess.nextExpectedDate, ReleaseScheduleGuesser.ONE_DAY)
    }

    @Test
    fun testDaily() {
        val releaseDates = ArrayList<Date>()
        releaseDates.add(makeDate("2024-01-01 16:30")) // Monday
        releaseDates.add(makeDate("2024-01-02 16:25"))
        releaseDates.add(makeDate("2024-01-03 16:35"))
        releaseDates.add(makeDate("2024-01-04 16:40"))
        releaseDates.add(makeDate("2024-01-05 16:20"))
        releaseDates.add(makeDate("2024-01-06 16:10"))
        releaseDates.add(makeDate("2024-01-07 16:32")) // Sunday

        // Next day
        var guess = ReleaseScheduleGuesser.performGuess(releaseDates)
        assertEquals(ReleaseScheduleGuesser.Schedule.DAILY, guess.schedule)
        assertClose(makeDate("2024-01-08 16:30"), guess.nextExpectedDate, 10 * ReleaseScheduleGuesser.ONE_MINUTE)

        // One-off early release
        releaseDates.add(makeDate("2024-01-08 10:00"))
        guess = ReleaseScheduleGuesser.performGuess(releaseDates)
        assertEquals(ReleaseScheduleGuesser.Schedule.DAILY, guess.schedule)
        assertClose(makeDate("2024-01-09 16:30"), guess.nextExpectedDate, 10 * ReleaseScheduleGuesser.ONE_MINUTE)
    }

    @Test
    fun testWeekdays() {
        val releaseDates = ArrayList<Date>()
        releaseDates.add(makeDate("2024-01-01 16:30")) // Monday
        releaseDates.add(makeDate("2024-01-02 16:25"))
        releaseDates.add(makeDate("2024-01-03 16:35"))
        releaseDates.add(makeDate("2024-01-04 16:40"))
        releaseDates.add(makeDate("2024-01-05 16:20")) // Friday
        releaseDates.add(makeDate("2024-01-08 16:20")) // Monday
        releaseDates.add(makeDate("2024-01-09 16:30"))
        releaseDates.add(makeDate("2024-01-10 16:40"))
        releaseDates.add(makeDate("2024-01-11 16:45")) // Thursday

        // Next day
        var guess = ReleaseScheduleGuesser.performGuess(releaseDates)
        assertEquals(ReleaseScheduleGuesser.Schedule.WEEKDAYS, guess.schedule)
        assertClose(makeDate("2024-01-12 16:30"), guess.nextExpectedDate, ReleaseScheduleGuesser.ONE_HOUR)

        // After weekend
        releaseDates.add(makeDate("2024-01-12 16:30")) // Friday
        guess = ReleaseScheduleGuesser.performGuess(releaseDates)
        assertClose(makeDate("2024-01-15 16:30"), guess.nextExpectedDate, ReleaseScheduleGuesser.ONE_HOUR)
    }

    @Test
    fun testWeekly() {
        val releaseDates = ArrayList<Date>()
        releaseDates.add(makeDate("2024-01-07 16:30")) // Sunday
        releaseDates.add(makeDate("2024-01-14 16:25"))
        releaseDates.add(makeDate("2024-01-21 14:25"))
        releaseDates.add(makeDate("2024-01-28 16:15"))

        // Next week
        var guess = ReleaseScheduleGuesser.performGuess(releaseDates)
        assertEquals(ReleaseScheduleGuesser.Schedule.WEEKLY, guess.schedule)
        assertClose(makeDate("2024-02-04 16:30"), guess.nextExpectedDate, 2 * ReleaseScheduleGuesser.ONE_HOUR)

        // One-off early release
        releaseDates.add(makeDate("2024-02-02 16:35"))
        guess = ReleaseScheduleGuesser.performGuess(releaseDates)
        assertEquals(ReleaseScheduleGuesser.Schedule.WEEKLY, guess.schedule)
        assertClose(makeDate("2024-02-11 16:30"), guess.nextExpectedDate, 2 * ReleaseScheduleGuesser.ONE_HOUR)

        // One-off late release
        releaseDates.add(makeDate("2024-02-13 16:35"))
        guess = ReleaseScheduleGuesser.performGuess(releaseDates)
        assertEquals(ReleaseScheduleGuesser.Schedule.WEEKLY, guess.schedule)
        assertClose(makeDate("2024-02-18 16:30"), guess.nextExpectedDate, 2 * ReleaseScheduleGuesser.ONE_HOUR)
    }

    @Test
    fun testMonthly() {
        val releaseDates = ArrayList<Date>()
        releaseDates.add(makeDate("2024-01-01 16:30"))
        releaseDates.add(makeDate("2024-02-01 16:30"))
        releaseDates.add(makeDate("2024-03-01 16:30"))
        releaseDates.add(makeDate("2024-04-01 16:30"))

        // Next month
        var guess = ReleaseScheduleGuesser.performGuess(releaseDates)
        assertEquals(ReleaseScheduleGuesser.Schedule.MONTHLY, guess.schedule)
        assertClose(makeDate("2024-05-01 16:30"), guess.nextExpectedDate, 10 * ReleaseScheduleGuesser.ONE_HOUR)

        // One-off early release
        releaseDates.add(makeDate("2024-04-30 16:30"))
        guess = ReleaseScheduleGuesser.performGuess(releaseDates)
        assertEquals(ReleaseScheduleGuesser.Schedule.MONTHLY, guess.schedule)
        assertClose(makeDate("2024-06-01 16:30"), guess.nextExpectedDate, 10 * ReleaseScheduleGuesser.ONE_HOUR)

        // One-off late release
        releaseDates.removeAt(releaseDates.size - 1)
        releaseDates.add(makeDate("2024-05-13 16:30"))
        guess = ReleaseScheduleGuesser.performGuess(releaseDates)
        assertEquals(ReleaseScheduleGuesser.Schedule.MONTHLY, guess.schedule)
        assertClose(makeDate("2024-06-01 16:30"), guess.nextExpectedDate, 10 * ReleaseScheduleGuesser.ONE_HOUR)
    }

    @Test
    fun testFourweekly() {
        val releaseDates = ArrayList<Date>()
        releaseDates.add(makeDate("2024-01-01 16:30"))
        releaseDates.add(makeDate("2024-01-29 16:30"))
        releaseDates.add(makeDate("2024-02-26 16:30"))
        releaseDates.add(makeDate("2024-03-25 16:30"))

        // 4 weeks later
        val guess = ReleaseScheduleGuesser.performGuess(releaseDates)
        assertEquals(ReleaseScheduleGuesser.Schedule.FOURWEEKLY, guess.schedule)
        assertClose(makeDate("2024-04-22 16:30"), guess.nextExpectedDate, 10 * ReleaseScheduleGuesser.ONE_HOUR)
    }

    @Test
    fun testUnknown() {
        val releaseDates = ArrayList<Date>()
        releaseDates.add(makeDate("2024-01-01 16:30"))
        releaseDates.add(makeDate("2024-01-04 16:30"))
        releaseDates.add(makeDate("2024-01-10 16:31"))
        releaseDates.add(makeDate("2024-01-11 16:30"))
        releaseDates.add(makeDate("2024-01-20 16:31"))
        releaseDates.add(makeDate("2024-01-22 16:30"))
        releaseDates.add(makeDate("2024-01-25 16:31"))
        releaseDates.add(makeDate("2024-01-25 16:30"))
        val guess = ReleaseScheduleGuesser.performGuess(releaseDates)
        assertEquals(ReleaseScheduleGuesser.Schedule.UNKNOWN, guess.schedule)
        assertClose(makeDate("2024-01-27 16:30"), guess.nextExpectedDate, 2 * ReleaseScheduleGuesser.ONE_DAY)
    }
}
