package de.danoeh.antennapod.ui.swipeactions

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas

import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

import java.util.Arrays
import java.util.Collections

import de.danoeh.antennapod.R
import de.danoeh.antennapod.ui.screen.AllEpisodesFragment
import de.danoeh.antennapod.ui.screen.download.CompletedDownloadsFragment
import de.danoeh.antennapod.ui.screen.FavoritesFragment
import de.danoeh.antennapod.ui.screen.InboxFragment
import de.danoeh.antennapod.ui.screen.PlaybackHistoryFragment
import de.danoeh.antennapod.ui.screen.queue.QueueFragment
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.ui.common.ThemeUtils
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemViewHolder
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

open class SwipeActions(dragDirs: Int, private val fragment: Fragment, private val tag: String) :
        ItemTouchHelper.SimpleCallback(dragDirs, ItemTouchHelper.RIGHT or ItemTouchHelper.LEFT), LifecycleObserver {
    companion object {
        const val PREF_NAME = "SwipeActionsPrefs"
        const val KEY_PREFIX_SWIPEACTIONS = "PrefSwipeActions"
        const val KEY_PREFIX_NO_ACTION = "PrefNoSwipeAction"

        private val swipeActions: List<SwipeAction> = Collections.unmodifiableList(
                Arrays.asList(AddToQueueSwipeAction(), RemoveFromInboxSwipeAction(),
                        StartDownloadSwipeAction(), MarkFavoriteSwipeAction(),
                        RemoveFromFavoritesSwipeAction(), TogglePlaybackStateSwipeAction(),
                        RemoveFromQueueSwipeAction(), DeleteSwipeAction(),
                        RemoveFromHistorySwipeAction()))

        @JvmStatic
        fun getAction(key: String): SwipeAction? {
            for (action in swipeActions) {
                if (action.getId() == key) {
                    return action
                }
            }
            return null
        }

        private fun getPrefs(context: Context, tag: String, defaultActions: String): Actions {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val prefsString = prefs.getString(KEY_PREFIX_SWIPEACTIONS + tag, defaultActions)

            return Actions(prefsString)
        }

        private fun getPrefs(context: Context, tag: String): Actions {
            return getPrefs(context, tag, "")
        }

        @JvmStatic
        fun getPrefsWithDefaults(context: Context, tag: String): Actions {
            val defaultActions: String = when (tag) {
                InboxFragment.TAG -> SwipeAction.ADD_TO_QUEUE + "," + SwipeAction.REMOVE_FROM_INBOX
                QueueFragment.TAG -> SwipeAction.REMOVE_FROM_QUEUE + "," + SwipeAction.REMOVE_FROM_QUEUE
                CompletedDownloadsFragment.TAG -> SwipeAction.DELETE + "," + SwipeAction.DELETE
                PlaybackHistoryFragment.TAG -> SwipeAction.REMOVE_FROM_HISTORY + "," + SwipeAction.REMOVE_FROM_HISTORY
                FavoritesFragment.TAG -> SwipeAction.REMOVE_FROM_FAVORITES + "," + SwipeAction.REMOVE_FROM_FAVORITES
                else -> SwipeAction.MARK_FAV + "," + SwipeAction.START_DOWNLOAD
            }

            return getPrefs(context, tag, defaultActions)
        }

        @JvmStatic
        fun isSwipeActionEnabled(context: Context, tag: String): Boolean {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_PREFIX_NO_ACTION + tag, true)
        }
    }

    private var filter: FeedItemFilter? = null

    internal var actions: Actions? = null
    internal var swipeOutEnabled = true
    internal var swipedOutTo = 0
    private val itemTouchHelper = ItemTouchHelper(this)

    constructor(fragment: Fragment, tag: String) : this(0, fragment, tag)

    init {
        reloadPreference()
        fragment.lifecycle.addObserver(this)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun reloadPreference() {
        actions = getPrefs(fragment.requireContext(), tag)
    }

    fun setFilter(filter: FeedItemFilter?) {
        this.filter = filter
    }

    fun attachTo(recyclerView: RecyclerView): SwipeActions {
        itemTouchHelper.attachToRecyclerView(recyclerView)
        return this
    }

    fun detach() {
        itemTouchHelper.attachToRecyclerView(null)
    }

    private fun isSwipeActionEnabled(): Boolean {
        return isSwipeActionEnabled(fragment.requireContext(), tag)
    }

    override fun onMove(recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int) {
        if (!actions!!.hasActions()) {
            //open settings dialog if no prefs are set
            SwipeActionsDialog(fragment.requireContext(), tag).show { reloadPreference() }
            return
        }

        val item = (viewHolder as EpisodeItemViewHolder).getFeedItem()!!

        (if (swipeDir == ItemTouchHelper.RIGHT) actions!!.right!! else actions!!.left!!)
                .performAction(item, fragment, filter)
    }

    override fun onChildDraw(c: Canvas, recyclerView: RecyclerView,
                             viewHolder: RecyclerView.ViewHolder,
                             dx: Float, dy: Float, actionState: Int, isCurrentlyActive: Boolean) {
        var dx = dx
        val right: SwipeAction
        val left: SwipeAction
        if (actions!!.hasActions()) {
            right = actions!!.right!!
            left = actions!!.left!!
        } else {
            right = ShowFirstSwipeDialogAction()
            left = right
        }

        //check if it will be removed
        val item = (viewHolder as EpisodeItemViewHolder).getFeedItem()!!
        val rightWillRemove = right.willRemove(filter, item)
        val leftWillRemove = left.willRemove(filter, item)
        val wontLeave = (dx > 0 && !rightWillRemove) || (dx < 0 && !leftWillRemove)

        //Limit swipe if it's not removed
        val maxMovement = recyclerView.getWidth() * 2 / 5
        val sign = if (dx > 0) 1 else -1
        val limitMovement = Math.min(maxMovement.toFloat(), sign * dx)
        val displacementPercentage = limitMovement / maxMovement
        val swipeThresholdReached = displacementPercentage >= 0.85f

        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && wontLeave) {
            swipeOutEnabled = false
            // Move slower when getting near the maxMovement
            dx = sign * maxMovement * 0.7f * sin((PI / 2) * displacementPercentage).toFloat()

            if (isCurrentlyActive) {
                val dir = if (dx > 0) ItemTouchHelper.RIGHT else ItemTouchHelper.LEFT
                swipedOutTo = if (swipeThresholdReached) dir else 0
            }
        } else {
            swipeOutEnabled = true
        }

        //add color and icon
        val context = fragment.requireContext()
        val themeColor = ThemeUtils.getColorFromAttr(context, android.R.attr.colorBackground)
        val actionColor = ThemeUtils.getColorFromAttr(context,
                if (dx > 0) right.getActionColor() else left.getActionColor())
        val builder = RecyclerViewSwipeDecorator.Builder(
                c, recyclerView, viewHolder, dx, dy, actionState, isCurrentlyActive)
                .addSwipeRightActionIcon(right.getActionIcon())
                .addSwipeLeftActionIcon(left.getActionIcon())
                .addSwipeRightBackgroundColor(ThemeUtils.getColorFromAttr(context, R.attr.background_elevated))
                .addSwipeLeftBackgroundColor(ThemeUtils.getColorFromAttr(context, R.attr.background_elevated))
                .setActionIconTint(ColorUtils.blendARGB(themeColor, actionColor,
                        if (!wontLeave || swipeThresholdReached) 1.0f else 0.7f))
        builder.create().decorate()

        super.onChildDraw(c, recyclerView, viewHolder, dx, dy, actionState, isCurrentlyActive)
    }

    override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
        return if (swipeOutEnabled) defaultValue * 1.5f else Float.MAX_VALUE
    }

    override fun getSwipeVelocityThreshold(defaultValue: Float): Float {
        return if (swipeOutEnabled) defaultValue * 0.6f else 0f
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        return if (swipeOutEnabled) super.getSwipeThreshold(viewHolder) else 1.0f
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)

        if (swipedOutTo != 0) {
            onSwiped(viewHolder, swipedOutTo)
            swipedOutTo = 0
        }
    }

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        if (!isSwipeActionEnabled()) {
            return makeMovementFlags(getDragDirs(recyclerView, viewHolder), 0)
        } else {
            return super.getMovementFlags(recyclerView, viewHolder)
        }
    }

    fun startDrag(holder: EpisodeItemViewHolder) {
        itemTouchHelper.startDrag(holder)
    }

    class Actions(prefs: String?) {
        var right: SwipeAction? = null
        var left: SwipeAction? = null

        init {
            val actions = prefs!!.split(",")
            if (actions.size == 2) {
                right = getAction(actions[0])
                left = getAction(actions[1])
            }
        }

        fun hasActions(): Boolean {
            return right != null && left != null
        }
    }
}
