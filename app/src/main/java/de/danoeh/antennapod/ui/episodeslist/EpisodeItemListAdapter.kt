package de.danoeh.antennapod.ui.episodeslist

import android.app.Activity
import android.view.ContextMenu
import android.view.InputDevice
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView

import de.danoeh.antennapod.ui.SelectableAdapter

import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.Collections

import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.ui.screen.episode.ItemPagerFragment

/**
 * List adapter for the list of new episodes.
 */
open class EpisodeItemListAdapter(mainActivity: FragmentActivity) :
        SelectableAdapter<EpisodeItemViewHolder>(mainActivity),
        View.OnCreateContextMenuListener {

    private val mainActivityRef: WeakReference<FragmentActivity>
    private var episodes: List<FeedItem> = ArrayList()
    private var longPressedItem: FeedItem? = null
    var longPressedPosition = 0 // used to init actionMode
    private var dummyViews = 0

    init {
        this.mainActivityRef = WeakReference(mainActivity)
        setHasStableIds(true)
    }

    fun setDummyViews(dummyViews: Int) {
        this.dummyViews = dummyViews
        notifyDataSetChanged()
    }

    fun updateItems(items: List<FeedItem>) {
        episodes = items
        notifyDataSetChanged()
        onSelectedItemsUpdated()
    }

    override fun getItemViewType(position: Int): Int {
        return R.id.view_type_episode_item
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeItemViewHolder {
        return EpisodeItemViewHolder(mainActivityRef.get()!!, parent)
    }

    override fun onBindViewHolder(holder: EpisodeItemViewHolder, pos: Int) {
        if (pos >= episodes.size) {
            beforeBindViewHolder(holder, pos)
            holder.bindDummy()
            afterBindViewHolder(holder, pos)
            holder.hideSeparatorIfNecessary()
            return
        }

        // Reset state of recycled views
        holder.coverHolder.setVisibility(View.VISIBLE)
        holder.dragHandle.setVisibility(View.GONE)

        beforeBindViewHolder(holder, pos)

        val item = episodes.get(pos)
        holder.bind(item)

        holder.itemView.setOnClickListener {
            if (!inActionMode()) {
                if (mainActivityRef.get() is MainActivity) {
                    (mainActivityRef.get() as MainActivity)
                            .loadChildFragment(ItemPagerFragment.newInstance(episodes, item))
                } else {
                    val fragment = ItemPagerFragment.newInstance(episodes, item)
                    mainActivityRef.get()!!.getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragmentContainer, fragment, "Items")
                            .addToBackStack("Items")
                            .commitAllowingStateLoss()
                }
            } else {
                toggleSelection(holder.getBindingAdapterPosition())
            }
        }
        holder.itemView.setOnCreateContextMenuListener(this)
        holder.itemView.setOnLongClickListener {
            longPressedItem = item
            longPressedPosition = holder.getBindingAdapterPosition()
            false
        }
        holder.itemView.setOnTouchListener { v, e ->
            if (e.isFromSource(InputDevice.SOURCE_MOUSE)
                    && e.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
                longPressedItem = item
                longPressedPosition = holder.getBindingAdapterPosition()
                false
            } else {
                false
            }
        }

        holder.itemView.setSelected(false)
        if (inActionMode()) {
            holder.itemView.setActivated(false)
            holder.secondaryActionButton.setOnClickListener {
                toggleSelection(holder.getBindingAdapterPosition())
            }
            if (isSelected(pos)) {
                holder.itemView.setSelected(true)
            }
        }

        afterBindViewHolder(holder, pos)
        holder.hideSeparatorIfNecessary()
    }

    protected open fun beforeBindViewHolder(holder: EpisodeItemViewHolder, pos: Int) {
    }

    protected open fun afterBindViewHolder(holder: EpisodeItemViewHolder, pos: Int) {
    }

    override fun onViewRecycled(holder: EpisodeItemViewHolder) {
        super.onViewRecycled(holder)
        // Set all listeners to null. This is required to prevent leaking fragments that have set a listener.
        // Activity -> recycledViewPool -> EpisodeItemViewHolder -> Listener -> Fragment (can not be garbage collected)
        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnCreateContextMenuListener(null)
        holder.itemView.setOnLongClickListener(null)
        holder.itemView.setOnTouchListener(null)
        holder.secondaryActionButton.setOnClickListener(null)
        holder.dragHandle.setOnTouchListener(null)
        holder.coverHolder.setOnTouchListener(null)
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

    override fun getItemId(position: Int): Long {
        if (position >= episodes.size) {
            return RecyclerView.NO_ID // Dummy views
        }
        val item = episodes.get(position)
        return if (item != null) item.getId() else RecyclerView.NO_POSITION.toLong()
    }

    override fun getItemCount(): Int {
        return dummyViews + episodes.size
    }

    protected fun getItem(index: Int): FeedItem {
        return episodes.get(index)
    }

    protected fun getActivity(): Activity {
        return mainActivityRef.get()!!
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        val inflater = mainActivityRef.get()!!.getMenuInflater()
        if (inActionMode()) {
            return
        }
        if (longPressedItem == null) {
            return
        }
        inflater.inflate(R.menu.feeditemlist_context, menu)
        menu.setHeaderTitle(longPressedItem!!.getTitle())
        FeedItemMenuHandler.onPrepareMenu(menu, Collections.singletonList(longPressedItem), R.id.skip_episode_item)
    }

    fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.getItemId() == R.id.multi_select) {
            startSelectMode(longPressedPosition)
            return true
        }
        return false
    }

    fun getSelectedItems(): List<FeedItem> {
        val items: MutableList<FeedItem> = ArrayList()
        for (i in 0 until getItemCount()) {
            if (isSelected(i)) {
                items.add(getItem(i))
            }
        }
        return items
    }

}
