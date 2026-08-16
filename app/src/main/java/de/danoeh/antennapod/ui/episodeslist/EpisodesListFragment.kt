package de.danoeh.antennapod.ui.episodeslist

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView

import androidx.appcompat.widget.Toolbar
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

import com.google.android.material.appbar.MaterialToolbar

import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.ui.screen.SearchFragment
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager
import de.danoeh.antennapod.ui.view.FloatingSelectMenu
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

import java.util.ArrayList
import java.util.Collections

import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.ui.common.ConfirmationDialog
import de.danoeh.antennapod.ui.MenuItemUtils
import de.danoeh.antennapod.ui.SelectableAdapter
import de.danoeh.antennapod.event.EpisodeDownloadEvent
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.event.FeedListUpdateEvent
import de.danoeh.antennapod.event.FeedUpdateRunningEvent
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.ui.swipeactions.SwipeActions
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.ui.common.EmptyViewHandler
import de.danoeh.antennapod.ui.common.LiftOnScrollListener
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * Shows unread or recently published episodes
 */
abstract class EpisodesListFragment : Fragment(),
        SelectableAdapter.OnSelectModeListener, Toolbar.OnMenuItemClickListener {
    companion object {
        const val TAG = "EpisodesListFragment"
        private const val KEY_UP_ARROW = "up_arrow"
        protected const val EPISODES_PER_PAGE = 150
    }

    protected var page = 1
    protected var isLoadingMore = false
    protected var hasMoreItems = false
    protected var displayUpArrow = false

    protected lateinit var recyclerView: EpisodeItemListRecyclerView
    protected lateinit var listAdapter: EpisodeItemListAdapter
    protected lateinit var emptyView: EmptyViewHandler
    protected lateinit var floatingSelectMenu: FloatingSelectMenu
    protected var toolbar: MaterialToolbar? = null
    protected lateinit var swipeRefreshLayout: SwipeRefreshLayout
    protected lateinit var swipeActions: SwipeActions
    private lateinit var progressBar: ProgressBar
    protected var episodes: List<FeedItem> = ArrayList()
    protected var disposable: Disposable? = null
    protected lateinit var txtvInformation: TextView

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        loadItems()
    }

    override fun onResume() {
        super.onResume()
        registerForContextMenu(recyclerView)
    }

    override fun onPause() {
        super.onPause()
        unregisterForContextMenu(recyclerView)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        if (disposable != null) {
            disposable!!.dispose()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.getItemId()
        if (itemId == R.id.refresh_item) {
            FeedUpdateManager.getInstance()!!.runOnceOrAsk(requireContext())
            return true
        } else if (itemId == R.id.action_search) {
            (getActivity() as MainActivity).loadChildFragment(SearchFragment.newInstance())
            return true
        }
        return false
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "onContextItemSelected() called with: " + "item = [" + item + "]")
        if (!getUserVisibleHint() || !isVisible() || !isMenuVisible()) {
            // The method is called on all fragments in a ViewPager, so this needs to be ignored in invisible ones.
            // Apparently, none of the visibility check method works reliably on its own, so we just use all.
            return false
        } else if (listAdapter.getLongPressedItem() == null) {
            Log.i(TAG, "Selected item or listAdapter was null, ignoring selection")
            return super.onContextItemSelected(item)
        } else if (listAdapter.onContextItemSelected(item)) {
            return true
        }
        val selectedItem = listAdapter.getLongPressedItem()
        return FeedItemMenuHandler.onMenuItemClicked(this, item.getItemId(), selectedItem!!)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        val root = inflater.inflate(R.layout.episodes_list_fragment, container, false)
        txtvInformation = root.findViewById(R.id.txtvInformation)
        toolbar = root.findViewById(R.id.toolbar)
        toolbar!!.setOnMenuItemClickListener(this)
        toolbar!!.setOnLongClickListener {
            recyclerView.scrollToPosition(5)
            recyclerView.post { recyclerView.smoothScrollToPosition(0) }
            false
        }
        displayUpArrow = getParentFragmentManager().getBackStackEntryCount() != 0
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        }
        (getActivity() as MainActivity).setupToolbarToggle(toolbar!!, displayUpArrow)

        recyclerView = root.findViewById(R.id.recyclerView)
        setupLoadMoreScrollListener()
        recyclerView.addOnScrollListener(LiftOnScrollListener(root.findViewById(R.id.appbar)))

        swipeActions = SwipeActions(this, getFragmentTag()).attachTo(recyclerView)
        swipeActions.setFilter(getFilter())

        val animator = recyclerView.getItemAnimator()
        if (animator is SimpleItemAnimator) {
            animator.setSupportsChangeAnimations(false)
        }

        swipeRefreshLayout = root.findViewById(R.id.swipeRefresh)
        swipeRefreshLayout.setDistanceToTriggerSync(getResources().getInteger(R.integer.swipe_refresh_distance))
        swipeRefreshLayout.setOnRefreshListener {
            FeedUpdateManager.getInstance()!!.runOnceOrAsk(requireContext())
        }

        listAdapter = object : EpisodeItemListAdapter(requireActivity()) {
            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(menu, v, menuInfo)
                if (!inActionMode()) {
                    menu.findItem(R.id.multi_select).setVisible(true)
                }
                MenuItemUtils.setOnClickListeners(menu) { item -> this@EpisodesListFragment.onContextItemSelected(item) }
            }

            override fun onSelectedItemsUpdated() {
                super.onSelectedItemsUpdated()
                FeedItemMenuHandler.onPrepareMenu(floatingSelectMenu.getMenu(), getSelectedItems())
                floatingSelectMenu.updateItemVisibility()
            }
        }
        listAdapter.setOnSelectModeListener(this)
        recyclerView.setAdapter(listAdapter)
        progressBar = root.findViewById(R.id.progressBar)
        progressBar.setVisibility(View.VISIBLE)

        emptyView = EmptyViewHandler(requireContext())
        emptyView.attachToRecyclerView(recyclerView)
        emptyView.setIcon(R.drawable.ic_feed)
        emptyView.setTitle(R.string.no_all_episodes_head_label)
        emptyView.setMessage(R.string.no_all_episodes_label)
        emptyView.updateAdapter(listAdapter)
        emptyView.hide()

        floatingSelectMenu = root.findViewById(R.id.floatingSelectMenu)
        floatingSelectMenu.inflate(R.menu.episodes_apply_action_speeddial)
        floatingSelectMenu.setOnMenuItemClickListener { menuItem ->
            if (listAdapter.getSelectedCount() == 0) {
                EventBus.getDefault().post(MessageEvent(getString(R.string.no_items_selected_message)))
                return@setOnMenuItemClickListener false
            }
            var confirmationString = 0
            if (listAdapter.getSelectedItems().size >= 25 || listAdapter.shouldSelectLazyLoadedItems()) {
                // Should ask for confirmation
                if (menuItem.getItemId() == R.id.mark_read_item) {
                    confirmationString = R.string.multi_select_mark_played_confirmation
                } else if (menuItem.getItemId() == R.id.mark_unread_item) {
                    confirmationString = R.string.multi_select_mark_unplayed_confirmation
                }
            }
            if (confirmationString == 0) {
                performMultiSelectAction(menuItem.getItemId())
            } else {
                object : ConfirmationDialog(requireActivity(), R.string.multi_select, confirmationString) {
                    override fun onConfirmButtonPressed(dialog: DialogInterface) {
                        performMultiSelectAction(menuItem.getItemId())
                    }
                }.createNewDialog().show()
            }
            return@setOnMenuItemClickListener true
        }

        return root
    }

    private fun performMultiSelectAction(actionItemId: Int) {
        val handler = EpisodeMultiSelectActionHandler(requireActivity(), actionItemId)
        Completable.fromAction(
                {
                    handler.handleAction(listAdapter.getSelectedItems())
                    if (listAdapter.shouldSelectLazyLoadedItems()) {
                        var applyPage = page + 1
                        var nextPage: List<FeedItem>
                        do {
                            nextPage = loadMoreData(applyPage)
                            handler.handleAction(nextPage)
                            applyPage++
                        } while (nextPage.size == EPISODES_PER_PAGE)
                    }
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ listAdapter.endSelectMode() },
                        { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    private fun setupLoadMoreScrollListener() {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(view: RecyclerView, deltaX: Int, deltaY: Int) {
                super.onScrolled(view, deltaX, deltaY)
                if (!isLoadingMore && hasMoreItems && recyclerView.isScrolledToBottom()) {
                    /* The end of the list has been reached. Load more data. */
                    page++
                    loadMoreItems()
                    isLoadingMore = true
                }
            }
        })
    }

    private fun loadMoreItems() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        isLoadingMore = true
        listAdapter.setDummyViews(1)
        listAdapter.notifyItemInserted(listAdapter.getItemCount() - 1)
        disposable = Observable.fromCallable<List<FeedItem>> { loadMoreData(page) }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { data ->
                            if (data.size < EPISODES_PER_PAGE) {
                                hasMoreItems = false
                            }
                            (episodes as MutableList<FeedItem>).addAll(data)
                            listAdapter.setDummyViews(0)
                            listAdapter.updateItems(episodes)
                            if (listAdapter.shouldSelectLazyLoadedItems()) {
                                listAdapter.setSelected(episodes.size - data.size, episodes.size, true)
                            }
                        }, { error ->
                            listAdapter.setDummyViews(0)
                            listAdapter.updateItems(Collections.emptyList())
                            Log.e(TAG, Log.getStackTraceString(error))
                        }, {
                            // Make sure to not always load 2 pages at once
                            recyclerView.post { isLoadingMore = false }
                        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listAdapter.endSelectMode()
    }

    override fun onStartSelectMode() {
        floatingSelectMenu.setVisibility(View.VISIBLE)
        recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
                recyclerView.getPaddingRight(),
                getResources().getDimension(R.dimen.floating_select_menu_height).toInt())
    }

    override fun onEndSelectMode() {
        floatingSelectMenu.setVisibility(View.GONE)
        recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
                recyclerView.getPaddingRight(), 0)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]")
        if (event.unreadStatusChanged && event.items.isEmpty()) {
            loadItems()
            return
        }
        for (item in event.items) {
            val pos = FeedItemEvent.indexOfItemWithId(episodes, item.getId())
            if (pos >= 0) {
                val list = episodes as MutableList<FeedItem>
                list.removeAt(pos)
                if (getFilter().matches(item)) {
                    list.add(pos, item)
                    listAdapter.notifyItemChangedCompat(pos)
                } else {
                    listAdapter.notifyItemRemoved(pos)
                }
            } else if (getFilter().matches(item)) {
                // Found something new
                loadItems()
                return
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        for (i in 0 until listAdapter.getItemCount()) {
            val holder = recyclerView.findViewHolderForAdapterPosition(i) as EpisodeItemViewHolder?
            if (holder != null && holder.isPlayingItem()) {
                holder.notifyPlaybackPositionUpdated(event)
                break
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onKeyUp(event: KeyEvent) {
        if (!isAdded() || !isVisible() || !isMenuVisible()) {
            return
        }
        when (event.getKeyCode()) {
            KeyEvent.KEYCODE_T -> recyclerView.smoothScrollToPosition(0)
            KeyEvent.KEYCODE_B -> recyclerView.smoothScrollToPosition(listAdapter.getItemCount() - 1)
            else -> Unit
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: EpisodeDownloadEvent) {
        for (downloadUrl in event.getUrls()) {
            val pos = EpisodeDownloadEvent.indexOfItemWithDownloadUrl(episodes, downloadUrl)
            if (pos >= 0) {
                listAdapter.notifyItemChangedCompat(pos)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusChanged(event: PlayerStatusEvent) {
        loadItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent) {
        loadItems()
    }

    protected fun loadItems() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Observable.fromCallable<Pair<List<FeedItem>, Int>> { Pair(loadData(), loadTotalItemCount()) }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { data ->
                            val firstLoaded = episodes.isEmpty()
                            episodes = data.first!!
                            hasMoreItems = !(page == 1 && episodes.size < EPISODES_PER_PAGE)
                            progressBar.setVisibility(View.GONE)
                            listAdapter.setDummyViews(0)
                            listAdapter.updateItems(episodes)
                            listAdapter.setTotalNumberOfItems(data.second!!)
                            if (firstLoaded) {
                                onItemsFirstLoaded()
                            }
                            updateToolbar()
                        }, { error ->
                            listAdapter.setDummyViews(0)
                            listAdapter.updateItems(Collections.emptyList())
                            Log.e(TAG, Log.getStackTraceString(error))
                        })
    }

    protected abstract fun loadData(): List<FeedItem>

    protected abstract fun loadMoreData(page: Int): List<FeedItem>

    protected abstract fun loadTotalItemCount(): Int

    protected abstract fun getFilter(): FeedItemFilter

    protected abstract fun getFragmentTag(): String

    protected open fun updateToolbar() {
    }

    protected open fun onItemsFirstLoaded() {
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedUpdateRunningEvent) {
        swipeRefreshLayout.setRefreshing(event.isFeedUpdateRunning)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }
}
