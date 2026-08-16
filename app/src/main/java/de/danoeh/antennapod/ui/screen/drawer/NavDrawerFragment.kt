package de.danoeh.antennapod.ui.screen.drawer

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar

import androidx.annotation.VisibleForTesting
import androidx.core.graphics.Insets
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.net.download.service.episode.autodownload.EpisodeCleanupAlgorithmFactory
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.ui.common.ConfirmationDialog
import de.danoeh.antennapod.ui.screen.subscriptions.FeedMenuHandler
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

import java.util.ArrayList
import java.util.Collections
import java.util.HashSet

import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity
import de.danoeh.antennapod.ui.MenuItemUtils
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.NavDrawerData
import de.danoeh.antennapod.ui.screen.feed.RemoveFeedDialog
import de.danoeh.antennapod.ui.screen.feed.RenameFeedDialog
import de.danoeh.antennapod.ui.screen.subscriptions.SubscriptionsFilterDialog
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.event.FeedListUpdateEvent
import de.danoeh.antennapod.event.QueueEvent
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.common.ThemeUtils
import de.danoeh.antennapod.ui.screen.home.HomeFragment
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

class NavDrawerFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        @VisibleForTesting
        const val PREF_LAST_FRAGMENT_TAG = "prefLastFragmentTag"
        private const val PREF_OPEN_FOLDERS = "prefOpenFolders"
        @VisibleForTesting
        const val PREF_NAME = "NavDrawerPrefs"
        const val TAG = "NavDrawerFragment"

        @JvmStatic
        fun saveLastNavFragment(context: Context, tag: String?) {
            Log.d(TAG, "saveLastNavFragment(tag: " + tag + ")")
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val edit = prefs.edit()
            if (tag != null) {
                edit.putString(PREF_LAST_FRAGMENT_TAG, tag)
            } else {
                edit.remove(PREF_LAST_FRAGMENT_TAG)
            }
            edit.apply()
        }

        @JvmStatic
        fun getLastNavFragment(context: Context): String? {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREF_LAST_FRAGMENT_TAG, HomeFragment.TAG)
        }
    }

    private var navDrawerData: NavDrawerData? = null
    private var reclaimableSpace = 0
    private var flatItemList: List<DrawerItem>? = null
    private var contextPressedItem: DrawerItem? = null
    private var navAdapter: NavListAdapter? = null
    private var disposable: Disposable? = null
    private lateinit var progressBar: ProgressBar
    private var openFolders: MutableSet<String> = HashSet()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        val root = inflater.inflate(R.layout.nav_list, container, false)
        setupDrawerRoundBackground(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            var navigationBarHeight = 0f
            val activity = getActivity()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && activity != null) {
                navigationBarHeight = if (getActivity()!!.getWindow().getNavigationBarDividerColor()
                        == Color.TRANSPARENT) 0f else 1 * getResources().getDisplayMetrics().density
                // Assuming the divider is 1dp in height
            }
            val bottomInset = Math.max(0f, Math.round(bars.bottom - navigationBarHeight).toFloat())
            (view.getLayoutParams() as ViewGroup.MarginLayoutParams).bottomMargin = bottomInset.toInt()
            insets
        }

        val preferences = getContext()!!.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        openFolders = HashSet(preferences.getStringSet(PREF_OPEN_FOLDERS, HashSet())!!) // Must not modify

        progressBar = root.findViewById(R.id.progressBar)
        val navList = root.findViewById<RecyclerView>(R.id.nav_list)
        navAdapter = NavListAdapter(itemAccess, getActivity()!!)
        navAdapter!!.setHasStableIds(true)
        navList.setAdapter(navAdapter!!)
        navList.setLayoutManager(LinearLayoutManager(getContext()!!))

        root.findViewById<View>(R.id.nav_settings).setOnClickListener {
            startActivity(Intent(getActivity(), PreferenceActivity::class.java))
        }

        preferences.registerOnSharedPreferenceChangeListener(this)
        return root
    }

    private fun setupDrawerRoundBackground(root: View) {
        // Akin to this logic:
        //   https://github.com/material-components/material-components-android/blob/8938da8c/lib/java/com/google/android/material/navigation/NavigationView.java#L405
        val shapeBuilder = ShapeAppearanceModel.builder()
        val cornerSize = getResources().getDimension(R.dimen.drawer_corner_size)
        val isRtl = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
        if (isRtl) {
            shapeBuilder.setTopLeftCornerSize(cornerSize).setBottomLeftCornerSize(cornerSize)
        } else {
            shapeBuilder.setTopRightCornerSize(cornerSize).setBottomRightCornerSize(cornerSize)
        }
        val drawable = MaterialShapeDrawable(shapeBuilder.build())
        val themeColor = ThemeUtils.getColorFromAttr(root.getContext(), android.R.attr.colorBackground)
        drawable.setFillColor(ColorStateList.valueOf(themeColor))
        root.setBackground(drawable)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.getDefault().unregister(this)
        if (disposable != null) {
            disposable!!.dispose()
        }
        getContext()!!.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val inflater = getActivity()!!.getMenuInflater()
        if (contextPressedItem!!.isFeed()) {
            menu.setHeaderTitle(contextPressedItem!!.asFeed()!!.getTitle())
            inflater.inflate(R.menu.nav_feed_context, menu)
            // episodes are not loaded, so we cannot check if the podcast has new or unplayed ones!
            FeedMenuHandler.onPrepareMenu(menu, Collections.singletonList(contextPressedItem!!.asFeed()))
        } else if (FeedPreferences.TAG_UNTAGGED == contextPressedItem!!.asTag()!!.getTitle()) {
            return
        } else {
            menu.setHeaderTitle(contextPressedItem!!.asTag()!!.getTitle())
            inflater.inflate(R.menu.nav_folder_context, menu)
        }
        MenuItemUtils.setOnClickListeners(menu) { item -> onContextItemSelected(item) }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val pressedItem = contextPressedItem
        contextPressedItem = null
        if (pressedItem == null) {
            return false
        }
        if (pressedItem.isFeed()) {
            return onFeedContextMenuClicked(pressedItem.asFeed()!!, item)
        } else {
            return onTagContextMenuClicked(pressedItem.asTag()!!, item)
        }
    }

    private fun onFeedContextMenuClicked(feed: Feed, item: MenuItem): Boolean {
        val itemId = item.getItemId()
        if (itemId == R.id.remove_archive_feed || itemId == R.id.remove_restore_feed) {
            RemoveFeedDialogClose(Collections.singletonList(feed)).show(getParentFragmentManager(), null)
            return true
        }
        if (FeedMenuHandler.onMenuItemClicked(this, itemId, feed)) {
            return true
        }
        return super.onContextItemSelected(item)
    }

    class RemoveFeedDialogClose : RemoveFeedDialog {
        constructor(feeds: List<Feed>) : super(feeds)

        constructor() : super()

        override fun onRemoveButtonPressed() {
            if (feeds.get(0).getId().toString() == getLastNavFragment(getContext()!!)) {
                // Make sure fragment is hidden before actually starting to delete
                (getActivity() as MainActivity).loadFragment(UserPreferences.getDefaultPage()!!, null)
                getActivity()!!.getSupportFragmentManager().executePendingTransactions()
            }
            super.onRemoveButtonPressed()
        }
    }

    private fun onTagContextMenuClicked(drawerItem: NavDrawerData.TagItem, item: MenuItem): Boolean {
        val itemId = item.getItemId()
        if (itemId == R.id.rename_folder_item) {
            RenameFeedDialog(getActivity()!!, drawerItem).show()
            return true
        } else if (itemId == R.id.delete_folder_item) {
            val dialog = object : ConfirmationDialog(getContext()!!, R.string.delete_tag_label,
                    getString(R.string.delete_tag_confirmation, drawerItem.getTitle())) {
                override fun onConfirmButtonPressed(dialog: DialogInterface) {
                    for (feed in drawerItem.getFeeds()) {
                        val preferences = feed.getPreferences()!!
                        (preferences.getTags() as MutableSet<String>).remove(drawerItem.getTitle())
                        DBWriter.setFeedPreferences(preferences)
                    }
                }
            }
            dialog.createNewDialog().show()

            return true
        }
        return super.onContextItemSelected(item)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUnreadItemsChanged(event: FeedItemEvent) {
        if (event.unreadStatusChanged) {
            loadData()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent) {
        loadData()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onQueueChanged(event: QueueEvent) {
        Log.d(TAG, "onQueueChanged(" + event + ")")
        // we are only interested in the number of queue items, not download status or position
        if (event.action == QueueEvent.Action.DELETED_MEDIA
                || event.action == QueueEvent.Action.SORTED
                || event.action == QueueEvent.Action.MOVED) {
            return
        }
        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private val itemAccess = object : NavListAdapter.ItemAccess {
        override fun getCount(): Int {
            if (flatItemList != null) {
                return flatItemList!!.size
            } else {
                return 0
            }
        }

        override fun getItem(position: Int): DrawerItem? {
            if (flatItemList != null && 0 <= position && position < flatItemList!!.size) {
                return flatItemList!![position]
            } else {
                return null
            }
        }

        override fun isSelected(position: Int): Boolean {
            val lastNavFragment = getLastNavFragment(getContext()!!)
            if (position < navAdapter!!.getSubscriptionOffset()) {
                return navAdapter!!.getFragmentTags()[position] == lastNavFragment
            } else if (StringUtils.isNumeric(lastNavFragment)) { // last fragment was not a list, but a feed
                val feedId = lastNavFragment!!.toLong()
                if (navDrawerData != null) {
                    val itemToCheck = flatItemList!![
                            position - navAdapter!!.getSubscriptionOffset()]
                    if (itemToCheck.isFeed()) {
                        // When the same feed is displayed multiple times, it should be highlighted multiple times.
                        return itemToCheck.asFeed()!!.getId() == feedId
                    }
                }
            }
            return false
        }

        override fun getQueueSize(): Int {
            return if (navDrawerData != null) navDrawerData!!.queueSize else 0
        }

        override fun getNumberOfNewItems(): Int {
            return if (navDrawerData != null) navDrawerData!!.numNewItems else 0
        }

        override fun getNumberOfDownloadedItems(): Int {
            return if (navDrawerData != null) navDrawerData!!.numDownloadedItems else 0
        }

        override fun getReclaimableItems(): Int {
            return reclaimableSpace
        }

        override fun getFeedCounterSum(): Int {
            if (navDrawerData == null) {
                return 0
            }
            var sum = 0
            for (counter in navDrawerData!!.feedCounters.values) {
                sum += counter
            }
            return sum
        }

        override fun onItemClick(position: Int) {
            val viewType = navAdapter!!.getItemViewType(position)
            if (viewType != NavListAdapter.VIEW_TYPE_SECTION_DIVIDER) {
                if (position < navAdapter!!.getSubscriptionOffset()) {
                    val tag = navAdapter!!.getFragmentTags()[position]
                    (getActivity() as MainActivity).loadFragment(tag, null)
                    (getActivity() as MainActivity).getBottomSheet()
                            .setState(BottomSheetBehavior.STATE_COLLAPSED)
                } else {
                    val pos = position - navAdapter!!.getSubscriptionOffset()
                    val clickedItem = flatItemList!![pos]

                    if (clickedItem.isFeed()) {
                        val feedId = clickedItem.asFeed()!!.getId()
                        (getActivity() as MainActivity).loadFeedFragmentById(feedId, null)
                        (getActivity() as MainActivity).getBottomSheet()
                                .setState(BottomSheetBehavior.STATE_COLLAPSED)
                    } else {
                        val folder = clickedItem.asTag()!!
                        if (openFolders.contains(folder.getTitle())) {
                            openFolders.remove(folder.getTitle())
                        } else {
                            openFolders.add(folder.getTitle())
                        }

                        getContext()!!.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                                .edit()
                                .putStringSet(PREF_OPEN_FOLDERS, openFolders)
                                .apply()

                        disposable = Observable.fromCallable {
                            makeFlatDrawerData(navDrawerData!!.tags, navDrawerData!!.feedCounters)
                        }
                                .subscribeOn(Schedulers.computation())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                        { result ->
                                            flatItemList = result
                                            navAdapter!!.notifyDataSetChanged()
                                        }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
                    }
                }
            } else if (UserPreferences.getSubscriptionsFilter().isEnabled()
                    && navAdapter!!.showSubscriptionList) {
                SubscriptionsFilterDialog().show(getChildFragmentManager(), "filter")
            }
        }

        override fun onItemLongClick(position: Int): Boolean {
            if (position < navAdapter!!.getFragmentTags().size) {
                DrawerPreferencesDialog(getContext()!!, Runnable {
                    navAdapter!!.notifyDataSetChanged()
                    if (UserPreferences.getHiddenDrawerItems().contains(getLastNavFragment(getContext()!!))) {
                        MainActivityStarter(getContext()!!)
                                .withFragmentLoaded(UserPreferences.getDefaultPage()!!)
                                .withClearBackStack()
                                .withDrawerOpen()
                                .start()
                    }
                }).show()
                return true
            } else {
                contextPressedItem = flatItemList!![position - navAdapter!!.getSubscriptionOffset()]
                return false
            }
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
            this@NavDrawerFragment.onCreateContextMenu(menu, v, menuInfo)
        }
    }

    private fun loadData() {
        disposable = Observable.fromCallable {
            val data = DBReader.getNavDrawerData(UserPreferences.getSubscriptionsFilter(),
                    UserPreferences.getFeedOrder(), UserPreferences.getFeedCounterSetting(),
                    Feed.STATE_SUBSCRIBED)
            reclaimableSpace = EpisodeCleanupAlgorithmFactory.build().getReclaimableItems()
            return@fromCallable Pair(data, makeFlatDrawerData(data.tags, data.feedCounters))
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { result ->
                            navDrawerData = result.first
                            flatItemList = result.second
                            navAdapter!!.notifyDataSetChanged()
                            progressBar.setVisibility(View.GONE) // Stays hidden once there is something in the list
                        }, { error ->
                            Log.e(TAG, Log.getStackTraceString(error))
                            progressBar.setVisibility(View.GONE)
                        })
    }

    private fun makeFlatDrawerData(tags: List<NavDrawerData.TagItem>,
                                   feedCounters: Map<Long, Int>?): List<DrawerItem> {
        val flatItems = ArrayList<DrawerItem>()
        for (tag in tags) {
            if (FeedPreferences.TAG_ROOT == tag.getTitle()) {
                for (feed in tag.getFeeds()) {
                    flatItems.add(DrawerItem(feed, feedCounter(feed, feedCounters), 0))
                }
                break
            }
        }
        for (tag in tags) {
            if (FeedPreferences.TAG_ROOT == tag.getTitle()
                    || FeedPreferences.TAG_UNTAGGED == tag.getTitle()) {
                continue
            }
            val tagItem = DrawerItem(tag)
            flatItems.add(tagItem)
            var counter = 0
            tag.setOpen(openFolders.contains(tag.getTitle()))
            for (feed in tag.getFeeds()) {
                counter += feedCounter(feed, feedCounters)
                if (tag.isOpen()) {
                    flatItems.add(DrawerItem(feed, feedCounter(feed, feedCounters), 1))
                }
            }
            tagItem.setCounter(counter)
        }
        return flatItems
    }

    private fun feedCounter(feed: Feed, feedCounters: Map<Long, Int>?): Int {
        if (feedCounters == null) {
            return 0
        }
        return if (feedCounters.containsKey(feed.getId())) feedCounters[feed.getId()]!! else 0
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (PREF_LAST_FRAGMENT_TAG == key) {
            navAdapter!!.notifyDataSetChanged() // Update selection
        }
    }
}
