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
import de.danoeh.antennapod.event.DownloadLogEvent
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.MenuItemUtils
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemListAdapter
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemViewHolder
import de.danoeh.antennapod.ui.screen.download.CompletedDownloadsFragment
import de.danoeh.antennapod.ui.screen.home.HomeSection
import de.danoeh.antennapod.ui.swipeactions.SwipeActions
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class DownloadsSection : HomeSection() {
    private var adapter: EpisodeItemListAdapter? = null
    private var items: List<FeedItem>? = null
    private var disposable: Disposable? = null

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        viewBinding!!.recyclerView.setPadding(0, 0, 0, 0)
        viewBinding!!.recyclerView.setOverScrollMode(RecyclerView.OVER_SCROLL_NEVER)
        viewBinding!!.recyclerView.setLayoutManager(LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false))
        adapter = object : EpisodeItemListAdapter(requireActivity()) {
            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(menu, v, menuInfo)
                MenuItemUtils.setOnClickListeners(menu, this@DownloadsSection::onContextItemSelected)
            }
        }
        adapter!!.setDummyViews(NUM_EPISODES)
        viewBinding!!.recyclerView.setAdapter(adapter)
        viewBinding!!.emptyLabel.setText(R.string.home_downloads_empty_text)

        val swipeActions = SwipeActions(this, CompletedDownloadsFragment.TAG)
        swipeActions.attachTo(viewBinding!!.recyclerView)
        swipeActions.setFilter(FeedItemFilter(FeedItemFilter.DOWNLOADED))
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
        (requireActivity() as MainActivity).loadChildFragment(CompletedDownloadsFragment())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        loadItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        if (adapter == null) {
            return
        }
        for (i in 0 until adapter!!.getItemCount()) {
            val holder = viewBinding!!.recyclerView.findViewHolderForAdapterPosition(i) as EpisodeItemViewHolder?
            if (holder != null && holder.isPlayingItem()) {
                holder.notifyPlaybackPositionUpdated(event)
                break
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownloadLogChanged(event: DownloadLogEvent) {
        loadItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusChanged(event: PlayerStatusEvent) {
        loadItems()
    }

    override fun getSectionTitle(): String {
        return getString(R.string.home_downloads_title)
    }

    override fun getMoreLinkTitle(): String {
        return getString(R.string.downloads_label)
    }

    private fun loadItems() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        val sortOrder: SortOrder = UserPreferences.getDownloadsSortedOrder()
        disposable = Observable.fromCallable { DBReader.getEpisodes(0, NUM_EPISODES, FILTER_DOWNLOADED, sortOrder) }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ downloads ->
                    items = downloads
                    adapter!!.setDummyViews(0)
                    adapter!!.updateItems(items!!)
                    viewBinding!!.emptyLabel.setVisibility(if (items!!.isEmpty()) View.VISIBLE else View.GONE)
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    companion object {
        const val TAG = "DownloadsSection"
        private const val NUM_EPISODES = 2
        private val FILTER_DOWNLOADED = FeedItemFilter(
                FeedItemFilter.DOWNLOADED, FeedItemFilter.INCLUDE_ALL_FEED_STATES)
    }
}
