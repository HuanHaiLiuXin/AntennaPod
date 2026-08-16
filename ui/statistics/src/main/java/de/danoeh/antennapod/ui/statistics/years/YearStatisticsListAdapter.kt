package de.danoeh.antennapod.ui.statistics.years

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.ui.common.Converter
import de.danoeh.antennapod.ui.statistics.R

import java.util.ArrayList
import java.util.Collections
import java.util.Locale

/**
 * Adapter for the yearly playback statistics list.
 */
class YearStatisticsListAdapter(internal val context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_FEED = 1
    }

    private val statisticsData: MutableList<DBReader.MonthlyStatisticsItem> = ArrayList()
    private val yearlyAggregate: MutableList<DBReader.MonthlyStatisticsItem> = ArrayList()

    override fun getItemCount(): Int {
        return yearlyAggregate.size + 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) TYPE_HEADER else TYPE_FEED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(context)
        if (viewType == TYPE_HEADER) {
            return HeaderHolder(inflater.inflate(R.layout.statistics_listitem_barchart, parent, false))
        }
        return StatisticsHolder(inflater.inflate(R.layout.statistics_year_listitem, parent, false))
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == TYPE_HEADER) {
            val holder = h as HeaderHolder
            holder.barChart.setData(statisticsData)
        } else {
            val holder = h as StatisticsHolder
            val statsItem = yearlyAggregate.get(position - 1)
            holder.year.setText(String.format(Locale.getDefault(), "%d ", statsItem.getYear()))
            holder.hours.setText(Converter.shortLocalizedDuration(context, statsItem.getTimePlayed() / 1000))
        }
    }

    fun update(statistics: List<DBReader.MonthlyStatisticsItem>) {
        var lastYear = if (statistics.size > 0) statistics.get(0).getYear() else 0
        var lastDataPoint = if (statistics.size > 0) (statistics.get(0).getMonth() - 1) + lastYear * 12 else 0
        var yearSum = 0L
        yearlyAggregate.clear()
        statisticsData.clear()
        for (statistic in statistics) {
            if (statistic.getYear() != lastYear) {
                val yearAggregate = DBReader.MonthlyStatisticsItem()
                yearAggregate.setYear(lastYear)
                yearAggregate.setTimePlayed(yearSum)
                yearlyAggregate.add(yearAggregate)
                yearSum = 0
                lastYear = statistic.getYear()
            }
            yearSum += statistic.getTimePlayed()
            while (lastDataPoint + 1 < (statistic.getMonth() - 1) + statistic.getYear() * 12) {
                lastDataPoint++
                val item = DBReader.MonthlyStatisticsItem()
                item.setYear(lastDataPoint / 12)
                item.setMonth(lastDataPoint % 12 + 1)
                statisticsData.add(item) // Compensate for months without playback
            }
            statisticsData.add(statistic)
            lastDataPoint = (statistic.getMonth() - 1) + statistic.getYear() * 12
        }
        val yearAggregate = DBReader.MonthlyStatisticsItem()
        yearAggregate.setYear(lastYear)
        yearAggregate.setTimePlayed(yearSum)
        yearlyAggregate.add(yearAggregate)
        Collections.reverse(yearlyAggregate)
        notifyDataSetChanged()
    }

    internal class HeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val barChart: BarChartView = itemView.findViewById(R.id.barChart)
    }

    internal class StatisticsHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val year: TextView = itemView.findViewById(R.id.yearLabel)
        val hours: TextView = itemView.findViewById(R.id.hoursLabel)
    }
}
