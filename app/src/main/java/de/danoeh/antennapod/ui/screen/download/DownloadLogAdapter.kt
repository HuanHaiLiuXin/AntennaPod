package de.danoeh.antennapod.ui.screen.download

import android.app.Activity
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import de.danoeh.antennapod.R
import de.danoeh.antennapod.actionbutton.DownloadActionButton
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.model.download.DownloadError
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedMedia
import org.greenrobot.eventbus.EventBus

/**
 * Displays a list of DownloadStatus entries.
 */
class DownloadLogAdapter(private val context: Activity) : BaseAdapter() {
    companion object {
        private const val TAG = "DownloadLogAdapter"
    }

    private var downloadLog: List<DownloadResult> = ArrayList()

    fun setDownloadLog(downloadLog: List<DownloadResult>) {
        this.downloadLog = downloadLog
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder: DownloadLogItemViewHolder
        if (convertView == null) {
            holder = DownloadLogItemViewHolder(context, parent)
            holder.itemView.setTag(holder)
        } else {
            holder = convertView.getTag() as DownloadLogItemViewHolder
        }
        bind(holder, getItem(position)!!, position)
        return holder.itemView
    }

    private fun bind(holder: DownloadLogItemViewHolder, status: DownloadResult, position: Int) {
        var statusText = ""
        if (status.getFeedfileType() == Feed.FEEDFILETYPE_FEED) {
            statusText += context.getString(R.string.download_type_feed)
        } else if (status.getFeedfileType() == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
            statusText += context.getString(R.string.download_type_media)
        }
        statusText += " · "
        statusText += DateUtils.getRelativeTimeSpanString(status.getCompletionDate().getTime(),
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, 0)
        holder.status.setText(statusText)

        if (status.getTitle() != null) {
            holder.title.setText(status.getTitle())
        } else {
            holder.title.setText(R.string.download_log_title_unknown)
        }

        if (status.isSuccessful()) {
            holder.icon.setImageResource(R.drawable.ic_check)
            holder.icon.setContentDescription(context.getString(R.string.download_successful))
            holder.secondaryActionButton.setVisibility(View.INVISIBLE)
            holder.reason.setVisibility(View.GONE)
            holder.tapForDetails.setVisibility(View.GONE)
        } else {
            if (status.getReason() == DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE) {
                holder.icon.setImageResource(R.drawable.ic_info)
            } else {
                holder.icon.setImageResource(R.drawable.ic_error)
            }
            holder.icon.setContentDescription(context.getString(R.string.error_label))
            holder.reason.setText(DownloadErrorLabel.from(status.getReason()!!))
            holder.reason.setVisibility(View.VISIBLE)
            holder.tapForDetails.setVisibility(View.VISIBLE)

            if (newerWasSuccessful(position, status.getFeedfileType(), status.getFeedfileId())) {
                holder.secondaryActionButton.setVisibility(View.INVISIBLE)
                holder.secondaryActionButton.setOnClickListener(null)
                holder.secondaryActionButton.setTag(null)
            } else {
                holder.secondaryActionIcon.setImageResource(R.drawable.ic_refresh)
                holder.secondaryActionButton.setVisibility(View.VISIBLE)

                if (status.getFeedfileType() == Feed.FEEDFILETYPE_FEED) {
                    holder.secondaryActionButton.setOnClickListener {
                        holder.secondaryActionButton.setVisibility(View.INVISIBLE)
                        val feed = DBReader.getFeed(status.getFeedfileId(), false, 0, 0)
                        if (feed == null) {
                            Log.e(TAG, "Could not find feed for feed id: " + status.getFeedfileId())
                            return@setOnClickListener
                        }
                        FeedUpdateManager.getInstance()!!.runOnce(context, feed)
                    }
                } else if (status.getFeedfileType() == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
                    holder.secondaryActionButton.setOnClickListener {
                        holder.secondaryActionButton.setVisibility(View.INVISIBLE)
                        val media = DBReader.getFeedMedia(status.getFeedfileId())
                        if (media == null) {
                            Log.e(TAG, "Could not find feed media for feed id: " + status.getFeedfileId())
                            return@setOnClickListener
                        }
                        DownloadActionButton(media.getItem()!!).onClick(context)
                        EventBus.getDefault().post(MessageEvent(
                                context.getResources().getString(R.string.status_downloading_label)))
                    }
                }
            }
        }
    }

    private fun newerWasSuccessful(downloadStatusIndex: Int, feedTypeId: Int, id: Long): Boolean {
        for (i in 0 until downloadStatusIndex) {
            val status = downloadLog[i]
            if (status.getFeedfileType() == feedTypeId && status.getFeedfileId() == id && status.isSuccessful()) {
                return true
            }
        }
        return false
    }

    override fun getCount(): Int {
        return downloadLog.size
    }

    override fun getItem(position: Int): DownloadResult? {
        if (position < downloadLog.size) {
            return downloadLog[position]
        }
        return null
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }
}
