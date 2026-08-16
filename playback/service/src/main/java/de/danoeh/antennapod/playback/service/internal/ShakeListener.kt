package de.danoeh.antennapod.playback.service.internal

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class ShakeListener : SensorEventListener {
    companion object {
        private val TAG = ShakeListener::class.java.getSimpleName()
    }

    private var mAccelerometer: Sensor? = null
    private var mSensorMgr: SensorManager? = null
    private val mSleepTimer: SleepTimer
    private val mContext: Context

    constructor(context: Context, sleepTimer: SleepTimer) {
        mContext = context
        mSleepTimer = sleepTimer
        resume()
    }

    private fun resume() {
        // only a precaution, the user should actually not be able to activate shake to reset
        // when the accelerometer is not available
        mSensorMgr = mContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (mSensorMgr == null) {
            throw UnsupportedOperationException("Sensors not supported")
        }
        mAccelerometer = mSensorMgr!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!mSensorMgr!!.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_UI)) { // if not supported
            mSensorMgr!!.unregisterListener(this)
            throw UnsupportedOperationException("Accelerometer not supported")
        }
    }

    fun pause() {
        if (mSensorMgr != null) {
            mSensorMgr!!.unregisterListener(this)
            mSensorMgr = null
        }
    }

    protected fun vibrate() {
        val vibrator: Vibrator?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = mContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator = vibratorManager.getDefaultVibrator()
        } else {
            vibrator = mContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val duration = 100L
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, 120))
            } else {
                vibrator.vibrate(duration)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val gX = event.values[0] / SensorManager.GRAVITY_EARTH
        val gY = event.values[1] / SensorManager.GRAVITY_EARTH
        val gZ = event.values[2] / SensorManager.GRAVITY_EARTH

        val gForce = Math.sqrt((gX * gX + gY * gY + gZ * gZ).toDouble())
        if (gForce > 2.25) {
            Log.d(TAG, "Detected shake " + gForce)
            mSleepTimer.reset()
            vibrate()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
    }

}
