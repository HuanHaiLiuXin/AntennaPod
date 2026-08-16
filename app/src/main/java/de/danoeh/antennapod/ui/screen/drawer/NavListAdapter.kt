package de.danoeh.antennapod.ui.screen.drawer

import android.app.Activity
import android.content.SharedPreferences
import android.view.ContextMenu
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import de.danoeh.antennapod.R
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.storage.database.NavDrawerData
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.common.ImagePlaceholder
import de.danoeh.antennapod.ui.screen.InboxFragment
import de.danoeh.antennapod.ui.screen.queue.QueueFragment
import de.danoeh.antennapod.ui.screen.subscriptions.SubscriptionFragment

import java.lang.ref.WeakReference
import java.text.NumberFormat
import java.util.ArrayList
import java.util.Collections

/**
 * BaseAdapter for the navigation drawer
 */
class NavListAdapter(private val itemAccess: ItemAccess, context: Activity) :
        RecyclerView.Adapter<NavListAdapter.Holder>(),
        SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        const val VIEW_TYPE_NAV = 0
        const val VIEW_TYPE_SECTION_DIVIDER = 1
        private const val VIEW_TYPE_SUBSCRIPTION = 2

        /**
         * a tag used as a placeholder to indicate if the subscription list should be displayed or not
         * This tag doesn't correspond to any specific activity.
         */
        const val SUBSCRIPTION_LIST_TAG = "SubscriptionList"
    }

    private val fragmentTags: MutableList<String> = ArrayList()
    private val activity: WeakReference<Activity> = WeakReference(context)
    var showSubscriptionList = true

    init {
        loadItems()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (UserPreferences.PREF_HIDDEN_DRAWER_ITEMS == key
                || UserPreferences.PREF_DRAWER_ITEM_ORDER == key) {
            loadItems()
        }
    }

    private fun loadItems() {
        val newTags = ArrayList(UserPreferences.getVisibleDrawerItemOrder())

        if (newTags.contains(SUBSCRIPTION_LIST_TAG)) {
            // we never want SUBSCRIPTION_LIST_TAG to be in 'tags'
            // since it doesn't actually correspond to a position in the list, but is
            // a placeholder that indicates if we should show the subscription list in the
            // nav drawer at all.
            showSubscriptionList = true
            newTags.remove(SUBSCRIPTION_LIST_TAG)
        } else {
            showSubscriptionList = false
        }

        fragmentTags.clear()
        fragmentTags.addAll(newTags.filterNotNull())
        notifyDataSetChanged()
    }

    fun getFragmentTags(): List<String> {
        return Collections.unmodifiableList(fragmentTags)
    }

    override fun getItemCount(): Int {
        val baseCount = getSubscriptionOffset()
        if (showSubscriptionList) {
            return baseCount + itemAccess.getCount()
        }
        return baseCount
    }

    override fun getItemId(position: Int): Long {
        val viewType = getItemViewType(position)
        if (viewType == VIEW_TYPE_SUBSCRIPTION) {
            return itemAccess.getItem(position - getSubscriptionOffset())!!.getId()
        } else if (viewType == VIEW_TYPE_NAV) {
            return -Math.abs(fragmentTags[position].hashCode().toLong()) - 1 // Folder IDs are >0
        } else {
            return 0
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (0 <= position && position < fragmentTags.size) {
            return VIEW_TYPE_NAV
        } else if (position < getSubscriptionOffset()) {
            return VIEW_TYPE_SECTION_DIVIDER
        } else {
            return VIEW_TYPE_SUBSCRIPTION
        }
    }

    fun getSubscriptionOffset(): Int {
        return if (fragmentTags.size > 0) fragmentTags.size + 1 else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val inflater = LayoutInflater.from(activity.get()!!)
        if (viewType == VIEW_TYPE_NAV) {
            return NavHolder(inflater.inflate(R.layout.nav_listitem, parent, false))
        } else if (viewType == VIEW_TYPE_SECTION_DIVIDER) {
            return DividerHolder(inflater.inflate(R.layout.nav_section_item, parent, false))
        } else {
            return FeedHolder(inflater.inflate(R.layout.nav_listitem, parent, false))
        }
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val viewType = getItemViewType(position)

        holder.itemView.setOnCreateContextMenuListener(null)
        if (viewType == VIEW_TYPE_NAV) {
            bindNavView(NavigationNames.getLabel(fragmentTags[position]), position, holder as NavHolder)
        } else if (viewType == VIEW_TYPE_SECTION_DIVIDER) {
            bindSectionDivider(holder as DividerHolder)
        } else {
            val itemPos = position - getSubscriptionOffset()
            val item = itemAccess.getItem(itemPos)!!
            bindListItem(item, holder as FeedHolder)
            if (item.isFeed()) {
                bindFeedView(item.asFeed()!!, holder)
            } else {
                bindTagView(item.asTag()!!, holder)
            }
            holder.itemView.setOnCreateContextMenuListener(itemAccess)
        }
        if (viewType != VIEW_TYPE_SECTION_DIVIDER) {
            holder.itemView.setSelected(itemAccess.isSelected(position))
            holder.itemView.setOnClickListener { itemAccess.onItemClick(position) }
            holder.itemView.setOnLongClickListener { itemAccess.onItemLongClick(position) }
            holder.itemView.setOnTouchListener { v, e ->
                if (e.isFromSource(InputDevice.SOURCE_MOUSE)
                        && e.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
                    itemAccess.onItemLongClick(position)
                    return@setOnTouchListener false
                }
                return@setOnTouchListener false
            }
        }
    }

    private fun bindNavView(@StringRes title: Int, position: Int, holder: NavHolder) {
        val context = activity.get()
        if (context == null) {
            return
        }
        holder.title.setText(title)

        // reset for re-use
        holder.count.setVisibility(View.GONE)
        holder.count.setOnClickListener(null)
        holder.count.setClickable(false)

        val tag = fragmentTags[position]
        if (tag == QueueFragment.TAG) {
            val queueSize = itemAccess.getQueueSize()
            if (queueSize > 0) {
                holder.count.setText(NumberFormat.getInstance().format(queueSize))
                holder.count.setVisibility(View.VISIBLE)
            }
        } else if (tag == InboxFragment.TAG) {
            val unreadItems = itemAccess.getNumberOfNewItems()
            if (unreadItems > 0) {
                holder.count.setText(NumberFormat.getInstance().format(unreadItems))
                holder.count.setVisibility(View.VISIBLE)
            }
        } else if (tag == SubscriptionFragment.TAG) {
            val sum = itemAccess.getFeedCounterSum()
            if (sum > 0) {
                holder.count.setText(NumberFormat.getInstance().format(sum))
                holder.count.setVisibility(View.VISIBLE)
            }
        }

        holder.image.setImageResource(NavigationNames.getDrawable(fragmentTags[position]))
    }

    private fun bindSectionDivider(holder: DividerHolder) {
        val context = activity.get()
        if (context == null) {
            return
        }

        if (UserPreferences.getSubscriptionsFilter().isEnabled() && showSubscriptionList) {
            holder.itemView.setEnabled(true)
            holder.feedsFilteredMsg.setVisibility(View.VISIBLE)
        } else {
            holder.itemView.setEnabled(false)
            holder.feedsFilteredMsg.setVisibility(View.GONE)
        }
    }

    private fun bindListItem(item: DrawerItem, holder: FeedHolder) {
        if (item.getCounter() > 0) {
            holder.count.setVisibility(View.VISIBLE)
            holder.count.setText(NumberFormat.getInstance().format(item.getCounter()))
        } else {
            holder.count.setVisibility(View.GONE)
        }
        holder.title.setText(item.getTitle())
        val padding = (activity.get()!!.getResources().getDimension(R.dimen.thumbnail_length_navlist) / 2).toInt()
        holder.itemView.setPadding(item.getLayer() * padding, 0, 0, 0)
    }

    private fun bindFeedView(feed: Feed, holder: FeedHolder) {
        val context = activity.get()
        if (context == null) {
            return
        }

        val radius = 4 * context.getResources().getDisplayMetrics().density
        Glide.with(context)
                .load(feed.getImageUrl())
                .apply(RequestOptions()
                        .placeholder(ImagePlaceholder.getDrawable(context, radius))
                        .error(ImagePlaceholder.getDrawable(context, radius))
                        .transform(FitCenter(),
                                RoundedCorners(radius.toInt()))
                        .dontAnimate())
                .into(holder.image)

        holder.failure.setVisibility(if (feed.hasLastUpdateFailed()) View.VISIBLE else View.GONE)
    }

    private fun bindTagView(tag: NavDrawerData.TagItem, holder: FeedHolder) {
        val context = activity.get()
        if (context == null) {
            return
        }
        if (tag.isOpen()) {
            holder.count.setVisibility(View.GONE)
        }
        if (FeedPreferences.TAG_UNTAGGED == tag.getTitle()) {
            holder.title.setText(R.string.tag_untagged)
        }
        Glide.with(context).clear(holder.image)
        holder.image.setImageResource(R.drawable.ic_tag)
        holder.failure.setVisibility(View.GONE)
    }

    open class Holder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class DividerHolder(itemView: View) : Holder(itemView) {
        val feedsFilteredMsg: LinearLayout = itemView.findViewById(R.id.nav_feeds_filtered_message)
    }

    class NavHolder(itemView: View) : Holder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.imgvCover)
        val title: TextView = itemView.findViewById(R.id.txtvTitle)
        val count: TextView = itemView.findViewById(R.id.txtvCount)
    }

    class FeedHolder(itemView: View) : Holder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.imgvCover)
        val title: TextView = itemView.findViewById(R.id.txtvTitle)
        val failure: ImageView = itemView.findViewById(R.id.itxtvFailure)
        val count: TextView = itemView.findViewById(R.id.txtvCount)
    }

    interface ItemAccess : View.OnCreateContextMenuListener {
        fun getCount(): Int

        fun getItem(position: Int): DrawerItem?

        fun isSelected(position: Int): Boolean

        fun getQueueSize(): Int

        fun getNumberOfNewItems(): Int

        fun getNumberOfDownloadedItems(): Int

        fun getReclaimableItems(): Int

        fun getFeedCounterSum(): Int

        fun onItemClick(position: Int)

        fun onItemLongClick(position: Int): Boolean

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?)
    }
}
