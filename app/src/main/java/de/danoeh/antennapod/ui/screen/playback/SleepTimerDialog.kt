package de.danoeh.antennapod.ui.screen.playback

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.util.Consumer
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import de.danoeh.antennapod.BuildConfig
import de.danoeh.antennapod.R
import de.danoeh.antennapod.databinding.TimeDialogBinding
import de.danoeh.antennapod.event.playback.SleepTimerUpdatedEvent
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.playback.base.PlayerStatus
import de.danoeh.antennapod.playback.service.PlaybackController
import de.danoeh.antennapod.playback.service.PlaybackService
import de.danoeh.antennapod.playback.service.internal.MediaLibrarySessionCallback
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences
import de.danoeh.antennapod.storage.preferences.SleepTimerType
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.common.Converter
import de.danoeh.antennapod.ui.common.Keyboard
import de.danoeh.antennapod.ui.common.ThemeUtils
import de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.TimeUnit

class SleepTimerDialog : BottomSheetDialogFragment() {
    private var controller: PlaybackController? = null
    private var viewBinding: TimeDialogBinding? = null
    private var disposable: Disposable? = null

    @Volatile
    private var currentQueueSize: Int? = null

    @Volatile
    private var currentMedia: FeedMedia? = null

    override fun onStart() {
        super.onStart()
        controller = object : PlaybackController(getActivity()!!) {
            override fun loadMediaInfo() {
            }
        }
        controller!!.init()
        EventBus.getDefault().register(this)

        disposable = Single.fromCallable {
            val media = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId())
            currentMedia = media
            if (media == null || media.getItem() == null) {
                0
            } else {
                DBReader.getRemainingQueueSize(media.getItemId())
            }
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    currentQueueSize = result
                    refreshUiState()
                }, { error ->
                    currentQueueSize = 0
                    refreshUiState()
                })
    }

    override fun onStop() {
        super.onStop()
        if (controller != null) {
            controller!!.release()
        }
        if (disposable != null) {
            disposable!!.dispose()
            disposable = null
        }
        EventBus.getDefault().unregister(this)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            setupFullHeight(bottomSheetDialog)
        }
        return dialog
    }

    private fun setupFullHeight(bottomSheetDialog: BottomSheetDialog) {
        val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            val behavior = BottomSheetBehavior.from(bottomSheet)
            val layoutParams = bottomSheet.getLayoutParams()
            bottomSheet.setLayoutParams(layoutParams)
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED)
        }
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = TimeDialogBinding.inflate(inflater)
        val spinnerContent: MutableList<String> = ArrayList()
        // add "title" for all options
        spinnerContent.add(getString(R.string.time_minutes))
        spinnerContent.add(getString(R.string.sleep_timer_episodes_label))
        val spinnerAdapter = ArrayAdapter<String>(
                getContext()!!, android.R.layout.simple_spinner_item, spinnerContent)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        viewBinding!!.sleepTimerType.setAdapter(spinnerAdapter)
        viewBinding!!.sleepTimerType.setSelection(SleepTimerPreferences.getSleepTimerType().index)
        viewBinding!!.sleepTimerType.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            private var sleepTimerTypeInitialized = false

            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                try {
                    SleepTimerPreferences.setLastTimer(getSelectedSleepTime().toString())
                } catch (ignore: NumberFormatException) {
                    // Ignore silently and just not save it
                }
                val sleepType = SleepTimerType.fromIndex(position)
                SleepTimerPreferences.setSleepTimerType(sleepType)
                // this callback is called even when the spinner is first initialized
                // we need to differentiate these calls
                if (sleepTimerTypeInitialized) {
                    // disable auto sleep timer if they've configured it for most of the day
                    if (isSleepTimerConfiguredForMostOfTheDay()) {
                        viewBinding!!.autoEnableCheckbox.setChecked(false)
                    }
                    viewBinding!!.timeEditText.setText(SleepTimerPreferences.lastTimerValue())
                }
                sleepTimerTypeInitialized = true
                refreshUiState()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        })
        viewBinding!!.timeDisplayContainer.setVisibility(View.GONE)
        viewBinding!!.timeEditText.setText(SleepTimerPreferences.lastTimerValue())
        viewBinding!!.timeEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
            }

            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
            }

            override fun afterTextChanged(editable: Editable) {
                refreshUiState()
            }
        })
        viewBinding!!.playbackPreferencesButton.setOnClickListener {
            val playbackIntent = Intent(getActivity(), PreferenceActivity::class.java)
            playbackIntent.putExtra(PreferenceActivity.OPEN_PLAYBACK_SETTINGS, true)
            startActivity(playbackIntent)
            dismiss()
        }
        refreshUiState()
        viewBinding!!.autoEnableCheckbox.setChecked(SleepTimerPreferences.autoEnable())
        viewBinding!!.shakeToResetCheckbox.setChecked(SleepTimerPreferences.shakeToReset())
        viewBinding!!.vibrateCheckbox.setChecked(SleepTimerPreferences.vibrate())
        refreshAutoEnableControls(SleepTimerPreferences.autoEnable())

        viewBinding!!.shakeToResetCheckbox.setOnCheckedChangeListener { buttonView, isChecked ->
            SleepTimerPreferences.setShakeToReset(isChecked) }
        viewBinding!!.vibrateCheckbox.setOnCheckedChangeListener { buttonView, isChecked ->
            SleepTimerPreferences.setVibrate(isChecked) }
        viewBinding!!.autoEnableCheckbox.setOnCheckedChangeListener { compoundButton, isChecked ->
            val mostOfDay = isSleepTimerConfiguredForMostOfTheDay()
            if (isChecked && mostOfDay && SleepTimerPreferences.getSleepTimerType() == SleepTimerType.EPISODES) {
                confirmAlwaysSleepTimerDialog()
            }
            refreshAutoEnableControls(isChecked)
        }
        updateAutoEnableText()

        viewBinding!!.changeTimesButton.setOnClickListener {
            val from = SleepTimerPreferences.autoEnableFrom()
            val to = SleepTimerPreferences.autoEnableTo()
            showTimeRangeDialog(getContext(), from, to)
        }
        viewBinding!!.disableSleeptimerButton.setOnClickListener {
            if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
                PlaybackController.bindToMedia3Service(getActivity()!!, Consumer { mediaController ->
                    mediaController.sendCustomCommand(
                            MediaLibrarySessionCallback.SESSION_COMMAND_DISABLE_SLEEP_TIMER,
                            Bundle.EMPTY)
                })
            } else if (controller != null) {
                controller!!.disableSleepTimer()
            }
        }
        viewBinding!!.setSleeptimerButton.setOnClickListener {
            if (!PlaybackService.isRunning
                    || (!BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE
                            && controller != null && controller!!.getStatus() != PlayerStatus.PLAYING)) {
                Snackbar.make(viewBinding!!.getRoot(), R.string.no_media_playing_label, Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            try {
                SleepTimerPreferences.setLastTimer("" + getSelectedSleepTime())
                val time = SleepTimerPreferences.timerMillisOrEpisodes()
                if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
                    PlaybackController.bindToMedia3Service(getActivity()!!, Consumer { mediaController ->
                        mediaController.sendCustomCommand(
                                MediaLibrarySessionCallback.SESSION_COMMAND_SET_SLEEP_TIMER,
                                Bundle.EMPTY)
                    })
                } else if (controller != null) {
                    controller!!.setSleepTimer(time)
                }
                Keyboard.hide(getActivity()!!)
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                Snackbar.make(viewBinding!!.getRoot(), R.string.time_dialog_invalid_input, Snackbar.LENGTH_LONG).show()
            }
        }
        return viewBinding!!.getRoot()
    }

    private fun isSleepTimerConfiguredForMostOfTheDay(): Boolean {
        return SleepTimerPreferences.autoEnableDuration() > SLEEP_DURATION_DAILY_HOURS_CUTOFF
    }

    private fun confirmAlwaysSleepTimerDialog() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sleep_timer_without_continuous_playback)
                .setMessage(R.string.sleep_timer_without_continuous_playback_message)
                .setNegativeButton(R.string.sleep_timer_without_continuous_playback,
                        { dialogInterface, i ->
                            // disable continuous playback and also disable the auto sleep timer
                            UserPreferences.setFollowQueue(false)
                            viewBinding!!.autoEnableCheckbox.setChecked(false)
                            refreshUiState()
                        })
                .setPositiveButton(R.string.sleep_timer_without_continuous_playback_change_hours,
                        { dialogInterface, i ->
                            val from = SleepTimerPreferences.autoEnableFrom()
                            val to = SleepTimerPreferences.autoEnableTo()
                            showTimeRangeDialog(getContext(), from, to)
                        })
                .create()
        dialog.setOnCancelListener { viewBinding!!.autoEnableCheckbox.setChecked(false) }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(ThemeUtils.getColorFromAttr(requireContext(), R.attr.colorError))
    }

    private fun getSelectedSleepTime(): Long {
        val time = viewBinding!!.timeEditText.getText().toString().toLong()
        if (time == 0L) {
            throw NumberFormatException("Timer must not be zero")
        }
        return time
    }

    private fun refreshAutoEnableControls(enabled: Boolean) {
        SleepTimerPreferences.setAutoEnable(enabled)
        viewBinding!!.changeTimesButton.setAlpha(if (enabled) 1.0f else 0.5f)
    }

    private fun refreshUiState() {
        // if we're using episode timer and continuous playback is disabled, don't
        // let the user use anything other than 1 episode
        val isEpisodeType = SleepTimerPreferences.getSleepTimerType() == SleepTimerType.EPISODES
        val noEpisodeSelection = isEpisodeType && !UserPreferences.isFollowQueue()

        if (noEpisodeSelection) {
            viewBinding!!.timeEditText.setEnabled(false)
            viewBinding!!.playbackPreferencesButton.setVisibility(View.VISIBLE)
            viewBinding!!.sleepTimerHintText.setText(R.string.multiple_sleep_episodes_while_continuous_playback_disabled)
            viewBinding!!.sleepTimerHintText.setVisibility(View.VISIBLE)
            viewBinding!!.autoEnableCheckbox.setVisibility(View.GONE)
            viewBinding!!.changeTimesButton.setVisibility(View.GONE)
            viewBinding!!.shakeToResetCheckbox.setVisibility(View.GONE)
            viewBinding!!.vibrateCheckbox.setVisibility(View.GONE)
            viewBinding!!.setSleeptimerButton.setEnabled(false)
        } else {
            viewBinding!!.playbackPreferencesButton.setVisibility(View.GONE)
            viewBinding!!.autoEnableCheckbox.setVisibility(View.VISIBLE)
            viewBinding!!.changeTimesButton.setVisibility(View.VISIBLE)
            viewBinding!!.shakeToResetCheckbox.setVisibility(if (isEpisodeType) View.GONE else View.VISIBLE)
            viewBinding!!.vibrateCheckbox.setVisibility(View.VISIBLE)
            viewBinding!!.setSleeptimerButton.setEnabled(true)
            viewBinding!!.timeEditText.setEnabled(true)
            var selectedSleepTime: Long
            try {
                selectedSleepTime = getSelectedSleepTime()
            } catch (nex: NumberFormatException) {
                selectedSleepTime = 0L
            }

            if (isEpisodeType) {
                // for episode timers check if the queue length exceeds the number of sleep episodes we have
                if (currentQueueSize != null && selectedSleepTime > currentQueueSize!!) {
                    viewBinding!!.sleepTimerHintText.setText(getResources().getQuantityString(
                            R.plurals.episodes_sleep_timer_exceeds_queue,
                            currentQueueSize!!,
                            currentQueueSize!!))
                    viewBinding!!.sleepTimerHintText.setVisibility(View.VISIBLE)
                } else {
                    viewBinding!!.sleepTimerHintText.setVisibility(View.GONE)
                }
            } else {
                // for time sleep timers check if the selected value exceeds the remaining play time in the episode
                val remaining = if (controller != null && currentMedia != null)
                    currentMedia!!.getDuration() - currentMedia!!.getPosition()
                else
                    Int.MAX_VALUE
                val timer = TimeUnit.MINUTES.toMillis(selectedSleepTime)
                if (timer > remaining && !UserPreferences.isFollowQueue()) {
                    val remainingMinutes = Math.toIntExact(TimeUnit.MILLISECONDS.toMinutes(remaining.toLong()))
                    viewBinding!!.sleepTimerHintText
                            .setText(getResources().getQuantityString(
                                    R.plurals.timer_exceeds_remaining_time_while_continuous_playback_disabled,
                                    remainingMinutes,
                                    remainingMinutes
                            ))
                    viewBinding!!.sleepTimerHintText.setVisibility(View.VISIBLE)
                } else {
                    // don't show it at all
                    viewBinding!!.sleepTimerHintText.setVisibility(View.GONE) // could maybe show duration in minutes
                }
            }
        }

        // disable extension for episodes if we're not moving to next one
        viewBinding!!.extendSleepFiveMinutesButton.setEnabled(!noEpisodeSelection)
        viewBinding!!.extendSleepTenMinutesButton.setEnabled(!noEpisodeSelection)
        viewBinding!!.extendSleepTwentyMinutesButton.setEnabled(!noEpisodeSelection)

        if (SleepTimerPreferences.getSleepTimerType() == SleepTimerType.CLOCK) {
            setupExtendButton(viewBinding!!.extendSleepFiveMinutesButton,
                    getString(R.string.extend_sleep_timer_label, EXTEND_FEW_MINUTES_DISPLAY_VALUE),
                    EXTEND_FEW_MINUTES)
            setupExtendButton(viewBinding!!.extendSleepTenMinutesButton,
                    getString(R.string.extend_sleep_timer_label, EXTEND_MID_MINUTES_DISPLAY_VALUE),
                    EXTEND_MID_MINUTES)
            setupExtendButton(viewBinding!!.extendSleepTwentyMinutesButton,
                    getString(R.string.extend_sleep_timer_label, EXTEND_LOTS_MINUTES_DISPLAY_VALUE),
                    EXTEND_LOTS_MINUTES)
        } else {
            setupExtendButton(viewBinding!!.extendSleepFiveMinutesButton,
                    "+" + getResources().getQuantityString(R.plurals.num_episodes,
                            EXTEND_FEW_EPISODES, EXTEND_FEW_EPISODES), EXTEND_FEW_EPISODES)
            setupExtendButton(viewBinding!!.extendSleepTenMinutesButton,
                    "+" + getResources().getQuantityString(R.plurals.num_episodes,
                            EXTEND_MID_EPISODES, EXTEND_MID_EPISODES), EXTEND_MID_EPISODES)
            setupExtendButton(viewBinding!!.extendSleepTwentyMinutesButton,
                    "+" + getResources().getQuantityString(R.plurals.num_episodes,
                            EXTEND_LOTS_EPISODES, EXTEND_LOTS_EPISODES), EXTEND_LOTS_EPISODES)
        }
    }

    internal fun setupExtendButton(button: TextView, text: String, extendValue: Int) {
        button.setText(text)
        button.setOnClickListener {
            if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
                PlaybackController.bindToMedia3Service(getActivity()!!, Consumer { mediaController ->
                    mediaController.sendCustomCommand(
                            MediaLibrarySessionCallback.SESSION_COMMAND_EXTEND_SLEEP_TIMER,
                            MediaLibrarySessionCallback.createBundle(extendValue.toLong()))
                })
            } else if (controller != null) {
                controller!!.extendSleepTimer(extendValue.toLong())
            }
        }
    }

    private fun showTimeRangeDialog(context: Context?, from: Int, to: Int) {
        val dialog = TimeRangeDialog(context!!, from, to)
        dialog.setOnDismissListener {
            SleepTimerPreferences.setAutoEnableFrom(dialog.getFrom())
            SleepTimerPreferences.setAutoEnableTo(dialog.getTo())
            val mostOfDay = isSleepTimerConfiguredForMostOfTheDay()
            // disable the checkbox if they've selected always
            // only change the state if true, don't change it regardless of flag (although we could)
            if (mostOfDay && SleepTimerPreferences.getSleepTimerType() == SleepTimerType.EPISODES) {
                confirmAlwaysSleepTimerDialog()
            } else if (!viewBinding!!.autoEnableCheckbox.isChecked()) {
                // if it's not checked, then make sure it's checked in UI too
                viewBinding!!.autoEnableCheckbox.setChecked(true)
            }
            updateAutoEnableText()
        }
        dialog.show()
    }

    private fun updateAutoEnableText() {
        val text: String
        val from = SleepTimerPreferences.autoEnableFrom()
        val to = SleepTimerPreferences.autoEnableTo()

        if (from == to) {
            text = getString(R.string.auto_enable_label)
        } else if (DateFormat.is24HourFormat(getContext())) {
            val formattedFrom = String.format(Locale.getDefault(), "%02d:00", from)
            val formattedTo = String.format(Locale.getDefault(), "%02d:00", to)
            text = getString(R.string.auto_enable_label_with_times, formattedFrom, formattedTo)
        } else {
            val formattedFrom = String.format(Locale.getDefault(), "%02d:00 %s",
                    from % 12, if (from >= 12) "PM" else "AM")
            val formattedTo = String.format(Locale.getDefault(), "%02d:00 %s",
                    to % 12, if (to >= 12) "PM" else "AM")
            text = getString(R.string.auto_enable_label_with_times, formattedFrom, formattedTo)
        }
        viewBinding!!.autoEnableCheckbox.setText(text)
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    @SuppressWarnings("unused")
    fun timerUpdated(event: SleepTimerUpdatedEvent) {
        viewBinding!!.timeDisplayContainer.setVisibility(
                if (event.isOver() || event.isCancelled()) View.GONE else View.VISIBLE)
        viewBinding!!.timeSetupContainer.setVisibility(if (event.isOver() || event.isCancelled()) View.VISIBLE else View.GONE)
        viewBinding!!.sleepTimerType.setEnabled(event.isOver() || event.isCancelled())

        if (SleepTimerPreferences.getSleepTimerType() == SleepTimerType.EPISODES) {
            viewBinding!!.time.setText(getResources().getQuantityString(R.plurals.num_episodes,
                    event.getDisplayTimeLeft().toInt(), event.getDisplayTimeLeft().toInt()))
        } else {
            viewBinding!!.time.setText(Converter.getDurationStringLong(event.getDisplayTimeLeft().toInt()))
        }
    }

    companion object {
        private const val EXTEND_FEW_MINUTES_DISPLAY_VALUE = 5
        private const val EXTEND_FEW_MINUTES = 5 * 1000 * 60
        private const val EXTEND_MID_MINUTES_DISPLAY_VALUE = 10
        private const val EXTEND_MID_MINUTES = 10 * 1000 * 60
        private const val EXTEND_LOTS_MINUTES_DISPLAY_VALUE = 30
        private const val EXTEND_LOTS_MINUTES = 30 * 1000 * 60
        private const val EXTEND_FEW_EPISODES = 1
        private const val EXTEND_MID_EPISODES = 2
        private const val EXTEND_LOTS_EPISODES = 3
        private const val SLEEP_DURATION_DAILY_HOURS_CUTOFF = 12
    }
}
