package de.danoeh.antennapod.ui.screen.home.sections

import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.util.Pair
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.event.EpisodeDownloadEvent
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.event.FeedListUpdateEvent
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.MenuItemUtils
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemListAdapter
import de.danoeh.antennapod.ui.screen.InboxFragment
import de.danoeh.antennapod.ui.screen.home.HomeSection
import de.danoeh.antennapod.ui.swipeactions.SwipeActions
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.ArrayList
import java.util.Locale

class InboxSection : HomeSection() {
    private var adapter: EpisodeItemListAdapter? = null
    private var items: List<FeedItem> = ArrayList()
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
                MenuItemUtils.setOnClickListeners(menu, this@InboxSection::onContextItemSelected)
            }
        }
        adapter!!.setDummyViews(NUM_EPISODES)
        viewBinding!!.recyclerView.setAdapter(adapter)
        viewBinding!!.emptyLabel.setText(R.string.home_new_empty_text)

        val swipeActions = SwipeActions(this, InboxFragment.TAG)
        swipeActions.attachTo(viewBinding!!.recyclerView)
        swipeActions.setFilter(FeedItemFilter(FeedItemFilter.NEW))
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
        (requireActivity() as MainActivity).loadChildFragment(InboxFragment())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        loadItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent) {
        loadItems()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: EpisodeDownloadEvent) {
        for (downloadUrl in event.getUrls()) {
            val pos = EpisodeDownloadEvent.indexOfItemWithDownloadUrl(items, downloadUrl)
            if (pos >= 0) {
                adapter!!.notifyItemChangedCompat(pos)
            }
        }
    }

    override fun getSectionTitle(): String {
        return getString(R.string.home_new_title)
    }

    override fun getMoreLinkTitle(): String {
        return getString(R.string.inbox_label)
    }

    private fun loadItems() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Observable.fromCallable {
            Pair(DBReader.getEpisodes(0, NUM_EPISODES,
                            FeedItemFilter(FeedItemFilter.NEW), UserPreferences.getInboxSortedOrder()),
                    DBReader.getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.NEW)))
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ data ->
                    items = data.first
                    adapter!!.setDummyViews(0)
                    adapter!!.updateItems(items)
                    viewBinding!!.emptyLabel.setVisibility(if (items.isEmpty()) View.VISIBLE else View.GONE)
                    viewBinding!!.numNewItemsLabel.setVisibility(if (!items.isEmpty()) View.VISIBLE else View.GONE)
                    if (data.second >= 100) {
                        viewBinding!!.numNewItemsLabel.setText(String.format(Locale.getDefault(), "%d+", 99))
                    } else {
                        viewBinding!!.numNewItemsLabel.setText(String.format(Locale.getDefault(), "%d", data.second))
                    }
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    companion object {
        const val TAG = "InboxSection"
        private const val NUM_EPISODES = 2
    }
}
