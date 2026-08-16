package de.danoeh.antennapod.ui.swipeactions

import android.content.Context

import androidx.fragment.app.Fragment
import java.util.Date

import de.danoeh.antennapod.R
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import org.greenrobot.eventbus.EventBus

class RemoveFromHistorySwipeAction : SwipeAction {

    override fun getId(): String {
        return SwipeAction.REMOVE_FROM_HISTORY
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_history_remove
    }

    override fun getActionColor(): Int {
        return R.attr.icon_purple
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.remove_history_label)
    }

    override fun performAction(item: FeedItem, fragment: Fragment, filter: FeedItemFilter?) {
        val lastPlayedTimeHistory = item.getMedia()!!.getLastPlayedTimeHistory()
        DBWriter.deleteFromPlaybackHistory(item)
        EventBus.getDefault().post(MessageEvent(fragment.getString(R.string.removed_history_label),
                { DBWriter.addItemToPlaybackHistory(item.getMedia(), lastPlayedTimeHistory!!) },
                fragment.getString(R.string.undo)))
    }

    override fun willRemove(filter: FeedItemFilter?, item: FeedItem): Boolean {
        return true
    }
}
