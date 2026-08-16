package de.danoeh.antennapod.ui.preferences.screen.about

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

import de.danoeh.antennapod.ui.common.ImagePlaceholder
import de.danoeh.antennapod.ui.preferences.R

/**
 * Displays a list of items that have a subtitle and an icon.
 */
class SimpleIconListAdapter<T : SimpleIconListAdapter.ListItem>(
        private val context: Context,
        private val listItems: List<T>) : ArrayAdapter<T>(context, R.layout.simple_icon_list_item, listItems) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        if (view == null) {
            view = View.inflate(context, R.layout.simple_icon_list_item, null)
        }

        val item = listItems.get(position)
        (view.findViewById<TextView>(R.id.title)).setText(item.title)
        (view.findViewById<TextView>(R.id.subtitle)).setText(item.subtitle)

        if (item.imageUrl == null) {
            view.findViewById<View>(R.id.icon).setVisibility(View.GONE)
        } else {
            val radius = 4 * context.getResources().getDisplayMetrics().density
            Glide.with(context)
                    .load(item.imageUrl)
                    .apply(RequestOptions()
                            .placeholder(ImagePlaceholder.getDrawable(context, radius))
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .transform(FitCenter(), RoundedCorners(radius.toInt()))
                            .dontAnimate())
                    .into(view.findViewById<ImageView>(R.id.icon))
        }
        return view
    }

    open class ListItem(val title: String, val subtitle: String, val imageUrl: String?)
}
