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

class PlayLocalActionButton(item: FeedItem) : ItemActionButton(item) {

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

        PlaybackServiceStarter(context, media)
                .callEvenIfRunning(true)
                .start()

        if (media.getMediaType() == MediaType.VIDEO) {
            context.startActivity(PlaybackService.getPlayerActivityIntent(context, media))
        }
    }
}
