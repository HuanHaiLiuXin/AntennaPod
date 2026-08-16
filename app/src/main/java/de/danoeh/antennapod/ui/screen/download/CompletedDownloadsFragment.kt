package de.danoeh.antennapod.ui.screen.download

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

import com.google.android.material.appbar.MaterialToolbar
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.event.FeedUpdateRunningEvent
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.ui.common.ConfirmationDialog
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemListAdapter
import de.danoeh.antennapod.ui.SelectableAdapter
import de.danoeh.antennapod.actionbutton.DeleteActionButton
import de.danoeh.antennapod.event.DownloadLogEvent
import de.danoeh.antennapod.ui.MenuItemUtils
import de.danoeh.antennapod.ui.screen.SearchFragment
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.ui.screen.feed.ItemSortDialog
import de.danoeh.antennapod.event.EpisodeDownloadEvent
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.ui.episodeslist.EpisodeMultiSelectActionHandler
import de.danoeh.antennapod.ui.swipeactions.SwipeActions
import de.danoeh.antennapod.ui.episodeslist.FeedItemMenuHandler
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.storage.preferences.UserPreferences
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

import java.util.ArrayList
import java.util.Collections
import java.util.HashSet

/**
 * Displays all completed downloads and provides a button to delete them.
 */
class CompletedDownloadsFragment : Fragment(),
        SelectableAdapter.OnSelectModeListener, Toolbar.OnMenuItemClickListener {
    companion object {
        const val TAG = "DownloadsFragment"
        const val ARG_SHOW_LOGS = "show_logs"
        private const val KEY_UP_ARROW = "up_arrow"
    }

    private var runningDownloads: MutableSet<String> = HashSet()
    private var items: MutableList<FeedItem> = ArrayList()
    private var adapter: CompletedDownloadsListAdapter? = null
    private lateinit var recyclerView: EpisodeItemListRecyclerView
    private var disposable: Disposable? = null
    private lateinit var emptyView: EmptyViewHandler
    private var displayUpArrow = false
    private lateinit var floatingSelectMenu: FloatingSelectMenu
    private lateinit var swipeActions: SwipeActions
    private lateinit var progressBar: ProgressBar
    private var toolbar: MaterialToolbar? = null
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.simple_list_fragment, container, false)
        toolbar = root.findViewById(R.id.toolbar)
        toolbar!!.setTitle(R.string.downloads_label)
        toolbar!!.inflateMenu(R.menu.downloads_completed)
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

        swipeRefreshLayout = root.findViewById(R.id.swipeRefresh)
        swipeRefreshLayout.setDistanceToTriggerSync(getResources().getInteger(R.integer.swipe_refresh_distance))
        swipeRefreshLayout.setOnRefreshListener {
            FeedUpdateManager.getInstance()!!.runOnceOrAsk(requireContext())
        }

        recyclerView = root.findViewById(R.id.recyclerView)
        adapter = CompletedDownloadsListAdapter(getActivity()!!)
        adapter!!.setOnSelectModeListener(this)
        recyclerView.setAdapter(adapter!!)
        recyclerView.addOnScrollListener(LiftOnScrollListener(root.findViewById(R.id.appbar)))
        swipeActions = SwipeActions(this, TAG).attachTo(recyclerView)
        swipeActions.setFilter(FeedItemFilter(FeedItemFilter.DOWNLOADED))

        progressBar = root.findViewById(R.id.progLoading)
        progressBar.setVisibility(View.VISIBLE)

        floatingSelectMenu = root.findViewById(R.id.floatingSelectMenu)
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
        if (getArguments() != null && getArguments()!!.getBoolean(ARG_SHOW_LOGS, false)) {
            DownloadLogFragment().show(getChildFragmentManager(), DownloadLogFragment.TAG)
        }

        addEmptyView()
        EventBus.getDefault().register(this)
        return root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        EventBus.getDefault().unregister(this)
        adapter!!.endSelectMode()
        if (toolbar != null) {
            toolbar!!.setOnMenuItemClickListener(null)
            toolbar!!.setOnLongClickListener(null)
        }
        super.onDestroyView()
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

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.getItemId() == R.id.refresh_item) {
            FeedUpdateManager.getInstance()!!.runOnceOrAsk(requireContext())
            return true
        } else if (item.getItemId() == R.id.action_download_logs) {
            DownloadLogFragment().show(getChildFragmentManager(), DownloadLogFragment.TAG)
            return true
        } else if (item.getItemId() == R.id.action_search) {
            (getActivity() as MainActivity).loadChildFragment(SearchFragment.newInstance())
            return true
        } else if (item.getItemId() == R.id.downloads_sort) {
            DownloadsSortDialog().show(getChildFragmentManager(), "SortDialog")
            return true
        } else if (item.getItemId() == R.id.action_delete_downloads_played) {
            val dialog = object : ConfirmationDialog(getActivity()!!,
                    R.string.delete_downloads_played, R.string.delete_downloads_played_confirmation) {
                override fun onConfirmButtonPressed(clickedDialog: DialogInterface) {
                    clickedDialog.dismiss()
                    Observable.fromCallable {
                        DBReader.getEpisodes(0, Integer.MAX_VALUE,
                                FeedItemFilter(FeedItemFilter.DOWNLOADED,
                                        FeedItemFilter.INCLUDE_ALL_FEED_STATES,
                                        FeedItemFilter.PLAYED), SortOrder.DATE_OLD_NEW)
                    }
                            .subscribeOn(Schedulers.computation())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({ items ->
                                EpisodeMultiSelectActionHandler(getActivity()!!, R.id.remove_item)
                                        .handleAction(items)
                            }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
                }
            }
            dialog.createNewDialog().show()
            return true
        }
        return false
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: EpisodeDownloadEvent) {
        val newRunningDownloads = HashSet<String>()
        for (url in event.getUrls()) {
            if (DownloadServiceInterface.get()!!.isDownloadingEpisode(url)) {
                newRunningDownloads.add(url)
            }
        }
        if (newRunningDownloads != runningDownloads) {
            runningDownloads = newRunningDownloads
            loadItems()
            return // Refreshed anyway
        }
        for (downloadUrl in event.getUrls()) {
            val pos = EpisodeDownloadEvent.indexOfItemWithDownloadUrl(items, downloadUrl)
            if (pos >= 0) {
                adapter!!.notifyItemChangedCompat(pos)
            }
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val selectedItem = adapter!!.getLongPressedItem()
        if (selectedItem == null) {
            Log.i(TAG, "Selected item at current position was null, ignoring selection")
            return super.onContextItemSelected(item)
        }
        if (adapter!!.onContextItemSelected(item)) {
            return true
        }

        return FeedItemMenuHandler.onMenuItemClicked(this, item.getItemId(), selectedItem)
    }

    private fun addEmptyView() {
        emptyView = EmptyViewHandler(getActivity()!!)
        emptyView.setIcon(R.drawable.ic_download)
        emptyView.setTitle(R.string.no_comp_downloads_head_label)
        emptyView.setMessage(R.string.no_comp_downloads_label)
        emptyView.attachToRecyclerView(recyclerView)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]")
        if (event.unreadStatusChanged && event.items.isEmpty()) {
            loadItems()
            return
        }
        if (adapter == null) {
            loadItems()
            return
        }
        for (item in event.items) {
            val pos = FeedItemEvent.indexOfItemWithId(items, item.getId())
            if (pos >= 0) {
                items.removeAt(pos)
                if (item.getMedia()!!.isDownloaded()) {
                    items.add(pos, item)
                    adapter!!.notifyItemChangedCompat(pos)
                } else {
                    adapter!!.notifyItemRemoved(pos)
                }
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
        loadItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownloadLogChanged(event: DownloadLogEvent) {
        loadItems()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedUpdateRunningEvent) {
        swipeRefreshLayout.setRefreshing(event.isFeedUpdateRunning)
    }

    private fun loadItems() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        emptyView.hide()
        disposable = Observable.fromCallable {
            val sortOrder = UserPreferences.getDownloadsSortedOrder()
            val downloadedItems = DBReader.getEpisodes(0, Integer.MAX_VALUE,
                    FeedItemFilter(FeedItemFilter.DOWNLOADED, FeedItemFilter.INCLUDE_ALL_FEED_STATES), sortOrder)

            val mediaUrls = ArrayList<String>()
            for (url in runningDownloads) {
                if (EpisodeDownloadEvent.indexOfItemWithDownloadUrl(downloadedItems, url) != -1) {
                    continue // Already in list
                }
                mediaUrls.add(url)
            }
            val currentDownloads = DBReader.getFeedItemsWithUrl(mediaUrls) as MutableList<FeedItem>
            currentDownloads.addAll(downloadedItems)
            return@fromCallable currentDownloads
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { result ->
                            items = result as MutableList<FeedItem>
                            adapter!!.setDummyViews(0)
                            progressBar.setVisibility(View.GONE)
                            adapter!!.updateItems(result)
                        }, { error ->
                            adapter!!.setDummyViews(0)
                            adapter!!.updateItems(Collections.emptyList())
                            Log.e(TAG, Log.getStackTraceString(error))
                        })
    }

    override fun onStartSelectMode() {
        swipeActions.detach()
        floatingSelectMenu.setVisibility(View.VISIBLE)
        recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
                recyclerView.getPaddingRight(),
                getResources().getDimension(R.dimen.floating_select_menu_height).toInt())
    }

    override fun onEndSelectMode() {
        floatingSelectMenu.setVisibility(View.GONE)
        swipeActions.attachTo(recyclerView)
        recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
                recyclerView.getPaddingRight(), 0)
    }

    private inner class CompletedDownloadsListAdapter(mainActivity: FragmentActivity) :
            EpisodeItemListAdapter(mainActivity) {

        override fun afterBindViewHolder(holder: EpisodeItemViewHolder, pos: Int) {
            if (!inActionMode()) {
                if (holder.getFeedItem()!!.isDownloaded()
                        && !UserPreferences.shouldDownloadsButtonActionPlay()) {
                    val actionButton = DeleteActionButton(getItem(pos))
                    actionButton.configure(holder.secondaryActionButton, holder.secondaryActionIcon, getActivity())
                }
            }
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
            super.onCreateContextMenu(menu, v, menuInfo)
            if (!inActionMode()) {
                menu.findItem(R.id.multi_select).setVisible(true)
            }
            MenuItemUtils.setOnClickListeners(menu) { item -> this@CompletedDownloadsFragment.onContextItemSelected(item) }
        }

        override fun onSelectedItemsUpdated() {
            super.onSelectedItemsUpdated()
            FeedItemMenuHandler.onPrepareMenu(floatingSelectMenu.getMenu(), getSelectedItems())
            floatingSelectMenu.updateItemVisibility()
        }
    }

    class DownloadsSortDialog : ItemSortDialog() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            sortOrder = UserPreferences.getDownloadsSortedOrder()
        }

        override fun onAddItem(title: Int, ascending: SortOrder, descending: SortOrder, ascendingIsDefault: Boolean) {
            if (ascending == SortOrder.DATE_OLD_NEW || ascending == SortOrder.DURATION_SHORT_LONG
                    || ascending == SortOrder.EPISODE_TITLE_A_Z || ascending == SortOrder.SIZE_SMALL_LARGE) {
                super.onAddItem(title, ascending, descending, ascendingIsDefault)
            }
        }

        override fun onSelectionChanged() {
            super.onSelectionChanged()
            UserPreferences.setDownloadsSortedOrder(sortOrder)
            EventBus.getDefault().post(DownloadLogEvent.listUpdated())
        }
    }
}
