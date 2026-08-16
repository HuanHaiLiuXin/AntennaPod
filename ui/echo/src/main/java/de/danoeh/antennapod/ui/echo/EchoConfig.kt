package de.danoeh.antennapod.ui.echo

import java.util.Calendar

class EchoConfig {
    companion object {
        const val RELEASE_YEAR = 2026

        @JvmStatic
        fun jan1(): Long {
            val date = Calendar.getInstance()
            date.set(Calendar.HOUR_OF_DAY, 0)
            date.set(Calendar.MINUTE, 0)
            date.set(Calendar.SECOND, 0)
            date.set(Calendar.MILLISECOND, 0)
            date.set(Calendar.DAY_OF_MONTH, 1)
            date.set(Calendar.MONTH, 0)
            date.set(Calendar.YEAR, RELEASE_YEAR)
            return date.getTimeInMillis()
        }

        @JvmStatic
        fun isCurrentlyVisible(): Boolean {
            return Calendar.getInstance().get(Calendar.YEAR) == RELEASE_YEAR
                    && Calendar.getInstance().get(Calendar.MONTH) == Calendar.DECEMBER
                    && Calendar.getInstance().get(Calendar.DAY_OF_MONTH) >= 10
        }
    }
}
