package de.danoeh.antennapod.ui.screen.subscriptions

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.appcompat.widget.Toolbar
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.event.FeedListUpdateEvent
import de.danoeh.antennapod.event.FeedUpdateRunningEvent
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.model.feed.SubscriptionsFilter
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.NavDrawerData
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.MenuItemUtils
import de.danoeh.antennapod.ui.SelectableAdapter
import de.danoeh.antennapod.ui.screen.AddFeedFragment
import de.danoeh.antennapod.ui.screen.SearchFragment

import de.danoeh.antennapod.ui.common.EmptyViewHandler
import de.danoeh.antennapod.ui.view.FloatingSelectMenu
import de.danoeh.antennapod.ui.common.ItemOffsetDecoration
import de.danoeh.antennapod.ui.common.LiftOnScrollListener
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

import java.util.Collections
import java.util.Locale

/**
 * Fragment for displaying feed subscriptions
 */
class SubscriptionFragment : Fragment(), Toolbar.OnMenuItemClickListener,
        SelectableAdapter.OnSelectModeListener {
    private lateinit var subscriptionRecycler: RecyclerView
    private var subscriptionAdapter: SubscriptionsRecyclerAdapter? = null
    private lateinit var tagsRecycler: RecyclerView
    private var tagAdapter: SubscriptionTagAdapter? = null
    private lateinit var emptyView: EmptyViewHandler
    private lateinit var feedsFilteredMsg: View
    private lateinit var toolbar: MaterialToolbar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var collapsingContainer: CollapsingToolbarLayout
    private var displayUpArrow = false
    private var shouldShowTags = false

    private var disposable: Disposable? = null
    private lateinit var prefs: SharedPreferences

    private lateinit var subscriptionAddButton: FloatingActionButton
    private lateinit var floatingSelectMenu: FloatingSelectMenu
    private var itemDecoration: RecyclerView.ItemDecoration? = null
    private var feeds: List<Feed>? = null
    private var stateToShow = Feed.STATE_SUBSCRIBED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = requireActivity().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (getArguments() != null) {
            stateToShow = requireArguments().getInt(ARGUMENT_STATE, Feed.STATE_SUBSCRIBED)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                             savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_subscriptions, container, false)
        toolbar = root.findViewById(R.id.toolbar)
        toolbar.setOnMenuItemClickListener(this)
        toolbar.setOnLongClickListener {
            subscriptionRecycler.scrollToPosition(5)
            subscriptionRecycler.post { subscriptionRecycler.smoothScrollToPosition(0) }
            false
        }
        displayUpArrow = getParentFragmentManager().getBackStackEntryCount() != 0
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        }
        (getActivity() as MainActivity).setupToolbarToggle(toolbar, displayUpArrow)
        toolbar.inflateMenu(R.menu.subscriptions)
        for (i in 1 until COLUMN_CHECKBOX_IDS.size) {
            // Do this in Java to localize numbers
            toolbar.getMenu().findItem(COLUMN_CHECKBOX_IDS[i])
                    .setTitle(String.format(Locale.getDefault(), "%d", i + MIN_NUM_COLUMNS))
        }
        refreshToolbarState()

        collapsingContainer = root.findViewById(R.id.collapsing_container)
        subscriptionRecycler = root.findViewById(R.id.subscriptions_grid)
        registerForContextMenu(subscriptionRecycler)
        subscriptionRecycler.addOnScrollListener(LiftOnScrollListener(root.findViewById(R.id.appbar)))
        subscriptionRecycler.addOnScrollListener(LiftOnScrollListener(collapsingContainer))
        subscriptionAdapter = object : SubscriptionsRecyclerAdapter(getActivity() as MainActivity) {
            override fun onSelectedItemsUpdated() {
                super.onSelectedItemsUpdated()
                FeedMenuHandler.onPrepareMenu(floatingSelectMenu.getMenu(), getSelectedItems())
                floatingSelectMenu.updateItemVisibility()
            }
        }
        setColumnNumber(prefs.getInt(PREF_NUM_COLUMNS, getDefaultNumOfColumns()))
        subscriptionAdapter!!.setOnSelectModeListener(this)
        subscriptionRecycler.setAdapter(subscriptionAdapter)
        setupEmptyView()

        progressBar = root.findViewById(R.id.progressBar)
        progressBar.setVisibility(View.VISIBLE)

        subscriptionAddButton = root.findViewById(R.id.subscriptions_add)
        subscriptionAddButton.setOnClickListener {
            if (getActivity() is MainActivity) {
                (getActivity() as MainActivity).loadChildFragment(AddFeedFragment())
            }
        }

        feedsFilteredMsg = root.findViewById(R.id.feeds_filtered_message)
        feedsFilteredMsg.setOnClickListener {
            SubscriptionsFilterDialog().show(getChildFragmentManager(), "filter")
        }
        val largePadding = displayUpArrow || !UserPreferences.isBottomNavigationEnabled()
        val paddingHorizontal = (getResources().getDisplayMetrics().density * (if (largePadding) 60 else 16)).toInt()
        val paddingVertical = (getResources().getDisplayMetrics().density * 4).toInt()
        feedsFilteredMsg.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)

        swipeRefreshLayout = root.findViewById(R.id.swipeRefresh)
        swipeRefreshLayout.setDistanceToTriggerSync(getResources().getInteger(R.integer.swipe_refresh_distance))
        swipeRefreshLayout.setOnRefreshListener { FeedUpdateManager.getInstance()!!.runOnceOrAsk(requireContext()) }

        floatingSelectMenu = root.findViewById(R.id.floatingSelectMenu)
        floatingSelectMenu.inflate(R.menu.nav_feed_action_speeddial)
        if (stateToShow == Feed.STATE_ARCHIVED) {
            toolbar.setTitle(R.string.archive_feed_label_noun)
            toolbar.getMenu().removeItem(R.id.subscriptions_filter)
            toolbar.getMenu().removeItem(R.id.refresh_item)
            toolbar.getMenu().removeItem(R.id.subscriptions_counter)
            toolbar.getMenu().removeItem(R.id.show_archive)
            floatingSelectMenu.getMenu()!!.removeItem(R.id.keep_updated)
            floatingSelectMenu.getMenu()!!.removeItem(R.id.notify_new_episodes)
            floatingSelectMenu.getMenu()!!.removeItem(R.id.autodownload)
            floatingSelectMenu.getMenu()!!.removeItem(R.id.autoDeleteDownload)
            floatingSelectMenu.getMenu()!!.removeItem(R.id.playback_speed)
            subscriptionAddButton.setVisibility(View.GONE)
        }
        floatingSelectMenu.setOnMenuItemClickListener { menuItem ->
            val selection = subscriptionAdapter!!.getSelectedItems()
            FeedMultiSelectActionHandler(requireActivity(), selection)
                    .handleAction(menuItem.getItemId())
            if (selection.size <= 1) {
                subscriptionAdapter!!.endSelectMode()
            }
            true
        }

        tagsRecycler = root.findViewById(R.id.tags_recycler)
        tagsRecycler.setLayoutManager(LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false))
        tagsRecycler.addItemDecoration(ItemOffsetDecoration(requireContext(), 4, 0))
        registerForContextMenu(tagsRecycler)
        tagAdapter = object : SubscriptionTagAdapter(requireActivity()) {
            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(menu, v, menuInfo)
                MenuItemUtils.setOnClickListeners(menu) { item -> this@SubscriptionFragment.onTagContextItemSelected(item) }
            }

            override fun onTagClick(tag: NavDrawerData.TagItem) {
                tagAdapter!!.setSelectedTag(tag.getTitle())
                loadSubscriptionsAndTags()
            }
        }
        if (stateToShow == Feed.STATE_SUBSCRIBED) {
            tagAdapter!!.setSelectedTag(prefs.getString(PREF_LAST_TAG, FeedPreferences.TAG_ROOT)!!)
        } else {
            tagAdapter!!.setSelectedTag(FeedPreferences.TAG_ROOT)
        }
        tagsRecycler.setAdapter(tagAdapter)
        return root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    private fun refreshToolbarState() {
        val columns = prefs.getInt(PREF_NUM_COLUMNS, getDefaultNumOfColumns())
        toolbar.getMenu().findItem(COLUMN_CHECKBOX_IDS[columns - MIN_NUM_COLUMNS]).setChecked(true)
        toolbar.getMenu().findItem(R.id.pref_show_subscription_title).setVisible(columns > 1)
        toolbar.getMenu().findItem(R.id.pref_show_subscription_title)
                .setChecked(UserPreferences.shouldShowSubscriptionTitle())
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedUpdateRunningEvent) {
        swipeRefreshLayout.setRefreshing(event.isFeedUpdateRunning)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.getItemId()
        if (itemId == R.id.refresh_item) {
            FeedUpdateManager.getInstance()!!.runOnceOrAsk(requireContext())
            return true
        } else if (itemId == R.id.subscriptions_filter) {
            SubscriptionsFilterDialog().show(getChildFragmentManager(), "filter")
            return true
        } else if (itemId == R.id.subscriptions_sort) {
            FeedSortDialog.showDialog(requireContext())
            return true
        } else if (itemId == R.id.subscriptions_counter) {
            FeedCounterDialog.showDialog(requireContext())
            return true
        } else if (itemId == R.id.subscription_display_list) {
            setColumnNumber(1)
            return true
        } else if (itemId == R.id.subscription_num_columns_2) {
            setColumnNumber(2)
            return true
        } else if (itemId == R.id.subscription_num_columns_3) {
            setColumnNumber(3)
            return true
        } else if (itemId == R.id.subscription_num_columns_4) {
            setColumnNumber(4)
            return true
        } else if (itemId == R.id.subscription_num_columns_5) {
            setColumnNumber(5)
            return true
        } else if (itemId == R.id.action_search) {
            if (stateToShow == Feed.STATE_ARCHIVED) {
                (getActivity() as MainActivity).loadChildFragment(
                        SearchFragment.newInstance(FeedItemFilter(FeedItemFilter.INCLUDE_ARCHIVED)))
            } else {
                (getActivity() as MainActivity).loadChildFragment(SearchFragment.newInstance())
            }
            return true
        } else if (itemId == R.id.pref_show_subscription_title) {
            item.setChecked(!item.isChecked())
            UserPreferences.setShouldShowSubscriptionTitle(item.isChecked())
            subscriptionAdapter!!.notifyDataSetChanged()
        } else if (itemId == R.id.show_archive) {
            val fragment = SubscriptionFragment.newInstance(Feed.STATE_ARCHIVED)
            (getActivity() as MainActivity).loadChildFragment(fragment)
            return true
        }
        return false
    }

    private fun setColumnNumber(columns: Int) {
        if (itemDecoration != null) {
            subscriptionRecycler.removeItemDecoration(itemDecoration!!)
            itemDecoration = null
        }
        val layoutManager: RecyclerView.LayoutManager
        if (columns == 1 && getDefaultNumOfColumns() == 5) { // Tablet
            layoutManager = GridLayoutManager(requireContext(), 2, RecyclerView.VERTICAL, false)
        } else if (columns == 1) {
            layoutManager = GridLayoutManager(requireContext(), 1, RecyclerView.VERTICAL, false)
        } else {
            layoutManager = GridLayoutManager(requireContext(), columns, RecyclerView.VERTICAL, false)
            itemDecoration = SubscriptionsRecyclerAdapter.GridDividerItemDecorator()
            subscriptionRecycler.addItemDecoration(itemDecoration!!)
        }
        subscriptionAdapter!!.setColumnCount(columns)
        subscriptionRecycler.setLayoutManager(layoutManager)
        prefs.edit().putInt(PREF_NUM_COLUMNS, columns).apply()
        refreshToolbarState()
    }

    private fun setupEmptyView() {
        emptyView = EmptyViewHandler(requireContext())
        emptyView.setIcon(R.drawable.ic_subscriptions)
        if (stateToShow == Feed.STATE_ARCHIVED) {
            emptyView.setTitle(R.string.no_archive_head_label)
            emptyView.setMessage(R.string.no_archive_label)
        } else {
            emptyView.setTitle(R.string.no_subscriptions_head_label)
            emptyView.setMessage(R.string.no_subscriptions_label)
        }
        emptyView.attachToRecyclerView(subscriptionRecycler)
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        loadSubscriptionsAndTags()
    }

    override fun onPause() {
        super.onPause()
        scrollPosition = getScrollPosition()
        if (stateToShow == Feed.STATE_SUBSCRIBED) {
            prefs.edit().putString(PREF_LAST_TAG, tagAdapter!!.getSelectedTag()).apply()
        }
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        if (disposable != null) {
            disposable!!.dispose()
        }
        if (subscriptionAdapter != null) {
            subscriptionAdapter!!.endSelectMode()
        }
    }

    private fun loadSubscriptionsAndTags() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        val filter = if (stateToShow == Feed.STATE_SUBSCRIBED)
            UserPreferences.getSubscriptionsFilter() else SubscriptionsFilter("")
        emptyView.hide()
        disposable = Observable.fromCallable {
            val navDrawerData = DBReader.getNavDrawerData(filter,
                    UserPreferences.getFeedOrder(), UserPreferences.getFeedCounterSetting(),
                    stateToShow)
            val tags = DBReader.getAllTags(stateToShow)
            return@fromCallable Pair(navDrawerData, tags)
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { result ->
                        var openedFolderFeeds: List<Feed> = Collections.emptyList()
                        if (FeedPreferences.TAG_ROOT == tagAdapter!!.getSelectedTag()) {
                            openedFolderFeeds = result.first.feeds
                        } else {
                            var tagExists = false
                            for (tag in result.first.tags) { // Filtered list
                                if (tag.getTitle().equals(tagAdapter!!.getSelectedTag())) {
                                    openedFolderFeeds = tag.getFeeds()
                                    tagExists = true
                                    break
                                }
                            }
                            if (!tagExists) {
                                tagAdapter!!.setSelectedTag(FeedPreferences.TAG_ROOT)
                                openedFolderFeeds = result.first.feeds
                            }
                        }

                        val firstLoaded = feeds == null || feeds!!.isEmpty()
                        if (feeds != null && feeds!!.size > openedFolderFeeds.size) {
                            // We have fewer items. This can result in items being selected that are no longer visible.
                            subscriptionAdapter!!.endSelectMode()
                        }
                        feeds = openedFolderFeeds
                        progressBar.setVisibility(View.GONE)
                        subscriptionAdapter!!.setItems(feeds!!, result.first.feedCounters)
                        if (firstLoaded) {
                            restoreScrollPosition(scrollPosition)
                        }
                        emptyView.updateVisibility()
                        shouldShowTags = false
                        if (tagAdapter != null) {
                            tagAdapter!!.setTags(result.second)
                            for (tag in result.second) {
                                if (FeedPreferences.TAG_ROOT != tag.getTitle()
                                        && FeedPreferences.TAG_UNTAGGED != tag.getTitle()) {
                                    shouldShowTags = true
                                    break
                                }
                            }
                            tagsRecycler.setVisibility(if (shouldShowTags) View.VISIBLE else View.GONE)
                            // Scroll to center the selected tag
                            tagsRecycler.post {
                                val selectedPosition = tagAdapter!!.getSelectedTagPosition()
                                if (selectedPosition < 0) {
                                    return@post
                                }
                                val layoutManager =
                                        tagsRecycler.getLayoutManager() as LinearLayoutManager
                                // Calculate offset to center the selected chip
                                val selectedView = layoutManager.findViewByPosition(selectedPosition)
                                if (selectedView != null) {
                                    val recyclerWidth = tagsRecycler.getWidth()
                                    val chipWidth = selectedView.getWidth()
                                    val offset = (recyclerWidth - chipWidth) / 2
                                    layoutManager.scrollToPositionWithOffset(selectedPosition, offset)
                                } else {
                                    tagsRecycler.scrollToPosition(selectedPosition)
                                }
                            }
                        }
                    }, { error ->
                        Log.e(TAG, Log.getStackTraceString(error))
                    })
        updateFilterVisibility()
    }

    private fun updateFilterVisibility() {
        if (!UserPreferences.getSubscriptionsFilter().isEnabled()) {
            feedsFilteredMsg.setVisibility(View.GONE)
        } else if (subscriptionAdapter!!.inActionMode()) {
            feedsFilteredMsg.setVisibility(View.INVISIBLE)
        } else {
            feedsFilteredMsg.setVisibility(View.VISIBLE)
        }
    }

    private fun getDefaultNumOfColumns(): Int {
        return getResources().getInteger(R.integer.subscriptions_default_num_of_columns)
    }

    private fun onTagContextItemSelected(item: MenuItem): Boolean {
        val selectedTag = tagAdapter!!.getLongPressedItem()
        if (selectedTag == null) {
            return false
        }
        return TagMenuHandler.onMenuItemClicked(this, selectedTag, item, tagAdapter!!)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent) {
        loadSubscriptionsAndTags()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUnreadItemsChanged(event: FeedItemEvent) {
        if (event.unreadStatusChanged) {
            loadSubscriptionsAndTags()
        }
    }

    private fun setCollapsingToolbarFlags(flags: Int) {
        val params = collapsingContainer.getLayoutParams() as AppBarLayout.LayoutParams
        params.setScrollFlags(flags)
        collapsingContainer.setLayoutParams(params)
    }

    override fun onEndSelectMode() {
        floatingSelectMenu.setVisibility(View.GONE)
        subscriptionAddButton.setVisibility(View.VISIBLE)
        tagsRecycler.setVisibility(if (shouldShowTags) View.VISIBLE else View.GONE)
        updateFilterVisibility()
        setCollapsingToolbarFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
                or AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                or AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED)
    }

    override fun onStartSelectMode() {
        floatingSelectMenu.setVisibility(View.VISIBLE)
        subscriptionAddButton.setVisibility(View.GONE)
        tagsRecycler.setVisibility(if (shouldShowTags) View.INVISIBLE else View.GONE)
        updateFilterVisibility()
        setCollapsingToolbarFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
                or AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED)
    }

    fun getScrollPosition(): Pair<Int, Int> {
        val layoutManager = subscriptionRecycler.getLayoutManager() as LinearLayoutManager
        val firstItem = layoutManager.findFirstVisibleItemPosition()
        val firstItemView = layoutManager.findViewByPosition(firstItem)
        val topOffset = if (firstItemView == null) 0 else firstItemView.getTop()
        return Pair(firstItem, topOffset)
    }

    fun restoreScrollPosition(scrollPosition: Pair<Int, Int>?) {
        if (scrollPosition == null || (scrollPosition.first == 0 && scrollPosition.second == 0)) {
            return
        }
        val layoutManager = subscriptionRecycler.getLayoutManager() as LinearLayoutManager
        layoutManager.scrollToPositionWithOffset(scrollPosition.first, scrollPosition.second)
    }

    companion object {
        const val TAG = "SubscriptionFragment"
        private const val PREFS = "SubscriptionFragment"
        private const val PREF_NUM_COLUMNS = "columns"
        private const val PREF_LAST_TAG = "last_tag"
        private const val KEY_UP_ARROW = "up_arrow"
        private const val ARGUMENT_STATE = "state"

        private const val MIN_NUM_COLUMNS = 1
        private val COLUMN_CHECKBOX_IDS = intArrayOf(
                R.id.subscription_display_list,
                R.id.subscription_num_columns_2,
                R.id.subscription_num_columns_3,
                R.id.subscription_num_columns_4,
                R.id.subscription_num_columns_5)

        private var scrollPosition: Pair<Int, Int>? = null

        fun newInstance(state: Int): SubscriptionFragment {
            val fragment = SubscriptionFragment()
            val args = Bundle()
            args.putInt(ARGUMENT_STATE, state)
            fragment.setArguments(args)
            return fragment
        }
    }
}
