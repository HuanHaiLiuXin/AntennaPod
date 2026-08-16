package de.danoeh.antennapod.ui.screen.subscriptions

import android.app.Activity
import android.view.ContextMenu
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import de.danoeh.antennapod.R
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.storage.database.NavDrawerData

import java.lang.ref.WeakReference
import java.util.ArrayList

open class SubscriptionTagAdapter(activity: Activity) :
        RecyclerView.Adapter<SubscriptionTagAdapter.TagViewHolder>(),
        View.OnCreateContextMenuListener {
    private val activityRef: WeakReference<Activity>
    private var tags: List<NavDrawerData.TagItem> = ArrayList()
    private var selectedTag: String? = null
    private var longPressedItem: NavDrawerData.TagItem? = null

    init {
        this.activityRef = WeakReference(activity)
    }

    fun setTags(tags: List<NavDrawerData.TagItem>) {
        this.tags = tags
        notifyDataSetChanged()
    }

    fun setSelectedTag(tag: String) {
        this.selectedTag = tag
        notifyDataSetChanged()
    }

    fun getSelectedTag(): String? {
        return selectedTag
    }

    fun getSelectedTagPosition(): Int {
        if (selectedTag == null) {
            return -1
        }
        for (i in tags.indices) {
            if (tags.get(i).getTitle().equals(selectedTag)) {
                return i
            }
        }
        return -1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tag_chip, parent, false)
        return TagViewHolder(view)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        val tag = tags.get(position)
        if (FeedPreferences.TAG_ROOT == tag.getTitle()) {
            holder.chip.setText(R.string.tag_all)
        } else if (FeedPreferences
                .TAG_UNTAGGED == tag.getTitle()) {
            holder.chip.setText(R.string.tag_untagged)
        } else {
            var title = tag.getTitle()
            if (title.length > 20) {
                title = title.substring(0, 19) + "…"
            }
            holder.chip.setText(title)
        }
        holder.chip.setChecked(tag.getTitle().equals(selectedTag))
        holder.chip.setElevation(0f)
        holder.chip.setOnClickListener { onTagClick(tag) }
        holder.chip.setOnTouchListener { v, e ->
            if (e.isFromSource(InputDevice.SOURCE_MOUSE)
                    && e.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
                longPressedItem = tag
            }
            false
        }
        holder.chip.setOnLongClickListener {
            longPressedItem = tag
            false
        }
        holder.chip.setOnCreateContextMenuListener(this)
    }

    override fun getItemCount(): Int {
        return if (tags != null) tags.size else 0
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        if (longPressedItem == null
                || FeedPreferences.TAG_ROOT == longPressedItem!!.getTitle()
                || FeedPreferences.TAG_UNTAGGED == longPressedItem!!.getTitle()) {
            return
        }
        val inflater = activityRef.get()!!.getMenuInflater()
        inflater.inflate(R.menu.nav_folder_context, menu)
        menu.setHeaderTitle(longPressedItem!!.getTitle())
    }

    protected open fun onTagClick(tag: NavDrawerData.TagItem) {
    }

    fun getLongPressedItem(): NavDrawerData.TagItem? {
        return longPressedItem
    }

    class TagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val chip: Chip = itemView.findViewById(R.id.tag_chip)
    }
}
