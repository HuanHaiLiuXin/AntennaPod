package de.danoeh.antennapod.ui.screen.subscriptions

import android.view.Menu
import android.view.MenuItem
import androidx.fragment.app.Fragment
import de.danoeh.antennapod.R
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.ui.screen.feed.RemoveFeedDialog
import de.danoeh.antennapod.ui.screen.feed.RenameFeedDialog
import de.danoeh.antennapod.ui.screen.feed.preferences.TagSettingsDialog
import de.danoeh.antennapod.ui.share.ShareUtils

import java.util.Collections

/**
 * Handles interactions with the FeedItemMenu.
 */
class FeedMenuHandler private constructor() {
    companion object {
        fun onPrepareMenu(menu: Menu?, selectedItems: List<Feed>?): Boolean {
            if (menu == null || selectedItems == null || selectedItems.isEmpty() || selectedItems.get(0) == null) {
                return false
            }
            var allSubscribed = true
            var allArchived = true
            for (feed in selectedItems) {
                if (feed.getState() != Feed.STATE_SUBSCRIBED) {
                    allSubscribed = false
                }
                if (feed.getState() != Feed.STATE_ARCHIVED) {
                    allArchived = false
                }
            }
            setItemVisibility(menu, R.id.remove_all_inbox_item, allSubscribed)
            setItemVisibility(menu, R.id.remove_archive_feed, !allArchived && allSubscribed)
            setItemVisibility(menu, R.id.remove_restore_feed, allArchived)
            val singleNonLocalFeedSelected = selectedItems.size == 1 && !selectedItems.get(0).isLocalFeed()
            setItemVisibility(menu, R.id.share_feed, singleNonLocalFeedSelected)
            return true
        }

        private fun setItemVisibility(menu: Menu, menuId: Int, visibility: Boolean) {
            val item = menu.findItem(menuId)
            if (item != null) {
                item.setVisible(visibility)
            }
        }

        fun onMenuItemClicked(fragment: Fragment, menuItemId: Int, selectedFeed: Feed): Boolean {
            val context = fragment.requireContext()
            if (menuItemId == R.id.rename_folder_item) {
                RenameFeedDialog(fragment.requireActivity(), selectedFeed).show()
            } else if (menuItemId == R.id.remove_all_inbox_item) {
                FeedMultiSelectActionHandler(fragment.requireActivity(), Collections.singletonList(selectedFeed))
                        .handleAction(R.id.remove_all_inbox_item)
            } else if (menuItemId == R.id.edit_tags) {
                TagSettingsDialog.newInstance(Collections.singletonList(selectedFeed.getPreferences()!!))
                        .show(fragment.getChildFragmentManager(), TagSettingsDialog.TAG)
            } else if (menuItemId == R.id.remove_archive_feed || menuItemId == R.id.remove_restore_feed) {
                RemoveFeedDialog(Collections.singletonList(selectedFeed))
                        .show(fragment.getChildFragmentManager(), null)
            } else if (menuItemId == R.id.share_feed) {
                ShareUtils.shareFeedLink(context, selectedFeed)
            } else {
                return false
            }
            return true
        }
    }
}
