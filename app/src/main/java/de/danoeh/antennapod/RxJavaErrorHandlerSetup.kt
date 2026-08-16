package de.danoeh.antennapod

import android.util.Log

import de.danoeh.antennapod.system.CrashReportWriter
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.plugins.RxJavaPlugins

class RxJavaErrorHandlerSetup private constructor() {
    companion object {
        private const val TAG = "RxJavaErrorHandler"

        @JvmStatic
        fun setupRxJavaErrorHandler() {
            RxJavaPlugins.setErrorHandler { exception ->
                if (exception is UndeliverableException) {
                    // Probably just disposed because the fragment was left
                    Log.d(TAG, "Ignored exception: " + Log.getStackTraceString(exception))
                    return@setErrorHandler
                }

                // Usually, undeliverable exceptions are wrapped in an UndeliverableException.
                // If an undeliverable exception is a NPE (or some others), wrapping does not happen.
                // AntennaPod threads might throw NPEs after disposing because we set controllers to null.
                // Just swallow all exceptions here.
                Log.e(TAG, Log.getStackTraceString(exception))
                CrashReportWriter.write(exception)

                if (BuildConfig.DEBUG) {
                    Thread.currentThread().getUncaughtExceptionHandler()!!
                            .uncaughtException(Thread.currentThread(), exception)
                }
            }
        }
    }
}
