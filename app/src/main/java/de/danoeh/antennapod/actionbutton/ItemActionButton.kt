package de.danoeh.antennapod.actionbutton

import android.content.Context
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import android.view.View

import de.danoeh.antennapod.playback.service.PlaybackStatus
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.storage.preferences.UserPreferences

abstract class ItemActionButton(protected val item: FeedItem) {

    @StringRes
    abstract fun getLabel(): Int

    @DrawableRes
    abstract fun getDrawable(): Int

    abstract fun onClick(context: Context)

    open fun getVisibility(): Int {
        return View.VISIBLE
    }

    fun configure(button: View, icon: ImageView, context: Context) {
        button.setVisibility(getVisibility())
        button.setContentDescription(context.getString(getLabel()))
        button.setOnClickListener { onClick(context) }
        icon.setImageResource(getDrawable())
    }

    companion object {
        @JvmStatic
        fun forItem(item: FeedItem): ItemActionButton {
            val media = item.getMedia()
            if (media == null) {
                return MarkAsPlayedActionButton(item)
            }

            val isDownloadingMedia = DownloadServiceInterface.get()!!.isDownloadingEpisode(media.getDownloadUrl()!!)
            if (PlaybackStatus.isCurrentlyPlaying(media)) {
                return PauseActionButton(item)
            } else if (item.getFeed()!!.isLocalFeed()) {
                return PlayLocalActionButton(item)
            } else if (media.isDownloaded()) {
                return PlayActionButton(item)
            } else if (isDownloadingMedia) {
                return CancelDownloadActionButton(item)
            } else if (UserPreferences.isStreamOverDownload()) {
                return StreamActionButton(item)
            } else {
                return DownloadActionButton(item)
            }
        }
    }
}
