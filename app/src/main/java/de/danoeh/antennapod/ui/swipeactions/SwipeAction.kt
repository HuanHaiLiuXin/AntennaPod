package de.danoeh.antennapod.ui.swipeactions

import android.content.Context

import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment

import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter

interface SwipeAction {

    fun getId(): String

    fun getTitle(context: Context): String

    @DrawableRes
    fun getActionIcon(): Int

    @AttrRes
    fun getActionColor(): Int

    fun performAction(item: FeedItem, fragment: Fragment, filter: FeedItemFilter?)

    fun willRemove(filter: FeedItemFilter?, item: FeedItem): Boolean

    companion object {
        const val ADD_TO_QUEUE = "ADD_TO_QUEUE"
        const val REMOVE_FROM_INBOX = "REMOVE_FROM_INBOX"
        const val START_DOWNLOAD = "START_DOWNLOAD"
        const val MARK_FAV = "MARK_FAV"
        const val REMOVE_FROM_FAVORITES = "REMOVE_FROM_FAVORITES"
        const val TOGGLE_PLAYED = "MARK_PLAYED"
        const val REMOVE_FROM_QUEUE = "REMOVE_FROM_QUEUE"
        const val DELETE = "DELETE"
        const val REMOVE_FROM_HISTORY = "REMOVE_FROM_HISTORY"
    }
}
