package de.danoeh.antennapod

import de.danoeh.antennapod.system.CrashReportWriter

class CrashReportExceptionHandler : Thread.UncaughtExceptionHandler {

    private val defaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler

    init {
        defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()!!
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        CrashReportWriter.write(throwable)
        defaultUncaughtExceptionHandler.uncaughtException(thread, throwable)
    }
}
