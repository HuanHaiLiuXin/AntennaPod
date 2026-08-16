package de.danoeh.antennapod.ui.screen.subscriptions

import android.content.DialogInterface
import android.view.MenuItem
import androidx.fragment.app.Fragment
import de.danoeh.antennapod.R
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.database.NavDrawerData
import de.danoeh.antennapod.ui.common.ConfirmationDialog
import de.danoeh.antennapod.ui.screen.feed.RenameFeedDialog

import java.util.HashSet

class TagMenuHandler private constructor() {
    companion object {
        fun onMenuItemClicked(fragment: Fragment, selectedTag: NavDrawerData.TagItem,
                              item: MenuItem, tagAdapter: SubscriptionTagAdapter): Boolean {
            val itemId = item.getItemId()
            if (itemId == R.id.rename_folder_item) {
                RenameFeedDialog(fragment.requireActivity(), selectedTag).show()
                return true
            } else if (itemId == R.id.delete_folder_item) {
                val dialog = object : ConfirmationDialog(fragment.requireContext(), R.string.delete_tag_label,
                        fragment.getString(R.string.delete_tag_confirmation, selectedTag.getTitle())) {

                    override fun onConfirmButtonPressed(dialog: DialogInterface) {
                        tagAdapter.setSelectedTag(FeedPreferences.TAG_ROOT)
                        for (feed in selectedTag.getFeeds()) {
                            val preferences = feed.getPreferences()
                            (preferences!!.getTags() as HashSet<String>).remove(selectedTag.getTitle())
                            DBWriter.setFeedPreferences(preferences)
                        }
                    }
                }
                dialog.createNewDialog().show()
                return true
            }
            return false
        }
    }
}
