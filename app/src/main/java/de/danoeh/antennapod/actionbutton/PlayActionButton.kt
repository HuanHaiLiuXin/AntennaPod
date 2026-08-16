package de.danoeh.antennapod.actionbutton

import android.content.Context
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import de.danoeh.antennapod.R
import de.danoeh.antennapod.playback.service.PlaybackService
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.playback.MediaType
import org.greenrobot.eventbus.EventBus

import java.util.Collections

class PlayActionButton(item: FeedItem) : ItemActionButton(item) {
    companion object {
        private const val TAG = "PlayActionButton"
    }

    override fun getLabel(): Int {
        return R.string.play_label
    }

    override fun getDrawable(): Int {
        return R.drawable.ic_play_24dp
    }

    override fun onClick(context: Context) {
        val media: FeedMedia? = item.getMedia()
        if (media == null) {
            return
        }
        if (!media.fileExists()) {
            Log.i(TAG, "Missing episode. Will update the database now.")
            media.setDownloaded(false, 0)
            media.setLocalFileUrl(null)
            DBWriter.setMediaDownloadInformation(media)
            EventBus.getDefault().post(FeedItemEvent(Collections.singletonList(media.getItem()), false))
            EventBus.getDefault().post(MessageEvent(context.getString(R.string.error_file_not_found)))
            return
        }
        PlaybackServiceStarter(context, media)
                .callEvenIfRunning(true)
                .start()

        if (media.getMediaType() == MediaType.VIDEO) {
            context.startActivity(PlaybackService.getPlayerActivityIntent(context, media))
        }
    }
}
