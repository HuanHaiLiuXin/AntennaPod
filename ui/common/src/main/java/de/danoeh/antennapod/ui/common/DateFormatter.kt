package de.danoeh.antennapod.ui.common

import android.content.Context

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar

/**
 * Formats dates.
 */
class DateFormatter {
    companion object {
        @JvmStatic
        fun formatAbbrev(context: Context, date: Date?): String {
            if (date == null) {
                return ""
            }
            val now = GregorianCalendar()
            val cal = GregorianCalendar()
            cal.setTime(date)
            val withinLastYear = now.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
            var format = android.text.format.DateUtils.FORMAT_ABBREV_ALL
            if (withinLastYear) {
                format = format or android.text.format.DateUtils.FORMAT_NO_YEAR
            }
            return android.text.format.DateUtils.formatDateTime(context, date.getTime(), format)
        }

        @JvmStatic
        fun formatForAccessibility(date: Date?): String {
            if (date == null) {
                return ""
            }
            return DateFormat.getDateInstance(DateFormat.LONG).format(date)
        }
    }
}
