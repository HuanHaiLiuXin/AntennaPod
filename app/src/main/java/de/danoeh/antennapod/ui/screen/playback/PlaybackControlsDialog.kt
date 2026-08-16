package de.danoeh.antennapod.ui.screen.playback

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.util.Consumer
import androidx.fragment.app.DialogFragment
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.playback.service.PlaybackController
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.ArrayList

class PlaybackControlsDialog : DialogFragment {
    private var dialog: AlertDialog? = null
    private val trackOptions: MutableList<TrackOption> = ArrayList()

    constructor() {
        // Empty constructor required for DialogFragment
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        setupAudioTracks()
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusEvent(event: PlayerStatusEvent) {
        setupAudioTracks()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        dialog = MaterialAlertDialogBuilder(getContext()!!)
                .setTitle(R.string.audio_controls)
                .setView(R.layout.audio_controls)
                .setPositiveButton(R.string.close_label, null).create()
        return dialog!!
    }

    private fun setupAudioTracks() {
        val container = dialog!!.findViewById<ViewGroup>(R.id.track_selection_container)!!

        PlaybackController.bindToMedia3Service(requireContext(), Consumer { controller ->
            val tracks = controller.getCurrentTracks()
            trackOptions.clear()
            for (group in tracks.getGroups()) {
                if (group.getType() != C.TRACK_TYPE_AUDIO || !group.isSupported()) {
                    continue
                }
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) {
                        continue
                    }
                    val format = group.getTrackFormat(i)
                    val label = formatLabel(format, i)
                    trackOptions.add(TrackOption(group.getMediaTrackGroup(), i, label, group.isTrackSelected(i)))
                }
            }

            if (getActivity() == null || !isAdded()) {
                return@Consumer
            }
            getActivity()!!.runOnUiThread {
                container.removeAllViews()
                val margin = (8 * getResources().getDisplayMetrics().density).toInt()
                for (idx in 0 until trackOptions.size) {
                    val chip = getLayoutInflater().inflate(R.layout.item_tag_chip, container, false) as Chip
                    val lp = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.setMargins(margin, 0, margin, 0)
                    chip.setLayoutParams(lp)
                    val opt = trackOptions.get(idx)
                    chip.setText(opt.label)
                    chip.setChecked(opt.selected)
                    val trackIndex = idx
                    chip.setOnClickListener {
                        selectTrack(trackIndex)
                        chip.setChecked(true)
                    }
                    container.addView(chip)
                }
            }
        })
    }

    private fun selectTrack(optionIndex: Int) {
        if (optionIndex < 0 || optionIndex >= trackOptions.size) {
            return
        }
        val opt = trackOptions.get(optionIndex)

        PlaybackController.bindToMedia3Service(requireContext(), Consumer { controller ->
            controller.setTrackSelectionParameters(controller.getTrackSelectionParameters()
                    .buildUpon()
                    .setOverrideForType(TrackSelectionOverride(opt.mediaTrackGroup, opt.trackIndex))
                    .build())
        })
    }

    private class TrackOption(val mediaTrackGroup: TrackGroup, val trackIndex: Int,
                              val label: String, val selected: Boolean) {
    }

    companion object {
        @JvmStatic
        fun newInstance(): PlaybackControlsDialog {
            val arguments = Bundle()
            val dialog = PlaybackControlsDialog()
            dialog.setArguments(arguments)
            return dialog
        }

        private fun formatLabel(format: Format, trackIndex: Int): String {
            if (format.label != null && !format.label!!.isEmpty()) {
                return format.label!!
            }
            if (format.language != null && !format.language!!.isEmpty()) {
                return format.language!!
            }
            return "Track " + (trackIndex + 1)
        }
    }
}
