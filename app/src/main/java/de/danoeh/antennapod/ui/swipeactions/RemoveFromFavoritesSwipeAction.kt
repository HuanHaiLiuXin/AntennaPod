package de.danoeh.antennapod.ui.swipeactions

import android.content.Context

import androidx.fragment.app.Fragment

import de.danoeh.antennapod.R
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter

class RemoveFromFavoritesSwipeAction : SwipeAction {

    override fun getId(): String {
        return SwipeAction.REMOVE_FROM_FAVORITES
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_star_border
    }

    override fun getActionColor(): Int {
        return R.attr.icon_yellow
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.remove_from_favorite_label)
    }

    override fun performAction(item: FeedItem, fragment: Fragment, filter: FeedItemFilter?) {
        DBWriter.toggleFavoriteItem(item)
    }

    override fun willRemove(filter: FeedItemFilter?, item: FeedItem): Boolean {
        return filter!!.showIsFavorite
    }
}
