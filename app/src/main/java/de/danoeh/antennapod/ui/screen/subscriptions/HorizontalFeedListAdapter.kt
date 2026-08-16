package de.danoeh.antennapod.ui.screen.subscriptions

import android.view.ContextMenu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.annotation.StringRes
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.ui.screen.feed.FeedItemlistFragment
import de.danoeh.antennapod.ui.common.SquareImageView

import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.Collections

open class HorizontalFeedListAdapter(mainActivity: MainActivity) :
        RecyclerView.Adapter<HorizontalFeedListAdapter.Holder>(),
        View.OnCreateContextMenuListener {
    private val mainActivityRef: WeakReference<MainActivity>
    private val data = ArrayList<Feed>()
    private var dummyViews = 0
    private var longPressedItem: Feed? = null
    @StringRes
    private var endButtonText = 0
    private var endButtonAction: Runnable? = null

    init {
        mainActivityRef = WeakReference(mainActivity)
    }

    fun setDummyViews(dummyViews: Int) {
        this.dummyViews = dummyViews
    }

    fun updateData(newData: List<Feed>) {
        data.clear()
        data.addAll(newData)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val convertView = View.inflate(mainActivityRef.get()!!, R.layout.horizontal_feed_item, null)
        return Holder(convertView)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (position == getItemCount() - 1 && endButtonAction != null) {
            holder.cardView.setVisibility(View.GONE)
            holder.actionButton.setVisibility(View.VISIBLE)
            holder.actionButton.setText(endButtonText)
            holder.actionButton.setOnClickListener { endButtonAction!!.run() }
            return
        }
        holder.cardView.setVisibility(View.VISIBLE)
        holder.actionButton.setVisibility(View.GONE)
        if (position >= data.size) {
            holder.itemView.setAlpha(0.1f)
            Glide.with(mainActivityRef.get()!!).clear(holder.imageView)
            holder.imageView.setImageResource(R.color.medium_gray)
            return
        }

        holder.itemView.setAlpha(1.0f)
        val podcast = data.get(position)
        holder.imageView.setContentDescription(podcast.getTitle())
        holder.imageView.setOnClickListener { onClick(podcast) }

        holder.imageView.setOnCreateContextMenuListener(this)
        holder.imageView.setOnLongClickListener {
            val currentItemPosition = holder.getBindingAdapterPosition()
            longPressedItem = data.get(currentItemPosition)
            false
        }

        Glide.with(mainActivityRef.get()!!)
                .load(podcast.getImageUrl())
                .apply(RequestOptions()
                        .placeholder(R.color.light_gray)
                        .fitCenter()
                        .dontAnimate())
                .into(holder.imageView)
    }

    protected open fun onClick(feed: Feed) {
        mainActivityRef.get()!!.loadChildFragment(FeedItemlistFragment.newInstance(feed.getId()))
    }

    fun getLongPressedItem(): Feed? {
        return longPressedItem
    }

    override fun getItemId(position: Int): Long {
        if (position >= data.size) {
            return RecyclerView.NO_ID // Dummy views
        }
        return data.get(position).getId()
    }

    override fun getItemCount(): Int {
        return dummyViews + data.size + (if (endButtonAction == null) 0 else 1)
    }

    override fun onCreateContextMenu(contextMenu: ContextMenu, view: View, contextMenuInfo: ContextMenu.ContextMenuInfo?) {
        val inflater = mainActivityRef.get()!!.getMenuInflater()
        if (longPressedItem == null) {
            return
        }
        inflater.inflate(R.menu.nav_feed_context, contextMenu)
        contextMenu.setHeaderTitle(longPressedItem!!.getTitle())
        FeedMenuHandler.onPrepareMenu(contextMenu, Collections.singletonList(longPressedItem!!))
    }

    fun setEndButton(@StringRes text: Int, action: Runnable?) {
        endButtonAction = action
        endButtonText = text
        notifyDataSetChanged()
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: SquareImageView = itemView.findViewById(R.id.discovery_cover)
        val cardView: CardView = itemView.findViewById(R.id.cardView)
        val actionButton: Button = itemView.findViewById(R.id.actionButton)

        init {
            imageView.setDirection(SquareImageView.DIRECTION_HEIGHT)
        }
    }
}
