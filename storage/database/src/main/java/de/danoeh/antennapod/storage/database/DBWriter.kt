package de.danoeh.antennapod.storage.database

import android.app.backup.BackupManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log

import android.view.KeyEvent
import androidx.documentfile.provider.DocumentFile

import com.google.common.util.concurrent.Futures
import de.danoeh.antennapod.event.DownloadLogEvent

import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter
import org.greenrobot.eventbus.EventBus

import java.io.File
import java.util.ArrayList
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.Semaphore

import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.event.FeedListUpdateEvent
import de.danoeh.antennapod.event.playback.PlaybackHistoryEvent
import de.danoeh.antennapod.event.QueueEvent
import de.danoeh.antennapod.event.FeedEvent
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction

/**
 * Provides methods for writing data to AntennaPod's database.
 * In general, DBWriter-methods will be executed on an internal ExecutorService.
 * Some methods return a Future-object which the caller can use for waiting for the method's completion. The returned Future's
 * will NOT contain any results.
 */
class DBWriter {

    companion object {
        private const val TAG = "DBWriter"

        private val dbExec: ExecutorService = Executors.newSingleThreadExecutor { r ->
            val t = Thread(r)
            t.setName("DatabaseExecutor")
            t.setPriority(Thread.MIN_PRIORITY)
            t
        }

        /**
         * Wait until all threads are finished to avoid the "Illegal connection pointer" error of
         * Robolectric. Call this method only for unit tests.
         */
        @JvmStatic
        fun tearDownTests() {
            // dbExec is single-threaded FIFO, so if a newly submitted task runs, all previous tasks must have finished
            val available = Semaphore(1, true)
            try {
                available.acquire()
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
            dbExec.submit { available.release() }
            try {
                available.acquire()
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }

        /**
         * Deletes a downloaded FeedMedia file from the storage device.
         *
         * @param context A context that is used for opening a database connection.
         */
        @JvmStatic
        fun deleteFeedMediaOfItem(context: Context,
                                  media: FeedMedia?): Future<*>? {
            return runOnDbThread {
                if (media == null) {
                    return@runOnDbThread
                }
                deleteFeedMediaSynchronous(context, media)
                EventBus.getDefault().post(FeedItemEvent(if (media.getItem() != null)
                    Collections.singletonList(media.getItem()!!) else Collections.emptyList(), false))
                if (UserPreferences.shouldDeleteRemoveFromQueue()) {
                    DBWriter.removeQueueItemSynchronous(context, false, *longArrayOf(media.getItemId()))
                }
            }
        }

        private fun deleteFeedMediaSynchronous(context: Context, media: FeedMedia) {
            Log.i(TAG, java.lang.String.format(Locale.US, "Requested to delete FeedMedia [id=%d, title=%s, downloaded=%s",
                    media.getId(), media.getEpisodeTitle(), media.isDownloaded()))
            var localDelete = false
            if (media.getLocalFileUrl() != null && media.getLocalFileUrl()!!.startsWith("content://")) {
                // Local feed
                val documentFile = DocumentFile.fromSingleUri(context, Uri.parse(media.getLocalFileUrl()))
                if (documentFile == null || !documentFile.exists() || !documentFile.delete()) {
                    Log.d(TAG, "Deletion of local file failed.")
                }
                media.setLocalFileUrl(null)
                localDelete = true
            } else if (media.getLocalFileUrl() != null) {
                // delete transcript file before the media file because the fileurl is needed
                if (media.getTranscriptFileUrl() != null) {
                    val transcriptFile = File(media.getTranscriptFileUrl())
                    if (transcriptFile.exists() && !transcriptFile.delete()) {
                        Log.d(TAG, "Deletion of transcript file failed.")
                    }
                }

                // delete downloaded media file
                val mediaFile = File(media.getLocalFileUrl())
                if (mediaFile.exists() && !mediaFile.delete()) {
                    Log.d(TAG, "Deletion of downloaded file failed.")
                }
                media.setDownloaded(false, 0)
                media.setLocalFileUrl(null)
                media.setHasEmbeddedPicture(false)
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.setMediaDownloadInformation(media)
                adapter.close()
            }

            if (media.getId() == PlaybackPreferences.getCurrentlyPlayingFeedMediaId()) {
                PlaybackPreferences.writeNoMediaPlaying()
                context.sendBroadcast(MediaButtonStarter.createIntent(context, KeyEvent.KEYCODE_MEDIA_STOP))
            }

            if (localDelete) {
                // Do full update of this feed to get rid of the item
                FeedUpdateManager.getInstance()!!.runOnce(context, media.getItem()!!.getFeed()!!)
            } else {
                if (media.getItem()!!.getFeed()!!.getState() != Feed.STATE_NOT_SUBSCRIBED) {
                    SynchronizationQueue.getInstance()!!.enqueueEpisodeAction(
                            EpisodeAction.Builder(media.getItem(), EpisodeAction.DELETE)
                                .currentTimestamp()
                                .build())
                }
            }
        }

        /**
         * Deletes a Feed and all downloaded files of its components like images and downloaded episodes.
         *
         * @param context A context that is used for opening a database connection.
         * @param feedId  ID of the Feed that should be deleted.
         */
        @JvmStatic
        fun deleteFeed(context: Context, feedId: Long): Future<*>? {
            return runOnDbThread {
                val feed = DBReader.getFeed(feedId, false, 0, Int.MAX_VALUE)
                if (feed == null) {
                    return@runOnDbThread
                }

                deleteFeedItemsSynchronous(context, feed.getItems()!!)

                // delete feed
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.removeFeed(feed)
                adapter.close()

                if (!feed.isLocalFeed() && feed.getState() != Feed.STATE_NOT_SUBSCRIBED) {
                    SynchronizationQueue.getInstance()!!.enqueueFeedRemoved(feed.getDownloadUrl())
                }
                EventBus.getDefault().post(FeedListUpdateEvent(feed))
            }
        }

        /**
         * Remove the listed items and their FeedMedia entries.
         * Deleting media also removes the download log entries.
         */
        @JvmStatic
        fun deleteFeedItems(context: Context, items: List<FeedItem>): Future<*>? {
            return runOnDbThread { deleteFeedItemsSynchronous(context, items) }
        }

        /**
         * Remove the listed items and their FeedMedia entries.
         * Deleting media also removes the download log entries.
         */
        private fun deleteFeedItemsSynchronous(context: Context, items: List<FeedItem>) {
            val queue = DBReader.getQueue()
            val removedFromQueue = ArrayList<FeedItem>()
            val deleted = ArrayList<FeedItem>()
            for (item in items) {
                if (queue.remove(item)) {
                    removedFromQueue.add(item)
                }
                if (item.getMedia() != null) {
                    if (item.getMedia()!!.getId() == PlaybackPreferences.getCurrentlyPlayingFeedMediaId()) {
                        // Applies to both downloaded and streamed media
                        PlaybackPreferences.writeNoMediaPlaying()
                        context.sendBroadcast(MediaButtonStarter.createIntent(context, KeyEvent.KEYCODE_MEDIA_STOP))
                    }
                    if (!item.getFeed()!!.isLocalFeed()) {
                        if (DownloadServiceInterface.get()!!.isDownloadingEpisode(item.getMedia()!!.getDownloadUrl()!!)) {
                            DownloadServiceInterface.get()!!.cancel(context, item.getMedia()!!)
                        }
                        if (item.getMedia()!!.isDownloaded()) {
                            deleteFeedMediaSynchronous(context, item.getMedia()!!)
                            deleted.add(item)
                        }
                    }
                }
            }

            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            if (!removedFromQueue.isEmpty()) {
                adapter.setQueue(queue)
            }
            adapter.removeFeedItems(items)
            adapter.close()

            for (item in removedFromQueue) {
                EventBus.getDefault().post(QueueEvent.irreversibleRemoved(item))
            }
            EventBus.getDefault().post(FeedItemEvent(deleted, false))

            // we assume we also removed download log entries for the feed or its media files.
            // especially important if download or refresh failed, as the user should not be able
            // to retry these
            EventBus.getDefault().post(DownloadLogEvent.listUpdated())

            val backupManager = BackupManager(context)
            backupManager.dataChanged()
        }

        /**
         * Deletes the entire playback history.
         */
        @JvmStatic
        fun clearPlaybackHistory(): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.clearPlaybackHistory()
                adapter.close()
                EventBus.getDefault().post(PlaybackHistoryEvent.listUpdated())
            }
        }

        /**
         * Deletes the entire download log.
         */
        @JvmStatic
        fun clearDownloadLog(): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.clearDownloadLog()
                adapter.close()
                EventBus.getDefault().post(DownloadLogEvent.listUpdated())
            }
        }

        @JvmStatic
        fun deleteFromPlaybackHistory(feedItem: FeedItem): Future<*>? {
            return addItemToPlaybackHistory(feedItem.getMedia(), Date(0))
        }

        /**
         * Adds a FeedMedia object to the playback history. A FeedMedia object is in the playback history if
         * its playback completion date is set to a non-null value. This method will set the playback completion date to the
         * current date regardless of the current value.
         *
         * @param media FeedMedia that should be added to the playback history.
         */
        @JvmStatic
        fun addItemToPlaybackHistory(media: FeedMedia?): Future<*>? {
            return addItemToPlaybackHistory(media, Date())
        }

        /**
         * Adds a FeedMedia object to the playback history. A FeedMedia object is in the playback history if
         * its playback completion date is set to a non-null value. This method will set the playback completion date to the
         * current date regardless of the current value.
         *
         * @param media FeedMedia that should be added to the playback history.
         * @param date LastPlayedTimeHistory for <code>media</code>
         */
        @JvmStatic
        fun addItemToPlaybackHistory(media: FeedMedia?, date: Date): Future<*>? {
            media!!.setLastPlayedTimeHistory(date)
            return runOnDbThread {
                Log.d(TAG, "Adding item to playback history")
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.setFeedMediaLastPlayedTimeHistory(media)
                adapter.close()
                EventBus.getDefault().post(PlaybackHistoryEvent.listUpdated())
            }
        }

        /**
         * Adds a Download status object to the download log.
         *
         * @param status The DownloadStatus object.
         */
        @JvmStatic
        fun addDownloadStatus(status: DownloadResult): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.setDownloadStatus(status)
                adapter.close()
                EventBus.getDefault().post(DownloadLogEvent.listUpdated())
            }

        }

        /**
         * Inserts a FeedItem in the queue at the specified index. The 'read'-attribute of the FeedItem will be set to
         * true. If the FeedItem is already in the queue, the queue will not be modified.
         *
         * @param context             A context that is used for opening a database connection.
         * @param itemId              ID of the FeedItem that should be added to the queue.
         * @param index               Destination index. Must be in range 0..queue.size()
         * @throws IndexOutOfBoundsException if index < 0 || index >= queue.size()
         */
        @JvmStatic
        fun addQueueItemAt(context: Context, itemId: Long, index: Int): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                val queue = DBReader.getQueue()

                if (!itemListContains(queue, itemId)) {
                    val item = DBReader.getFeedItem(itemId)
                    if (item != null) {
                        queue.add(index, item)
                        adapter.setQueue(queue)
                        item.addTag(FeedItem.TAG_QUEUE)
                        EventBus.getDefault().post(QueueEvent.added(item, index))
                        EventBus.getDefault().post(FeedItemEvent(Collections.singletonList(item), false))
                        if (item.isNew()) {
                            DBWriter.markItemsPlayed(FeedItem.UNPLAYED, false, Collections.singletonList(item))
                        }
                    }
                }

                adapter.close()
                AutoDownloadManager.getInstance()!!.autodownloadUndownloadedItems(context)
            }
        }

        /**
         * Appends FeedItem objects to the end of the queue. The 'read'-attribute of all items will be set to true.
         * If a FeedItem is already in the queue, the FeedItem will not change its position in the queue.
         *
         * @param context  A context that is used for opening a database connection.
         * @param items    FeedItem objects that should be added to the queue.
         */
        @JvmStatic
        fun addQueueItem(context: Context, vararg items: FeedItem): Future<*>? {
            return runOnDbThread {
                if (items.size < 1) {
                    return@runOnDbThread
                }

                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                val queue = DBReader.getQueue()

                val markAsUnplayed = ArrayList<FeedItem>()
                val events = ArrayList<QueueEvent>()
                val updatedItems = ArrayList<FeedItem>()
                val positionCalculator =
                        ItemEnqueuePositionCalculator(UserPreferences.getEnqueueLocation())
                val currentlyPlaying = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId())
                var insertPosition = positionCalculator.calcPosition(queue, currentlyPlaying)
                for (item in items) {
                    if (itemListContains(queue, item.getId())) {
                        continue
                    } else if (!item.hasMedia()) {
                        continue
                    }
                    queue.add(insertPosition, item)
                    events.add(QueueEvent.added(item, insertPosition))

                    item.addTag(FeedItem.TAG_QUEUE)
                    updatedItems.add(item)
                    if (item.isNew()) {
                        markAsUnplayed.add(item)
                    }
                    insertPosition++
                }
                if (!updatedItems.isEmpty()) {
                    applySortOrder(queue, events)
                    adapter.setQueue(queue)
                    for (event in events) {
                        EventBus.getDefault().post(event)
                    }
                    EventBus.getDefault().post(FeedItemEvent(updatedItems, false))
                    DBWriter.markItemsPlayed(FeedItem.UNPLAYED, false, markAsUnplayed)
                }
                adapter.close()
                AutoDownloadManager.getInstance()!!.autodownloadUndownloadedItems(context)
            }
        }

        /**
         * Sorts the queue depending on the configured sort order.
         * If the queue is not in keep sorted mode, nothing happens.
         *
         * @param queue  The queue to be sorted.
         * @param events Replaces the events by a single SORT event if the list has to be sorted automatically.
         */
        private fun applySortOrder(queue: MutableList<FeedItem>, events: MutableList<QueueEvent>) {
            if (!UserPreferences.isQueueKeepSorted()) {
                // queue is not in keep sorted mode, there's nothing to do
                return
            }

            // Sort queue by configured sort order
            val sortOrder = UserPreferences.getQueueKeepSortedOrder()
            if (sortOrder == SortOrder.RANDOM) {
                // do not shuffle the list on every change
                return
            }
            val permutor = FeedItemPermutors.getPermutor(sortOrder)
            permutor.reorder(queue)

            // Replace ADDED events by a single SORTED event
            events.clear()
            events.add(QueueEvent.sorted(queue))
        }

        /**
         * Removes all FeedItem objects from the queue.
         */
        @JvmStatic
        fun clearQueue(): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.clearQueue()
                adapter.close()
                EventBus.getDefault().post(QueueEvent.cleared())
            }
        }

        /**
         * Removes a FeedItem object from the queue.
         *
         * @param context             A context that is used for opening a database connection.
         * @param performAutoDownload true if an auto-download process should be started after the operation.
         * @param item                FeedItem that should be removed.
         */
        @JvmStatic
        fun removeQueueItem(context: Context,
                            performAutoDownload: Boolean, item: FeedItem): Future<*>? {
            return runOnDbThread { removeQueueItemSynchronous(context, performAutoDownload, *longArrayOf(item.getId())) }
        }

        @JvmStatic
        fun removeQueueItem(context: Context, performAutoDownload: Boolean,
                            vararg itemIds: Long): Future<*>? {
            return runOnDbThread { removeQueueItemSynchronous(context, performAutoDownload, *itemIds) }
        }

        private fun removeQueueItemSynchronous(context: Context,
                                               performAutoDownload: Boolean,
                                               vararg itemIds: Long) {
            if (itemIds.size < 1) {
                return
            }
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            val queue = DBReader.getQueue()

            var queueModified = false
            val events = ArrayList<QueueEvent>()
            val updatedItems = ArrayList<FeedItem>()
            for (itemId in itemIds) {
                val position = indexInItemList(queue, itemId)
                if (position >= 0) {
                    val item = DBReader.getFeedItem(itemId)
                    if (item == null) {
                        Log.e(TAG, "removeQueueItem - item in queue but somehow cannot be loaded."
                                + " Item ignored. It should never happen. id:" + itemId)
                        continue
                    }
                    queue.removeAt(position)
                    item.removeTag(FeedItem.TAG_QUEUE)
                    events.add(QueueEvent.removed(item))
                    updatedItems.add(item)
                    queueModified = true
                } else {
                    Log.v(TAG, "removeQueueItem - item  not in queue:" + itemId)
                }
            }
            if (queueModified) {
                adapter.setQueue(queue)
                for (event in events) {
                    EventBus.getDefault().post(event)
                }
                EventBus.getDefault().post(FeedItemEvent(updatedItems, false))
            } else {
                Log.w(TAG, "Queue was not modified by call to removeQueueItem")
            }
            adapter.close()
            if (performAutoDownload) {
                AutoDownloadManager.getInstance()!!.autodownloadUndownloadedItems(context)
            }
        }

        @JvmStatic
        fun toggleFavoriteItem(item: FeedItem): Future<*>? {
            if (item.isTagged(FeedItem.TAG_FAVORITE)) {
                return removeFavoriteItems(Collections.singletonList(item))
            } else {
                return addFavoriteItems(Collections.singletonList(item))
            }
        }

        @JvmStatic
        fun addFavoriteItems(items: List<FeedItem>): Future<*>? {
            for (item in items) {
                item.addTag(FeedItem.TAG_FAVORITE)
            }
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance().open()
                adapter.addFavoriteItems(items)
                adapter.close()
                EventBus.getDefault().post(FeedItemEvent(items, false))
            }
        }

        @JvmStatic
        fun removeFavoriteItems(items: List<FeedItem>): Future<*>? {
            for (item in items) {
                item.removeTag(FeedItem.TAG_FAVORITE)
            }
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance().open()
                adapter.removeFavoriteItems(items)
                adapter.close()
                EventBus.getDefault().post(FeedItemEvent(items, false))
            }
        }

        /**
         * Changes the position of a FeedItem in the queue.
         *
         * @param from            Source index. Must be in range 0..queue.size()-1.
         * @param to              Destination index. Must be in range 0..queue.size()-1.
         * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
         *                        false if the caller wants to avoid unexpected updates of the GUI.
         * @throws IndexOutOfBoundsException if (to < 0 || to >= queue.size()) || (from < 0 || from >= queue.size())
         */
        @JvmStatic
        fun moveQueueItem(from: Int, to: Int, broadcastUpdate: Boolean): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                val queue = DBReader.getQueue()

                if (from >= 0 && from < queue.size && to >= 0 && to < queue.size) {
                    val item = queue.removeAt(from)
                    queue.add(to, item)

                    adapter.setQueue(queue)
                    if (broadcastUpdate) {
                        EventBus.getDefault().post(QueueEvent.moved(item, to))
                    }
                }
                adapter.close()
            }
        }

        @JvmStatic
        fun moveQueueItemsToTop(items: List<FeedItem>): Future<*>? {
            return runOnDbThread { moveQueueItemsSynchronous(true, items) }
        }

        @JvmStatic
        fun moveQueueItemsToBottom(items: List<FeedItem>): Future<*>? {
            return runOnDbThread { moveQueueItemsSynchronous(false, items) }
        }

        private fun moveQueueItemsSynchronous(moveToTop: Boolean, items: List<FeedItem>) {
            if (items.isEmpty()) {
                return
            }

            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            val queue = DBReader.getQueue()

            val selectedItems = if (moveToTop) ArrayList(items) else items
            if (moveToTop) {
                Collections.reverse(selectedItems)
            }

            var queueModified = false
            val events = ArrayList<QueueEvent>()

            queue.removeAll(selectedItems)
            events.add(QueueEvent.setQueue(queue))

            for (item in selectedItems) {
                val newIndex = if (moveToTop) 0 else queue.size
                queue.add(newIndex, item)
                events.add(QueueEvent.moved(item, newIndex))
                queueModified = true
            }

            if (queueModified) {
                adapter.setQueue(queue)
                for (event in events) {
                    EventBus.getDefault().post(event)
                }
            } else {
                Log.w(TAG, "moveToTop: " + moveToTop + " - Queue was not modified.")
            }
            adapter.close()
        }

        @JvmStatic
        fun resetPagedFeedPage(feed: Feed): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.resetPagedFeedPage(feed)
                adapter.close()
            }
        }

        /**
         * Sets the 'read'-attribute of a FeedItem to the specified value.
         *
         * @param played             New value of the 'read'-attribute one of FeedItem.PLAYED,
         *                           FeedItem.NEW, FeedItem.UNPLAYED
         * @param resetMediaPosition true if this method should also reset the position of the FeedItem's FeedMedia object.
         * @param items              The FeedItem objects to be updated
         */
        @JvmStatic
        fun markItemsPlayed(played: Int, resetMediaPosition: Boolean, items: List<FeedItem>): Future<*>? {
            for (item in items) {
                if (item.hasMedia() && resetMediaPosition) {
                    item.getMedia()!!.setPosition(0)
                }
                item.setPlayState(played)
            }
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.setFeedItemsRead(played, resetMediaPosition, items)
                adapter.close()
                EventBus.getDefault().post(FeedItemEvent(items, true))
            }
        }

        /**
         * Sets the 'read'-attribute of all NEW FeedItems of a specific Feed to UNPLAYED.
         *
         * @param feedId ID of the Feed.
         */
        @JvmStatic
        fun removeFeedNewFlag(feedId: Long): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.setFeedItems(FeedItem.NEW, FeedItem.UNPLAYED, feedId)
                adapter.close()
                EventBus.getDefault().post(FeedItemEvent(Collections.emptyList(), true))
            }
        }

        /**
         * Sets the 'read'-attribute of all NEW FeedItems to UNPLAYED.
         */
        @JvmStatic
        fun removeAllNewFlags(): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.setFeedItems(FeedItem.NEW, FeedItem.UNPLAYED)
                adapter.close()
                EventBus.getDefault().post(FeedItemEvent(Collections.emptyList(), true))
            }
        }

        internal fun addNewFeed(context: Context, vararg feeds: Feed): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.setCompleteFeed(*feeds)
                adapter.close()

                for (feed in feeds) {
                    if (!feed.isLocalFeed() && feed.getState() != Feed.STATE_NOT_SUBSCRIBED) {
                        SynchronizationQueue.getInstance()!!.enqueueFeedAdded(feed.getDownloadUrl())
                    }
                }

                val backupManager = BackupManager(context)
                backupManager.dataChanged()
            }
        }

        internal fun setCompleteFeed(vararg feeds: Feed): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.setCompleteFeed(*feeds)
                adapter.close()
            }
        }

        @JvmStatic
        fun setItemList(items: List<FeedItem>): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.storeFeedItemlist(items)
                adapter.close()
                EventBus.getDefault().post(FeedItemEvent(items, false))
            }
        }

        /**
         * Saves a FeedMedia object in the database. This method will save all attributes of the FeedMedia object. The
         * contents of FeedComponent-attributes (e.g. the FeedMedia's 'item'-attribute) will not be saved.
         * Use carefully to avoid overwriting properties with stale data.
         *
         * @param media The FeedMedia object.
         */
        @JvmStatic
        fun setFeedMedia(media: FeedMedia): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.setMedia(media)
                adapter.close()
            }
        }

        /**
         * Saves the downloaded file url and download state of a FeedMedia object
         *
         * @param media The FeedMedia object.
         */
        @JvmStatic
        fun setMediaDownloadInformation(media: FeedMedia): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.setMediaDownloadInformation(media)
                adapter.close()
            }
        }

        /**
         * Saves the 'position', 'duration' and 'last played time' attributes of a FeedMedia object
         *
         * @param media The FeedMedia object.
         */
        @JvmStatic
        fun setFeedMediaPlaybackInformation(media: FeedMedia): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.setFeedMediaPlaybackInformation(media)
                adapter.close()
            }
        }

        /**
         * Saves a FeedItem object in the database. This method will save all attributes of the FeedItem object including
         * the content of FeedComponent-attributes.
         *
         * @param item The FeedItem object.
         * @param unreadStatusChanged Whether the unread status of this item or related items has changed.
         */
        @JvmStatic
        fun setFeedItem(item: FeedItem, unreadStatusChanged: Boolean): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.setSingleFeedItem(item)
                adapter.close()
                EventBus.getDefault().post(FeedItemEvent(Collections.singletonList(item), unreadStatusChanged))
            }
        }

        /**
         * Updates download URL of a feed
         */
        @JvmStatic
        fun updateFeedDownloadURL(original: String, updated: String): Future<*>? {
            Log.d(TAG, "updateFeedDownloadURL(original: " + original + ", updated: " + updated + ")")
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.setFeedDownloadUrl(original, updated)
                adapter.close()
            }
        }

        /**
         * Saves a FeedPreferences object in the database. The Feed ID of the FeedPreferences-object MUST NOT be 0.
         *
         * @param preferences The FeedPreferences object.
         */
        @JvmStatic
        fun setFeedPreferences(preferences: FeedPreferences): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.setFeedPreferences(preferences)
                adapter.close()
                EventBus.getDefault().post(FeedListUpdateEvent(preferences.getFeedID()))
            }
        }

        private fun itemListContains(items: List<FeedItem>, itemId: Long): Boolean {
            return indexInItemList(items, itemId) >= 0
        }

        private fun indexInItemList(items: List<FeedItem>, itemId: Long): Int {
            for (i in items.indices) {
                val item = items[i]
                if (item.getId() == itemId) {
                    return i
                }
            }
            return -1
        }

        /**
         * Saves if a feed's last update failed
         *
         * @param lastUpdateFailed true if last update failed
         */
        @JvmStatic
        fun setFeedLastUpdateFailed(feedId: Long,
                                    lastUpdateFailed: Boolean): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.setFeedLastUpdateFailed(feedId, lastUpdateFailed)
                adapter.close()
                EventBus.getDefault().post(FeedListUpdateEvent(feedId))
            }
        }

        @JvmStatic
        fun setFeedCustomTitle(feed: Feed): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.setFeedCustomTitle(feed.getId(), feed.getCustomTitle())
                adapter.close()
                EventBus.getDefault().post(FeedListUpdateEvent(feed))
            }
        }

        @JvmStatic
        fun setFeedState(context: Context, feed: Feed, newState: Int): Future<*>? {
            val oldState = feed.getState()
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.setFeedState(feed.getId(), newState)
                feed.setState(newState)
                if (oldState == Feed.STATE_NOT_SUBSCRIBED && newState == Feed.STATE_SUBSCRIBED) {
                    feed.getPreferences()!!.setKeepUpdated(true)
                    DBWriter.setFeedPreferences(feed.getPreferences()!!)
                    FeedUpdateManager.getInstance()!!.runOnceOrAsk(context, feed)
                    SynchronizationQueue.getInstance()!!.enqueueFeedAdded(feed.getDownloadUrl())
                    DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                            SortOrder.DATE_NEW_OLD, 0, Int.MAX_VALUE)
                    for (item in feed.getItems()!!) {
                        if (item.isPlayed()) {
                            SynchronizationQueue.getInstance()!!.enqueueEpisodePlayed(item.getMedia()!!, true)
                        }
                    }
                }
                adapter.close()
                EventBus.getDefault().post(FeedListUpdateEvent(feed))
            }
        }

        /**
         * Sort the FeedItems in the queue with the given the named sort order.
         *
         * @param broadcastUpdate `true` if this operation should trigger a
         *                        QueueUpdateBroadcast. This option should be set to `false`
         *                        if the caller wants to avoid unexpected updates of the GUI.
         */
        @JvmStatic
        fun reorderQueue(sortOrder: SortOrder?, broadcastUpdate: Boolean): Future<*>? {
            if (sortOrder == null) {
                Log.w(TAG, "reorderQueue() - sortOrder is null. Do nothing.")
                return runOnDbThread { }
            }
            val permutor = FeedItemPermutors.getPermutor(sortOrder)
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                val queue = DBReader.getQueue()

                permutor.reorder(queue)
                adapter.setQueue(queue)
                if (broadcastUpdate) {
                    EventBus.getDefault().post(QueueEvent.sorted(queue))
                }
                adapter.close()
            }
        }

        /**
         * Set filter of the feed
         *
         * @param feedId       The feed's ID
         * @param filterValues Values that represent properties to filter by
         */
        @JvmStatic
        fun setFeedItemsFilter(feedId: Long,
                               filterValues: Set<String>): Future<*>? {
            Log.d(TAG, "setFeedItemsFilter() called with: " + "feedId = [" + feedId + "], filterValues = [" + filterValues + "]")
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.setFeedItemFilter(feedId, filterValues)
                adapter.close()
                EventBus.getDefault().post(FeedEvent(FeedEvent.Action.FILTER_CHANGED, feedId))
            }
        }

        /**
         * Set item sort order of the feed
         *
         */
        @JvmStatic
        fun setFeedItemSortOrder(feedId: Long, sortOrder: SortOrder?): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.setFeedItemSortOrder(feedId, sortOrder)
                adapter.close()
                EventBus.getDefault().post(FeedEvent(FeedEvent.Action.SORT_ORDER_CHANGED, feedId))
            }
        }

        /**
         * Reset the statistics in DB
         */
        @JvmStatic
        fun resetStatistics(): Future<*>? {
            return runOnDbThread {
                val adapter = PodDBAdapter.getInstance()
                adapter.open()
                adapter.resetAllMediaPlayedDuration()
                adapter.close()
            }
        }

        /**
         * Removes the feed with the given download url. This method should NOT be executed on the GUI thread.
         *
         * @param context     Used for accessing the db
         * @param downloadUrl URL of the feed.
         */
        @JvmStatic
        fun removeFeedWithDownloadUrl(context: Context, downloadUrl: String) {
            val adapter = PodDBAdapter.getInstance()
            adapter.open()
            var feedId = 0L
            try {
                adapter.getFeedCursorDownloadUrls(false).use { cursor ->
                    if (cursor.moveToFirst()) {
                        do {
                            if (cursor.getString(1) == downloadUrl) {
                                feedId = cursor.getLong(0)
                            }
                        } while (cursor.moveToNext())
                    }
                }
            } finally {
                adapter.close()
            }

            if (feedId != 0L) {
                try {
                    deleteFeed(context, feedId)!!.get()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                } catch (e: ExecutionException) {
                    e.printStackTrace()
                }
            } else {
                Log.w(TAG, "removeFeedWithDownloadUrl: Could not find feed with url: " + downloadUrl)
            }
        }

        /**
         * Submit to the DB thread only if caller is not already on the DB thread. Otherwise,
         * just execute synchronously
         */
        private fun runOnDbThread(runnable: Runnable): Future<*>? {
            if ("DatabaseExecutor" == Thread.currentThread().getName()) {
                runnable.run()
                return Futures.immediateFuture<Any?>(null)
            } else {
                return dbExec.submit(runnable)
            }
        }
    }
}
