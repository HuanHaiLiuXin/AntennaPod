package de.danoeh.antennapod.ui.screen.feed

import android.content.res.Configuration
import android.graphics.LightingColorFilter
import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.appcompat.widget.Toolbar
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.databinding.FeedItemListFragmentBinding
import de.danoeh.antennapod.event.EpisodeDownloadEvent
import de.danoeh.antennapod.event.FeedEvent
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.event.FeedListUpdateEvent
import de.danoeh.antennapod.event.FeedUpdateRunningEvent
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.event.QueueEvent
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.CoverLoader
import de.danoeh.antennapod.ui.FeedItemFilterDialog
import de.danoeh.antennapod.ui.MenuItemUtils
import de.danoeh.antennapod.ui.SelectableAdapter
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.cleaner.HtmlToPlainText
import de.danoeh.antennapod.ui.common.ClipboardUtils
import de.danoeh.antennapod.ui.common.IntentUtils
import de.danoeh.antennapod.ui.common.OnCollapseChangeListener
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemListAdapter
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemViewHolder
import de.danoeh.antennapod.ui.episodeslist.EpisodeMultiSelectActionHandler
import de.danoeh.antennapod.ui.episodeslist.FeedItemMenuHandler
import de.danoeh.antennapod.ui.episodeslist.MoreContentListFooterUtil
import de.danoeh.antennapod.ui.glide.FastBlurTransformation
import de.danoeh.antennapod.ui.screen.SearchFragment
import de.danoeh.antennapod.ui.screen.download.DownloadLogDetailsDialog
import de.danoeh.antennapod.ui.screen.download.DownloadLogFragment
import de.danoeh.antennapod.ui.screen.episode.ItemPagerFragment
import de.danoeh.antennapod.ui.screen.feed.preferences.FeedSettingsFragment
import de.danoeh.antennapod.ui.screen.subscriptions.FeedMenuHandler
import de.danoeh.antennapod.ui.swipeactions.SwipeActions
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.ExecutionException

/**
 * Displays a list of FeedItems.
 */
