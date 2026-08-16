package de.danoeh.antennapod.actionbutton

import android.content.Context
import android.view.KeyEvent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import de.danoeh.antennapod.R
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.playback.base.BuildConfig
import de.danoeh.antennapod.playback.service.PlaybackController
import de.danoeh.antennapod.playback.service.PlaybackStatus
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter

class PauseActionButton(item: FeedItem) : ItemActionButton(item) {

    override fun getLabel(): Int {
        return R.string.pause_label
    }

    override fun getDrawable(): Int {
        return R.drawable.ic_pause
    }

    override fun onClick(context: Context) {
        val media: FeedMedia? = item.getMedia()
        if (media == null) {
            return
        }

        if (!PlaybackStatus.isCurrentlyPlaying(media)) {
            return
        }

        if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
            PlaybackController.bindToMedia3Service(context) { controller -> controller.pause() }
            return
        }

        context.sendBroadcast(MediaButtonStarter.createIntent(context, KeyEvent.KEYCODE_MEDIA_PAUSE))
    }
}
