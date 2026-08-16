package de.danoeh.antennapod.ui.discovery

import android.content.Context
import android.widget.ArrayAdapter
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

import de.danoeh.antennapod.net.discovery.PodcastSearchResult
import de.danoeh.antennapod.ui.common.ImagePlaceholder

class OnlineSearchAdapter(
        /**
         * Related Context
         */
        private val context: Context,
        /**
         * List holding the podcasts found in the search
         */
        private val data: List<PodcastSearchResult>) : ArrayAdapter<PodcastSearchResult>(context, 0, data) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        //Current podcast
        val podcast = data.get(position)

        //ViewHolder
        val viewHolder: PodcastViewHolder

        //Resulting view
        val view: View

        //Handle view holder stuff
        if (convertView == null) {
            view = View.inflate(context, R.layout.online_search_listitem, null)
            viewHolder = PodcastViewHolder(view)
            view.setTag(viewHolder)
        } else {
            view = convertView
            viewHolder = view.getTag() as PodcastViewHolder
        }

        // Set the title
        viewHolder.titleView.setText(podcast.title)
        if (podcast.author != null && !podcast.author!!.trim().isEmpty()) {
            viewHolder.authorView.setText(podcast.author)
            viewHolder.authorView.setVisibility(View.VISIBLE)
        } else if (podcast.feedUrl != null && !podcast.feedUrl!!.contains("itunes.apple.com")) {
            viewHolder.authorView.setText(podcast.feedUrl)
            viewHolder.authorView.setVisibility(View.VISIBLE)
        } else {
            viewHolder.authorView.setVisibility(View.GONE)
        }

        val radius = 4 * context.getResources().getDisplayMetrics().density
        Glide.with(context)
                .load(podcast.imageUrl)
                .apply(RequestOptions()
                    .placeholder(ImagePlaceholder.getDrawable(context, radius))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .transform(FitCenter(),
                            RoundedCorners(radius.toInt()))
                    .dontAnimate())
                .into(viewHolder.coverView)

        //Feed the grid view
        return view
    }

    /**
     * View holder object for the GridView
     */
    internal class PodcastViewHolder(view: View) {

        /**
         * ImageView holding the Podcast image
         */
        val coverView: ImageView = view.findViewById(R.id.imgvCover)

        /**
         * TextView holding the Podcast title
         */
        val titleView: TextView = view.findViewById(R.id.txtvTitle)

        val authorView: TextView = view.findViewById(R.id.txtvAuthor)
    }
}
