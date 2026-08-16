package de.danoeh.antennapod.ui.screen.home.sections

import android.content.Context
import android.content.SharedPreferences
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
import de.danoeh.antennapod.event.FeedListUpdateEvent
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.ui.MenuItemUtils
import de.danoeh.antennapod.ui.screen.home.HomeSection
import de.danoeh.antennapod.ui.screen.subscriptions.FeedMenuHandler
import de.danoeh.antennapod.ui.screen.subscriptions.HorizontalFeedListAdapter
import de.danoeh.antennapod.ui.screen.subscriptions.SubscriptionFragment
import de.danoeh.antennapod.ui.statistics.StatisticsFragment
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.ArrayList
import java.util.Collections

class SubscriptionsSection : HomeSection() {
    private var listAdapter: HorizontalFeedListAdapter? = null
    private var disposable: Disposable? = null

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        viewBinding!!.recyclerView.setLayoutManager(
                LinearLayoutManager(getActivity(), RecyclerView.HORIZONTAL, false))
        listAdapter = object : HorizontalFeedListAdapter(getActivity() as MainActivity) {
            override fun onCreateContextMenu(contextMenu: ContextMenu, view: View,
                                             contextMenuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(contextMenu, view, contextMenuInfo)
                MenuItemUtils.setOnClickListeners(contextMenu, this@SubscriptionsSection::onContextItemSelected)
                FeedMenuHandler.onPrepareMenu(contextMenu, Collections.singletonList(getLongPressedItem()))
            }
        }
        listAdapter!!.setDummyViews(NUM_FEEDS)
        viewBinding!!.recyclerView.setAdapter(listAdapter)
        val paddingHorizontal = (12 * getResources().getDisplayMetrics().density).toInt()
        viewBinding!!.recyclerView.setPadding(paddingHorizontal, 0, paddingHorizontal, 0)
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
        (requireActivity() as MainActivity).loadChildFragment(SubscriptionFragment())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent) {
        loadItems()
    }

    override fun getSectionTitle(): String {
        return getString(R.string.home_classics_title)
    }

    override fun getMoreLinkTitle(): String {
        return getString(R.string.subscriptions_label)
    }

    private fun loadItems() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        val prefs: SharedPreferences = getContext()!!
                .getSharedPreferences(StatisticsFragment.PREF_NAME, Context.MODE_PRIVATE)
        val includeMarkedAsPlayed = prefs.getBoolean(StatisticsFragment.PREF_INCLUDE_MARKED_PLAYED, false)

        val threeYearsAgo = System.currentTimeMillis() - 3L * 365L * 24L * 60L * 60L * 1000L
        disposable = Observable.fromCallable {
            DBReader.getStatistics(includeMarkedAsPlayed, threeYearsAgo, Long.MAX_VALUE).feedTime
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ statisticsData ->
                    Collections.sort(statisticsData) { item1, item2 ->
                        java.lang.Long.compare(item2.timePlayed, item1.timePlayed) }
                    val feeds: MutableList<Feed> = ArrayList()
                    var i = 0
                    while (i < statisticsData.size && feeds.size < NUM_FEEDS) {
                        if (statisticsData.get(i).feed.getState() != Feed.STATE_SUBSCRIBED) {
                            i++
                            continue
                        }
                        feeds.add(statisticsData.get(i).feed)
                        i++
                    }
                    listAdapter!!.setDummyViews(0)
                    listAdapter!!.updateData(feeds)
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    companion object {
        const val TAG = "SubscriptionsSection"
        private const val NUM_FEEDS = 8
    }
}
