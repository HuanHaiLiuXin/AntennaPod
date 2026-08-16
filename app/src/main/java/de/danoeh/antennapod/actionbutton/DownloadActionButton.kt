package de.danoeh.antennapod.actionbutton

import android.content.Context
import android.view.View
import android.widget.Toast

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.storage.preferences.UsageStatistics
import de.danoeh.antennapod.net.common.NetworkUtils

class DownloadActionButton(item: FeedItem) : ItemActionButton(item) {
    companion object {
        private const val TIMEOUT_NETWORK_WARN_SECONDS = 300
        private const val BYPASS_TYPE_NOW = 1
        private const val BYPASS_TYPE_LATER = 2

        private var bypassCellularNetworkType = 0
        private var bypassCellularNetworkWarningTimer = 0L
    }

    override fun getLabel(): Int {
        return R.string.download_label
    }

    override fun getDrawable(): Int {
        return R.drawable.ic_download
    }

    override fun getVisibility(): Int {
        return if (item.getFeed()!!.isLocalFeed()) View.INVISIBLE else View.VISIBLE
    }

    override fun onClick(context: Context) {
        val media: FeedMedia? = item.getMedia()
        if (media == null || shouldNotDownload(media)) {
            return
        }

        UsageStatistics.logAction(UsageStatistics.ACTION_DOWNLOAD)

        val timeSinceBypass = System.currentTimeMillis() / 1000 - bypassCellularNetworkWarningTimer
        val shouldBypass = timeSinceBypass < TIMEOUT_NETWORK_WARN_SECONDS
        if (shouldBypass && bypassCellularNetworkType == BYPASS_TYPE_NOW) {
            Toast.makeText(context, context.getResources().getQuantityString(R.plurals.mobile_download_notice,
                    TIMEOUT_NETWORK_WARN_SECONDS / 60, TIMEOUT_NETWORK_WARN_SECONDS / 60), Toast.LENGTH_LONG).show()
        }
        if (NetworkUtils.isEpisodeDownloadAllowed() || shouldBypass) {
            DownloadServiceInterface.get()!!.downloadNow(context, item, bypassCellularNetworkType == BYPASS_TYPE_NOW)
        } else {
            val builder = MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.confirm_mobile_download_dialog_title)
                    .setPositiveButton(R.string.confirm_mobile_download_dialog_download_later,
                            { d, w ->
                                bypassCellularNetworkType = BYPASS_TYPE_LATER
                                bypassCellularNetworkWarningTimer = System.currentTimeMillis() / 1000
                                DownloadServiceInterface.get()!!.downloadNow(context, item, false)
                            })
                    .setNeutralButton(R.string.confirm_mobile_download_dialog_allow_this_time,
                            { d, w ->
                                bypassCellularNetworkType = BYPASS_TYPE_NOW
                                bypassCellularNetworkWarningTimer = System.currentTimeMillis() / 1000
                                DownloadServiceInterface.get()!!.downloadNow(context, item, true)
                            })
                    .setNegativeButton(R.string.cancel_label, null)
            if (NetworkUtils.isNetworkRestricted() && NetworkUtils.isVpnOverWifi()) {
                builder.setMessage(context.getString(R.string.confirm_mobile_download_dialog_message)
                        + "\n\n" + context.getString(R.string.confirm_mobile_download_dialog_message_vpn))
            } else {
                builder.setMessage(R.string.confirm_mobile_download_dialog_message)
            }

            builder.show()
        }
    }

    private fun shouldNotDownload(media: FeedMedia): Boolean {
        val isDownloading = DownloadServiceInterface.get()!!.isDownloadingEpisode(media.getDownloadUrl()!!)
        return isDownloading || media.isDownloaded()
    }
}
