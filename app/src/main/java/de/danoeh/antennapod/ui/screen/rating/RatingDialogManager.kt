package de.danoeh.antennapod.ui.screen.rating

import android.content.Context
import android.content.SharedPreferences

import java.util.concurrent.TimeUnit

import android.util.Log
import androidx.fragment.app.FragmentActivity
import de.danoeh.antennapod.BuildConfig
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.StatisticsItem
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlin.Pair

class RatingDialogManager(private val fragmentActivity: FragmentActivity) {
    companion object {
        private const val AFTER_DAYS = 20
        private const val TAG = "RatingDialog"
        private const val PREFS_NAME = "RatingPrefs"
        private const val KEY_RATED = "KEY_WAS_RATED"
        private const val KEY_FIRST_START_DATE = "KEY_FIRST_HIT_DATE"
    }

    private val preferences: SharedPreferences
    private var disposable: Disposable? = null

    init {
        preferences = fragmentActivity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun showIfNeeded() {
        if (isRated() || BuildConfig.DEBUG || "free" == BuildConfig.FLAVOR) {
            return
        } else if (!enoughTimeSinceInstall()) {
            return
        }

        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Observable.fromCallable<Pair<Long, Long>>(
                {
                    val statisticsData = DBReader.getStatistics(false, 0L, Long.MAX_VALUE)
                    var totalTime = 0L
                    for (item in statisticsData.feedTime) {
                        totalTime += item.timePlayed
                    }
                    Pair(totalTime, statisticsData.oldestDate)
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    val totalTime = result.first
                    val oldestDate = result.second
                    if (totalTime < TimeUnit.SECONDS.convert(15, TimeUnit.HOURS)) {
                        return@subscribe
                    } else if (oldestDate > System.currentTimeMillis()
                            - TimeUnit.MILLISECONDS.convert(AFTER_DAYS.toLong(), TimeUnit.DAYS)) {
                        return@subscribe // In case the app was opened but nothing was played
                    }
                    RatingDialogFragment.newInstance(result.first, result.second)
                            .show(fragmentActivity.getSupportFragmentManager(), TAG)
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    private fun isRated(): Boolean {
        return preferences.getBoolean(KEY_RATED, false)
    }

    fun saveRated() {
        preferences.edit().putBoolean(KEY_RATED, true).apply()
    }

    fun resetStartDate() {
        preferences.edit().putLong(KEY_FIRST_START_DATE, System.currentTimeMillis()).apply()
    }

    private fun enoughTimeSinceInstall(): Boolean {
        if (preferences.getLong(KEY_FIRST_START_DATE, 0L) == 0L) {
            resetStartDate()
            return false
        }
        val now = System.currentTimeMillis()
        val firstDate = preferences.getLong(KEY_FIRST_START_DATE, now)
        val diff = now - firstDate
        val diffDays = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)
        return diffDays >= AFTER_DAYS
    }
}
