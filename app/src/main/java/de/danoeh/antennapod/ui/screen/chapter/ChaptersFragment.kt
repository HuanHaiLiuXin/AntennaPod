package de.danoeh.antennapod.ui.screen.chapter

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.playback.service.PlaybackController
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.ui.chapters.ChapterUtils
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class ChaptersFragment : AppCompatDialogFragment() {
    companion object {
        const val TAG = "ChaptersFragment"
    }

    private var adapter: ChaptersListAdapter? = null
    private var disposable: Disposable? = null
    private var focusedChapter = -1
    private var media: Playable? = null
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var progressBar: ProgressBar

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.chapters_label))
                .setView(onCreateView(getLayoutInflater()))
                .setPositiveButton(getString(R.string.close_label), null) //dismisses
                .setNeutralButton(getString(R.string.refresh_label), null)
                .create()
        dialog.show()
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setVisibility(View.INVISIBLE)
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
            progressBar.setVisibility(View.VISIBLE)
            loadMediaInfo(true)
        }

        return dialog
    }

    fun onCreateView(inflater: LayoutInflater): View {
        val root = inflater.inflate(R.layout.simple_list_fragment, null, false)
        root.findViewById<View>(R.id.toolbar).setVisibility(View.GONE)
        val recyclerView = root.findViewById<RecyclerView>(R.id.recyclerView)
        progressBar = root.findViewById(R.id.progLoading)
        layoutManager = LinearLayoutManager(getActivity())
        recyclerView.setLayoutManager(layoutManager)
        recyclerView.addItemDecoration(DividerItemDecoration(recyclerView.getContext(),
                layoutManager.getOrientation()))

        adapter = ChaptersListAdapter(getActivity()!!, ChaptersListAdapter.Callback { pos ->
            val chapter = adapter!!.getItem(pos)
            PlaybackController.bindToMedia3Service(getActivity()!!) { controller ->
                if (!controller.isPlaying()) {
                    controller.play()
                }
                controller.seekTo(chapter!!.getStart())
            }
            updateChapterSelection(pos, true)
        })
        recyclerView.setAdapter(adapter!!)

        progressBar.setVisibility(View.VISIBLE)

        val wrapHeight = CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT, CoordinatorLayout.LayoutParams.WRAP_CONTENT)
        recyclerView.setLayoutParams(wrapHeight)

        return root
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        loadMediaInfo(false)
    }

    override fun onStop() {
        super.onStop()

        if (disposable != null) {
            disposable!!.dispose()
        }
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusEvent(event: PlayerStatusEvent) {
        loadMediaInfo(false)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        updateChapterSelection(getCurrentChapter(media, event.getPosition()), false)
        adapter!!.notifyTimeChanged(event.getPosition().toLong())
    }

    private fun getCurrentChapter(media: Playable?, position: Int): Int {
        if (media == null) {
            return -1
        }
        return Chapter.getAfterPosition(media.getChapters(), position)
    }

    private fun loadMediaInfo(forceRefresh: Boolean) {
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Maybe.create<Playable> { emitter ->
            val media = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId())
            if (media != null) {
                ChapterUtils.loadChapters(media, getContext()!!, forceRefresh)
                emitter.onSuccess(media)
            } else {
                emitter.onComplete()
            }
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ media -> onMediaChanged(media) },
                        { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    private fun onMediaChanged(media: Playable) {
        this.media = media
        focusedChapter = -1
        if (adapter == null) {
            return
        }
        if (media.getChapters() != null && media.getChapters()!!.size == 0) {
            dismiss()
            Toast.makeText(getContext(), R.string.no_chapters_label, Toast.LENGTH_LONG).show()
        } else {
            progressBar.setVisibility(View.GONE)
        }
        adapter!!.setMedia(media)
        (getDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEUTRAL).setVisibility(View.INVISIBLE)
        if (media is FeedMedia && media.getItem() != null
                && !TextUtils.isEmpty(media.getItem()!!.getPodcastIndexChapterUrl())) {
            (getDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEUTRAL).setVisibility(View.VISIBLE)
        }
        val positionOfCurrentChapter = getCurrentChapter(media, media.getPosition())
        updateChapterSelection(positionOfCurrentChapter, true)
    }

    private fun updateChapterSelection(position: Int, scrollTo: Boolean) {
        if (adapter == null) {
            return
        }

        if (position != -1 && focusedChapter != position) {
            focusedChapter = position
            adapter!!.notifyChapterChanged(focusedChapter)
            if (scrollTo && (layoutManager.findFirstCompletelyVisibleItemPosition() >= position
                            || layoutManager.findLastCompletelyVisibleItemPosition() <= position)) {
                layoutManager.scrollToPositionWithOffset(position, 100)
            }
        }
    }
}
