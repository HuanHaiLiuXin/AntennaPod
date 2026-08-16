package de.danoeh.antennapod.ui.discovery

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import de.danoeh.antennapod.net.discovery.PodcastSearchResult
import de.danoeh.antennapod.ui.common.ImagePlaceholder

import java.util.ArrayList

class FeedDiscoverAdapter(private val context: Context) : BaseAdapter() {

    private val data: MutableList<PodcastSearchResult> = ArrayList()

    fun updateData(newData: List<PodcastSearchResult>) {
        data.clear()
        data.addAll(newData)
        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return data.size
    }

    override fun getItem(position: Int): PodcastSearchResult {
        return data.get(position)
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        val holder: Holder

        if (view == null) {
            view = View.inflate(context, R.layout.quick_feed_discovery_item, null)
            holder = Holder()
            holder.imageView = view.findViewById(R.id.discovery_cover)
            view.setTag(holder)
        } else {
            holder = view.getTag() as Holder
        }

        val podcast = getItem(position)
        holder.imageView!!.setContentDescription(podcast.title)
        var imageUrl = podcast.imageUrl
        if (context.getSharedPreferences("MainActivityPrefs", Context.MODE_PRIVATE)
                .getBoolean("screenshot_mode", false)) {
            imageUrl = "https://picsum.photos/400/400?random=" + position
        }

        val radius = 8 * context.getResources().getDisplayMetrics().density
        Glide.with(context)
                .load(imageUrl)
                .apply(RequestOptions()
                        .placeholder(ImagePlaceholder.getDrawable(context, radius))
                        .transform(FitCenter(), RoundedCorners(radius.toInt()))
                        .dontAnimate())
                .into(holder.imageView!!)

        return view
    }

    internal class Holder {
        var imageView: ImageView? = null
    }
}
