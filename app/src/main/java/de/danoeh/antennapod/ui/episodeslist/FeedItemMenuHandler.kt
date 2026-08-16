package de.danoeh.antennapod.ui.episodeslist

import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem

import androidx.fragment.app.Fragment

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import java.util.Arrays
import java.util.Collections

import de.danoeh.antennapod.R
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.playback.service.PlaybackServiceInterface
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.ui.common.IntentUtils
import de.danoeh.antennapod.playback.service.PlaybackStatus
import de.danoeh.antennapod.ui.share.ShareUtils
import de.danoeh.antennapod.ui.share.ShareDialog
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter
import de.danoeh.antennapod.ui.view.LocalDeleteModal
import org.greenrobot.eventbus.EventBus

/**
 * Handles interactions with the FeedItemMenu.
 */
class FeedItemMenuHandler private constructor() {

    companion object {

        private const val TAG = "FeedItemMenuHandler"

        /**
         * This method should be called in the prepare-methods of menus. It changes
         * the visibility of the menu items depending on a FeedItem's attributes.
         *
         * @param menu          An instance of Menu
         * @param selectedItems The FeedItem for which the menu is supposed to be prepared
         * @param excludeIds Menu item that should be excluded
         * @return Returns true if selectedItem is not null.
         */
        @JvmStatic
        fun onPrepareMenu(menu: Menu?, selectedItems: List<FeedItem>?, vararg excludeIds: Int): Boolean {
            if (menu == null || selectedItems == null || selectedItems.isEmpty() || selectedItems.get(0) == null) {
                return false
            }
            var canSkip = false
            var canRemoveFromQueue = false
            var canAddToQueue = false
            var canVisitWebsite = false
            var canShare = false
            var canRemoveFromInbox = false
            var canMarkPlayed = false
            var canMarkUnplayed = false
            var canResetPosition = false
            var canDelete = false
            var canDownload = false
            var canAddFavorite = false
            var canRemoveFavorite = false
            var canShowTranscript = false
            var canShowSocialInteract = false

            for (item in selectedItems) {
                val hasMedia = item.getMedia() != null
                val isDownloading = hasMedia
                        && DownloadServiceInterface.get()!!.isDownloadingEpisode(item.getMedia()!!.getDownloadUrl())
                canSkip = canSkip or (hasMedia && PlaybackStatus.isPlaying(item.getMedia()))
                canRemoveFromQueue = canRemoveFromQueue or item.isTagged(FeedItem.TAG_QUEUE)
                canAddToQueue = canAddToQueue or (hasMedia && !item.isTagged(FeedItem.TAG_QUEUE))
                canVisitWebsite = canVisitWebsite or (!item.getFeed()!!.isLocalFeed() && ShareUtils.hasLinkToShare(item))
                canShare = canShare or !item.getFeed()!!.isLocalFeed()
                canRemoveFromInbox = canRemoveFromInbox or item.isNew()
                canMarkPlayed = canMarkPlayed or !item.isPlayed()
                canMarkUnplayed = canMarkUnplayed or item.isPlayed()
                canResetPosition = canResetPosition or (hasMedia && item.getMedia()!!.getPosition() != 0)
                canDelete = canDelete or ((hasMedia && item.getMedia()!!.isDownloaded()) || isDownloading)
                canDownload = canDownload or (hasMedia && !item.getMedia()!!.isDownloaded() && !isDownloading)
                canAddFavorite = canAddFavorite or !item.isTagged(FeedItem.TAG_FAVORITE)
                canRemoveFavorite = canRemoveFavorite or item.isTagged(FeedItem.TAG_FAVORITE)
                canShowTranscript = canShowTranscript or item.hasTranscript()
                canShowSocialInteract = canShowSocialInteract or (item.getSocialInteractUrl() != null)
            }

            if (selectedItems.size > 1) {
                canVisitWebsite = false
                canShare = false
                canShowTranscript = false
                canShowSocialInteract = false
                if (canAddFavorite) {
                    canRemoveFavorite = false
                }
            }

            setItemVisibility(menu, R.id.skip_episode_item, canSkip)
            setItemVisibility(menu, R.id.remove_from_queue_item, canRemoveFromQueue)
            setItemVisibility(menu, R.id.add_to_queue_item, canAddToQueue)
            setItemVisibility(menu, R.id.visit_website_item, canVisitWebsite)
            setItemVisibility(menu, R.id.share_item, canShare)
            setItemVisibility(menu, R.id.remove_inbox_item, canRemoveFromInbox)
            setItemVisibility(menu, R.id.mark_read_item, canMarkPlayed)
            setItemVisibility(menu, R.id.mark_unread_item, canMarkUnplayed)
            setItemVisibility(menu, R.id.reset_position, canResetPosition)
            setItemVisibility(menu, R.id.open_social_interact_url, canShowSocialInteract)

            // Display proper strings when item has no media
            if (selectedItems.size == 1 && selectedItems.get(0).getMedia() == null) {
                setItemTitle(menu, R.id.mark_read_item, R.string.mark_read_no_media_label)
                setItemTitle(menu, R.id.mark_unread_item, R.string.mark_unread_label_no_media)
            } else {
                setItemTitle(menu, R.id.mark_read_item, R.string.mark_as_played_label)
                setItemTitle(menu, R.id.mark_unread_item, R.string.mark_as_unplayed_label)
            }

            setItemVisibility(menu, R.id.add_to_favorites_item, canAddFavorite)
            setItemVisibility(menu, R.id.remove_from_favorites_item, canRemoveFavorite)
            setItemVisibility(menu, R.id.remove_item, canDelete)
            setItemVisibility(menu, R.id.download_item, canDownload)
            setItemVisibility(menu, R.id.transcript_item, canShowTranscript)

            if (selectedItems.size == 1 && selectedItems.get(0).getFeed()!!.getState() == Feed.STATE_NOT_SUBSCRIBED) {
                setItemVisibility(menu, R.id.mark_read_item, false)
            }

            if (excludeIds != null) {
                for (id in excludeIds) {
                    setItemVisibility(menu, id, false)
                }
            }
            return true
        }

        /**
         * Used to set the viability of a menu item.
         * This method also does some null-checking so that neither menu nor the menu item are null
         * in order to prevent nullpointer exceptions.
         * @param menu The menu that should be used
         * @param menuId The id of the menu item that will be used
         * @param visibility The new visibility status of given menu item
         * */
        private fun setItemVisibility(menu: Menu, menuId: Int, visibility: Boolean) {
            if (menu == null) {
                return
            }
            val item = menu.findItem(menuId)
            if (item != null) {
                item.setVisible(visibility)
            }
        }

        /**
         * This method allows to replace to String of a menu item with a different one.
         * @param menu Menu item that should be used
         * @param id The id of the string that is going to be replaced.
         * @param noMedia The id of the new String that is going to be used.
         * */
        @JvmStatic
        fun setItemTitle(menu: Menu, id: Int, noMedia: Int) {
            val item = menu.findItem(id)
            if (item != null) {
                item.setTitle(noMedia)
            }
        }

        /**
         * Default menu handling for the given FeedItem.
         * A Fragment instance, (rather than the more generic Context), is needed as a parameter
         * to support some UI operations, e.g., creating a Snackbar.
         */
        @JvmStatic
        fun onMenuItemClicked(fragment: Fragment, menuItemId: Int, selectedItem: FeedItem): Boolean {

            val context = fragment.requireContext()
            if (menuItemId == R.id.skip_episode_item) {
                context.sendBroadcast(MediaButtonStarter.createIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT))
            } else if (menuItemId == R.id.remove_item) {
                LocalDeleteModal.showLocalFeedDeleteWarningIfNecessary(context, Arrays.asList(selectedItem),
                        { DBWriter.deleteFeedMediaOfItem(context, selectedItem.getMedia()!!) })
            } else if (menuItemId == R.id.remove_inbox_item) {
                removeNewFlagWithUndo(fragment, selectedItem)
            } else if (menuItemId == R.id.mark_read_item) {
                EpisodeMultiSelectActionHandler(fragment.requireActivity(), R.id.mark_read_item)
                        .handleAction(Collections.singletonList(selectedItem))
            } else if (menuItemId == R.id.mark_unread_item) {
                EpisodeMultiSelectActionHandler(fragment.requireActivity(), R.id.mark_unread_item)
                        .handleAction(Collections.singletonList(selectedItem))
            } else if (menuItemId == R.id.add_to_queue_item) {
                DBWriter.addQueueItem(context, selectedItem)
            } else if (menuItemId == R.id.remove_from_queue_item) {
                DBWriter.removeQueueItem(context, true, selectedItem)
            } else if (menuItemId == R.id.add_to_favorites_item) {
                DBWriter.addFavoriteItems(Collections.singletonList(selectedItem))
            } else if (menuItemId == R.id.remove_from_favorites_item) {
                DBWriter.removeFavoriteItems(Collections.singletonList(selectedItem))
            } else if (menuItemId == R.id.reset_position) {
                selectedItem.getMedia()!!.setPosition(0)
                if (PlaybackPreferences.getCurrentlyPlayingFeedMediaId() == selectedItem.getMedia()!!.getId()) {
                    PlaybackPreferences.writeNoMediaPlaying()
                    IntentUtils.sendLocalBroadcast(context, PlaybackServiceInterface.ACTION_SHUTDOWN_PLAYBACK_SERVICE)
                }
                DBWriter.markItemsPlayed(FeedItem.UNPLAYED, true, Collections.singletonList(selectedItem))
            } else if (menuItemId == R.id.visit_website_item) {
                IntentUtils.openInBrowser(context, selectedItem.getLinkWithFallback()!!)
            } else if (menuItemId == R.id.open_social_interact_url) {
                MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.visit_social_interact_confirm_dialog_title)
                        .setMessage(context.getString(R.string.visit_social_interact_confirm_dialog_message,
                                selectedItem.getSocialInteractUrl()))
                        .setPositiveButton(R.string.confirm_label, { dialog, which ->
                            IntentUtils.openInBrowser(context, selectedItem.getSocialInteractUrl()!!)
                        })
                        .setNegativeButton(R.string.cancel_label, null)
                        .show()
            } else if (menuItemId == R.id.share_item) {
                val shareDialog = ShareDialog.newInstance(selectedItem)
                shareDialog.show((fragment.requireActivity().getSupportFragmentManager()), "ShareEpisodeDialog")
            } else {
                Log.d(TAG, "Unknown menuItemId: " + menuItemId)
                return false
            }
            // Refresh menu state

