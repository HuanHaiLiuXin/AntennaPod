package de.danoeh.antennapod.actionbutton

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import android.view.View

import de.danoeh.antennapod.R
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.storage.database.DBWriter

import java.util.Collections

class MarkAsPlayedActionButton(item: FeedItem) : ItemActionButton(item) {

    override fun getLabel(): Int {
        return if (item.hasMedia()) R.string.mark_as_played_label else R.string.mark_read_no_media_label
    }

    override fun getDrawable(): Int {
        return R.drawable.ic_check
    }

    override fun onClick(context: Context) {
        if (!item.isPlayed()) {
            DBWriter.markItemsPlayed(FeedItem.PLAYED, true, Collections.singletonList(item))
        }
    }

    override fun getVisibility(): Int {
        return if (item.isPlayed()) View.INVISIBLE else View.VISIBLE
    }
}
