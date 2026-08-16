package de.danoeh.antennapod.ui.swipeactions

import android.content.Context
import androidx.fragment.app.Fragment

import java.util.Collections

import de.danoeh.antennapod.R
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.ui.view.LocalDeleteModal

class DeleteSwipeAction : SwipeAction {

    override fun getId(): String {
        return SwipeAction.DELETE
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_delete
    }

    override fun getActionColor(): Int {
        return R.attr.icon_red
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.delete_label)
    }

    override fun performAction(item: FeedItem, fragment: Fragment, filter: FeedItemFilter?) {
        if (!item.isDownloaded()) {
            return
        }
        LocalDeleteModal.showLocalFeedDeleteWarningIfNecessary(
                fragment.requireContext(), Collections.singletonList(item),
                { DBWriter.deleteFeedMediaOfItem(fragment.requireContext(), item.getMedia()) })
    }

    override fun willRemove(filter: FeedItemFilter?, item: FeedItem): Boolean {
        return filter!!.showDownloaded && item.isDownloaded()
    }
}
