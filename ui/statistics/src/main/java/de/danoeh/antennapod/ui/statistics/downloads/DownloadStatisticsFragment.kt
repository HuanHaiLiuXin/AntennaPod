package de.danoeh.antennapod.ui.statistics.downloads

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar

import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.ui.statistics.R
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

import java.util.Collections

/**
 * Displays the 'download statistics' screen
 */
class DownloadStatisticsFragment : Fragment() {
    companion object {
        private const val TAG = "DownloadStatisticsFragment"
    }

    private var disposable: Disposable? = null
    private var downloadStatisticsList: RecyclerView? = null
    private var progressBar: ProgressBar? = null
    private var listAdapter: DownloadStatisticsListAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.statistics_fragment, container, false)
        downloadStatisticsList = root.findViewById(R.id.statistics_list)
        progressBar = root.findViewById(R.id.progressBar)
        listAdapter = DownloadStatisticsListAdapter(requireContext(), this)
        downloadStatisticsList!!.setLayoutManager(LinearLayoutManager(getContext()))
        downloadStatisticsList!!.setAdapter(listAdapter)
        return root
    }

    override fun onStart() {
        super.onStart()
        refreshDownloadStatistics()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.statistics_reset).setVisible(false)
        menu.findItem(R.id.statistics_filter).setVisible(false)
    }

    private fun refreshDownloadStatistics() {
        progressBar!!.setVisibility(View.VISIBLE)
        downloadStatisticsList!!.setVisibility(View.GONE)
        loadStatistics()
    }

    private fun loadStatistics() {
        if (disposable != null) {
            disposable!!.dispose()
        }

        disposable =
                Observable.fromCallable<DBReader.StatisticsResult> {
                    // Filters do not matter here
                    val statisticsData = DBReader.getStatistics(false, 0L, Long.MAX_VALUE)
                    Collections.sort(statisticsData.feedTime) { item1, item2 ->
                        java.lang.Long.compare(item2.totalDownloadSize, item1.totalDownloadSize) }
                    statisticsData
                }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    listAdapter!!.update(result.feedTime)
                    progressBar!!.setVisibility(View.GONE)
                    downloadStatisticsList!!.setVisibility(View.VISIBLE)
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }
}
