package de.danoeh.antennapod.playback.service.internal

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.event.playback.SleepTimerUpdatedEvent
import de.danoeh.antennapod.model.playback.TimerValue
import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences

open class ClockSleepTimer : SleepTimer {
    companion object {
        private const val TAG = "ClockSleepTimer"
    }

    private val context: Context
    private var initialWaitingTime: Long = 0
    private var timeLeft: Long = 0
    private var isRunning = false
    private var lastTick = 0L
    private var hasVibrated = false
    private var shakeListener: ShakeListener? = null

    constructor(context: Context) {
        this.context = context
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    open fun playbackPositionUpdate(playbackPositionEvent: PlaybackPositionEvent) {
        Log.d(TAG, "playback position updated")
        val now = System.currentTimeMillis()
        val timeSinceLastTick = now - lastTick
        lastTick = now
        if (timeSinceLastTick > 10 * 1000) {
            return // Ticks should arrive every second. If they didn't, playback was paused for a while.
        }
        timeLeft -= timeSinceLastTick

        val left = getTimeLeft()
        EventBus.getDefault().postSticky(SleepTimerUpdatedEvent.updated(left))
        if (timeLeft < SleepTimer.NOTIFICATION_THRESHOLD) {
            notifyAboutExpiry()
        }
        if (timeLeft <= 0) {
            Log.d(TAG, "Clock Sleep timer expired")
            stop()
        }
    }

    protected fun vibrate() {
        val vibrator: Vibrator?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator = vibratorManager.getDefaultVibrator()
        } else {
            vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (vibrator == null) {
            return
        }
        val duration = 500L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(duration)
        }
    }

    protected fun notifyAboutExpiry() {
        Log.d(TAG, "Sleep timer is about to expire")
        if (SleepTimerPreferences.vibrate() && !hasVibrated) {
            vibrate()
            hasVibrated = true
        }
        // start listening for shakes if shake to reset is enabled
        if (shakeListener == null && SleepTimerPreferences.shakeToReset()) {
            shakeListener = ShakeListener(getContext(), this)
        }
    }

    override fun isActive(): Boolean {
        return isRunning && timeLeft > 0
    }

    override fun start(initialWaitingTime: Long) {
        this.initialWaitingTime = initialWaitingTime
        this.timeLeft = initialWaitingTime

        // mark the sleep timer as active before firing the events
        // the event processors may immediately check if the sleep timer is active
        isRunning = true
        lastTick = System.currentTimeMillis()

        // make sure we've registered for events first
        EventBus.getDefault().register(this)
        val left = getTimeLeft()
        EventBus.getDefault().post(SleepTimerUpdatedEvent.justEnabled(left))

        EventBus.getDefault().postSticky(SleepTimerUpdatedEvent.updated(left))
    }

    override fun stop() {
        timeLeft = 0
        EventBus.getDefault().unregister(this)

        if (shakeListener != null) {
            shakeListener!!.pause()
        }
        shakeListener = null
        EventBus.getDefault().postSticky(SleepTimerUpdatedEvent.cancelled())
    }

    protected fun getContext(): Context {
        return context
    }

    override fun getTimeLeft(): TimerValue {
        return TimerValue(timeLeft, timeLeft)
    }

    override fun updateRemainingTime(waitingTimeOrEpisodes: Long) {
        this.timeLeft = waitingTimeOrEpisodes
    }

    override fun reset() {
        EventBus.getDefault().post(SleepTimerUpdatedEvent.cancelled())
        updateRemainingTime(initialWaitingTime)
        EventBus.getDefault().post(SleepTimerUpdatedEvent.justEnabled(getTimeLeft()))
    }

    override fun isEndingThisEpisode(episodeRemainingMillis: Long): Boolean {
        return episodeRemainingMillis >= getTimeLeft().getMillisValue()
    }

    override fun shouldContinueToNextEpisode(): Boolean {
        return getTimeLeft().getMillisValue() > 0
    }

    override fun episodeFinishedPlayback() {
        //no-op
    }
}
