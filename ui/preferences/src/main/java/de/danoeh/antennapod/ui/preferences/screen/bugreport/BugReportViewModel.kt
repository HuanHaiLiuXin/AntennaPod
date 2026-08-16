package de.danoeh.antennapod.ui.preferences.screen.bugreport

import android.app.Application
import android.os.Build
import android.text.format.DateUtils

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Objects

import de.danoeh.antennapod.system.CrashReportWriter
import de.danoeh.antennapod.system.utils.PackageUtils
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * Viewmodel encapsulating all data and business logic required
 * to present the report bug UI.
 */
class BugReportViewModel(application: Application) : AndroidViewModel(application) {
    /**
     * Device runtime environment information
     */
    class EnvironmentInfo internal constructor(application: Application) {
        internal val applicationVersion: String?
        internal val androidVersion: String?
        internal val androidOsVersion: String?
        internal val deviceManufacturer: String?
        internal val deviceModel: String?
        internal val deviceName: String?
        internal val productName: String?

        init {
            this.applicationVersion = PackageUtils.getApplicationVersion(application)
            this.androidVersion = Build.VERSION.RELEASE
            this.androidOsVersion = System.getProperty("os.version")
            this.deviceManufacturer = Build.MANUFACTURER
            this.deviceModel = Build.MODEL
            this.deviceName = Build.DEVICE
            this.productName = Build.PRODUCT
        }

        fun getFriendlyDeviceName(): String {
            if (Build.MODEL.lowercase(Locale.getDefault()).startsWith(Build.MANUFACTURER
                            .lowercase(Locale.getDefault()))) {
                return Build.MODEL
            }
            return Build.MANUFACTURER + " " + Build.MODEL
        }
    }

    /**
     * Contents of the latest crash log / stacktrace file
     */
    class CrashLogInfo internal constructor() {
        internal val timestamp: Date?
        internal val content: String

        init {
            this.timestamp = CrashReportWriter.getTimestamp()
            this.content = CrashReportWriter.read()
        }

        fun getTimestamp(): Date? {
            return timestamp
        }

        fun getContent(): String {
            return content
        }

        fun isAvailable(): Boolean {
            return timestamp != null && !content.isEmpty()
        }
    }

    /**
     * Full UI state required by the report bug presentation layer
     */
    class UiState internal constructor(application: Application) {
        enum class CrashLogState {
            UNAVAILABLE,
            SHOWN_COLLAPSED,
            SHOWN_EXPANDED
        }

        private val environmentInfo: EnvironmentInfo
        private val crashLogInfo: CrashLogInfo
        private var crashLogState: CrashLogState

        private val formattedEnvironmentInfo: String
        private var formattedCrashLogTimestamp: String? = null
        private var formattedCrashLog: String? = null

        init {
            this.environmentInfo = EnvironmentInfo(application)
            this.crashLogInfo = CrashLogInfo()

            this.formattedEnvironmentInfo = "## Environment" +
                    "\nAndroid version: " + environmentInfo.androidVersion +
                    "\nOS version: " + environmentInfo.androidOsVersion +
                    "\nAntennaPod version: " + environmentInfo.applicationVersion +
                    "\nModel: " + environmentInfo.deviceModel +
                    "\nDevice: " + environmentInfo.deviceName +
                    "\nProduct: " + environmentInfo.productName +
                    "\nManufacturer: " + environmentInfo.deviceManufacturer

            if (crashLogInfo.isAvailable()) {
                this.formattedCrashLogTimestamp = DateUtils.formatDateTime(
                        application, crashLogInfo.timestamp!!.getTime(),
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME)
                val df = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                this.formattedCrashLog = "## Crash info" +
                        "\nTime: " + df.format(crashLogInfo.getTimestamp()) +
                        "\nAntennaPod version: " + environmentInfo.applicationVersion +
                        "\n" +
                        "\nStackTrace" +
                        "\n```" +
                        "\n" + crashLogInfo.getContent() +
                        "\n```"
                this.crashLogState = CrashLogState.SHOWN_COLLAPSED
            } else {
                this.crashLogState = CrashLogState.UNAVAILABLE
            }
        }

        fun getEnvironmentInfo(): EnvironmentInfo {
            return this.environmentInfo
        }

        fun getCrashLogInfo(): CrashLogInfo {
            return this.crashLogInfo
        }

        fun getCrashLogState(): CrashLogState {
            return this.crashLogState
        }

        fun getFormattedCrashLogTimestamp(): String? {
            return this.formattedCrashLogTimestamp
        }

        fun getBugReportWithMarkup(): String {
            if (crashLogInfo.isAvailable()) {
                return getEnvironmentInfoWithMarkup() + "\n\n" + getCrashInfoWithMarkup()
            }
            return formattedEnvironmentInfo
        }

        fun getEnvironmentInfoWithMarkup(): String {
            return formattedEnvironmentInfo
        }

        fun getCrashInfoWithMarkup(): String? {
            return this.formattedCrashLog
        }

        internal fun setCrashLogState(crashLogState: CrashLogState) {
            this.crashLogState = crashLogState
        }
    }

    private val uiState = MutableLiveData<UiState>()
    private val disposable: Disposable

    init {
        // Does file I/O, so we have to use a background thread
        this.disposable = Observable.fromCallable { UiState(application) }
                .subscribeOn(Schedulers.computation())
                .subscribe { this.uiState.postValue(it) }
    }

    override fun onCleared() {
        super.onCleared()
        disposable.dispose()
    }

    fun getState(): LiveData<UiState> {
        return uiState
    }

    fun requireCurrentState(): UiState {
        return Objects.requireNonNull(uiState.getValue(), "UiState is NULL!")!!
    }

    fun setCrashLogState(crashLogState: UiState.CrashLogState) {
        val currentUiState = uiState.getValue()

        if (currentUiState != null) {
            if (currentUiState.getCrashLogState() != crashLogState) {
                currentUiState.setCrashLogState(crashLogState)
                uiState.setValue(currentUiState!!)
            }
        }
    }
}
