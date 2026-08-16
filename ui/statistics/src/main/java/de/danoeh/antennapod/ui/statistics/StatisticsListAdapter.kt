package de.danoeh.antennapod.ui.statistics

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

import de.danoeh.antennapod.storage.database.StatisticsItem

/**
 * Parent Adapter for the playback and download statistics list.
 */
abstract class StatisticsListAdapter(protected val context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_FEED = 1
    }

    private var statisticsData: List<StatisticsItem>? = null
    protected var pieChartData: PieChartView.PieChartData? = null

    override fun getItemCount(): Int {
        return statisticsData!!.size + 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) TYPE_HEADER else TYPE_FEED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(context)
        if (viewType == TYPE_HEADER) {
            return HeaderHolder(inflater.inflate(R.layout.statistics_listitem_total, parent, false))
        }
        return StatisticsHolder(inflater.inflate(R.layout.statistics_listitem, parent, false))
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == TYPE_HEADER) {
            val holder = h as HeaderHolder
            holder.pieChart.setData(pieChartData!!)
            holder.totalTime.setText(getHeaderValue())
            holder.totalText.setText(getHeaderCaption())
        } else {
            val holder = h as StatisticsHolder
            val statsItem = statisticsData!!.get(position - 1)
            Glide.with(context)
                    .load(statsItem.feed.getImageUrl())
                    .apply(RequestOptions()
                            .placeholder(R.color.light_gray)
                            .error(R.color.light_gray)
                            .fitCenter()
                            .dontAnimate())
                    .into(holder.image)

            holder.title.setText(statsItem.feed.getTitle())
            holder.chip.setTextColor(pieChartData!!.getColorOfItem(position - 1))
            onBindFeedViewHolder(holder, statsItem)
        }
    }

    fun update(statistics: List<StatisticsItem>) {
        statisticsData = statistics
        pieChartData = generateChartData(statistics)
        notifyDataSetChanged()
    }

    internal class HeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val totalTime: TextView = itemView.findViewById(R.id.total_time)
        val pieChart: PieChartView = itemView.findViewById(R.id.pie_chart)
        val totalText: TextView = itemView.findViewById(R.id.total_description)
    }

    class StatisticsHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.imgvCover)
        val title: TextView = itemView.findViewById(R.id.txtvTitle)
        val value: TextView = itemView.findViewById(R.id.txtvValue)
        val chip: TextView = itemView.findViewById(R.id.chip)
    }

    protected abstract fun getHeaderCaption(): String

    protected abstract fun getHeaderValue(): String

    protected abstract fun generateChartData(statisticsData: List<StatisticsItem>): PieChartView.PieChartData

    protected abstract fun onBindFeedViewHolder(holder: StatisticsHolder, item: StatisticsItem)
}
