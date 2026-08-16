package de.danoeh.antennapod.ui.screen.home.sections

import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.event.EpisodeDownloadEvent
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.event.QueueEvent
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.ui.MenuItemUtils
import de.danoeh.antennapod.ui.episodeslist.HorizontalItemListAdapter
import de.danoeh.antennapod.ui.episodeslist.HorizontalItemViewHolder
import de.danoeh.antennapod.ui.screen.home.HomeSection
import de.danoeh.antennapod.ui.screen.queue.QueueFragment
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.ArrayList

class QueueSection : HomeSection() {
    private var listAdapter: HorizontalItemListAdapter? = null
    private var disposable: Disposable? = null
    private var queue: List<FeedItem> = ArrayList()

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        listAdapter = object : HorizontalItemListAdapter(getActivity() as MainActivity) {
            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(menu, v, menuInfo)
                MenuItemUtils.setOnClickListeners(menu, this@QueueSection::onContextItemSelected)
            }
        }
        listAdapter!!.setDummyViews(NUM_EPISODES)
        viewBinding!!.recyclerView.setLayoutManager(
                LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false))
        viewBinding!!.recyclerView.setAdapter(listAdapter)
        val paddingHorizontal = (12 * getResources().getDisplayMetrics().density).toInt()
        viewBinding!!.recyclerView.setPadding(paddingHorizontal, 0, paddingHorizontal, 0)
        viewBinding!!.emptyLabel.setText(R.string.home_continue_empty_text)
        return view
    }

    override fun onStart() {
        super.onStart()
        loadItems()
    }

    override fun onStop() {
        super.onStop()
        if (disposable != null) {
            disposable!!.dispose()
        }
    }

    override fun handleMoreClick() {
        (requireActivity() as MainActivity).loadChildFragment(QueueFragment())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onQueueChanged(event: QueueEvent) {
        loadItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusChanged(event: PlayerStatusEvent) {
        loadItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]")
        if (queue == null) {
            return
        }
        for (i in 0 until event.items.size) {
            val item = event.items.get(i)
            val pos = FeedItemEvent.indexOfItemWithId(queue, item.getId())
            if (pos >= 0) {
                (queue as ArrayList<FeedItem>).removeAt(pos)
                (queue as ArrayList<FeedItem>).add(pos, item)
                listAdapter!!.notifyItemChangedCompat(pos)
            }
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: EpisodeDownloadEvent) {
        for (downloadUrl in event.getUrls()) {
            val pos = EpisodeDownloadEvent.indexOfItemWithDownloadUrl(queue, downloadUrl)
            if (pos >= 0) {
                listAdapter!!.notifyItemChangedCompat(pos)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        if (listAdapter == null) {
            return
        }
        var foundCurrentlyPlayingItem = false
        var currentlyPlayingItemIsFirst = true
        for (i in 0 until listAdapter!!.getItemCount()) {
            val holder = viewBinding!!.recyclerView.findViewHolderForAdapterPosition(i) as HorizontalItemViewHolder?
            if (holder == null) {
                continue
            }
            if (holder.isCurrentlyPlayingItem()) {
                holder.notifyPlaybackPositionUpdated(event)
                foundCurrentlyPlayingItem = true
                currentlyPlayingItemIsFirst = i == 0
                break
            }
        }
        if (!foundCurrentlyPlayingItem || !currentlyPlayingItemIsFirst) {
            loadItems()
        }
    }

    override fun getSectionTitle(): String {
        return getString(R.string.home_continue_title)
    }

    override fun getMoreLinkTitle(): String {
        return getString(R.string.queue_label)
    }

    private fun loadItems() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Observable.fromCallable { DBReader.getPausedQueue(NUM_EPISODES) }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ queue ->
                    this.queue = queue
                    listAdapter!!.setDummyViews(0)
                    listAdapter!!.updateData(queue)
                    viewBinding!!.emptyLabel.setVisibility(if (queue.isEmpty()) View.VISIBLE else View.GONE)
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    companion object {
        const val TAG = "QueueSection"
        private const val NUM_EPISODES = 8
    }
}
