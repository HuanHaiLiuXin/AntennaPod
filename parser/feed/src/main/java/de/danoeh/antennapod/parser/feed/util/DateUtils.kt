package de.danoeh.antennapod.parser.feed.util

import org.apache.commons.lang3.StringUtils

import java.text.ParseException
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Parses several date formats.
 */
abstract class DateUtils private constructor() {
    companion object {
        private val TIME_ZONE_GMT = TimeZone.getTimeZone("GMT")
        private val RFC822_DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
                dateFormat.timeZone = TIME_ZONE_GMT
                return dateFormat
            }
        }

        @JvmStatic
        fun parse(input: String?): Date? {
            if (input == null) {
                throw IllegalArgumentException("Date must not be null")
            }
            try {
                return RFC822_DATE_FORMAT.get().parse(input)
            } catch (ignored: ParseException) {
                // Feed not following the specification? Now start all our expensive workarounds.
            }
            var date = input.trim().replace('/', '-').replace(Regex("( ){2,}+"), " ")

            // remove colon from timezone to avoid differences between Android and Java SimpleDateFormat
            date = date.replace(Regex("([+-]\\d\\d):(\\d\\d)$"), "$1$2")

            // CEST is widely used but not in the "ISO 8601 Time zone" list. Let's hack around.
            date = date.replace(Regex("CEST$"), "+0200")
            date = date.replace(Regex("CET$"), "+0100")

            // some generators use "Sept" for September
            date = date.replace(Regex("\\bSept\\b"), "Sep")

            // if datetime is more precise than seconds, make sure the value is in ms
            if (date.contains(".")) {
                val start = date.indexOf('.')
                var current = start + 1
                while (current < date.length && Character.isDigit(date[current])) {
                    current++
                }
                // even more precise than microseconds: discard further decimal places
                if (current - start > 4) {
                    if (current < date.length - 1) {
                        date = date.substring(0, start + 4) + date.substring(current)
                    } else {
                        date = date.substring(0, start + 4)
                    }
                    // less than 4 decimal places: pad to have a consistent format for the parser
                } else if (current - start < 4) {
                    if (current < date.length - 1) {
                        date = date.substring(0, current) + StringUtils.repeat("0", 4 - (current - start)) +
                                date.substring(current)
                    } else {
                        date = date.substring(0, current) + StringUtils.repeat("0", 4 - (current - start))
                    }
                }
            }
            val patterns = arrayOf(
                    "dd MMM yy HH:mm:ss Z",
                    "dd MMM yy HH:mm Z",
                    "EEE, dd MMM yyyy HH:mm:ss Z",
                    "EEE, dd MMM yyyy HH:mm:ss",
                    "EEE, dd MMMM yyyy HH:mm:ss Z",
                    "EEE, dd MMMM yyyy HH:mm:ss",
                    "EEEE, dd MMM yyyy HH:mm:ss Z",
                    "EEEE, dd MMM yy HH:mm:ss Z",
                    "EEEE, dd MMM yyyy HH:mm:ss",
                    "EEEE, dd MMM yy HH:mm:ss",
                    "EEE MMM d HH:mm:ss yyyy",
                    "EEE, dd MMM yyyy HH:mm Z",
                    "EEE, dd MMM yyyy HH:mm",
                    "EEE, dd MMMM yyyy HH:mm Z",
                    "EEE, dd MMMM yyyy HH:mm",
                    "EEEE, dd MMM yyyy HH:mm Z",
                    "EEEE, dd MMM yy HH:mm Z",
                    "EEEE, dd MMM yyyy HH:mm",
                    "EEEE, dd MMM yy HH:mm",
                    "EEE MMM d HH:mm yyyy",
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS Z",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS",
                    "yyyy-MM-dd'T'HH:mm:ssZ",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                    "yyyy-MM-ddZ",
                    "yyyy-MM-dd",
                    "EEE d MMM yyyy HH:mm:ss 'GMT'Z (z)"
            )

            val parser = SimpleDateFormat("", Locale.US)
            parser.isLenient = false
            parser.timeZone = TIME_ZONE_GMT

            val pos = ParsePosition(0)
            for (pattern in patterns) {
                parser.applyPattern(pattern)
                pos.index = 0
                try {
                    val result = parser.parse(date, pos)
                    if (result != null && pos.index == date.length) {
                        return result
                    }
                } catch (ignored: Exception) {
                    // Ignore
                }
            }

            // if date string starts with a weekday, try parsing date string without it
            if (date.matches(Regex("^\\w+, .*$"))) {
                return parse(date.substring(date.indexOf(',') + 1))
            }

            System.out.println("Could not parse date string \"" + input + "\" [" + date + "]")
            return null
        }

        /**
         * Parses the date but if the date is in the future, returns null.
         */
        @JvmStatic
        fun parseOrNullIfFuture(input: String?): Date? {
            val date = parse(input)
            if (date == null) {
                return null
            }
            val now = Date()
            if (date.after(now)) {
                return null
            }
            return date
        }

        /**
         * Takes a string of the form [HH:]MM:SS[.mmm] and converts it to
         * milliseconds.
         *
         * @throws java.lang.NumberFormatException if the number segments contain invalid numbers.
         */
        @JvmStatic
        fun parseTimeString(time: String?): Long {
            val parts = time!!.split(":")
            var result = 0L
            var idx = 0
            if (parts.size == 3) {
                // string has hours
                result += Integer.parseInt(parts[idx]) * 3600000L
                idx++
            }
            if (parts.size >= 2) {
                result += Integer.parseInt(parts[idx]) * 60000L
                idx++
                result += (java.lang.Float.parseFloat(parts[idx]) * 1000L).toLong()
            }
            return result
        }
    }
}
