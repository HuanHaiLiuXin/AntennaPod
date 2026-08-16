package de.danoeh.antennapod.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider

import de.danoeh.antennapod.ui.common.Converter
import java.io.File
import java.net.URLEncoder

import de.danoeh.antennapod.R
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia

/** Utility methods for sharing data */
class ShareUtils private constructor() {
    companion object {
        private const val TAG = "ShareUtils"
        private const val ABBREVIATE_MAX_LENGTH = 50

        @JvmStatic
        fun shareLink(context: Context, text: String) {
            val intent = ShareCompat.IntentBuilder(context)
                    .setType("text/plain")
                    .setText(text)
                    .setChooserTitle(R.string.share_url_label)
                    .createChooserIntent()
            context.startActivity(intent)
        }

        @JvmStatic
        fun shareFeedLink(context: Context, feed: Feed) {
            var feedurl = URLEncoder.encode(feed.getDownloadUrl()!!, "UTF-8")
            feedurl = feedurl.replace("htt", "%68%74%74") // To not confuse users by having a url inside a url
            val text = feed.getTitle() + "\n\n" +
                    "https://antennapod.org/deeplink/subscribe/?url=" + feedurl +
                    "&title=" + URLEncoder.encode(feed.getTitle()!!, "UTF-8")
            shareLink(context, text)
        }

        @JvmStatic
        fun hasLinkToShare(item: FeedItem): Boolean {
            return item.getLinkWithFallback() != null
        }

        @JvmStatic
        fun getSocialFeedItemShareText(context: Context, item: FeedItem,
                                       withPosition: Boolean, abbreviate: Boolean): String {
            var text = item.getFeed()!!.getTitle() + ": "

            if (abbreviate && item.getTitle()!!.length > ABBREVIATE_MAX_LENGTH) {
                text += item.getTitle()!!.substring(0, ABBREVIATE_MAX_LENGTH) + "…"
            } else {
                text += item.getTitle()
            }

            if (item.getMedia() != null && withPosition) {
                text += "\n" + context.getResources().getString(R.string.share_starting_position_label) + ": "
                text += Converter.getDurationStringLong(item.getMedia()!!.getPosition())
            }

            if (hasLinkToShare(item)) {
                if (!abbreviate) {
                    text += "\n"
                }
                text += "\n" + context.getResources().getString(R.string.share_dialog_episode_website_label) + ": "
                if (abbreviate && item.getLinkWithFallback()!!.length > ABBREVIATE_MAX_LENGTH) {
                    text += item.getLinkWithFallback()!!.substring(0, ABBREVIATE_MAX_LENGTH) + "…"
                } else {
                    text += item.getLinkWithFallback()
                }
            }

            if (item.getMedia() != null && item.getMedia()!!.getDownloadUrl() != null) {
                if (!abbreviate) {
                    text += "\n"
                }
                text += "\n" + context.getResources().getString(R.string.share_dialog_media_file_label) + ": "
                if (abbreviate && item.getMedia()!!.getDownloadUrl()!!.length > ABBREVIATE_MAX_LENGTH) {
                    text += item.getMedia()!!.getDownloadUrl()!!.substring(0, ABBREVIATE_MAX_LENGTH) + "…"
                } else {
                    text += item.getMedia()!!.getDownloadUrl()
                }
                if (withPosition) {
                    text += "#t=" + item.getMedia()!!.getPosition() / 1000
                }
            }
            return text
        }

        @JvmStatic
        fun shareFeedItemFile(context: Context, media: FeedMedia) {
            val fileUri = FileProvider.getUriForFile(context, context.getString(R.string.provider_authority),
                    File(media.getLocalFileUrl()!!))

            ShareCompat.IntentBuilder(context)
                    .setType(media.getMimeType())
                    .addStream(fileUri)
                    .setChooserTitle(R.string.share_file_label)
                    .startChooser()

            Log.e(TAG, "shareFeedItemFile called")
        }
    }
}
