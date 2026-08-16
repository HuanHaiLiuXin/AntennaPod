package de.danoeh.antennapod.ui.screen.playback

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.core.util.Consumer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import de.danoeh.antennapod.R
import de.danoeh.antennapod.event.playback.SpeedChangedEvent
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.playback.base.BuildConfig
import de.danoeh.antennapod.playback.service.PlaybackController
import de.danoeh.antennapod.playback.service.internal.MediaLibrarySessionCallback
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.common.ItemOffsetDecoration
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.DecimalFormatSymbols
import java.util.ArrayList
import java.util.Collections
import java.util.Locale

open class VariableSpeedDialog : BottomSheetDialogFragment {
    private var adapter: SpeedSelectionAdapter? = null
    private var controller: PlaybackController? = null
    private val selectedSpeeds: MutableList<Float>
    private var speedSeekBar: PlaybackSpeedSeekBar? = null
    private var addCurrentSpeedChip: Chip? = null
    private var skipSilenceCheckbox: CheckBox? = null
    private var disposable: Disposable? = null

    constructor() {
        val format = DecimalFormatSymbols(Locale.US)
        format.setDecimalSeparator('.')
        selectedSpeeds = ArrayList(UserPreferences.getPlaybackSpeedArray())
    }

    override fun onStart() {
        super.onStart()
        controller = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {
                this@VariableSpeedDialog.loadMediaInfo()
            }
        }
        controller!!.init()
        EventBus.getDefault().register(this)
        loadMediaInfo()
    }

    override fun onStop() {
        super.onStop()
        controller!!.release()
        controller = null
        EventBus.getDefault().unregister(this)
        if (disposable != null) {
            disposable!!.dispose()
        }
    }

    private fun loadMediaInfo() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Observable.fromCallable<Playable> {
            if (controller == null) {
                throw NullPointerException()
            }
            // Make sure the media is loaded in case getCurrentPlaybackSpeedMultiplier has to access it
            controller!!.getMedia()!!
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ pair ->
                    if (controller == null) {
                        return@subscribe
                    }
                    updateSpeed(SpeedChangedEvent(controller!!.getCurrentPlaybackSpeedMultiplier()))
                    updateSkipSilence(controller!!.getCurrentPlaybackSkipSilence())
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun updateSpeed(event: SpeedChangedEvent) {
        speedSeekBar!!.updateSpeed(event.getNewSpeed())
        addCurrentSpeedChip!!.setText(String.format(Locale.getDefault(), "%1$.2f", event.getNewSpeed()))
    }

    fun updateSkipSilence(skipSilence: Boolean) {
        skipSilenceCheckbox!!.setChecked(skipSilence)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = View.inflate(getContext(), R.layout.speed_select_dialog, null)
        speedSeekBar = root.findViewById(R.id.speed_seek_bar)
        speedSeekBar!!.setProgressChangedListener { multiplier ->
            UserPreferences.setPlaybackSpeed(multiplier)
            if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
                PlaybackController.bindToMedia3Service(requireContext(),
                        Consumer { controller -> controller.setPlaybackSpeed(multiplier) })
            } else if (controller != null) {
                controller!!.setPlaybackSpeed(multiplier)
            }
        }
        val selectedSpeedsGrid = root.findViewById<RecyclerView>(R.id.selected_speeds_grid)
        selectedSpeedsGrid.setLayoutManager(GridLayoutManager(getContext(), 3))
        selectedSpeedsGrid.addItemDecoration(ItemOffsetDecoration(requireContext(), 4))
        adapter = SpeedSelectionAdapter()
        adapter!!.setHasStableIds(true)
        selectedSpeedsGrid.setAdapter(adapter)

        addCurrentSpeedChip = root.findViewById(R.id.add_current_speed_chip)
        addCurrentSpeedChip!!.setCloseIconVisible(true)
        addCurrentSpeedChip!!.setCloseIconResource(R.drawable.ic_add)
        addCurrentSpeedChip!!.setOnCloseIconClickListener { addCurrentSpeed() }
        addCurrentSpeedChip!!.setCloseIconContentDescription(getString(R.string.add_preset))
        addCurrentSpeedChip!!.setOnClickListener { addCurrentSpeed() }

        skipSilenceCheckbox = root.findViewById(R.id.skipSilence)
        skipSilenceCheckbox!!.setChecked(UserPreferences.isSkipSilence())
        skipSilenceCheckbox!!.setOnCheckedChangeListener { buttonView, isChecked ->
            UserPreferences.setSkipSilence(isChecked)
            if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
                PlaybackController.bindToMedia3Service(requireContext(), Consumer { mediaController ->
                    mediaController.sendCustomCommand(MediaLibrarySessionCallback.SESSION_COMMAND_SKIP_SILENCE,
                            MediaLibrarySessionCallback.createBundle(isChecked))
                })
            } else if (controller != null) {
                controller!!.setSkipSilence(isChecked)
            }
        }
        return root
    }

    private fun addCurrentSpeed() {
        val newSpeed = speedSeekBar!!.getCurrentSpeed()
        if (selectedSpeeds.contains(newSpeed)) {
            Snackbar.make(addCurrentSpeedChip!!,
                    getString(R.string.preset_already_exists, newSpeed), Snackbar.LENGTH_LONG).show()
        } else {
            selectedSpeeds.add(newSpeed)
            Collections.sort(selectedSpeeds)
            UserPreferences.setPlaybackSpeedArray(selectedSpeeds)
            adapter!!.notifyDataSetChanged()
        }
    }

    inner class SpeedSelectionAdapter : RecyclerView.Adapter<SpeedSelectionAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val chip = Chip(getContext())
            chip.setTextAlignment(View.TEXT_ALIGNMENT_CENTER)
            return ViewHolder(chip)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val speed = selectedSpeeds.get(position)

            holder.chip.setText(String.format(Locale.getDefault(), "%1$.2f", speed))
            holder.chip.setOnLongClickListener {
                selectedSpeeds.remove(speed)
                UserPreferences.setPlaybackSpeedArray(selectedSpeeds)
                notifyDataSetChanged()
                true
            }
            holder.chip.setOnClickListener {
                UserPreferences.setPlaybackSpeed(speed)
                if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
                    PlaybackController.bindToMedia3Service(requireContext(),
                            Consumer { controller -> controller.setPlaybackSpeed(speed) })
                } else if (controller != null) {
                    controller!!.setPlaybackSpeed(speed)
                }
                Handler(Looper.getMainLooper()).postDelayed({ dismiss() }, 200)
            }
        }

        override fun getItemCount(): Int {
            return selectedSpeeds.size
        }

        override fun getItemId(position: Int): Long {
            return selectedSpeeds.get(position).hashCode().toLong()
        }

        inner class ViewHolder(chip: Chip) : RecyclerView.ViewHolder(chip) {
            var chip: Chip = chip
        }
    }

    companion object {
        private const val TAG = "VariableSpeedDialog"
    }
}
