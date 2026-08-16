package de.danoeh.antennapod.actionbutton

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

import de.danoeh.antennapod.R
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.storage.database.DBWriter

class CancelDownloadActionButton(item: FeedItem) : ItemActionButton(item) {

    override fun getLabel(): Int {
        return R.string.cancel_download_label
    }

    override fun getDrawable(): Int {
        return R.drawable.ic_cancel
    }

    override fun onClick(context: Context) {
        val media: FeedMedia? = item.getMedia()
        DownloadServiceInterface.get()!!.cancel(context, media!!)
        item.disableAutoDownload()
        DBWriter.setFeedItem(item, false)
    }
}
