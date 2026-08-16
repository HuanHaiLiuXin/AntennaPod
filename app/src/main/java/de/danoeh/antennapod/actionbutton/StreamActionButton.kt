package de.danoeh.antennapod.actionbutton

import android.content.Context

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

import de.danoeh.antennapod.R
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.playback.MediaType
import de.danoeh.antennapod.playback.service.PlaybackService
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter
import de.danoeh.antennapod.storage.preferences.UsageStatistics

class StreamActionButton(item: FeedItem) : ItemActionButton(item) {

    override fun getLabel(): Int {
        return R.string.stream_label
    }

    override fun getDrawable(): Int {
        return R.drawable.ic_stream
    }

    override fun onClick(context: Context) {
        val media: FeedMedia? = item.getMedia()
        if (media == null) {
            return
        }
        UsageStatistics.logAction(UsageStatistics.ACTION_STREAM)

        PlaybackServiceStarter(context, media)
                .callEvenIfRunning(true)
                .start()

        if (media.getMediaType() == MediaType.VIDEO) {
            context.startActivity(PlaybackService.getPlayerActivityIntent(context, media))
        }
    }
}
