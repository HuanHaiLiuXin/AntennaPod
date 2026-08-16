package de.danoeh.antennapod.ui.screen.queue

import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView

import androidx.appcompat.widget.Toolbar
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.event.playback.SpeedChangedEvent
import de.danoeh.antennapod.ui.screen.InboxFragment
import de.danoeh.antennapod.ui.screen.SearchFragment
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager
import de.danoeh.antennapod.ui.episodes.PlaybackSpeedUtils
import de.danoeh.antennapod.ui.view.FloatingSelectMenu
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

import java.util.Collections

import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.ui.SelectableAdapter
import de.danoeh.antennapod.ui.common.ConfirmationDialog
import de.danoeh.antennapod.ui.MenuItemUtils
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.ui.common.Converter
import de.danoeh.antennapod.ui.screen.feed.ItemSortDialog
import de.danoeh.antennapod.event.EpisodeDownloadEvent
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.event.FeedUpdateRunningEvent
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.event.QueueEvent
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.ui.episodeslist.EpisodeMultiSelectActionHandler
import de.danoeh.antennapod.ui.swipeactions.SwipeActions
import de.danoeh.antennapod.ui.episodeslist.FeedItemMenuHandler
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.common.EmptyViewHandler
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemListRecyclerView
import de.danoeh.antennapod.ui.common.LiftOnScrollListener
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemViewHolder
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * Shows all items in the queue.
 */