            return true
        }

        /**
         * Remove new flag with additional UI logic to allow undo with Snackbar.
         *
         * Undo is useful for Remove new flag, given there is no UI to undo it otherwise
         * ,i.e., there is (context) menu item for add new flag
         */
        @JvmStatic
        fun markReadWithUndo(fragment: Fragment, item: FeedItem?,
                             playState: Int, showSnackbar: Boolean) {
            if (item == null) {
                return
            }

            Log.d(TAG, "markReadWithUndo(" + item.getId() + ")")
            // we're marking it as unplayed since the user didn't actually play it
            // but they don't want it considered 'NEW' anymore
            DBWriter.markItemsPlayed(playState, false, Collections.singletonList(item))

            val context = fragment.requireContext()
            val h = Handler(context.getMainLooper())
            val r = Runnable {
                val media = item.getMedia()
                if (media == null) {
                    return@Runnable
                }
                val shouldAutoDelete = UserPreferences.isAutoDelete()
                        && (!item.getFeed()!!.isLocalFeed() || UserPreferences.isAutoDeleteLocal())
                val smartMarkAsPlayedSecs = UserPreferences.getSmartMarkAsPlayedSecs()
                val almostEnded = media.getDuration() > 0
                        && media.getPosition() >= media.getDuration() - smartMarkAsPlayedSecs * 1000
                if (almostEnded && shouldAutoDelete) {
                    DBWriter.deleteFeedMediaOfItem(context, media)
                }
            }

            val message: String
            when (playState) {
                FeedItem.PLAYED -> message = fragment.getResources().getQuantityString(
                        R.plurals.marked_as_played_message, 1, 1)
                else -> {
                    if (item.getPlayState() == FeedItem.NEW) {
                        //was new
                        message = fragment.getString(R.string.removed_from_inbox_message)
                    } else {
                        //was played
                        message = fragment.getResources().getQuantityString(
                                R.plurals.marked_as_unplayed_message, 1, 1)
                    }
                }
            }

            if (showSnackbar) {
                EventBus.getDefault().post(MessageEvent(message,
                        {
                            DBWriter.markItemsPlayed(item.getPlayState(), false, Collections.singletonList(item))
                            // don't forget to cancel the thing that's going to remove the media
                            h.removeCallbacks(r)
                        }, fragment.getString(R.string.undo)))
            }
            h.postDelayed(r, 2000)
        }

        @JvmStatic
        fun removeNewFlagWithUndo(fragment: Fragment, item: FeedItem) {
            markReadWithUndo(fragment, item, FeedItem.UNPLAYED, false)
        }
    }
}
