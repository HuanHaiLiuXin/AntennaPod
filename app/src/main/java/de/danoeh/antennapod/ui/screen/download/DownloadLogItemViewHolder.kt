package de.danoeh.antennapod.ui.screen.download

import android.content.Context
import android.text.Layout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.danoeh.antennapod.R
import de.danoeh.antennapod.ui.common.CircularProgressBar

class DownloadLogItemViewHolder(context: Context, parent: ViewGroup) :
        RecyclerView.ViewHolder(LayoutInflater.from(context).inflate(R.layout.downloadlog_item, parent, false)) {
    val secondaryActionButton: View = itemView.findViewById(R.id.secondaryActionButton)
    val secondaryActionIcon: ImageView = itemView.findViewById(R.id.secondaryActionIcon)
    val secondaryActionProgress: CircularProgressBar = itemView.findViewById(R.id.secondaryActionProgress)
    val icon: ImageView = itemView.findViewById(R.id.icon)
    val title: TextView = itemView.findViewById(R.id.txtvTitle)
    val status: TextView = itemView.findViewById(R.id.status)
    val reason: TextView = itemView.findViewById(R.id.txtvReason)
    val tapForDetails: TextView = itemView.findViewById(R.id.txtvTapForDetails)

    init {
        title.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
        itemView.setTag(this)
    }
}
