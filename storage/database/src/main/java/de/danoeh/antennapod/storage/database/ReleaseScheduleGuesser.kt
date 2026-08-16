package de.danoeh.antennapod.storage.database

import java.util.ArrayList
import java.util.Arrays
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.GregorianCalendar
import java.util.HashSet

/**
 * Can be used to guess the release schedule of podcasts based on a sorted list of past release dates
 */
class ReleaseScheduleGuesser {
    companion object {
        internal const val ONE_MINUTE = 60 * 1000L
        internal const val ONE_HOUR = ONE_MINUTE * 60
        internal const val ONE_DAY = ONE_HOUR * 24
        internal const val ONE_WEEK = ONE_DAY * 7
        internal const val ONE_MONTH = ONE_DAY * 30
        private const val MAX_UNIQUE_DATES = 20
        private const val MULTIPLE_PER_DAY_THRESHOLD = 2

        private fun addTime(date: GregorianCalendar, time: Long) {
            date.setTime(Date(date.getTime().getTime() + time))
        }

        private fun addTimeUntilOnAllowedDay(date: GregorianCalendar, amount: Long, allowedDays: List<Int>) {
            do {
                addTime(date, amount)
            } while (!allowedDays.contains(date.get(Calendar.DAY_OF_WEEK)))
        }

        private fun <T> getMedian(list: List<T>): T {
            return list[list.size / 2]
        }

        private fun getStats(releaseDates: List<Date>): Stats {
            val hours = ArrayList<Float>()
            val distances = ArrayList<Long>()
            val daysOfWeek = IntArray(8)
            val daysOfMonth = IntArray(32)
            for (i in releaseDates.indices) {
                val d = releaseDates[i]
                val calendar = GregorianCalendar()
                calendar.setTime(d)
                hours.add(calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60f)
                if (i > 0) {
                    distances.add(d.getTime() - releaseDates[i - 1].getTime())
                }
                daysOfWeek[calendar.get(Calendar.DAY_OF_WEEK)]++
                daysOfMonth[calendar.get(Calendar.DAY_OF_MONTH)]++
            }

            var mostOftenDayOfWeek = 1
            var mostOftenDayOfWeekNum = 0
            for (i in Calendar.SUNDAY..Calendar.SATURDAY) {
                if (daysOfWeek[i] > mostOftenDayOfWeekNum) {
                    mostOftenDayOfWeekNum = daysOfWeek[i]
                    mostOftenDayOfWeek = i
                }
            }

            var mostOftenDayOfMonth = 1
            var mostOftenDayOfMonthNum = 0
            for (i in 1 until 31) {
                if (daysOfMonth[i] > mostOftenDayOfMonthNum) {
                    mostOftenDayOfMonthNum = daysOfMonth[i]
                    mostOftenDayOfMonth = i
                }
            }

            Collections.sort(hours) { a, b -> java.lang.Float.compare(a, b) }
            val medianHour = getMedian(hours)
            Collections.sort(distances) { a, b -> java.lang.Long.compare(a, b) }
            val medianDistance = getMedian(distances)

            var avgDeltaToMedianDistance = 0f
            for (distance in distances) {
                avgDeltaToMedianDistance += Math.abs(distance - medianDistance).toFloat()
            }
            avgDeltaToMedianDistance /= distances.size

            return Stats(medianHour, medianDistance.toFloat(), avgDeltaToMedianDistance,
                    daysOfWeek, daysOfMonth, mostOftenDayOfWeek, mostOftenDayOfMonth)
        }

        private fun getLargeDays(stats: Stats, maxDaysOff: Int): List<Int> {
            val largeDays = ArrayList<Int>()
            for (i in Calendar.SUNDAY..Calendar.SATURDAY) {
                if (stats.daysOfWeek[i] > maxDaysOff) {
                    largeDays.add(i)
                }
            }
            return largeDays
        }

        private fun getNormalizedDate(date: Date): Date {
            val current = GregorianCalendar()
            current.setTime(date)
            current.set(Calendar.HOUR_OF_DAY, 0)
            current.set(Calendar.MINUTE, 0)
            current.set(Calendar.SECOND, 0)
            current.set(Calendar.MILLISECOND, 0)
            return current.getTime()
        }

        @JvmStatic
        fun performGuess(releaseDates: List<Date>): Guess {
            Collections.sort(releaseDates)
            val uniqueDates = HashSet<Date>()
            var releaseDatesLowerIndex = releaseDates.size
            while (releaseDatesLowerIndex > 0 && uniqueDates.size < MAX_UNIQUE_DATES) {
                val normalizedDate = getNormalizedDate(releaseDates[--releaseDatesLowerIndex])
                uniqueDates.add(normalizedDate)
            }

            var truncatedDates = releaseDates
            if (releaseDates.size <= 1) {
                return Guess(Schedule.UNKNOWN, null, null, false)
            } else if (releaseDates.size > MAX_UNIQUE_DATES) {
                truncatedDates = releaseDates.subList(releaseDatesLowerIndex, releaseDates.size)
            }

            val stats = getStats(truncatedDates)
            val maxTotalWrongDays = Math.max(1, truncatedDates.size / 5)
            val maxSingleDayOff = truncatedDates.size / 10

            val last = GregorianCalendar()
            last.setTime(truncatedDates[truncatedDates.size - 1])
            last.set(Calendar.HOUR_OF_DAY, stats.medianHour.toInt())
            last.set(Calendar.MINUTE, ((stats.medianHour - Math.floor(stats.medianHour.toDouble())) * 60).toInt())
            last.set(Calendar.SECOND, 0)
            last.set(Calendar.MILLISECOND, 0)

            val multipleReleasesPerDay = (truncatedDates.size - uniqueDates.size) >= MULTIPLE_PER_DAY_THRESHOLD
            if (multipleReleasesPerDay) {
                val averagePerDayAmount = truncatedDates.size.toFloat() / uniqueDates.size
                val date = getNormalizedDate(last.getTime())
                var releasesToday = 0
                for (releaseDate in truncatedDates) {
                    if (date == getNormalizedDate(releaseDate)) {
                        releasesToday++
                    }
                }

                val distance: Long
                if (releasesToday <= averagePerDayAmount) {
                    distance = stats.medianDistance.toLong()
                } else {
                    distance = ONE_DAY
                }

                val largeDays = getLargeDays(stats, 0)
                addTimeUntilOnAllowedDay(last, distance, largeDays)
                var schedule = Schedule.SPECIFIC_DAYS
                if (largeDays.size == 7) {
                    schedule = Schedule.DAILY
                } else if (largeDays.size == 5 && largeDays.containsAll(Arrays.asList(
                        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY))) {
                    schedule = Schedule.WEEKDAYS
                }

                return Guess(schedule, largeDays, last.getTime(), true)
            } else if (Math.abs(stats.medianDistance - ONE_DAY) < 2 * ONE_HOUR
                    && stats.avgDeltaToMedianDistance < 2 * ONE_HOUR) {
                addTime(last, ONE_DAY)
                return Guess(Schedule.DAILY, Arrays.asList(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY), last.getTime(),
                        false)
            } else if (Math.abs(stats.medianDistance - ONE_WEEK) < ONE_DAY
                    && stats.avgDeltaToMedianDistance < 2 * ONE_DAY) {
                // Just using last.set(Calendar.DAY_OF_WEEK) could skip a week
                // when the last release is delayed over week boundaries
                addTime(last, 3 * ONE_DAY)
                do {
                    addTime(last, ONE_DAY)
                } while (last.get(Calendar.DAY_OF_WEEK) != stats.mostOftenDayOfWeek)
                return Guess(Schedule.WEEKLY, listOf(stats.mostOftenDayOfWeek), last.getTime(), false)
            } else if (Math.abs(stats.medianDistance - 2 * ONE_WEEK) < ONE_DAY
                    && stats.avgDeltaToMedianDistance < 2 * ONE_DAY) {
                // Just using last.set(Calendar.DAY_OF_WEEK) could skip a week
                // when the last release is delayed over week boundaries
                addTime(last, 10 * ONE_DAY)
                do {
                    addTime(last, ONE_DAY)
                } while (last.get(Calendar.DAY_OF_WEEK) != stats.mostOftenDayOfWeek)
                return Guess(Schedule.BIWEEKLY, listOf(stats.mostOftenDayOfWeek), last.getTime(), false)
            } else if (Math.abs(stats.medianDistance - ONE_MONTH) < 5 * ONE_DAY
                    && stats.avgDeltaToMedianDistance < 5 * ONE_DAY) {
                if (stats.daysOfMonth[stats.mostOftenDayOfMonth] >= truncatedDates.size - maxTotalWrongDays) {
                    // Just using last.set(Calendar.DAY_OF_MONTH) could skip a week
                    // when the last release is delayed over week boundaries
                    addTime(last, 2 * ONE_WEEK)
                    do {
                        addTime(last, ONE_DAY)
                    } while (last.get(Calendar.DAY_OF_MONTH) != stats.mostOftenDayOfMonth)
                    return Guess(Schedule.MONTHLY, null, last.getTime(), false)
                }

                addTime(last, 3 * ONE_WEEK + 3 * ONE_DAY)
                do {
                    addTime(last, ONE_DAY)
                } while (last.get(Calendar.DAY_OF_WEEK) != stats.mostOftenDayOfWeek)
                return Guess(Schedule.FOURWEEKLY, listOf(stats.mostOftenDayOfWeek), last.getTime(), false)
            }

            // Find release days
            val largeDays = getLargeDays(stats, maxSingleDayOff)

            // Ensure that all release days are used similarly often
            val averageDays = truncatedDates.size / largeDays.size
            var matchesAverageDays = true
            for (day in largeDays) {
                if (stats.daysOfWeek[day] < averageDays - maxSingleDayOff) {
                    matchesAverageDays = false
                    break
                }
            }

            if (matchesAverageDays && stats.medianDistance < ONE_WEEK) {
                // Fixed daily release schedule (eg Mo, Thu, Fri)
                addTimeUntilOnAllowedDay(last, ONE_DAY, largeDays)

                if (largeDays.size == 5 && largeDays.containsAll(Arrays.asList(
                        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY))) {
                    return Guess(Schedule.WEEKDAYS, largeDays, last.getTime(), false)
                }
                return Guess(Schedule.SPECIFIC_DAYS, largeDays, last.getTime(), false)
            } else if (largeDays.size == 1) {
                // Probably still weekly with more exceptions than others
                addTimeUntilOnAllowedDay(last, ONE_DAY, largeDays)
                return Guess(Schedule.WEEKLY, largeDays, last.getTime(), false)
            }
            addTime(last, (0.6f * stats.medianDistance).toLong())
            return Guess(Schedule.UNKNOWN, null, last.getTime(), false)
        }
    }

