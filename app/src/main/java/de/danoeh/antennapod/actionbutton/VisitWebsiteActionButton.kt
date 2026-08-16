package de.danoeh.antennapod.actionbutton

import android.content.Context
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import de.danoeh.antennapod.R
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.ui.common.IntentUtils

class VisitWebsiteActionButton(item: FeedItem) : ItemActionButton(item) {

    override fun getLabel(): Int {
        return R.string.visit_website_label
    }

    override fun getDrawable(): Int {
        return R.drawable.ic_web
    }

    override fun onClick(context: Context) {
        IntentUtils.openInBrowser(context, item.getLink()!!)
    }

    override fun getVisibility(): Int {
        return if (item.getLink() == null) View.INVISIBLE else View.VISIBLE
    }
}
