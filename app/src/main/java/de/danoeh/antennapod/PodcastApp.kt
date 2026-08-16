package de.danoeh.antennapod

import android.app.Application
import android.util.Log

import com.google.android.material.color.DynamicColors

import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.EventBusException

/** Main application class. */
class PodcastApp : Application() {
    companion object {
        private const val TAG = "PodcastApp"
    }

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(CrashReportExceptionHandler())
        RxJavaErrorHandlerSetup.setupRxJavaErrorHandler()

        try {
            // Robolectric calls onCreate for every test, which causes problems with static members
            EventBus.builder()
                    .logNoSubscriberMessages(false)
                    .sendNoSubscriberEvent(false)
                    .installDefaultEventBus()
        } catch (e: EventBusException) {
            Log.d(TAG, e.message!!)
        }

        DynamicColors.applyToActivitiesIfAvailable(this)
        ClientConfigurator.initialize(this)
        PreferenceUpgrader.checkUpgrades(this)
    }
}
