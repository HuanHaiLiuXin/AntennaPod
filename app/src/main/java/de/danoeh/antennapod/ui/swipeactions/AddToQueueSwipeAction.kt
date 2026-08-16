package de.danoeh.antennapod.ui.swipeactions

import android.content.Context

import androidx.fragment.app.Fragment

import de.danoeh.antennapod.R
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import org.greenrobot.eventbus.EventBus

class AddToQueueSwipeAction : SwipeAction {

    override fun getId(): String {
        return SwipeAction.ADD_TO_QUEUE
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_playlist_play
    }

    override fun getActionColor(): Int {
        return R.attr.colorAccent
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.add_to_queue_label)
    }

    override fun performAction(item: FeedItem, fragment: Fragment, filter: FeedItemFilter?) {
        if (item.isTagged(FeedItem.TAG_QUEUE)) {
            RemoveFromQueueSwipeAction().performAction(item, fragment, filter)
        } else if (item.getMedia() == null) {
            EventBus.getDefault().post(MessageEvent(fragment.getString(R.string.no_media_label)))
        } else {
            DBWriter.addQueueItem(fragment.requireContext(), item)
        }
    }

    override fun willRemove(filter: FeedItemFilter?, item: FeedItem): Boolean {
        if (item.getMedia() == null) {
            return false
        }
        return filter!!.showNotQueued || filter.showNew
    }
}