class QueueFragment : Fragment(), Toolbar.OnMenuItemClickListener,
        SelectableAdapter.OnSelectModeListener {
    private lateinit var infoBar: TextView
    private lateinit var recyclerView: EpisodeItemListRecyclerView
    private var recyclerAdapter: QueueRecyclerAdapter? = null
    private lateinit var emptyView: EmptyViewHandler
    private var toolbar: MaterialToolbar? = null
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var displayUpArrow = false

    private var queue: MutableList<FeedItem>? = null

    private var disposable: Disposable? = null
    private lateinit var swipeActions: SwipeActions
    private lateinit var prefs: SharedPreferences

    private lateinit var floatingSelectMenu: FloatingSelectMenu
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getActivity()!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    override fun onStart() {
        super.onStart()
        loadItems()
        EventBus.getDefault().register(this)
    }

    override fun onPause() {
        super.onPause()
        val scrollPosition = recyclerView.getScrollPosition()
        prefs.edit().putInt(SCROLL_POSITION_KEY, scrollPosition.first)
                .putInt(SCROLL_OFFSET_KEY, scrollPosition.second).apply()
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        if (disposable != null) {
            disposable!!.dispose()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: QueueEvent) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]")
        if (queue == null) {
            return
        } else if (recyclerAdapter == null) {
            loadItems()
            return
        }
        var position: Int
        when (event.action) {
            QueueEvent.Action.ADDED -> {
                queue!!.add(event.position, event.item!!)
                recyclerAdapter!!.notifyItemInserted(event.position)
            }
            QueueEvent.Action.SET_QUEUE, //Deliberate fall-through
            QueueEvent.Action.SORTED -> {
                queue = event.items as MutableList<FeedItem>
                recyclerAdapter!!.updateItems(event.items!!)
            }
            QueueEvent.Action.REMOVED, QueueEvent.Action.IRREVERSIBLE_REMOVED -> {
                position = FeedItemEvent.indexOfItemWithId(queue!!, event.item!!.getId())
                if (position >= 0) {
                    queue!!.removeAt(position)
                    recyclerAdapter!!.notifyItemRemoved(position)
                }
            }
            QueueEvent.Action.CLEARED -> {
                queue!!.clear()
                recyclerAdapter!!.updateItems(queue!!)
            }
            QueueEvent.Action.MOVED -> {
                position = FeedItemEvent.indexOfItemWithId(queue!!, event.item!!.getId())
                if (position >= 0) {
                    queue!!.add(event.position, queue!!.removeAt(position))
                    recyclerAdapter!!.notifyItemMoved(position, event.position)
                }
            }
            else -> return
        }
        recyclerAdapter!!.updateDragDropEnabled()
        refreshToolbarState()
        refreshInfoBar()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]")
        if (event.unreadStatusChanged && event.items.isEmpty()) {
            loadItems()
            refreshToolbarState()
            return
        }
        if (queue == null) {
            return
        } else if (recyclerAdapter == null) {
            loadItems()
            return
        }
        for (i in 0 until event.items.size) {
            val item = event.items.get(i)
            val pos = FeedItemEvent.indexOfItemWithId(queue!!, item.getId())
            if (pos >= 0) {
                queue!!.removeAt(pos)
                queue!!.add(pos, item)
                recyclerAdapter!!.notifyItemChangedCompat(pos)
                refreshInfoBar()
            }
        }
        if (event.unreadStatusChanged) {
            refreshToolbarState()
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: EpisodeDownloadEvent) {
        if (queue == null) {
            return
        }
        for (downloadUrl in event.getUrls()) {
            val pos = EpisodeDownloadEvent.indexOfItemWithDownloadUrl(queue!!, downloadUrl)
            if (pos >= 0) {
                recyclerAdapter!!.notifyItemChangedCompat(pos)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        if (recyclerAdapter != null) {
            for (i in 0 until recyclerAdapter!!.getItemCount()) {
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
        refreshToolbarState()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onKeyUp(event: KeyEvent) {
        if (!isAdded() || !isVisible() || !isMenuVisible()) {
            return
        }
        when (event.getKeyCode()) {
            KeyEvent.KEYCODE_T -> recyclerView.smoothScrollToPosition(0)
            KeyEvent.KEYCODE_B -> recyclerView.smoothScrollToPosition(recyclerAdapter!!.getItemCount() - 1)
            else -> {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (recyclerAdapter != null) {
            recyclerAdapter!!.endSelectMode()
        }
        recyclerAdapter = null
        if (toolbar != null) {
            toolbar!!.setOnMenuItemClickListener(null)
            toolbar!!.setOnLongClickListener(null)
        }
    }

    private fun refreshToolbarState() {
        val keepSorted = UserPreferences.isQueueKeepSorted()
        toolbar!!.getMenu().findItem(R.id.queue_lock).setChecked(UserPreferences.isQueueLocked())
        toolbar!!.getMenu().findItem(R.id.queue_lock).setVisible(!keepSorted)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedUpdateRunningEvent) {
        swipeRefreshLayout.setRefreshing(event.isFeedUpdateRunning)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun updateSpeed(event: SpeedChangedEvent) {
        refreshInfoBar()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.getItemId()
        if (itemId == R.id.queue_lock) {
            toggleQueueLock()
            return true
        } else if (itemId == R.id.queue_sort) {
            QueueSortDialog().show(getChildFragmentManager().beginTransaction(), "SortDialog")
            return true
        } else if (itemId == R.id.refresh_item) {
            FeedUpdateManager.getInstance()!!.runOnceOrAsk(requireContext())
            return true
        } else if (itemId == R.id.clear_queue) {
            // make sure the user really wants to clear the queue
            val conDialog = object : ConfirmationDialog(getActivity()!!,
                    R.string.clear_queue_label,
                    R.string.clear_queue_confirmation_msg) {

                override fun onConfirmButtonPressed(dialog: DialogInterface) {
                    dialog.dismiss()
                    DBWriter.clearQueue()
                }
            }
            conDialog.createNewDialog().show()
            return true
        } else if (itemId == R.id.action_search) {
            (getActivity() as MainActivity).loadChildFragment(
                    SearchFragment.newInstance(FeedItemFilter(FeedItemFilter.QUEUED,
                            FeedItemFilter.INCLUDE_ALL_FEED_STATES)))
            return true
        }
        return false
    }

    private fun toggleQueueLock() {
        val isLocked = UserPreferences.isQueueLocked()
        if (isLocked) {
            setQueueLocked(false)
        } else {
            val shouldShowLockWarning = prefs.getBoolean(PREF_SHOW_LOCK_WARNING, true)
            if (!shouldShowLockWarning) {
                setQueueLocked(true)
            } else {
                val builder = MaterialAlertDialogBuilder(getContext()!!)
                builder.setTitle(R.string.lock_queue)
                builder.setMessage(R.string.queue_lock_warning)

                val view = View.inflate(getContext()!!, R.layout.checkbox_do_not_show_again, null)
                val checkDoNotShowAgain = view.findViewById<CheckBox>(R.id.checkbox_do_not_show_again)
                builder.setView(view)

                builder.setPositiveButton(R.string.lock_queue) { dialog, which ->
                    prefs.edit().putBoolean(PREF_SHOW_LOCK_WARNING, !checkDoNotShowAgain.isChecked()).apply()
                    setQueueLocked(true)
                }
                builder.setNegativeButton(R.string.cancel_label, null)
                builder.show()
            }
        }
    }

    private fun setQueueLocked(locked: Boolean) {
        UserPreferences.setQueueLocked(locked)
        refreshToolbarState()
        if (recyclerAdapter != null) {
            recyclerAdapter!!.updateDragDropEnabled()
        }
        if (queue!!.isEmpty()) {
            if (locked) {
                EventBus.getDefault().post(MessageEvent(getString(R.string.queue_locked)))
            } else {
                EventBus.getDefault().post(MessageEvent(getString(R.string.queue_unlocked)))
            }
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "onContextItemSelected() called with: " + "item = [" + item + "]")
        if (!isVisible() || recyclerAdapter == null) {
            return false
        }
        val selectedItem = recyclerAdapter!!.getLongPressedItem()
        if (selectedItem == null) {
            Log.i(TAG, "Selected item was null, ignoring selection")
            return super.onContextItemSelected(item)
        }

        val position = FeedItemEvent.indexOfItemWithId(queue!!, selectedItem.getId())
        if (position < 0) {
            Log.i(TAG, "Selected item no longer exist, ignoring selection")
            return super.onContextItemSelected(item)
        }
        if (recyclerAdapter!!.onContextItemSelected(item)) {
            return true
        }

        val itemId = item.getItemId()
        if (!recyclerAdapter!!.inActionMode()) {
            if (itemId == R.id.move_to_top_item) {
                queue!!.add(0, queue!!.removeAt(position))
                recyclerAdapter!!.notifyItemMoved(position, 0)
                DBWriter.moveQueueItemsToTop(Collections.singletonList(selectedItem))
                return true
            } else if (itemId == R.id.move_to_bottom_item) {
                queue!!.add(queue!!.removeAt(position))
                recyclerAdapter!!.notifyItemMoved(position, queue!!.size - 1)
                DBWriter.moveQueueItemsToBottom(Collections.singletonList(selectedItem))
                return true
            }
        }
        return FeedItemMenuHandler.onMenuItemClicked(this, item.getItemId(), selectedItem)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        val root = inflater.inflate(R.layout.queue_fragment, container, false)
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
        toolbar!!.inflateMenu(R.menu.queue)
        refreshToolbarState()
        progressBar = root.findViewById(R.id.progressBar)
        progressBar.setVisibility(View.VISIBLE)

        infoBar = root.findViewById(R.id.info_bar)
        val largePadding = displayUpArrow || !UserPreferences.isBottomNavigationEnabled()
        val paddingHorizontal = (getResources().getDisplayMetrics().density * (if (largePadding) 60 else 16)).toInt()
        infoBar.setPadding(paddingHorizontal, 0, paddingHorizontal, 0)

        recyclerView = root.findViewById(R.id.recyclerView)
        val animator = recyclerView.getItemAnimator()
        if (animator is SimpleItemAnimator) {
            animator.setSupportsChangeAnimations(false)
        }
        registerForContextMenu(recyclerView)
        recyclerView.addOnScrollListener(LiftOnScrollListener(root.findViewById(R.id.appbar)))

        swipeActions = QueueSwipeActions()
        swipeActions.setFilter(FeedItemFilter(FeedItemFilter.QUEUED))
        swipeActions.attachTo(recyclerView)

        recyclerAdapter = object : QueueRecyclerAdapter(getActivity() as MainActivity, swipeActions) {
            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(menu, v, menuInfo)
                MenuItemUtils.setOnClickListeners(menu) { item -> this@QueueFragment.onContextItemSelected(item) }
            }

            override fun onSelectedItemsUpdated() {
                super.onSelectedItemsUpdated()
                val menu = floatingSelectMenu.getMenu()
                val selectedItems = getSelectedItems()
                FeedItemMenuHandler.onPrepareMenu(floatingSelectMenu.getMenu(), getSelectedItems(),
                        R.id.add_to_queue_item, R.id.remove_inbox_item)

                val canMove = canMove(queue!!, selectedItems)
                menu!!.findItem(R.id.move_to_top_item).setVisible(canMove.first)
                menu!!.findItem(R.id.move_to_bottom_item).setVisible(canMove.second)

                floatingSelectMenu.updateItemVisibility()
            }
        }
        recyclerAdapter!!.setOnSelectModeListener(this)
        recyclerView.setAdapter(recyclerAdapter)

        swipeRefreshLayout = root.findViewById(R.id.swipeRefresh)
        swipeRefreshLayout.setDistanceToTriggerSync(getResources().getInteger(R.integer.swipe_refresh_distance))
        swipeRefreshLayout.setOnRefreshListener { FeedUpdateManager.getInstance()!!.runOnceOrAsk(requireContext()) }

        emptyView = EmptyViewHandler(getContext()!!)
        emptyView.attachToRecyclerView(recyclerView)
        emptyView.setIcon(R.drawable.ic_playlist_play)
        emptyView.setTitle(R.string.no_items_header_label)
        emptyView.setMessage(R.string.no_items_label)
        emptyView.updateAdapter(recyclerAdapter)

        floatingSelectMenu = root.findViewById(R.id.floatingSelectMenu)
        floatingSelectMenu.inflate(R.menu.episodes_apply_action_speeddial)
        floatingSelectMenu.setOnMenuItemClickListener { menuItem ->
            if (recyclerAdapter!!.getSelectedCount() == 0) {
                EventBus.getDefault().post(MessageEvent(getString(R.string.no_items_selected_message)))
                return@setOnMenuItemClickListener false
            }
            EpisodeMultiSelectActionHandler(getActivity()!!, menuItem.getItemId())
                    .handleAction(recyclerAdapter!!.getSelectedItems())
            recyclerAdapter!!.endSelectMode()
            return@setOnMenuItemClickListener true
        }
        return root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    private fun refreshInfoBar() {
        var timeLeft = 0L
        for (item in queue!!) {
            var playbackSpeed = 1f
            if (UserPreferences.timeRespectsSpeed()) {
                playbackSpeed = PlaybackSpeedUtils.getCurrentPlaybackSpeed(item.getMedia())
            }
            if (item.getMedia() != null) {
                val itemTimeLeft = (item.getMedia()!!.getDuration() - item.getMedia()!!.getPosition()).toLong()
                timeLeft += (itemTimeLeft / playbackSpeed).toLong()
            }
        }
        val episodes = getResources().getQuantityString(R.plurals.num_episodes, queue!!.size, queue!!.size)
        val time = Converter.getDurationStringLocalized(getResources(), timeLeft, false)
        infoBar.setText(getString(R.string.queue_time_left_label, episodes, time))

        if (recyclerAdapter!!.inActionMode()) {
            infoBar.setVisibility(View.INVISIBLE)
        } else {
            infoBar.setVisibility(View.VISIBLE)
        }
    }

    private fun loadItems() {
        Log.d(TAG, "loadItems()")
        if (disposable != null) {
            disposable!!.dispose()
        }
        if (queue == null) {
            emptyView.hide()
        }
        disposable = Observable.fromCallable {
            val displayGoToInboxButton = DBReader.getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.NEW)) > 0
            Pair(DBReader.getQueue(), displayGoToInboxButton)
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ itemsAndDisplayButton ->
                    val restoreScrollPosition = queue == null || queue!!.isEmpty()
                    queue = itemsAndDisplayButton.first
                    if (itemsAndDisplayButton.second) {
                        emptyView.setMessage(R.string.no_queue_items_inbox_has_items_label)
                        emptyView.setButtonText(R.string.no_queue_items_inbox_has_items_button_label)
                        emptyView.setButtonVisibility(View.VISIBLE)
                        emptyView.setButtonOnClickListener { (getActivity() as MainActivity)
                                .loadChildFragment(InboxFragment()) }
                    }
                    progressBar.setVisibility(View.GONE)
                    recyclerAdapter!!.setDummyViews(0)
                    recyclerAdapter!!.updateItems(queue!!)
                    if (restoreScrollPosition) {
                        val scrollPosition = Pair(
                                prefs.getInt(SCROLL_POSITION_KEY, 0), prefs.getInt(SCROLL_OFFSET_KEY, 0))
                        recyclerView.restoreScrollPosition(scrollPosition)
                    }
                    refreshInfoBar()
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    override fun onStartSelectMode() {
        swipeActions.detach()
        floatingSelectMenu.setVisibility(View.VISIBLE)
        recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
                recyclerView.getPaddingRight(),
                getResources().getDimension(R.dimen.floating_select_menu_height).toInt())
        refreshToolbarState()
        refreshInfoBar()
    }

    override fun onEndSelectMode() {
        floatingSelectMenu.setVisibility(View.GONE)
        recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
                recyclerView.getPaddingRight(), 0)
        infoBar.setVisibility(View.VISIBLE)
        swipeActions.attachTo(recyclerView)
        refreshInfoBar()
    }

    class QueueSortDialog : ItemSortDialog() {
        internal var turnedOffKeepSortedForRandom = false

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                  savedInstanceState: Bundle?): View? {
            if (UserPreferences.isQueueKeepSorted()) {
                sortOrder = UserPreferences.getQueueKeepSortedOrder()
            }
            val view = super.onCreateView(inflater, container, savedInstanceState)
            viewBinding!!.keepSortedCheckbox.setVisibility(View.VISIBLE)
            viewBinding!!.keepSortedCheckbox.setChecked(UserPreferences.isQueueKeepSorted())
            // Disable until something gets selected
            viewBinding!!.keepSortedCheckbox.setEnabled(UserPreferences.isQueueKeepSorted())
            return view
        }

        override fun onAddItem(title: Int, ascending: SortOrder, descending: SortOrder, ascendingIsDefault: Boolean) {
            var ascendingIsDefault = ascendingIsDefault
            if (ascending == SortOrder.EPISODE_FILENAME_A_Z || ascending == SortOrder.SIZE_SMALL_LARGE) {
                return
            }
            if (ascending == SortOrder.DATE_OLD_NEW || ascending == SortOrder.SMART_SHUFFLE_OLD_NEW) {
                ascendingIsDefault = true
            }
            super.onAddItem(title, ascending, descending, ascendingIsDefault)
        }

        override fun onSelectionChanged() {
            super.onSelectionChanged()
            if (sortOrder == SortOrder.RANDOM) {
                turnedOffKeepSortedForRandom = turnedOffKeepSortedForRandom or viewBinding!!.keepSortedCheckbox.isChecked()
                viewBinding!!.keepSortedCheckbox.setChecked(false)
                viewBinding!!.keepSortedCheckbox.setEnabled(false)
            } else {
                if (turnedOffKeepSortedForRandom) {
                    viewBinding!!.keepSortedCheckbox.setChecked(true)
                    turnedOffKeepSortedForRandom = false
                }
                viewBinding!!.keepSortedCheckbox.setEnabled(true)
            }
            UserPreferences.setQueueKeepSorted(viewBinding!!.keepSortedCheckbox.isChecked())
            UserPreferences.setQueueKeepSortedOrder(sortOrder)
            DBWriter.reorderQueue(sortOrder, true)
        }
    }

    private inner class QueueSwipeActions : SwipeActions(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, this@QueueFragment, TAG) {

        // Position tracking whilst dragging
        internal var dragFrom = -1
        internal var dragTo = -1

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                            target: RecyclerView.ViewHolder): Boolean {
            val fromPosition = viewHolder.getBindingAdapterPosition()
            val toPosition = target.getBindingAdapterPosition()

            // Update tracked position
            if (dragFrom == -1) {
                dragFrom = fromPosition
            }
            dragTo = toPosition

            val from = viewHolder.getBindingAdapterPosition()
            val to = target.getBindingAdapterPosition()
            Log.d(TAG, "move(" + from + ", " + to + ") in memory")
            if (queue == null || from >= queue!!.size || to >= queue!!.size || from < 0 || to < 0) {
                return false
            }
            queue!!.add(to, queue!!.removeAt(from))
            recyclerAdapter!!.notifyItemMoved(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            if (disposable != null) {
                disposable!!.dispose()
            }

            //SwipeActions
            super.onSwiped(viewHolder, direction)
        }

        override fun isLongPressDragEnabled(): Boolean {
            return false
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            // Check if drag finished
            if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
                reallyMoved(dragFrom, dragTo)
            }

            dragFrom = -1
            dragTo = -1
        }

        private fun reallyMoved(from: Int, to: Int) {
            // Write drag operation to database
            Log.d(TAG, "Write to database move(" + from + ", " + to + ")")
            DBWriter.moveQueueItem(from, to, true)
        }

    }

    companion object {
        const val TAG = "QueueFragment"
        private const val KEY_UP_ARROW = "up_arrow"
        private const val SCROLL_POSITION_KEY = "scroll_position"
        private const val SCROLL_OFFSET_KEY = "scroll_offset"
        private const val PREFS = "QueueFragment"
        private const val PREF_SHOW_LOCK_WARNING = "show_lock_warning"

        /**
         * This method checks if the selected items are allowed to be moved to the top, bottom or both of the Queue.
         * @param queue The FeedItems currently in the Queue.
         * @param selectedItems The FeedItems for which the check is performed.
         * @return A Pair of booleans where
         *           [0] is true if moving to the top is allowed, false otherwise.
         *           [1] is true if moving to the bottom is allowed, false otherwise.
         * */
        fun canMove(queue: List<FeedItem>, selectedItems: List<FeedItem>): Pair<Boolean, Boolean> {
            val queueSize = queue.size
            val selectedSize = selectedItems.size
            // No manual reordering allowed or reordering would be a no-op.
            if (selectedItems.isEmpty() || queue.isEmpty() || UserPreferences.isQueueLocked()
                    || UserPreferences.isQueueKeepSorted() || selectedSize == queueSize) {
                return Pair(false, false)
            }
            val isFirstItemSelected = selectedItems.get(0).getId() == queue.get(0).getId()
            val isLastItemSelected = selectedItems.get(selectedSize - 1).getId() == queue.get(queueSize - 1).getId()
            // If only one item is selected and its already at the top of the list, disable option to move item to the top.
            // If the item is already at the bottom of the list, disable the option to move it to the bottom.
            if (selectedSize == 1) {
                return Pair(!isFirstItemSelected, !isLastItemSelected)
            }
            // If contiguous from the top, moving items to the top is disabled, as they are already there.
            if (isFirstItemSelected && selectedItems.equals(queue.subList(0, selectedSize))) {
                return Pair(false, true)
            }
            // If contiguous from the bottom, moving items to the bottom is disabled, as they are already there.
            if (isLastItemSelected && selectedItems.equals(queue.subList(queueSize - selectedSize, queueSize))) {
                return Pair(true, false)
            }
            return Pair(true, true)
        }
    }
}
