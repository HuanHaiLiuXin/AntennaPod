package de.danoeh.antennapod.ui.screen

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.appcompat.widget.SearchView
import com.google.android.material.appbar.MaterialToolbar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.ui.common.Keyboard
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemListAdapter
import de.danoeh.antennapod.ui.SelectableAdapter
import de.danoeh.antennapod.ui.screen.subscriptions.HorizontalFeedListAdapter
import de.danoeh.antennapod.ui.MenuItemUtils
import de.danoeh.antennapod.event.EpisodeDownloadEvent
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.ui.episodeslist.EpisodeMultiSelectActionHandler
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.ui.episodeslist.FeedItemMenuHandler
import de.danoeh.antennapod.net.discovery.CombinedSearcher
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter
import de.danoeh.antennapod.ui.discovery.OnlineSearchFragment
import de.danoeh.antennapod.ui.common.EmptyViewHandler
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemListRecyclerView
import de.danoeh.antennapod.ui.view.FloatingSelectMenu
import de.danoeh.antennapod.ui.common.LiftOnScrollListener
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemViewHolder
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

import java.util.Collections
import de.danoeh.antennapod.ui.screen.subscriptions.FeedMenuHandler
import de.danoeh.antennapod.event.FeedListUpdateEvent

/**
 * Performs a search operation on all feeds or one specific feed and displays the search result.
 */
class SearchFragment : Fragment(), SelectableAdapter.OnSelectModeListener {
    companion object {
        private const val TAG = "SearchFragment"
        private const val ARG_QUERY = "query"
        private const val ARG_FEED = "feed"
        private const val ARG_FEED_NAME = "feedName"
        private const val ARG_FILTER = "filter"
        private const val SEARCH_DEBOUNCE_INTERVAL = 1500

        /**
         * Create a new SearchFragment that searches all feeds.
         */
        @JvmStatic
        fun newInstance(): SearchFragment {
            val fragment = SearchFragment()
            val args = Bundle()
            args.putLong(ARG_FEED, 0)
            args.putSerializable(ARG_FILTER, FeedItemFilter.unfiltered())
            fragment.setArguments(args)
            return fragment
        }

        /**
         * Create a new SearchFragment that searches all feeds with pre-defined query.
         */
        @JvmStatic
        fun newInstance(query: String): SearchFragment {
            val fragment = newInstance()
            fragment.getArguments()!!.putString(ARG_QUERY, query)
            return fragment
        }

        /**
         * Create a new SearchFragment that searches one specific feed.
         */
        @JvmStatic
        fun newInstance(feed: Long, feedTitle: String?): SearchFragment {
            val fragment = newInstance()
            fragment.getArguments()!!.putLong(ARG_FEED, feed)
            fragment.getArguments()!!.putString(ARG_FEED_NAME, feedTitle)
            return fragment
        }

        @JvmStatic
        fun newInstance(filter: FeedItemFilter): SearchFragment {
            val fragment = newInstance()
            fragment.getArguments()!!.putSerializable(ARG_FILTER, filter)
            return fragment
        }
    }

