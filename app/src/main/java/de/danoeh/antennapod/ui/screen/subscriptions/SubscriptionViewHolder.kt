package de.danoeh.antennapod.ui.screen.subscriptions

import android.app.Activity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import de.danoeh.antennapod.R
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.CoverLoader
import de.danoeh.antennapod.ui.common.ThemeUtils

import java.lang.ref.WeakReference
import java.text.NumberFormat

class SubscriptionViewHolder(itemView: View, mainActivity: Activity) : RecyclerView.ViewHolder(itemView) {
    val title: TextView = itemView.findViewById(R.id.titleLabel)
    val coverImage: ImageView = itemView.findViewById(R.id.coverImage)
    val count: TextView = itemView.findViewById(R.id.countViewPill)
    val fallbackTitle: TextView = itemView.findViewById(R.id.fallbackTitleLabel)
    val gradient: ImageView = itemView.findViewById(R.id.gradientOverlay)
    val selectIcon: ImageView? = itemView.findViewById(R.id.selectedIcon)
    val card: CardView? = itemView.findViewById(R.id.outerContainer)
    val errorIcon: View = itemView.findViewById(R.id.errorIcon)
    val mainActivityRef: WeakReference<Activity> = WeakReference(mainActivity)

    fun bind(feed: Feed, columnCount: Int, counter: Int) {
        title.setText(feed.getTitle())
        fallbackTitle.setText(feed.getTitle())
        coverImage.setContentDescription(feed.getTitle())
        if (counter > 0) {
            count.setText(NumberFormat.getInstance().format(counter))
            count.setVisibility(View.VISIBLE)
        } else {
            count.setVisibility(View.GONE)
        }

        val coverLoader = CoverLoader()
        val textAndImageCombined = feed.isLocalFeed() && feed.getImageUrl() != null
                && feed.getImageUrl()!!.startsWith(Feed.PREFIX_GENERATIVE_COVER)
        coverLoader.withUri(feed.getImageUrl())
        errorIcon.setVisibility(if (feed.hasLastUpdateFailed()) View.VISIBLE else View.GONE)

        if (UserPreferences.shouldShowSubscriptionTitle() || columnCount == 1) {
            // No need for fallback title when already showing title
            fallbackTitle.setVisibility(View.GONE)
        } else {
            coverLoader.withPlaceholderView(fallbackTitle, textAndImageCombined)
        }
        coverLoader.withCoverView(coverImage)
        coverLoader.load()

        if (card != null) {
            card.setCardBackgroundColor(ThemeUtils.getColorFromAttr(
                    mainActivityRef.get()!!, R.attr.colorSurfaceContainer))
        }

        val textPadding = if (columnCount <= 3) 16 else 8
        title.setPadding(textPadding, textPadding, textPadding, textPadding)
        fallbackTitle.setPadding(textPadding, textPadding, textPadding, textPadding)

        var textSize = 14
        if (columnCount == 3) {
            textSize = 15
        } else if (columnCount == 2) {
            textSize = 16
        }
        title.setTextSize(textSize.toFloat())
        fallbackTitle.setTextSize(textSize.toFloat())
    }
}
