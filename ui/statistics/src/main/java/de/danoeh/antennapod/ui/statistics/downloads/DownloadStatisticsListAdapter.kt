package de.danoeh.antennapod.ui.statistics.downloads

import android.content.Context
import android.text.format.Formatter
import androidx.fragment.app.Fragment
import de.danoeh.antennapod.storage.database.StatisticsItem
import de.danoeh.antennapod.ui.statistics.PieChartView
import de.danoeh.antennapod.ui.statistics.R
import de.danoeh.antennapod.ui.statistics.StatisticsListAdapter
import de.danoeh.antennapod.ui.statistics.feed.FeedStatisticsDialogFragment

/**
 * Adapter for the download statistics list.
 */
class DownloadStatisticsListAdapter(context: Context, private val fragment: Fragment) : StatisticsListAdapter(context) {
    private var cacheEpisodes = 0

    override fun getHeaderCaption(): String {
        return context.getResources().getQuantityString(
                R.plurals.total_size_downloaded_podcasts, cacheEpisodes, cacheEpisodes)
    }

    override fun getHeaderValue(): String {
        return Formatter.formatShortFileSize(context, pieChartData!!.getSum().toLong())
    }

    override fun generateChartData(statisticsData: List<StatisticsItem>): PieChartView.PieChartData {
        val dataValues = FloatArray(statisticsData.size)
        cacheEpisodes = 0
        for (i in statisticsData.indices) {
            val item = statisticsData.get(i)
            dataValues[i] = item.totalDownloadSize.toFloat()
            cacheEpisodes += item.episodesDownloadCount.toInt()
        }
        return PieChartView.PieChartData(dataValues)
    }

    override fun onBindFeedViewHolder(holder: StatisticsHolder, item: StatisticsItem) {
        val numEpisodes = item.episodesDownloadCount.toInt()
        var text = Formatter.formatShortFileSize(context, item.totalDownloadSize)
        text += " • " + context.getResources().getQuantityString(R.plurals.num_episodes, numEpisodes, numEpisodes)
        holder.value.setText(text)

        holder.itemView.setOnClickListener {
            FeedStatisticsDialogFragment.newInstance(item.feed.getId(), item.feed.getTitle())
                    .show(fragment.getChildFragmentManager().beginTransaction(), "FeedStatistics")
        }
    }

}