    private var adapter: EpisodeItemListAdapter? = null
    private var adapterFeeds: HorizontalFeedListAdapter? = null
    private var disposableFeeds: Disposable? = null
    private var disposableEpisodes: Disposable? = null
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyViewHandler: EmptyViewHandler
    private lateinit var recyclerView: EpisodeItemListRecyclerView
    private var results: MutableList<FeedItem>? = null
    private lateinit var chipGroup: ChipGroup
    private lateinit var searchView: SearchView
    private lateinit var floatingSelectMenu: FloatingSelectMenu
    private lateinit var automaticSearchDebouncer: Handler
    private var lastQueryChange = 0L
    private var isOtherViewInFoucus = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        automaticSearchDebouncer = Handler(Looper.getMainLooper())
    }

    override fun onStop() {
        super.onStop()
        if (disposableFeeds != null) {
            disposableFeeds!!.dispose()
        }
        if (disposableEpisodes != null) {
            disposableEpisodes!!.dispose()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        val layout = inflater.inflate(R.layout.search_fragment, container, false)
        setupToolbar(layout.findViewById<MaterialToolbar>(R.id.toolbar))
        progressBar = layout.findViewById(R.id.progressBar)
        recyclerView = layout.findViewById(R.id.recyclerView)
        floatingSelectMenu = layout.findViewById(R.id.floatingSelectMenu)
        registerForContextMenu(recyclerView)
        adapter = object : EpisodeItemListAdapter(getActivity()!!) {
            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(menu, v, menuInfo)
                if (!inActionMode()) {
                    menu.findItem(R.id.multi_select).setVisible(true)
                }
                MenuItemUtils.setOnClickListeners(menu) { item -> this@SearchFragment.onContextItemSelected(item) }
            }

            override fun onSelectedItemsUpdated() {
                super.onSelectedItemsUpdated()
                FeedItemMenuHandler.onPrepareMenu(floatingSelectMenu.getMenu(), getSelectedItems(),
                        R.id.remove_inbox_item)
                floatingSelectMenu.updateItemVisibility()
            }
        }
        adapter!!.setOnSelectModeListener(this)
        recyclerView.setAdapter(adapter!!)
        recyclerView.addOnScrollListener(LiftOnScrollListener(layout.findViewById(R.id.appbar)))

        val recyclerViewFeeds = layout.findViewById<RecyclerView>(R.id.recyclerViewFeeds)
        val layoutManagerFeeds = LinearLayoutManager(getActivity())
        layoutManagerFeeds.setOrientation(RecyclerView.HORIZONTAL)
        recyclerViewFeeds.setLayoutManager(layoutManagerFeeds)
        adapterFeeds = object : HorizontalFeedListAdapter(getActivity() as MainActivity) {
            override fun onCreateContextMenu(contextMenu: ContextMenu, view: View,
                                             contextMenuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(contextMenu, view, contextMenuInfo)
                MenuItemUtils.setOnClickListeners(contextMenu) { item -> this@SearchFragment.onContextItemSelected(item) }
            }

            override fun onClick(feed: Feed) {
                if (adapter != null && adapter!!.inActionMode()) {
                    adapter!!.endSelectMode()
                }
                super.onClick(feed)
            }
        }
        recyclerViewFeeds.setAdapter(adapterFeeds!!)

        emptyViewHandler = EmptyViewHandler(getContext()!!)
        emptyViewHandler.attachToRecyclerView(recyclerView)
        emptyViewHandler.setIcon(R.drawable.ic_search)
        emptyViewHandler.setTitle(R.string.type_to_search)
        EventBus.getDefault().register(this)

        chipGroup = layout.findViewById(R.id.filter_chips)
        updateChipVisibility()
        if (getArguments()!!.getString(ARG_QUERY, null) != null) {
            search()
        }
        searchView.setOnQueryTextFocusChangeListener { view, hasFocus ->
            if (hasFocus && !isOtherViewInFoucus) {
                Keyboard.show(getContext()!!, view.findFocus())
            }
        }
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    Keyboard.hide(getActivity()!!)
                }
            }
        })
        floatingSelectMenu.inflate(R.menu.episodes_apply_action_speeddial)
        floatingSelectMenu.setOnMenuItemClickListener { menuItem ->
            if (adapter!!.getSelectedCount() == 0) {
                EventBus.getDefault().post(MessageEvent(getString(R.string.no_items_selected_message)))
                return@setOnMenuItemClickListener false
            }
            EpisodeMultiSelectActionHandler(getActivity()!!, menuItem.getItemId())
                    .handleAction(adapter!!.getSelectedItems())
            adapter!!.endSelectMode()
            return@setOnMenuItemClickListener true
        }

        return layout
    }

    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.getDefault().unregister(this)
    }

    private fun setupToolbar(toolbar: MaterialToolbar) {
        toolbar.setTitle(R.string.search_label)
        toolbar.setNavigationOnClickListener { getParentFragmentManager().popBackStack() }
        toolbar.inflateMenu(R.menu.search)

        val item = toolbar.getMenu().findItem(R.id.action_search)
        item.expandActionView()
        searchView = item.getActionView() as SearchView
        searchView.setQueryHint(getString(R.string.search_label))
        searchView.setQuery(getArguments()!!.getString(ARG_QUERY), true)
        searchView.requestFocus()
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(s: String): Boolean {
                searchView.clearFocus()
                searchWithProgressBar()
                return true
            }

            override fun onQueryTextChange(s: String): Boolean {
                automaticSearchDebouncer.removeCallbacksAndMessages(null)
                if (s.isEmpty() || s.endsWith(" ") || (lastQueryChange != 0L
                                && System.currentTimeMillis() > lastQueryChange + SEARCH_DEBOUNCE_INTERVAL)) {
                    search()
                } else {
                    automaticSearchDebouncer.postDelayed({
                        search()
                        lastQueryChange = 0 // Don't search instantly with first symbol after some pause
                    }, (SEARCH_DEBOUNCE_INTERVAL / 2).toLong())
                }
                lastQueryChange = System.currentTimeMillis()
                return false
            }
        })
        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                getParentFragmentManager().popBackStack()
                return true
            }
        })
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val selectedFeedItem = adapterFeeds!!.getLongPressedItem()
        if (selectedFeedItem != null
                && FeedMenuHandler.onMenuItemClicked(this, item.getItemId(), selectedFeedItem)) {
            return true
        }
        val selectedItem = adapter!!.getLongPressedItem()
        if (selectedItem != null) {
            if (adapter!!.onContextItemSelected(item)) {
                return true
            }
            if (FeedItemMenuHandler.onMenuItemClicked(this, item.getItemId(), selectedItem)) {
                return true
            }
        }
        return super.onContextItemSelected(item)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent) {
        search()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]")
        if (event.unreadStatusChanged && event.items.isEmpty()) {
            search()
            return
        }
        if (results == null) {
            return
        } else if (adapter == null) {
            search()
            return
        }
        val currentResults = results!!
        for (item in event.items) {
            val pos = FeedItemEvent.indexOfItemWithId(currentResults, item.getId())
            if (pos >= 0) {
                currentResults.removeAt(pos)
                currentResults.add(pos, item)
                adapter!!.notifyItemChangedCompat(pos)
            }
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: EpisodeDownloadEvent) {
        if (results == null) {
            return
        }
        val currentResults = results!!
        for (downloadUrl in event.getUrls()) {
            val pos = EpisodeDownloadEvent.indexOfItemWithDownloadUrl(currentResults, downloadUrl)
            if (pos >= 0) {
                adapter!!.notifyItemChangedCompat(pos)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        if (adapter != null) {
            for (i in 0 until adapter!!.getItemCount()) {
                val holder = recyclerView.findViewHolderForAdapterPosition(i) as EpisodeItemViewHolder?
                if (holder != null && holder.isPlayingItem()) {
                    holder.notifyPlaybackPositionUpdated(event)
                    break
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusChanged(event: PlayerStatusEvent) {
        search()
    }

    private fun searchWithProgressBar() {
        progressBar.setVisibility(View.VISIBLE)
        emptyViewHandler.hide()
        search()
    }

    private fun updateChipVisibility() {
        chipGroup.removeAllViews()
        val filter = getArguments()!!.getSerializable(ARG_FILTER) as FeedItemFilter?
        if (filter != null && filter.showQueued) {
            addChip(getString(R.string.queue_label)) {
                getArguments()!!.putSerializable(ARG_FILTER, filter.without(FeedItemFilter.QUEUED))
                searchWithProgressBar()
            }
        }
        if (filter != null && filter.includeArchived && !filter.includeSubscribed && !filter.includeNotSubscribed) {
            addChip(getString(R.string.archive_feed_label_noun)) {
                getArguments()!!.putSerializable(ARG_FILTER, filter.without(FeedItemFilter.INCLUDE_ARCHIVED))
                searchWithProgressBar()
            }
        }
        if (getArguments()!!.getLong(ARG_FEED, 0) != 0L) {
            addChip(getArguments()!!.getString(ARG_FEED_NAME, "")) {
                getArguments()!!.putLong(ARG_FEED, 0)
                searchWithProgressBar()
            }
        }
        chipGroup.setVisibility(if (chipGroup.getChildCount() > 0) View.VISIBLE else View.GONE)
    }

    private fun addChip(text: String?, closeListener: View.OnClickListener) {
        val chip = getLayoutInflater().inflate(R.layout.item_tag_chip, chipGroup, false) as Chip
        chip.setText(text)
        chip.setCheckable(false)
        chip.setCloseIconVisible(true)
        chip.setOnCloseIconClickListener(closeListener)
        chipGroup.addView(chip)
    }

    private fun search() {
        if (disposableFeeds != null) {
            disposableFeeds!!.dispose()
        }
        if (disposableEpisodes != null) {
            disposableEpisodes!!.dispose()
        }
        val feed = getArguments()!!.getLong(ARG_FEED, 0)
        val activeFilter = (getArguments()!!.getSerializable(ARG_FILTER) as FeedItemFilter?)
                ?: FeedItemFilter.unfiltered()
        for (value in activeFilter.getValues()) {
            if (!value!!.isEmpty()
                    && value != FeedItemFilter.QUEUED
                    && value != FeedItemFilter.INCLUDE_ARCHIVED
                    && value != FeedItemFilter.INCLUDE_SUBSCRIBED
                    && value != FeedItemFilter.INCLUDE_NOT_SUBSCRIBED) {
                throw IllegalArgumentException("SearchFragment does not support filter: " + value)
            }
        }
        val hasEpisodeFilter = activeFilter.showQueued || activeFilter.showPlayed || activeFilter.showUnplayed
                || activeFilter.showNew || activeFilter.showDownloaded || activeFilter.showIsFavorite
                || activeFilter.showInHistory || activeFilter.showPaused
        val isSearchingFeed = feed != 0L
        updateChipVisibility()
        adapterFeeds!!.setEndButton(R.string.search_online,
                if (isSearchingFeed || hasEpisodeFilter) null else Runnable { searchOnline() })

        val query = searchView.getQuery().toString()
        if (query.isEmpty()) {
            emptyViewHandler.setTitle(R.string.type_to_search)
            return
        }
        if (feed != 0L || hasEpisodeFilter) {
            // Search within a feed or with episode-level filters: don't show subscription results
            adapterFeeds!!.updateData(Collections.emptyList())
        } else {
            disposableFeeds = Observable.fromCallable { DBReader.searchFeeds(query, activeFilter) }
                    .subscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ results ->
                        progressBar.setVisibility(View.GONE)
                        adapterFeeds!!.updateData(results)
                        emptyViewHandler.setTitle(getString(R.string.no_results_for_query, query))
                    }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
        }
        disposableEpisodes = Observable.fromCallable { DBReader.searchFeedItems(feed, query, activeFilter) }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ results ->
                    progressBar.setVisibility(View.GONE)
                    this.results = results as MutableList<FeedItem>
                    adapter!!.updateItems(results)
                    emptyViewHandler.setTitle(getString(R.string.no_results_for_query, searchView.getQuery()))
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    private fun searchOnline() {
        if (adapter != null && adapter!!.inActionMode()) {
            adapter!!.endSelectMode()
        }
        searchView.clearFocus()
        Keyboard.hide(getActivity()!!)
        val query = searchView.getQuery().toString()
        if (query.matches(Regex("http[s]?://.*"))) {
            startActivity(OnlineFeedviewActivityStarter(getContext()!!, query).getIntent())
            return
        }
        (getActivity() as MainActivity).loadChildFragment(
                OnlineSearchFragment.newInstance(CombinedSearcher::class.java, query))
    }

    override fun onStartSelectMode() {
        searchViewFocusOff()
        floatingSelectMenu.setVisibility(View.VISIBLE)
        recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
                recyclerView.getPaddingRight(),
                getResources().getDimension(R.dimen.floating_select_menu_height).toInt())
    }

    override fun onEndSelectMode() {
        floatingSelectMenu.setVisibility(View.GONE)
        recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
                recyclerView.getPaddingRight(), 0)
        searchViewFocusOn()
    }

    private fun searchViewFocusOff() {
        isOtherViewInFoucus = true
        searchView.clearFocus()
    }

    private fun searchViewFocusOn() {
        isOtherViewInFoucus = false
        searchView.requestFocus()
    }
}
