package de.danoeh.antennapod.ui.echo

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.ui.echo.databinding.EchoActivityBinding
import de.danoeh.antennapod.ui.echo.screen.EchoScreen
import de.danoeh.antennapod.ui.echo.screen.FinalShareScreen
import de.danoeh.antennapod.ui.echo.screen.HoarderScreen
import de.danoeh.antennapod.ui.echo.screen.HoursPlayedScreen
import de.danoeh.antennapod.ui.echo.screen.IntroScreen
import de.danoeh.antennapod.ui.echo.screen.QueueScreen
import de.danoeh.antennapod.ui.echo.screen.ThanksScreen
import de.danoeh.antennapod.ui.echo.screen.TimeReleasePlayScreen
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

import java.util.Collections
import java.util.concurrent.TimeUnit

class EchoActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "EchoActivity"
        private const val NUM_SCREENS = 7
    }

    private var viewBinding: EchoActivityBinding? = null
    private var currentScreenIdx = -1
    private var progressPaused = false
    private var progress = 0f
    private var echoProgress: EchoProgress? = null
    private var redrawTimer: Disposable? = null
    private var timeTouchDown = 0L
    private var timeLastFrame = 0L
    private var disposable: Disposable? = null
    private var screens: List<EchoScreen>? = null
    private var currentScreen: EchoScreen? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false)
        super.onCreate(savedInstanceState)
        screens = listOf(IntroScreen(this, getLayoutInflater()),
                HoursPlayedScreen(this, getLayoutInflater()), QueueScreen(this, getLayoutInflater()),
                TimeReleasePlayScreen(this, getLayoutInflater()), HoarderScreen(this, getLayoutInflater()),
                ThanksScreen(this, getLayoutInflater()), FinalShareScreen(this, getLayoutInflater()))
        viewBinding = EchoActivityBinding.inflate(getLayoutInflater())
        viewBinding!!.closeButton.setOnClickListener { finish() }
        viewBinding!!.screenContainer.setOnTouchListener { v, event ->
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                progressPaused = true
                timeTouchDown = System.currentTimeMillis()
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                progressPaused = false
                if (timeTouchDown + 500 > System.currentTimeMillis()) {
                    val newScreen: Int
                    if (event.getX() < 0.5f * viewBinding!!.screenContainer.getMeasuredWidth()) {
                        newScreen = Math.max(currentScreenIdx - 1, 0)
                    } else {
                        newScreen = Math.min(currentScreenIdx + 1, NUM_SCREENS - 1)
                        if (currentScreenIdx == NUM_SCREENS - 1) {
                            finish()
                        }
                    }
                    progress = newScreen.toFloat()
                    echoProgress!!.setProgress(progress)
                    loadScreen(newScreen, false)
                }
            }
            return@setOnTouchListener true
        }
        echoProgress = EchoProgress(NUM_SCREENS)
        viewBinding!!.echoProgressImage.setImageDrawable(echoProgress)
        setContentView(viewBinding!!.getRoot())
        loadScreen(0, false)
        loadStatistics()
    }

    override fun onStart() {
        super.onStart()
        redrawTimer = Flowable.timer(20, TimeUnit.MILLISECONDS)
                .observeOn(Schedulers.computation())
                .repeat()
                .subscribe {
                    if (progressPaused) {
                        return@subscribe
                    }
                    currentScreen!!.postInvalidate()
                    if (progress >= NUM_SCREENS - 0.001f) {
                        return@subscribe
                    }
                    var timePassed = System.currentTimeMillis() - timeLastFrame
                    timeLastFrame = System.currentTimeMillis()
                    if (timePassed > 500) {
                        timePassed = 0
                    }
                    progress = Math.min(NUM_SCREENS - 0.001f, progress + timePassed / 10000.0f)
                    echoProgress!!.setProgress(progress)
                    viewBinding!!.echoProgressImage.postInvalidate()
                    loadScreen(progress.toInt(), false)
                }
    }

    override fun onStop() {
        super.onStop()
        redrawTimer!!.dispose()
        if (disposable != null) {
            disposable!!.dispose()
        }
    }

    private fun loadScreen(screen: Int, force: Boolean) {
        if (screen == currentScreenIdx && !force) {
            return
        }
        currentScreenIdx = screen
        currentScreen = screens!!.get(currentScreenIdx)
        runOnUiThread {
            viewBinding!!.screenContainer.removeAllViews()
            viewBinding!!.screenContainer.addView(currentScreen!!.getView())
        }
    }

    private fun loadStatistics() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Observable.fromCallable<DBReader.StatisticsResult>(
                {
                    val statisticsData = DBReader.getStatistics(
                            false, EchoConfig.jan1(), Long.MAX_VALUE)
                    Collections.sort(statisticsData.feedTime) { item1, item2 ->
                        java.lang.Long.compare(item2.timePlayed, item1.timePlayed) }
                    statisticsData
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    for (screen in screens!!) {
                        screen.startLoading(result)
                    }
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }
}
