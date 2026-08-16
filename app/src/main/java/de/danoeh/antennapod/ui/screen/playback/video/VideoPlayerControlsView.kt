package de.danoeh.antennapod.ui.screen.playback.video

import android.content.Context
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import android.view.animation.ScaleAnimation
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import de.danoeh.antennapod.R
import de.danoeh.antennapod.databinding.VideoPlayerControlsBinding
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.event.playback.SpeedChangedEvent
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.common.Converter
import de.danoeh.antennapod.ui.episodes.TimeSpeedConverter
import de.danoeh.antennapod.ui.screen.feed.preferences.SkipPreferenceDialog
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class VideoPlayerControlsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
                                                        defStyleAttr: Int = 0) :
        FrameLayout(context, attrs, defStyleAttr) {
    private var binding: VideoPlayerControlsBinding? = null
    private var controlsShowing = true
    private var lastScreenTap = 0L
    private val tapDownPosition = Point()
    private val autoHideHandler = Handler(Looper.getMainLooper())
    private var maxInsetBottom = 0
    private var seekProgress = 0f
    private var currentDuration = 0
    private var currentSpeedMultiplier = 1.0f

    interface ControlsListener {
        fun onPlayPause()

        fun onRewind()

        fun onFastForward()

        fun onSeek(positionMs: Int)
    }

    private var listener: ControlsListener? = null

    init {
        init()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        EventBus.getDefault().register(this)
    }

    override fun onDetachedFromWindow() {
        cancelAutoHide()
        EventBus.getDefault().unregister(this)
        super.onDetachedFromWindow()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        updatePosition(event.getPosition(), event.getDuration(), currentSpeedMultiplier)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: SpeedChangedEvent) {
        currentSpeedMultiplier = event.getNewSpeed()
    }

    private fun init() {
        binding = VideoPlayerControlsBinding.inflate(LayoutInflater.from(getContext()), this, true)
        hideControls(false)
        binding!!.playButton.setIsVideoScreen(true)
        binding!!.playButton.setOnClickListener {
            if (listener != null) {
                listener!!.onPlayPause()
            }
            resetAutoHide()
        }

        binding!!.rewindButton.setOnClickListener {
            if (listener != null) {
                listener!!.onRewind()
            }
            resetAutoHide()
        }

        binding!!.rewindButton.setOnLongClickListener {
            SkipPreferenceDialog.showSkipPreference(getContext()!!,
                    SkipPreferenceDialog.SkipDirection.SKIP_REWIND, null)
            true
        }

        binding!!.fastForwardButton.setOnClickListener {
            if (listener != null) {
                listener!!.onFastForward()
            }
            resetAutoHide()
        }

        binding!!.fastForwardButton.setOnLongClickListener {
            SkipPreferenceDialog.showSkipPreference(getContext()!!,
                    SkipPreferenceDialog.SkipDirection.SKIP_FORWARD, null)
            true
        }

        binding!!.durationLabel.setOnClickListener {
            UserPreferences.setShowRemainTimeSetting(!UserPreferences.shouldShowRemainingTime())
            resetAutoHide()
        }

        binding!!.bottomControlsContainer.setOnTouchListener { view, motionEvent -> true }

        binding!!.sbPosition.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    seekProgress = progress / seekBar.getMax().toFloat()
                    updateSeekPosition((seekProgress * currentDuration).toInt(), currentSpeedMultiplier)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                binding!!.seekCardView.setScaleX(.8f)
                binding!!.seekCardView.setScaleY(.8f)
                binding!!.seekCardView.animate()
                        .setInterpolator(FastOutSlowInInterpolator())
                        .alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(200)
                        .start()
                cancelAutoHide()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (listener != null) {
                    listener!!.onSeek((seekProgress * currentDuration).toInt())
                }
                binding!!.seekCardView.setScaleX(1f)
                binding!!.seekCardView.setScaleY(1f)
                binding!!.seekCardView.animate()
                        .setInterpolator(FastOutSlowInInterpolator())
                        .alpha(0f).scaleX(.8f).scaleY(.8f)
                        .setDuration(200)
                        .start()
                resetAutoHide()
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            maxInsetBottom = Math.max(maxInsetBottom, systemBarInsets.bottom)
            val horizontal = Math.max(systemBarInsets.left, systemBarInsets.right)
            binding!!.seekBarContainer.setPadding(horizontal, 0, horizontal, maxInsetBottom)
            binding!!.toolbarContainer.setPadding(horizontal, 0, horizontal, 0)
            insets
        }
    }

    fun setListener(listener: ControlsListener?) {
        this.listener = listener
    }

    fun getToolbar(): Toolbar {
        return binding!!.toolbar
    }

    override fun setOnTouchListener(listener: View.OnTouchListener?) {
        super.setOnTouchListener(listener)
    }

    fun updatePosition(positionMs: Int, durationMs: Int, speedMultiplier: Float) {
        currentDuration = durationMs
        currentSpeedMultiplier = speedMultiplier
        val converter = TimeSpeedConverter(speedMultiplier)
        val currentPosition = converter.convert(positionMs)
        val duration = converter.convert(durationMs)

        if (currentPosition == Playable.INVALID_TIME || duration == Playable.INVALID_TIME) {
            return
        }

        binding!!.positionLabel.setText(Converter.getDurationStringLong(currentPosition))

        if (UserPreferences.shouldShowRemainingTime()) {
            val remainingTime = converter.convert(durationMs - positionMs)
            binding!!.durationLabel.setText("-" + Converter.getDurationStringLong(remainingTime))
        } else {
            binding!!.durationLabel.setText(Converter.getDurationStringLong(duration))
        }

        val progress = currentPosition.toFloat() / duration
        binding!!.sbPosition.setProgress((progress * binding!!.sbPosition.getMax()).toInt())
    }

    fun updateSeekPosition(positionMs: Int, speedMultiplier: Float) {
        val converter = TimeSpeedConverter(speedMultiplier)
        val seekPosition = converter.convert(positionMs)
        binding!!.seekPositionLabel.setText(Converter.getDurationStringLong(seekPosition))
    }

    fun setPlayButtonShowsPlay(showPlay: Boolean) {
        binding!!.playButton.setIsShowPlay(showPlay)
    }

    fun setBufferingProgress(progress: Float) {
        binding!!.sbPosition.setSecondaryProgress((progress * binding!!.sbPosition.getMax()).toInt())
    }

    fun showControls() {
        if (controlsShowing) {
            return
        }
        controlsShowing = true
        binding!!.bottomControlsContainer.setVisibility(View.VISIBLE)
        binding!!.controlsContainer.setVisibility(View.VISIBLE)
        binding!!.toolbarContainer.setVisibility(View.VISIBLE)
        val animation = AnimationUtils.loadAnimation(getContext(), R.anim.fade_in)
        if (animation != null) {
            binding!!.bottomControlsContainer.startAnimation(animation)
            binding!!.controlsContainer.startAnimation(animation)
            binding!!.toolbarContainer.startAnimation(animation)
        }
        resetAutoHide()
    }

    fun hideControls(animate: Boolean) {
        if (!controlsShowing) {
            return
        }
        controlsShowing = false
        if (animate) {
            val animation = AnimationUtils.loadAnimation(getContext(), R.anim.fade_out)
            if (animation != null) {
                binding!!.bottomControlsContainer.startAnimation(animation)
                binding!!.controlsContainer.startAnimation(animation)
                binding!!.toolbarContainer.startAnimation(animation)
            }
        }
        binding!!.bottomControlsContainer.setVisibility(View.GONE)
        binding!!.controlsContainer.setVisibility(View.GONE)
        binding!!.toolbarContainer.setVisibility(View.GONE)
        cancelAutoHide()
    }

    fun toggleControls() {
        if (controlsShowing) {
            hideControls(true)
        } else {
            showControls()
        }
    }

    fun resetAutoHide() {
        cancelAutoHide()
        autoHideHandler.postDelayed({ autoHide() }, AUTO_HIDE_DELAY_MS)
    }

    fun cancelAutoHide() {
        autoHideHandler.removeCallbacksAndMessages(null)
    }

    private fun autoHide() {
        if (controlsShowing) {
            hideControls(true)
        }
    }

    fun handleTouchEvent(event: MotionEvent, isPictureInPictureMode: Boolean) {
        if (isPictureInPictureMode) {
            return
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            tapDownPosition.x = event.getX().toInt()
            tapDownPosition.y = event.getY().toInt()
            return
        }

        if (event.getAction() != MotionEvent.ACTION_UP) {
            return
        }

        cancelAutoHide()

        if (System.currentTimeMillis() - lastScreenTap < 300) {
            if (event.getX() > getMeasuredWidth() / 2.0f) {
                if (listener != null) {
                    listener!!.onFastForward()
                }
                showSkipAnimation(true)
            } else {
                if (listener != null) {
                    listener!!.onRewind()
                }
                showSkipAnimation(false)
            }
            if (controlsShowing) {
                hideControls(false)
            }
            lastScreenTap = System.currentTimeMillis()
            return
        }

        val moveDistance = Math.sqrt(Math.pow((event.getX() - tapDownPosition.x).toDouble(), 2.0)
                + Math.pow((event.getY() - tapDownPosition.y).toDouble(), 2.0))
        if (moveDistance > 0.1 * getMeasuredHeight()) {
            return
        }

        toggleControls()
        lastScreenTap = System.currentTimeMillis()
    }

    private fun showSkipAnimation(isForward: Boolean) {
        val skipAnimation = AnimationSet(true)
        skipAnimation.addAnimation(ScaleAnimation(1f, 2f, 1f, 2f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f))
        skipAnimation.addAnimation(AlphaAnimation(1f, 0f))
        skipAnimation.setFillAfter(false)
        skipAnimation.setDuration(800)

        val params = binding!!.skipAnimationImage.getLayoutParams() as FrameLayout.LayoutParams
        if (isForward) {
            binding!!.skipAnimationImage.setImageResource(R.drawable.ic_fast_forward_video_white)
            params.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        } else {
            binding!!.skipAnimationImage.setImageResource(R.drawable.ic_fast_rewind_video_white)
            params.gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        }

        binding!!.skipAnimationImage.setVisibility(View.VISIBLE)
        binding!!.skipAnimationImage.setLayoutParams(params)
        binding!!.skipAnimationImage.startAnimation(skipAnimation)
        skipAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
            }

            override fun onAnimationEnd(animation: Animation) {
                binding!!.skipAnimationImage.setVisibility(View.GONE)
            }

            override fun onAnimationRepeat(animation: Animation) {
            }
        })
    }

    fun setProgressBarVisibility(visibility: Int) {
        binding!!.progressBar.setVisibility(visibility)
    }

    companion object {
        private const val AUTO_HIDE_DELAY_MS = 2500L
    }
}
