package de.danoeh.antennapod.actionbutton

import android.content.Context
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

import java.util.Collections

import de.danoeh.antennapod.R
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.ui.view.LocalDeleteModal

class DeleteActionButton(item: FeedItem) : ItemActionButton(item) {

    override fun getLabel(): Int {
        return R.string.delete_label
    }

    override fun getDrawable(): Int {
        return R.drawable.ic_delete
    }

    override fun onClick(context: Context) {
        val media: FeedMedia? = item.getMedia()
        if (media == null) {
            return
        }

        LocalDeleteModal.showLocalFeedDeleteWarningIfNecessary(context, Collections.singletonList(item),
                { DBWriter.deleteFeedMediaOfItem(context, media) })
    }

    override fun getVisibility(): Int {
        if (item.getMedia() != null && item.getMedia()!!.isDownloaded()) {
            return View.VISIBLE
        }

        return View.INVISIBLE
    }
}
