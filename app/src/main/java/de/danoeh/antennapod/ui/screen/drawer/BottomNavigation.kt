package de.danoeh.antennapod.ui.screen.drawer

import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.IdRes
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.ListPopupWindow
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.internal.ViewUtils
import com.google.android.material.navigation.NavigationBarView
import de.danoeh.antennapod.R
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.event.FeedListUpdateEvent
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.preferences.UserPreferences
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

import java.util.ArrayList

open class BottomNavigation(private val bottomNavigationView: BottomNavigationView) {
    companion object {
        private const val TAG = "BottomNavigation"
    }

    private val context: Context = bottomNavigationView.getContext()

    private var bottomNavigationBadgeLoader: Disposable? = null

    init {
        ViewUtils.doOnApplyWindowInsets(bottomNavigationView) { view, insets, initialPadding -> insets }
    }

    fun buildMenu() {
        val drawerItems = ArrayList(UserPreferences.getVisibleDrawerItemOrder())
        drawerItems.remove(NavListAdapter.SUBSCRIPTION_LIST_TAG)

        val menu = bottomNavigationView.getMenu()
        menu.clear()
        val maxItems = Math.min(5, bottomNavigationView.getMaxItemCount())
        var i = 0
        while (i < drawerItems.size && i < maxItems - 1) {
            val tag = drawerItems[i]
            val item = menu.add(0, NavigationNames.getBottomNavigationItemId(tag),
                    0, context.getString(NavigationNames.getShortLabel(tag)))
            item.setIcon(NavigationNames.getDrawable(tag))
            i++
        }
        val moreItem = menu.add(0, R.id.bottom_navigation_more, 0, context.getString(R.string.overflow_more))
        moreItem.setIcon(R.drawable.dots_vertical)
        bottomNavigationView.setOnItemSelectedListener(bottomItemSelectedListener)
        updateBottomNavigationBadgeIfNeeded()
    }

    private fun updateBottomNavigationBadgeIfNeeded() {
        if (bottomNavigationView.getMenu().findItem(R.id.bottom_navigation_inbox) == null) {
            return
        }
        if (bottomNavigationBadgeLoader != null) {
            bottomNavigationBadgeLoader!!.dispose()
        }
        bottomNavigationBadgeLoader = Observable.fromCallable {
            DBReader.getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.NEW))
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    val badge = bottomNavigationView.getOrCreateBadge(R.id.bottom_navigation_inbox)
                    badge.setVisible(result > 0)
                    badge.setNumber(result)
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    private val bottomItemSelectedListener = NavigationBarView.OnItemSelectedListener { item ->
        if (item.getItemId() == R.id.bottom_navigation_more) {
            showBottomNavigationMorePopup()
            false
        } else {
            onItemSelected(item.getItemId())
            true
        }
    }

    private fun showBottomNavigationMorePopup() {
        val drawerItems = ArrayList(UserPreferences.getVisibleDrawerItemOrder())
        drawerItems.remove(NavListAdapter.SUBSCRIPTION_LIST_TAG)

        val popupMenuItems = ArrayList<MenuItem>()
        var i = bottomNavigationView.getMaxItemCount() - 1
        while (i < drawerItems.size) {
            val tag = drawerItems[i]
            val item = MenuBuilder(context).add(0, NavigationNames.getBottomNavigationItemId(tag),
                    0, context.getString(NavigationNames.getLabel(tag)))
            item.setIcon(NavigationNames.getDrawable(tag))
            popupMenuItems.add(item)
            i++
        }
        val customizeItem = MenuBuilder(context).add(0, R.id.bottom_navigation_customize,
                0, context.getString(R.string.pref_nav_drawer_items_title))
        customizeItem.setIcon(R.drawable.ic_pencil)
        popupMenuItems.add(customizeItem)

        val settingsItem = MenuBuilder(context).add(0, R.id.bottom_navigation_settings,
                0, context.getString(R.string.settings_label))
        settingsItem.setIcon(R.drawable.ic_settings)
        popupMenuItems.add(settingsItem)

        val listPopupWindow = ListPopupWindow(context)
        listPopupWindow.setWidth((250 * context.getResources().getDisplayMetrics().density).toInt())
        listPopupWindow.setAnchorView(bottomNavigationView)
        listPopupWindow.setAdapter(BottomNavigationMoreAdapter(context, popupMenuItems))
        listPopupWindow.setOnItemClickListener { parent, view, position, id ->
            val itemId = popupMenuItems[position].getItemId()
            if (itemId == R.id.bottom_navigation_customize) {
                DrawerPreferencesDialog(context, Runnable { buildMenu() }).show()
            } else {
                onItemSelected(itemId)
            }
            listPopupWindow.dismiss()
        }
        listPopupWindow.setDropDownGravity(Gravity.END or Gravity.BOTTOM)
        listPopupWindow.setModal(true)
        listPopupWindow.show()
    }

    fun updateSelectedItem(tag: String) {
        var bottomSelectedItem = NavigationNames.getBottomNavigationItemId(tag)
        if (bottomNavigationView.getMenu().findItem(bottomSelectedItem) == null) {
            bottomSelectedItem = R.id.bottom_navigation_more
        }
        bottomNavigationView.setOnItemSelectedListener(null)
        bottomNavigationView.setSelectedItemId(bottomSelectedItem)
        bottomNavigationView.setOnItemSelectedListener(bottomItemSelectedListener)
    }

    open fun onItemSelected(@IdRes itemId: Int) {
    }

    fun hide() {
        bottomNavigationView.setVisibility(View.GONE)
    }

    fun onCreateView() {
        EventBus.getDefault().register(this)
    }

    fun onDestroyView() {
        EventBus.getDefault().unregister(this)
        if (bottomNavigationBadgeLoader != null) {
            bottomNavigationBadgeLoader!!.dispose()
            bottomNavigationBadgeLoader = null
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUnreadItemsChanged(event: FeedItemEvent) {
        if (event.unreadStatusChanged) {
            updateBottomNavigationBadgeIfNeeded()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent) {
        updateBottomNavigationBadgeIfNeeded()
    }
}
