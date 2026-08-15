package de.danoeh.antennapod.storage.database

import de.danoeh.antennapod.parser.feed.util.DateUtils
import org.apache.commons.io.IOUtils
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Collections
import java.util.Comparator
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale

class ReleaseScheduleGuesserRealWorldTest {
    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
    }

    private fun printHistogram(histogram: IntArray) {
        var max = 0
        for (x in histogram) {
            max = Math.max(x, max)
        }
        for (row in 8 downTo 0) {
            for (x in histogram) {
                System.out.print(if (x > row.toDouble() * (max / 9.0)) "#" else " ")
            }
            System.out.println()
        }
        for (col in histogram.indices) {
            System.out.print(if (col % 5 == 0) "|" else " ")
        }
        System.out.println()
    }

    @Test
    @Throws(Exception::class)
    fun testRealWorld() {
        val inputStream = javaClass.classLoader!!.getResource("release_dates.csv")!!.openStream()
        var numCorrectDay = 0
        var numFoundSchedule = 0
        var numFoundScheduleAndCorrectDay = 0
        var num3hoursCorrect = 0
        var numOffByMoreThan2days = 0
        val histogram = IntArray(101)

        val csv = IOUtils.toString(inputStream, "UTF-8")
        val lines = csv.split("\n")
        var totalPodcasts = 0
        var lineNr = 0
        for (line in lines) {
            lineNr++
            val dates = line.split(";")
            var releaseDates: MutableList<Date> = ArrayList()
            for (date in dates) {
                releaseDates.add(DateUtils.parse(date)!!)
            }
            Collections.sort(releaseDates, Comparator.comparingLong { it.getTime() })
            val dateActual = releaseDates[releaseDates.size - 1]
            // Remove most recent one and possible duplicates of episodes on the same day
            do {
                releaseDates = releaseDates.subList(0, releaseDates.size - 1)
            } while (releaseDates[releaseDates.size - 1].getTime()
                    > dateActual.getTime() - 30 * ReleaseScheduleGuesser.ONE_MINUTE)
            if (releaseDates.size <= 3) {
                continue
            }
            totalPodcasts++
            val guess = ReleaseScheduleGuesser.performGuess(releaseDates)
            assertNotNull(guess.nextExpectedDate)

            val is3hoursClose = Math.abs(dateActual.getTime() - guess.nextExpectedDate!!.getTime()) <
                    3 * ReleaseScheduleGuesser.ONE_HOUR
            //noinspection ConstantValue
            if (false) {
                System.out.println(lineNr.toString() + " guessed: " + DATE_FORMAT.format(guess.nextExpectedDate!!) +
                        ", actual: " + DATE_FORMAT.format(dateActual) +
                        " " + guess.schedule.name + if (is3hoursClose) " ✔" else "")
            }
            val deltaTime = dateActual.getTime() - guess.nextExpectedDate!!.getTime()
            val histogramClass = Math.max(0, Math.min(100L, deltaTime / ReleaseScheduleGuesser.ONE_HOUR + 50)).toInt()
            histogram[histogramClass]++
            val foundSchedule = guess.schedule != ReleaseScheduleGuesser.Schedule.UNKNOWN
            if (foundSchedule) {
                numFoundSchedule++
            }
            val calendarExpected = GregorianCalendar()
            calendarExpected.setTime(dateActual)
            val calendarGuessed = GregorianCalendar()
            calendarGuessed.setTime(guess.nextExpectedDate)
            if (calendarExpected.get(Calendar.DAY_OF_YEAR) == calendarGuessed.get(Calendar.DAY_OF_YEAR)) {
                numCorrectDay++
                if (foundSchedule) {
                    numFoundScheduleAndCorrectDay++
                }
            }
            if (Math.abs(deltaTime) > 2 * ReleaseScheduleGuesser.ONE_DAY) {
                numOffByMoreThan2days++
            }
            if (is3hoursClose) {
                num3hoursCorrect++
            }
        }

        System.out.println("Podcasts tested: " + totalPodcasts)

        val schedulePercentage = 100.0 * numFoundSchedule / totalPodcasts
        System.out.println("Found schedule: " + schedulePercentage)
        val offByLessThan3HoursPercentage = 100.0 * num3hoursCorrect / totalPodcasts
        System.out.println("Off by less than 3 hours: " + offByLessThan3HoursPercentage)
        val scheduleAndCorrectDayPercentage = 100.0 * numFoundScheduleAndCorrectDay / numFoundSchedule
        System.out.println("Correct day when schedule found: " + scheduleAndCorrectDayPercentage)
        val correctDayPercentage = 100.0 * numCorrectDay / totalPodcasts
        System.out.println("Correct day: " + correctDayPercentage)
        val offByLessThan2daysPercentage = 100.0 * (totalPodcasts - numOffByMoreThan2days) / totalPodcasts
        System.out.println("Off by less than 2 days: " + offByLessThan2daysPercentage)

        assertTrue(schedulePercentage > 80)
        assertTrue(offByLessThan3HoursPercentage > 55)
        assertTrue(scheduleAndCorrectDayPercentage > 70)
        assertTrue(correctDayPercentage > 60)
        assertTrue(offByLessThan2daysPercentage > 70)

        printHistogram(histogram)
    }
}