class FeedItemlistFragment : Fragment(), AdapterView.OnItemClickListener,
        Toolbar.OnMenuItemClickListener, SelectableAdapter.OnSelectModeListener {
    companion object {
        const val TAG = "ItemlistFragment"
        private const val ARGUMENT_FEED_ID = "argument.de.danoeh.antennapod.feed_id"
        private const val KEY_UP_ARROW = "up_arrow"
        protected const val EPISODES_PER_PAGE = 150

        /**
         * Creates new ItemlistFragment which shows the Feeditems of a specific
         * feed. Sets 'showFeedtitle' to false
         *
         * @param feedId The id of the feed to show
         * @return the newly created instance of an ItemlistFragment
         */
        @JvmStatic
        fun newInstance(feedId: Long): FeedItemlistFragment {
            val i = FeedItemlistFragment()
            val b = Bundle()
            b.putLong(ARGUMENT_FEED_ID, feedId)
            i.setArguments(b)
            return i
        }
    }

    protected var page = 1
    protected var isLoadingMore = false
    protected var hasMoreItems = false

    private var adapter: FeedItemListAdapter? = null
    private var swipeActions: SwipeActions? = null
    private var nextPageLoader: MoreContentListFooterUtil? = null
    private var displayUpArrow = false
    private var feedID = 0L
    private var feed: Feed? = null
    private var disposable: Disposable? = null
    private var viewBinding: FeedItemListFragmentBinding? = null
    private var scrollPosition: Pair<Int, Int>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = getArguments()!!
        feedID = args.getLong(ARGUMENT_FEED_ID)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        viewBinding = FeedItemListFragmentBinding.inflate(inflater)
        viewBinding!!.toolbar.inflateMenu(R.menu.feedlist)
        viewBinding!!.toolbar.setOnMenuItemClickListener(this)
        viewBinding!!.toolbar.setOnLongClickListener {
            viewBinding!!.recyclerView.scrollToPosition(5)
            viewBinding!!.recyclerView.post { viewBinding!!.recyclerView.smoothScrollToPosition(0) }
            viewBinding!!.appBar.setExpanded(true)
            false
        }
        displayUpArrow = getParentFragmentManager().getBackStackEntryCount() != 0
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        }
        if (getActivity() is MainActivity) {
            (getActivity() as MainActivity).setupToolbarToggle(viewBinding!!.toolbar, displayUpArrow)
        } else {
            viewBinding!!.toolbar.setNavigationIcon(R.drawable.ic_close)
            viewBinding!!.toolbar.setNavigationOnClickListener { getActivity()!!.finish() }
        }
        updateToolbar()
        setupLoadMoreScrollListener()
        setupHeaderView()

        adapter = FeedItemListAdapter(getActivity()!!)
        adapter!!.setOnSelectModeListener(this)
        viewBinding!!.recyclerView.setAdapter(adapter)
        swipeActions = SwipeActions(this, TAG).attachTo(viewBinding!!.recyclerView)
        viewBinding!!.progressBar.setVisibility(View.VISIBLE)

        val iconTintManager =
                ToolbarIconTintManager(viewBinding!!.toolbar, viewBinding!!.collapsingToolbar)
        viewBinding!!.appBar.addOnOffsetChangedListener(iconTintManager)
        viewBinding!!.appBar.addOnOffsetChangedListener(object : OnCollapseChangeListener(viewBinding!!.collapsingToolbar) {
            override fun onCollapseChanged(isCollapsed: Boolean) {
                if (feed == null) {
                    return
                }
                viewBinding!!.toolbar.setTitle(if (isCollapsed) feed!!.getTitle() else "")
            }
        })

        nextPageLoader = MoreContentListFooterUtil(viewBinding!!.moreContent.moreContentListFooter)
        nextPageLoader!!.setClickListener(object : MoreContentListFooterUtil.Listener {
            override fun onClick() {
                if (feed != null) {
                    FeedUpdateManager.getInstance()!!.runOnce(getContext()!!, feed!!, true)
                }
            }
        })
        viewBinding!!.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(view: RecyclerView, deltaX: Int, deltaY: Int) {
                super.onScrolled(view, deltaX, deltaY)
                updateRecyclerPadding()
            }
        })

        EventBus.getDefault().register(this)

        viewBinding!!.swipeRefresh.setDistanceToTriggerSync(getResources().getInteger(R.integer.swipe_refresh_distance))
        viewBinding!!.swipeRefresh.setOnRefreshListener {
            FeedUpdateManager.getInstance()!!.runOnceOrAsk(requireContext(), feed)
        }

        loadItems()

        viewBinding!!.floatingSelectMenu.inflate(R.menu.episodes_apply_action_speeddial)
        viewBinding!!.floatingSelectMenu.setOnMenuItemClickListener { menuItem ->
            if (adapter!!.getSelectedCount() == 0) {
                EventBus.getDefault().post(MessageEvent(getString(R.string.no_items_selected_message)))
                return@setOnMenuItemClickListener false
            }
            val handler
                    = EpisodeMultiSelectActionHandler(getActivity()!!, menuItem.getItemId())
            Completable.fromAction { handleActionForAllSelectedItems(handler) }
                    .subscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ adapter!!.endSelectMode() },
                            { error -> Log.e(TAG, Log.getStackTraceString(error)) })
            return@setOnMenuItemClickListener true
        }
        return viewBinding!!.getRoot()
    }

    private fun handleActionForAllSelectedItems(handler: EpisodeMultiSelectActionHandler) {
        handler.handleAction(adapter!!.getSelectedItems())
        if (adapter!!.shouldSelectLazyLoadedItems()) {
            var applyPage = page + 1
            var nextPage: List<FeedItem>
            do {
                nextPage = loadMoreData(applyPage)
                handler.handleAction(nextPage)
                applyPage++
            } while (nextPage.size == EPISODES_PER_PAGE)
        }
    }

    private fun loadMoreData(page: Int): List<FeedItem> {
        val loadedFeed = DBReader.getFeed(feedID, true, (page - 1) * EPISODES_PER_PAGE, EPISODES_PER_PAGE)
        return if (loadedFeed != null) loadedFeed.getItems()!! else Collections.emptyList()
    }

    private fun updateRecyclerPadding() {
        val hasMorePages = feed != null && feed!!.isPaged() && feed!!.getNextPageLink() != null
        val pageLoaderVisible = viewBinding!!.recyclerView.isScrolledToBottom() && hasMorePages
        nextPageLoader!!.getRoot().setVisibility(if (pageLoaderVisible) View.VISIBLE else View.GONE)
        var paddingBottom = 0
        if (adapter!!.inActionMode()) {
            paddingBottom = getResources().getDimension(R.dimen.floating_select_menu_height).toInt()
        } else if (pageLoaderVisible) {
            paddingBottom = nextPageLoader!!.getRoot().getMeasuredHeight()
        }
        viewBinding!!.recyclerView.setPadding(viewBinding!!.recyclerView.getPaddingLeft(), 0,
                viewBinding!!.recyclerView.getPaddingRight(), paddingBottom)
    }

    override fun onPause() {
        super.onPause()
        scrollPosition = viewBinding!!.recyclerView.getScrollPosition()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        EventBus.getDefault().unregister(this)
        if (disposable != null) {
            disposable!!.dispose()
        }
        adapter!!.endSelectMode()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    private fun updateToolbar() {
        if (feed == null) {
            return
        }
        viewBinding!!.toolbar.getMenu().findItem(R.id.visit_website_item)!!.setVisible(feed!!.getLink() != null)
        viewBinding!!.toolbar.getMenu().findItem(R.id.refresh_complete_item)!!.setVisible(feed!!.isPaged())
        if (StringUtils.isBlank(feed!!.getLink())) {
            viewBinding!!.toolbar.getMenu().findItem(R.id.visit_website_item)!!.setVisible(false)
        }
        if (feed!!.getState() == Feed.STATE_NOT_SUBSCRIBED) {
            viewBinding!!.toolbar.getMenu().findItem(R.id.sort_items)!!.setVisible(false)
            viewBinding!!.toolbar.getMenu().findItem(R.id.refresh_item)!!.setVisible(false)
            viewBinding!!.toolbar.getMenu().findItem(R.id.action_search)!!.setVisible(false)
        } else if (feed!!.getState() == Feed.STATE_ARCHIVED) {
            viewBinding!!.toolbar.getMenu().findItem(R.id.sort_items)!!.setVisible(false)
        }
        FeedMenuHandler.onPrepareMenu(viewBinding!!.toolbar.getMenu(), Collections.singletonList(feed))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val horizontalSpacing = getResources().getDimension(R.dimen.additional_horizontal_spacing).toInt()
        viewBinding!!.header.headerContainer.setPadding(
                horizontalSpacing, viewBinding!!.header.headerContainer.getPaddingTop(),
                horizontalSpacing, viewBinding!!.header.headerContainer.getPaddingBottom())
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (feed == null) {
            EventBus.getDefault().post(getString(R.string.please_wait_for_data))
            return true
        }
        if (item.getItemId() == R.id.visit_website_item) {
            IntentUtils.openInBrowser(getContext()!!, feed!!.getLink()!!)
            return true
        } else if (item.getItemId() == R.id.refresh_item) {
            FeedUpdateManager.getInstance()!!.runOnceOrAsk(getContext()!!, feed!!)
            return true
        } else if (item.getItemId() == R.id.refresh_complete_item) {
            Thread {
                feed!!.setNextPageLink(feed!!.getDownloadUrl())
                feed!!.setPageNr(0)
                try {
                    DBWriter.resetPagedFeedPage(feed!!)!!.get()
                    FeedUpdateManager.getInstance()!!.runOnce(getContext()!!, feed!!)
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            }.start()
            return true
        } else if (item.getItemId() == R.id.sort_items) {
            SingleFeedSortDialog.newInstance(feed!!).show(getChildFragmentManager(), "SortDialog")
            return true
        } else if (item.getItemId() == R.id.remove_archive_feed || item.getItemId() == R.id.remove_restore_feed) {
            RemoveFeedDialogClose(Collections.singletonList(feed!!)).show(getParentFragmentManager(), null)
            return true
        } else if (item.getItemId() == R.id.action_search) {
            (getActivity() as MainActivity).loadChildFragment(SearchFragment.newInstance(feed!!.getId(), feed!!.getTitle()))
            return true
        }

        return FeedMenuHandler.onMenuItemClicked(this, item.getItemId(), feed!!)
    }

    class RemoveFeedDialogClose : RemoveFeedDialog {
        constructor(feeds: List<Feed>) : super(feeds)

        constructor() : super()

        override fun onRemoveButtonPressed() {
            // Make sure fragment is hidden before actually starting to delete
            (getActivity() as MainActivity).loadFragment(UserPreferences.getDefaultPage()!!, null)
            getActivity()!!.getSupportFragmentManager().executePendingTransactions()
            super.onRemoveButtonPressed()
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

    private fun setupLoadMoreScrollListener() {
        viewBinding!!.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(view: RecyclerView, deltaX: Int, deltaY: Int) {
                super.onScrolled(view, deltaX, deltaY)
                if (!isLoadingMore && hasMoreItems && viewBinding!!.recyclerView.isScrolledToBottom()) {
                    /* The end of the list has been reached. Load more data. */
                    page++
                    loadMoreItems()
                    isLoadingMore = true
                }
            }
        })
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val activity = getActivity() as MainActivity
        activity.loadChildFragment(ItemPagerFragment.newInstance(feed!!.getItems()!!, feed!!.getItems()!!.get(position)))
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: FeedEvent) {
        Log.d(TAG, "onEvent() called with: " + "event = [" + event + "]")
        if (event.feedId == feedID) {
            loadItems()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]")
        if (event.unreadStatusChanged && event.items.isEmpty()) {
            updateUi()
            return
        }
        if (feed == null || feed!!.getItems() == null) {
            return
        }
        val items = feed!!.getItems() as ArrayList<FeedItem>
        for (i in 0 until event.items.size) {
            val item = event.items.get(i)
            val pos = FeedItemEvent.indexOfItemWithId(feed!!.getItems()!!, item.getId())
            if (pos >= 0) {
                items.removeAt(pos)
                items.add(pos, item)
                adapter!!.notifyItemChangedCompat(pos)
            } else if (item.getFeedId() == feedID) {
                // Filtered-out item of this feed was touched, reload all
                updateUi()
                return
            }
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: EpisodeDownloadEvent) {
        if (feed == null) {
            return
        }
        for (downloadUrl in event.getUrls()) {
            val pos = EpisodeDownloadEvent.indexOfItemWithDownloadUrl(feed!!.getItems()!!, downloadUrl)
            if (pos >= 0) {
                adapter!!.notifyItemChangedCompat(pos)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        for (i in 0 until adapter!!.getItemCount()) {
            val holder = viewBinding!!.recyclerView.findViewHolderForAdapterPosition(i) as EpisodeItemViewHolder?
            if (holder != null && holder.isPlayingItem()) {
                holder.notifyPlaybackPositionUpdated(event)
                break
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onQueueChanged(event: QueueEvent) {
        updateUi()
    }

    override fun onStartSelectMode() {
        viewBinding!!.floatingSelectMenu.setVisibility(View.VISIBLE)
        swipeActions!!.detach()
        updateRecyclerPadding()
        updateToolbar()
    }

    override fun onEndSelectMode() {
        viewBinding!!.floatingSelectMenu.setVisibility(View.GONE)
        updateRecyclerPadding()
        swipeActions!!.attachTo(viewBinding!!.recyclerView)
    }

    private fun updateUi() {
        loadItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusChanged(event: PlayerStatusEvent) {
        updateUi()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent) {
        if (feed != null && event.contains(feed!!)) {
            updateUi()
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedUpdateRunningEvent) {
        nextPageLoader!!.setLoadingState(event.isFeedUpdateRunning)
        if (!event.isFeedUpdateRunning) {
            nextPageLoader!!.getRoot().setVisibility(View.GONE)
        }
        viewBinding!!.swipeRefresh.setRefreshing(event.isFeedUpdateRunning)
    }

    private fun refreshHeaderView() {
        if (viewBinding == null || feed == null) {
            Log.e(TAG, "Unable to refresh header view")
            return
        }
        loadFeedImage()
        if (feed!!.hasLastUpdateFailed()) {
            viewBinding!!.header.txtvFailure.setVisibility(View.VISIBLE)
        } else {
            viewBinding!!.header.txtvFailure.setVisibility(View.GONE)
        }
        if ((!feed!!.getPreferences()!!.getKeepUpdated() && feed!!.getState() != Feed.STATE_NOT_SUBSCRIBED)
                || feed!!.getState() == Feed.STATE_ARCHIVED) {
            viewBinding!!.header.txtvUpdatesDisabled.setText(R.string.updates_disabled_label)
            viewBinding!!.header.txtvUpdatesDisabled.setVisibility(View.VISIBLE)
        } else {
            viewBinding!!.header.txtvUpdatesDisabled.setVisibility(View.GONE)
        }
        viewBinding!!.header.txtvTitle.setText(feed!!.getTitle())
        viewBinding!!.header.txtvAuthor.setText(feed!!.getAuthor())
        viewBinding!!.header.descriptionContainer.setVisibility(View.GONE)
        if (feed!!.getState() == Feed.STATE_NOT_SUBSCRIBED) {
            viewBinding!!.header.descriptionContainer.setVisibility(View.VISIBLE)
            viewBinding!!.header.headerDescriptionLabel.setText(HtmlToPlainText.getPlainText(feed!!.getDescription()))
            viewBinding!!.header.subscribeNagLabel.setVisibility(
                    if (feed!!.hasInteractedWithEpisode()) View.VISIBLE else View.GONE)
        } else if (feed!!.getItemFilter() != null) {
            val filter = feed!!.getItemFilter()!!
            if (filter.getValues().size > 0) {
                viewBinding!!.header.txtvInformation.setText(R.string.filtered_label)
                viewBinding!!.header.txtvInformation.setOnClickListener {
                    FeedItemFilterDialog.newInstance(feed!!).show(getChildFragmentManager(), null) }
                viewBinding!!.header.txtvInformation.setVisibility(View.VISIBLE)
            } else {
                viewBinding!!.header.txtvInformation.setVisibility(View.GONE)
            }
        } else {
            viewBinding!!.header.txtvInformation.setVisibility(View.GONE)
        }
        val isNotSubscribed = feed!!.getState() == Feed.STATE_NOT_SUBSCRIBED
        val isArchived = feed!!.getState() == Feed.STATE_ARCHIVED
        val showSettingsButtons = !isNotSubscribed && !isArchived
        viewBinding!!.header.butShowInfo.setVisibility(if (!isNotSubscribed) View.VISIBLE else View.GONE)
        viewBinding!!.header.butFilter.setVisibility(if (showSettingsButtons) View.VISIBLE else View.GONE)
        viewBinding!!.header.butShowSettings.setVisibility(if (showSettingsButtons) View.VISIBLE else View.GONE)
        viewBinding!!.header.butSubscribe.setVisibility(if (isNotSubscribed) View.VISIBLE else View.GONE)
        viewBinding!!.header.butRestore.setVisibility(if (isArchived) View.VISIBLE else View.GONE)

        if (isNotSubscribed && feed!!.getLastRefreshAttempt() < System.currentTimeMillis() - 1000L * 3600 * 24) {
            FeedUpdateManager.getInstance()!!.runOnce(getContext()!!, feed!!, true)
        }
    }

    private fun setupHeaderView() {
        // https://github.com/bumptech/glide/issues/529
        viewBinding!!.imgvBackground.setColorFilter(LightingColorFilter(0xff666666.toInt(), 0x000000))
        viewBinding!!.header.butShowInfo.setOnClickListener { showFeedInfo() }
        viewBinding!!.header.imgvCover.setOnClickListener { showFeedInfo() }
        viewBinding!!.header.headerDescriptionLabel.setOnClickListener { showFeedInfo() }
        viewBinding!!.header.butSubscribe.setOnClickListener {
            if (feed == null) {
                return@setOnClickListener
            }
            DBWriter.setFeedState(getContext()!!, feed!!, Feed.STATE_SUBSCRIBED)
            val mainActivityStarter = MainActivityStarter(getContext()!!)
            mainActivityStarter.withOpenFeed(feed!!.getId())
            getActivity()!!.finish()
            startActivity(mainActivityStarter.getIntent())
        }
        viewBinding!!.header.butRestore.setOnClickListener {
            if (feed == null) {
                return@setOnClickListener
            }
            DBWriter.setFeedState(getContext()!!, feed!!, Feed.STATE_SUBSCRIBED)
        }
        viewBinding!!.header.butShowSettings.setOnClickListener {
            if (feed == null) {
                return@setOnClickListener
            }
            (getActivity() as MainActivity).loadChildFragment(FeedSettingsFragment.newInstance(feed!!))
        }
        viewBinding!!.header.butFilter.setOnClickListener {
            if (feed == null) {
                return@setOnClickListener
            }
            FeedItemFilterDialog.newInstance(feed!!).show(getChildFragmentManager(), null)
        }
        viewBinding!!.header.txtvFailure.setOnClickListener { showErrorDetails() }
        viewBinding!!.header.txtvAuthor.setOnLongClickListener {
            ClipboardUtils.copyText(viewBinding!!.header.txtvAuthor)
            true
        }
        viewBinding!!.header.txtvTitle.setOnLongClickListener {
            ClipboardUtils.copyText(viewBinding!!.header.txtvTitle)
            true
        }
    }

    private fun showErrorDetails() {
        Maybe.fromCallable<DownloadResult> {
            val feedDownloadLog = DBReader.getFeedDownloadLog(feedID, 1)
            if (feedDownloadLog.isEmpty() || feedDownloadLog.get(0).isSuccessful()) {
                null
            } else {
                feedDownloadLog.get(0)
            }
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { downloadStatus -> DownloadLogDetailsDialog.newInstance(downloadStatus, false)
                            .show(getChildFragmentManager(), DownloadLogDetailsDialog.TAG) },
                    { error -> error.printStackTrace() },
                    { DownloadLogFragment().show(getChildFragmentManager(), DownloadLogFragment.TAG) })
    }

    private fun showFeedInfo() {
        if (feed == null) {
            return
        }
        val fragment = FeedInfoFragment.newInstance(feed!!)
        if (getActivity() is MainActivity) {
            (getActivity() as MainActivity).loadChildFragment(fragment)
        } else {
            getActivity()!!.getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, fragment, "Info")
                    .addToBackStack("Info")
                    .commitAllowingStateLoss()
        }
    }

    private fun loadFeedImage() {
        Glide.with(this)
                .load(feed!!.getImageUrl())
                .apply(RequestOptions()
                    .placeholder(R.color.image_readability_tint)
                    .error(R.color.image_readability_tint)
                    .transform(FastBlurTransformation())
                    .dontAnimate())
                .into(viewBinding!!.imgvBackground)

        Glide.with(this)
                .load(feed!!.getImageUrl())
                .apply(RequestOptions()
                    .placeholder(R.color.light_gray)
                    .error(R.color.light_gray)
                    .fitCenter()
                    .dontAnimate())
                .into(viewBinding!!.header.imgvCover)
    }

    private fun loadItems() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Observable.fromCallable<Pair<Feed, Int>>(
                {
                    feed = DBReader.getFeed(feedID, true, 0, page * EPISODES_PER_PAGE)
                    val count = DBReader.getFeedEpisodeCount(feed!!.getId(), feed!!.getItemFilter()!!)
                    Pair(feed!!, count)
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { result ->
                        hasMoreItems = !(page == 1 && feed!!.getItems()!!.size < EPISODES_PER_PAGE)
                        swipeActions!!.setFilter(feed!!.getItemFilter())
                        refreshHeaderView()
                        viewBinding!!.progressBar.setVisibility(View.GONE)
                        adapter!!.setDummyViews(0)
                        adapter!!.updateItems(feed!!.getItems()!!)
                        adapter!!.setTotalNumberOfItems(result.second)
                        updateToolbar()
                        viewBinding!!.recyclerView.restoreScrollPosition(scrollPosition)
                        scrollPosition = null
                    }, { error ->
                        feed = null
                        refreshHeaderView()
                        adapter!!.setDummyViews(0)
                        adapter!!.updateItems(Collections.emptyList())
                        updateToolbar()
                        Log.e(TAG, Log.getStackTraceString(error))
                    })
    }

    private fun loadMoreItems() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        isLoadingMore = true
        adapter!!.setDummyViews(1)
        adapter!!.notifyItemInserted(adapter!!.getItemCount() - 1)
        disposable = Observable.fromCallable<List<FeedItem>> { loadMoreData(page) }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { items ->
                            if (items.size < EPISODES_PER_PAGE) {
                                hasMoreItems = false
                            }
                            (feed!!.getItems() as ArrayList<FeedItem>).addAll(items)
                            adapter!!.setDummyViews(0)
                            adapter!!.updateItems(feed!!.getItems()!!)
                            if (adapter!!.shouldSelectLazyLoadedItems()) {
                                adapter!!.setSelected(feed!!.getItems()!!.size - items.size,
                                        feed!!.getItems()!!.size, true)
                            }
                        }, { error ->
                            adapter!!.setDummyViews(0)
                            adapter!!.updateItems(Collections.emptyList())
                            Log.e(TAG, Log.getStackTraceString(error))
                        }, {
                            // Make sure to not always load 2 pages at once
                            viewBinding!!.recyclerView.post { isLoadingMore = false }
                        })
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onKeyUp(event: KeyEvent) {
        if (!isAdded() || !isVisible() || !isMenuVisible()) {
            return
        }
        when (event.getKeyCode()) {
            KeyEvent.KEYCODE_T ->
                viewBinding!!.recyclerView.smoothScrollToPosition(0)
            KeyEvent.KEYCODE_B ->
                viewBinding!!.recyclerView.smoothScrollToPosition(adapter!!.getItemCount() - 1)
            else -> Unit
        }
    }

    private inner class FeedItemListAdapter(mainActivity: FragmentActivity) : EpisodeItemListAdapter(mainActivity) {

        override fun beforeBindViewHolder(holder: EpisodeItemViewHolder, pos: Int) {
            holder.coverHolder.setVisibility(View.GONE) // Load it ourselves
        }

        override fun afterBindViewHolder(holder: EpisodeItemViewHolder, pos: Int) {
            holder.coverHolder.setVisibility(View.VISIBLE)
            CoverLoader()
                    .withUri(holder.getFeedItem()!!.getImageLocation()) // Ignore "Show episode cover" setting
                    .withFallbackUri(holder.getFeedItem()!!.getFeed()!!.getImageUrl())
                    .withPlaceholderView(holder.placeholder)
                    .withCoverView(holder.cover)
                    .load()
            if (feed!!.getState() != Feed.STATE_SUBSCRIBED) {
                holder.secondaryActionButton.setVisibility(View.GONE)
            }
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
            super.onCreateContextMenu(menu, v, menuInfo)
            if (!inActionMode() && feed!!.getState() != Feed.STATE_NOT_SUBSCRIBED) {
                menu.findItem(R.id.multi_select)!!.setVisible(true)
            }
            MenuItemUtils.setOnClickListeners(menu, this@FeedItemlistFragment::onContextItemSelected)
        }

        override fun onSelectedItemsUpdated() {
            super.onSelectedItemsUpdated()
            FeedItemMenuHandler.onPrepareMenu(viewBinding!!.floatingSelectMenu.getMenu(), getSelectedItems())
            viewBinding!!.floatingSelectMenu.updateItemVisibility()
        }
    }
}
