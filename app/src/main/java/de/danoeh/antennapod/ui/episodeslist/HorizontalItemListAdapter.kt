package de.danoeh.antennapod.ui.episodeslist

import android.view.ContextMenu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.ui.screen.episode.ItemPagerFragment
import de.danoeh.antennapod.model.feed.FeedItem

import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.Collections

open class HorizontalItemListAdapter(mainActivity: MainActivity) : RecyclerView.Adapter<HorizontalItemViewHolder>(),
        View.OnCreateContextMenuListener {
    private val mainActivityRef: WeakReference<MainActivity>
    private var data: List<FeedItem> = ArrayList()
    private var longPressedItem: FeedItem? = null
    private var dummyViews = 0

    init {
        this.mainActivityRef = WeakReference(mainActivity)
        setHasStableIds(true)
    }

    fun setDummyViews(dummyViews: Int) {
        this.dummyViews = dummyViews
    }

    fun updateData(newData: List<FeedItem>) {
        data = newData
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HorizontalItemViewHolder {
        return HorizontalItemViewHolder(mainActivityRef.get()!!, parent)
    }

    override fun onBindViewHolder(holder: HorizontalItemViewHolder, position: Int) {
        if (position >= data.size) {
            holder.bindDummy()
            return
        }

        val item = data.get(position)
        holder.bind(item)

        holder.card.setOnCreateContextMenuListener(this)
        holder.card.setOnLongClickListener {
            longPressedItem = item
            false
        }
        holder.secondaryActionIcon.setOnCreateContextMenuListener(this)
        holder.secondaryActionIcon.setOnLongClickListener {
            longPressedItem = item
            false
        }
        holder.card.setOnClickListener {
            val activity = mainActivityRef.get()
            if (activity != null) {
                activity.loadChildFragment(ItemPagerFragment.newInstance(data, item))
            }
        }
    }

    override fun getItemId(position: Int): Long {
        if (position >= data.size) {
            return RecyclerView.NO_ID // Dummy views
        }
        return data.get(position).getId()
    }

    override fun getItemCount(): Int {
        return dummyViews + data.size
    }

    override fun onViewRecycled(holder: HorizontalItemViewHolder) {
        super.onViewRecycled(holder)
        // Set all listeners to null. This is required to prevent leaking fragments that have set a listener.
        // Activity -> recycledViewPool -> ViewHolder -> Listener -> Fragment (can not be garbage collected)
        holder.card.setOnClickListener(null)
        holder.card.setOnCreateContextMenuListener(null)
        holder.card.setOnLongClickListener(null)
        holder.secondaryActionIcon.setOnClickListener(null)
        holder.secondaryActionIcon.setOnCreateContextMenuListener(null)
        holder.secondaryActionIcon.setOnLongClickListener(null)
    }

    /**
     * [notifyItemChanged] is final, so we can not override.
     * Calling [notifyItemChanged] may bind the item to a new ViewHolder and execute a transition.
     * This causes flickering and breaks the download animation that stores the old progress in the View.
     * Instead, we tell the adapter to use partial binding by calling [notifyItemChanged].
     * We actually ignore the payload and always do a full bind but calling the partial bind method ensures
     * that ViewHolders are always re-used.
     *
     * @param position Position of the item that has changed
     */
    fun notifyItemChangedCompat(position: Int) {
        notifyItemChanged(position, "foo")
    }

    fun getLongPressedItem(): FeedItem? {
        return longPressedItem
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        val inflater: MenuInflater = mainActivityRef.get()!!.getMenuInflater()
        if (longPressedItem == null) {
            return
        }
        menu.clear()
        inflater.inflate(R.menu.feeditemlist_context, menu)
        menu.setHeaderTitle(longPressedItem!!.getTitle())
        FeedItemMenuHandler.onPrepareMenu(menu, Collections.singletonList(longPressedItem), R.id.skip_episode_item)
    }


}
