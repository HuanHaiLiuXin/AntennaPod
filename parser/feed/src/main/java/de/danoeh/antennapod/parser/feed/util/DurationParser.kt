package de.danoeh.antennapod.parser.feed.util

import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS

class DurationParser {
    companion object {
        @JvmStatic
        fun inMillis(durationStr: String): Long {
            val parts = durationStr.trim().split(":")

            if (parts.size == 1) {
                return toMillis(parts[0])
            } else if (parts.size == 2) {
                return toMillis("0", parts[0], parts[1])
            } else if (parts.size == 3) {
                return toMillis(parts[0], parts[1], parts[2])
            } else {
                throw NumberFormatException()
            }
        }

        private fun toMillis(hours: String, minutes: String, seconds: String): Long {
            return HOURS.toMillis(java.lang.Long.parseLong(hours))
                    + MINUTES.toMillis(java.lang.Long.parseLong(minutes))
                    + toMillis(seconds)
        }

        private fun toMillis(seconds: String): Long {
            if (seconds.contains(".")) {
                val value = java.lang.Float.parseFloat(seconds)
                val millis = value % 1
                return SECONDS.toMillis(value.toLong()) + (millis * 1000).toLong()
            } else {
                return SECONDS.toMillis(java.lang.Long.parseLong(seconds))
            }
        }
    }
}