    enum class Schedule {
        DAILY, WEEKDAYS, SPECIFIC_DAYS,
        WEEKLY, BIWEEKLY, FOURWEEKLY,
        MONTHLY, UNKNOWN
    }

    class Guess {
        @JvmField
        val schedule: Schedule
        @JvmField
        val days: List<Int>?
        @JvmField
        val nextExpectedDate: Date?
        @JvmField
        val multipleReleasesPerDay: Boolean

        constructor(schedule: Schedule, days: List<Int>?, nextExpectedDate: Date?, multipleReleasesPerDay: Boolean) {
            this.schedule = schedule
            this.days = days
            this.nextExpectedDate = nextExpectedDate
            this.multipleReleasesPerDay = multipleReleasesPerDay
        }
    }

    private class Stats {
        val medianHour: Float
        val medianDistance: Float
        val avgDeltaToMedianDistance: Float
        val daysOfWeek: IntArray
        val daysOfMonth: IntArray
        val mostOftenDayOfWeek: Int
        val mostOftenDayOfMonth: Int

        constructor(medianHour: Float, medianDistance: Float, avgDeltaToMedianDistance: Float,
                    daysOfWeek: IntArray, daysOfMonth: IntArray, mostOftenDayOfWeek: Int, mostOftenDayOfMonth: Int) {
            this.medianHour = medianHour
            this.medianDistance = medianDistance
            this.avgDeltaToMedianDistance = avgDeltaToMedianDistance
            this.daysOfWeek = daysOfWeek
            this.daysOfMonth = daysOfMonth
            this.mostOftenDayOfWeek = mostOftenDayOfWeek
            this.mostOftenDayOfMonth = mostOftenDayOfMonth
        }
    }
}
