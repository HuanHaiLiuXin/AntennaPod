package de.danoeh.antennapod.ui.statistics.subscriptions

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.danoeh.antennapod.event.StatisticsEvent
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.ui.statistics.R
import de.danoeh.antennapod.ui.statistics.StatisticsFragment
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

import java.util.Collections

/**
 * Displays the 'playback statistics' screen
 */
class SubscriptionStatisticsFragment : Fragment() {
    companion object {
        private const val TAG = "SubscriptionStatisticsFragment"
    }

    private var disposable: Disposable? = null
    private var feedStatisticsList: RecyclerView? = null
    private var progressBar: ProgressBar? = null
    private var listAdapter: PlaybackStatisticsListAdapter? = null
    private var statisticsResult: DBReader.StatisticsResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.statistics_fragment, container, false)
        feedStatisticsList = root.findViewById(R.id.statistics_list)
        progressBar = root.findViewById(R.id.progressBar)
        listAdapter = PlaybackStatisticsListAdapter(this)
        feedStatisticsList!!.setLayoutManager(LinearLayoutManager(getContext()))
        feedStatisticsList!!.setAdapter(listAdapter)
        EventBus.getDefault().register(this)
        return root
    }

    override fun onStart() {
        super.onStart()
        refreshStatistics()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.getDefault().unregister(this)
        if (disposable != null) {
            disposable!!.dispose()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun statisticsEvent(event: StatisticsEvent) {
        refreshStatistics()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.statistics_reset).setVisible(true)
        menu.findItem(R.id.statistics_filter).setVisible(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.getItemId() == R.id.statistics_filter) {
            if (statisticsResult != null) {
                StatisticsFilterDialog(getContext()!!, statisticsResult!!.oldestDate).show()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun refreshStatistics() {
        progressBar!!.setVisibility(View.VISIBLE)
        feedStatisticsList!!.setVisibility(View.GONE)
        loadStatistics()
    }

    private fun loadStatistics() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        val prefs: SharedPreferences = getContext()!!.getSharedPreferences(StatisticsFragment.PREF_NAME, Context.MODE_PRIVATE)
        val includeMarkedAsPlayed = prefs.getBoolean(StatisticsFragment.PREF_INCLUDE_MARKED_PLAYED, false)
        val timeFilterFrom = prefs.getLong(StatisticsFragment.PREF_FILTER_FROM, 0L)
        val timeFilterTo = prefs.getLong(StatisticsFragment.PREF_FILTER_TO, Long.MAX_VALUE)
        disposable = Observable.fromCallable<DBReader.StatisticsResult>(
                {
                    val statisticsData = DBReader.getStatistics(
                            includeMarkedAsPlayed, timeFilterFrom, timeFilterTo)
                    Collections.sort(statisticsData.feedTime) { item1, item2 ->
                        java.lang.Long.compare(item2.timePlayed, item1.timePlayed) }
                    statisticsData
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    statisticsResult = result
                    // When "from" is "today", set it to today
                    listAdapter!!.setTimeFilter(includeMarkedAsPlayed, Math.max(
                                Math.min(timeFilterFrom, System.currentTimeMillis()), result.oldestDate),
                            Math.min(timeFilterTo, System.currentTimeMillis()))
                    listAdapter!!.update(result.feedTime)
                    progressBar!!.setVisibility(View.GONE)
                    feedStatisticsList!!.setVisibility(View.VISIBLE)
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }
}
