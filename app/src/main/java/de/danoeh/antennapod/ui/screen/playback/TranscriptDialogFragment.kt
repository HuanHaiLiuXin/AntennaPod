package de.danoeh.antennapod.ui.screen.playback

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.databinding.TranscriptDialogBinding
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.Transcript
import de.danoeh.antennapod.model.feed.TranscriptSegment
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.playback.service.PlaybackController
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.ui.transcript.TranscriptUtils
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class TranscriptDialogFragment : DialogFragment(),
        TranscriptAdapter.SegmentClickListener {
    private var viewBinding: TranscriptDialogBinding? = null
    private var disposable: Disposable? = null
    private var media: Playable? = null
    private var transcript: Transcript? = null
    private var adapter: TranscriptAdapter? = null
    private var doInitialScroll = true
    private var layoutManager: LinearLayoutManager? = null

    override fun onResume() {
        val params = getDialog()!!.getWindow()!!.getAttributes()
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        getDialog()!!.getWindow()!!.setAttributes(params)
        super.onResume()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        viewBinding = TranscriptDialogBinding.inflate(getLayoutInflater())
        layoutManager = LinearLayoutManager(requireContext())
        viewBinding!!.transcriptList.setLayoutManager(layoutManager)

        adapter = TranscriptAdapter(requireContext(), this)
        viewBinding!!.transcriptList.setAdapter(adapter)
        viewBinding!!.transcriptList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    viewBinding!!.followAudioCheckbox.setChecked(false)
                }
            }
        })

        viewBinding!!.toolbar.inflateMenu(R.menu.transcript)
        viewBinding!!.toolbar.setOnMenuItemClickListener(this::onMenuItemClick)

        viewBinding!!.followAudioCheckbox.setChecked(true)
        viewBinding!!.progLoading.setVisibility(View.VISIBLE)
        doInitialScroll = true

        val dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(viewBinding!!.getRoot())
                .setNegativeButton(R.string.close_label, null)
                .create()
        setMultiselectMode(false)
        return dialog
    }

    private fun setMultiselectMode(multiselectMode: Boolean) {
        adapter!!.setMultiselectMode(multiselectMode)
        viewBinding!!.toolbar.getMenu().findItem(R.id.action_copy)!!.setVisible(multiselectMode)
        viewBinding!!.toolbar.getMenu().findItem(R.id.action_cancel_copy)!!.setVisible(multiselectMode)
        viewBinding!!.toolbar.getMenu().findItem(R.id.action_select_all)!!.setVisible(multiselectMode)
        viewBinding!!.toolbar.getMenu().findItem(R.id.action_refresh)!!.setVisible(!multiselectMode)
        viewBinding!!.followAudioCheckbox.setChecked(!multiselectMode)
    }

    private fun copySelectedText() {
        val selectedText = adapter!!.getSelectedText()
        val clipboardManager = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(getString(R.string.transcript), selectedText))
        }
        if (Build.VERSION.SDK_INT <= 32) {
            EventBus.getDefault().post(MessageEvent(getString(R.string.copied_to_clipboard)))
        }
    }

    override fun onTranscriptClicked(pos: Int, segment: TranscriptSegment) {
        if (adapter!!.isMultiselectMode()) {
            adapter!!.toggleSelection(pos)
        } else {
            val startTime = segment.getStartTime()
            val endTime = segment.getEndTime()

            scrollToPosition(pos)
            PlaybackController.bindToMedia3Service(requireActivity(), Consumer { controller ->
                if (!(controller.getCurrentPosition() >= startTime
                                && controller.getCurrentPosition() <= endTime)) {
                    controller.seekTo(startTime)
                } else if (controller.isPlaying()) {
                    controller.pause()
                } else {
                    controller.play()
                }
            })
            adapter!!.notifyItemChanged(pos)
            viewBinding!!.followAudioCheckbox.setChecked(true)
        }
    }

    override fun onTranscriptLongClicked(position: Int, seg: TranscriptSegment) {
        if (!adapter!!.isMultiselectMode()) {
            setMultiselectMode(true)
            adapter!!.toggleSelection(position)
        }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        loadMediaInfo(false)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusEvent(event: PlayerStatusEvent) {
        loadMediaInfo(false)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        if (transcript == null) {
            return
        }
        val pos = transcript!!.findSegmentIndexBefore(event.getPosition().toLong())
        scrollToPosition(pos)
    }

    private fun loadMediaInfo(forceRefresh: Boolean) {
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Maybe.create<Playable>({ emitter ->
            val media = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId())
            if (media is FeedMedia) {
                this.media = media

                transcript = TranscriptUtils.loadTranscript(media, forceRefresh)
                doInitialScroll = true
                media.setTranscript(transcript)
                emitter.onSuccess(media)
            } else {
                emitter.onComplete()
            }
        })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ media -> onMediaChanged(media) },
                        { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    private fun onMediaChanged(media: Playable) {
        if (media !is FeedMedia) {
            return
        }
        this.media = media

        if (!media.hasTranscript()) {
            dismiss()
            Toast.makeText(getContext(), R.string.no_transcript_label, Toast.LENGTH_LONG).show()
            return
        }

        viewBinding!!.progLoading.setVisibility(View.GONE)
        adapter!!.setMedia(media)
    }

    fun scrollToPosition(pos: Int) {
        if (pos <= 0) {
            return
        }
        if (!viewBinding!!.followAudioCheckbox.isChecked() && !doInitialScroll) {
            return
        }
        doInitialScroll = false

        val quickScroll = Math.abs(layoutManager!!.findFirstVisibleItemPosition() - pos) > 5
        if (layoutManager!!.findFirstVisibleItemPosition() < pos - 1
                && !viewBinding!!.transcriptList.canScrollVertically(1)) {
            return
        }
        if (quickScroll) {
            viewBinding!!.transcriptList.scrollToPosition(pos - 1)
            // Additionally, smooth scroll, so that currently active segment is on top of screen
        }
        val smoothScroller = object : LinearSmoothScroller(getContext()) {
            override fun getVerticalSnapPreference(): Int {
                return LinearSmoothScroller.SNAP_TO_START
            }

            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                return (if (quickScroll) 200 else 1000) / displayMetrics.densityDpi.toFloat()
            }
        }
        smoothScroller.setTargetPosition(pos - 1)
        layoutManager!!.startSmoothScroll(smoothScroller)
    }

    override fun onStop() {
        super.onStop()
        if (disposable != null) {
            disposable!!.dispose()
        }
        EventBus.getDefault().unregister(this)
    }

    private fun onMenuItemClick(item: MenuItem): Boolean {
        val id = item.getItemId()
        if (id == R.id.action_refresh) {
            viewBinding!!.progLoading.setVisibility(View.VISIBLE)
            loadMediaInfo(true)
            return true
        } else if (id == R.id.action_copy) {
            copySelectedText()
            setMultiselectMode(false)
            return true
        } else if (id == R.id.action_cancel_copy) {
            setMultiselectMode(false)
            return true
        } else if (id == R.id.action_select_all) {
            adapter!!.selectAll()
            return true
        }
        return false
    }

    companion object {
        const val TAG = "TranscriptFragment"
    }
}
