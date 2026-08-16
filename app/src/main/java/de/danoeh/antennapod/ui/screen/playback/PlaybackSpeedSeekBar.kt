package de.danoeh.antennapod.ui.screen.playback

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.core.util.Consumer
import de.danoeh.antennapod.R
import java.util.Locale

class PlaybackSpeedSeekBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
                                                     defStyleAttr: Int = 0) :
        FrameLayout(context, attrs, defStyleAttr) {
    private var seekBar: SeekBar? = null
    private var progressChangedListener: Consumer<Float>? = null

    init {
        setup()
    }

    private fun setup() {
        View.inflate(getContext(), R.layout.playback_speed_seek_bar, this)
        seekBar = findViewById(R.id.playback_speed)
        findViewById<View>(R.id.butDecSpeed).setOnClickListener {
            seekBar!!.setProgress(seekBar!!.getProgress() - 1)
            if (progressChangedListener != null) {
                progressChangedListener!!.accept(getCurrentSpeed())
            }
        }
        findViewById<View>(R.id.butIncSpeed).setOnClickListener {
            seekBar!!.setProgress(seekBar!!.getProgress() + 1)
            if (progressChangedListener != null) {
                progressChangedListener!!.accept(getCurrentSpeed())
            }
        }

        seekBar!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && progressChangedListener != null) {
                    progressChangedListener!!.accept(getCurrentSpeed())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }
        })
    }

    fun updateSpeed(speedMultiplier: Float) {
        seekBar!!.setProgress(Math.round(20 * speedMultiplier - 10))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            seekBar!!.setStateDescription(String.format(Locale.getDefault(), "%1$.2f", speedMultiplier))
        }
    }

    fun setProgressChangedListener(progressChangedListener: Consumer<Float>) {
        this.progressChangedListener = progressChangedListener
    }

    fun getCurrentSpeed(): Float {
        return (seekBar!!.getProgress() + 10) / 20.0f
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        seekBar!!.setEnabled(enabled)
        findViewById<View>(R.id.butDecSpeed).setEnabled(enabled)
        findViewById<View>(R.id.butIncSpeed).setEnabled(enabled)
    }
}
