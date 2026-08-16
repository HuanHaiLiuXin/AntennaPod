package de.danoeh.antennapod.ui.statistics.subscriptions

import android.text.format.DateFormat
import androidx.fragment.app.Fragment
import de.danoeh.antennapod.storage.database.StatisticsItem
import de.danoeh.antennapod.ui.common.Converter
import de.danoeh.antennapod.ui.statistics.PieChartView
import de.danoeh.antennapod.ui.statistics.R
import de.danoeh.antennapod.ui.statistics.StatisticsListAdapter
import de.danoeh.antennapod.ui.statistics.feed.FeedStatisticsDialogFragment

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter for the playback statistics list.
 */
class PlaybackStatisticsListAdapter(private val fragment: Fragment) : StatisticsListAdapter(fragment.getContext()!!) {

    private var timeFilterFrom = 0L
    private var timeFilterTo = Long.MAX_VALUE
    private var includeMarkedAsPlayed = false

    fun setTimeFilter(includeMarkedAsPlayed: Boolean, timeFilterFrom: Long, timeFilterTo: Long) {
        this.includeMarkedAsPlayed = includeMarkedAsPlayed
        this.timeFilterFrom = timeFilterFrom
        this.timeFilterTo = timeFilterTo
    }

    override fun getHeaderCaption(): String {
        if (includeMarkedAsPlayed) {
            return context.getString(R.string.statistics_counting_total)
        }
        val skeleton = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMM yyyy")
        val dateFormat = SimpleDateFormat(skeleton, Locale.getDefault())
        val dateFrom = dateFormat.format(Date(timeFilterFrom))
        // FilterTo is first day of next month => Subtract one day
        val dateTo = dateFormat.format(Date(timeFilterTo - 24L * 3600000L))
        return context.getString(R.string.statistics_counting_range, dateFrom, dateTo)
    }

    override fun getHeaderValue(): String {
        return Converter.shortLocalizedDuration(context, pieChartData!!.getSum().toLong())
    }

    override fun generateChartData(statisticsData: List<StatisticsItem>): PieChartView.PieChartData {
        val dataValues = FloatArray(statisticsData.size)
        for (i in statisticsData.indices) {
            val item = statisticsData.get(i)
            dataValues[i] = item.timePlayed.toFloat()
        }
        return PieChartView.PieChartData(dataValues)
    }

    override fun onBindFeedViewHolder(holder: StatisticsHolder, statsItem: StatisticsItem) {
        val time = statsItem.timePlayed
        holder.value.setText(Converter.shortLocalizedDuration(context, time))

        holder.itemView.setOnClickListener {
            FeedStatisticsDialogFragment.newInstance(statsItem.feed.getId(), statsItem.feed.getTitle())
                    .show(fragment.getChildFragmentManager().beginTransaction(), "FeedStatistics")
        }
    }
}
