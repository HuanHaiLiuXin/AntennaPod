package de.danoeh.antennapod.ui.screen.subscriptions

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.SelectableAdapter
import de.danoeh.antennapod.ui.common.ThemeUtils
import de.danoeh.antennapod.ui.screen.feed.FeedItemlistFragment

import java.lang.ref.WeakReference
import java.util.ArrayList

/**
 * Adapter for subscriptions
 */
open class SubscriptionsRecyclerAdapter(mainActivity: MainActivity) :
        SelectableAdapter<SubscriptionViewHolder>(mainActivity) {
    private val mainActivityRef: WeakReference<MainActivity>
    private var listItems: List<Feed>
    private var feedCounters: Map<Long, Int>
    private var columnCount = 3

    init {
        mainActivityRef = WeakReference(mainActivity)
        listItems = ArrayList()
        feedCounters = emptyMap()
        setHasStableIds(true)
    }

    fun setColumnCount(columnCount: Int) {
        this.columnCount = columnCount
    }

    fun getItem(position: Int): Any {
        return listItems.get(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubscriptionViewHolder {
        if (viewType == R.id.view_type_subscription_list) {
            val itemView = LayoutInflater.from(mainActivityRef.get()!!)
                    .inflate(R.layout.subscription_list_item, parent, false)
            return SubscriptionViewHolder(itemView, mainActivityRef.get()!!)
        }
        val itemView = LayoutInflater.from(mainActivityRef.get()!!)
                .inflate(R.layout.subscription_grid_item, parent, false)
        itemView.findViewById<View>(R.id.titleLabel).setVisibility(
                if (viewType == R.id.view_type_subscription_grid_with_title) View.VISIBLE else View.GONE)
        return SubscriptionViewHolder(itemView, mainActivityRef.get()!!)
    }

    override fun onBindViewHolder(holder: SubscriptionViewHolder, position: Int) {
        val feed = listItems.get(position)
        holder.bind(feed, columnCount, if (feedCounters.containsKey(feed.getId())) feedCounters.get(feed.getId())!! else 0)
        var cardMargin = 0
        if (inActionMode()) {
            if (holder.selectIcon != null) {
                holder.selectIcon.setVisibility(View.VISIBLE)
                holder.gradient.setVisibility(View.VISIBLE)
                holder.itemView.setSelected(isSelected(position))
                holder.selectIcon.setImageResource(if (isSelected(position))
                    R.drawable.circle_checked else R.drawable.circle_unchecked)
                cardMargin = if (isSelected(position)) convertDpToPixel(
                        holder.itemView.getContext(), 12f).toInt() else 0
                holder.count.setVisibility(View.GONE)
            } else {
                holder.itemView.setSelected(isSelected(position))
                holder.itemView.setBackgroundResource(android.R.color.transparent)
                if (isSelected(position)) {
                    holder.itemView.setBackgroundColor(0x88000000.toInt()
                            + (0xffffff and ThemeUtils.getColorFromAttr(mainActivityRef.get()!!, R.attr.colorAccent)))
                }
            }
        } else {
            holder.itemView.setSelected(false)
            holder.itemView.setBackgroundResource(android.R.color.transparent)
            if (holder.selectIcon != null) {
                holder.selectIcon.setVisibility(View.GONE)
                holder.gradient.setVisibility(View.GONE)
            }
        }
        animateCardMargin(holder, cardMargin)

        holder.itemView.setOnLongClickListener {
            if (!inActionMode()) {
                startSelectMode(holder.getBindingAdapterPosition())
                return@setOnLongClickListener true
            }
            return@setOnLongClickListener false
        }
        holder.itemView.setOnClickListener {
            if (inActionMode()) {
                toggleSelection(holder.getBindingAdapterPosition())
            } else {
                val fragment = FeedItemlistFragment.newInstance(feed.getId())
                mainActivityRef.get()!!.loadChildFragment(fragment)
            }
        }
    }

    override fun toggleSelection(pos: Int) {
        setSelected(pos, !isSelected(pos))
        notifyItemChanged(pos, java.lang.Boolean.TRUE)
        if (getSelectedCount() == 0) {
            endSelectMode()
        }
    }

    override fun onBindViewHolder(holder: SubscriptionViewHolder, position: Int,
                                  payloads: List<Any>) {
        if (payloads.isEmpty() || holder.selectIcon == null) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        holder.itemView.setSelected(isSelected(position))
        holder.selectIcon.setImageResource(if (isSelected(position))
            R.drawable.circle_checked else R.drawable.circle_unchecked)
        val targetMargin = if (isSelected(position))
            convertDpToPixel(holder.itemView.getContext(), 12f).toInt() else 0
        animateCardMargin(holder, targetMargin)
    }

    private fun animateCardMargin(holder: SubscriptionViewHolder, targetMargin: Int) {
        if (holder.selectIcon == null) {
            return
        }
        val params = holder.card!!.getLayoutParams() as FrameLayout.LayoutParams
        val startMargin = params.leftMargin
        if (startMargin == targetMargin) {
            return
        }
        val animator = ValueAnimator.ofInt(startMargin, targetMargin)
        animator.setDuration(100)
        animator.addUpdateListener { a ->
            val margin = a.getAnimatedValue() as Int
            params.leftMargin = margin
            params.topMargin = margin
            params.rightMargin = margin
            params.bottomMargin = margin
            holder.card!!.setLayoutParams(params)
        }
        animator.start()
    }

    override fun getItemCount(): Int {
        return listItems.size
    }

    override fun getItemId(position: Int): Long {
        if (position >= listItems.size) {
            return RecyclerView.NO_ID // Dummy views
        }
        return listItems.get(position).getId()
    }

    fun getSelectedItems(): List<Feed> {
        val items = ArrayList<Feed>()
        for (i in 0 until getItemCount()) {
            if (isSelected(i)) {
                items.add(listItems.get(i))
            }
        }
        return items
    }

    fun setItems(listItems: List<Feed>, feedCounters: Map<Long, Int>) {
        this.listItems = listItems
        this.feedCounters = feedCounters
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        if (columnCount == 1) {
            return R.id.view_type_subscription_list
        } else if (UserPreferences.shouldShowSubscriptionTitle()) {
            return R.id.view_type_subscription_grid_with_title
        } else {
            return R.id.view_type_subscription_grid_without_title
        }
    }

    companion object {
        fun convertDpToPixel(context: Context, dp: Float): Float {
            return dp * context.getResources().getDisplayMetrics().density
        }
    }

    class GridDividerItemDecorator : RecyclerView.ItemDecoration() {
        override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            super.onDraw(c, parent, state)
        }

        override fun getItemOffsets(outRect: Rect,
                                    view: View,
                                    parent: RecyclerView,
                                    state: RecyclerView.State) {
            super.getItemOffsets(outRect, view, parent, state)
            val context = parent.getContext()
            val insetOffset = convertDpToPixel(context, 1f).toInt()
            outRect.set(insetOffset, insetOffset, insetOffset, insetOffset)
        }
    }
}
