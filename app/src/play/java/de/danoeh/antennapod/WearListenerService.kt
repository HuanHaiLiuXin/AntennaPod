package de.danoeh.antennapod

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.net.sync.wearinterface.WearDataPaths
import de.danoeh.antennapod.net.sync.wearinterface.WearSerializer
import de.danoeh.antennapod.playback.service.PlaybackController
import de.danoeh.antennapod.playback.service.PlaybackService
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

import java.util.Collections

class WearListenerService : WearableListenerService() {
    companion object {
        private const val TAG = "WearListenerService"
        private const val MAX_ITEMS = 100
    }

    override fun onCreate() {
        super.onCreate()
        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onPlayerStatusEvent(event: PlayerStatusEvent) {
        val media = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId())
        if (media == null || media.getItem() == null) {
            return
        }
        val isPlaying = PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PLAYING
        val payload = WearSerializer.nowPlayingToBytes(media.getItem()!!, isPlaying)
        Wearable.getNodeClient(this).getConnectedNodes()
                .addOnSuccessListener { nodes ->
                    for (node in nodes) {
                        Wearable.getMessageClient(this).sendMessage(node.getId(), WearDataPaths.NOW_PLAYING, payload)
                    }
                }
    }

    override fun onMessageReceived(event: MessageEvent) {
        val path = event.getPath()
        val sourceNodeId = event.getSourceNodeId()
        Log.d(TAG, "Message received: " + path + " from " + sourceNodeId)
        Completable.fromAction { handleMessage(path, sourceNodeId) }
                .subscribeOn(Schedulers.computation())
                .subscribe(
                        { },
                        { throwable -> Log.e(TAG, "Failed to handle wearable message: " + path, throwable) })
    }

    private fun handleMessage(path: String, sourceNodeId: String) {
        when (path) {
            WearDataPaths.NOW_PLAYING -> {
                val media = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId())
                if (media == null) {
                    return
                }
                if (!PlaybackService.isRunning) {
                    reply(sourceNodeId, WearDataPaths.NOW_PLAYING,
                            WearSerializer.nowPlayingToBytes(media.getItem()!!, false))
                    return
                }
                PlaybackController.bindToMedia3Service(this) { controller ->
                    media.setPosition(controller.getCurrentPosition().toInt())
                    if (controller.getDuration() > 0) {
                        media.setDuration(controller.getDuration().toInt())
                    }
                    reply(sourceNodeId, WearDataPaths.NOW_PLAYING,
                            WearSerializer.nowPlayingToBytes(media.getItem()!!, true))
                }
            }
            WearDataPaths.PAUSE -> PlaybackController.bindToMedia3Service(this) { controller -> controller.pause() }
            WearDataPaths.SKIP_FORWARD -> PlaybackController.bindToMedia3Service(this) { controller -> controller.seekForward() }
            WearDataPaths.SKIP_BACKWARD -> PlaybackController.bindToMedia3Service(this) { controller -> controller.seekBack() }
            WearDataPaths.QUEUE -> reply(sourceNodeId, path, WearSerializer.episodesToBytes(DBReader.getQueue()))
            WearDataPaths.DOWNLOADS -> reply(sourceNodeId, path, WearSerializer.episodesToBytes(DBReader.getEpisodes(0, MAX_ITEMS,
                    FeedItemFilter(FeedItemFilter.DOWNLOADED), SortOrder.DATE_NEW_OLD)))
            WearDataPaths.EPISODES -> reply(sourceNodeId, path, WearSerializer.episodesToBytes(DBReader.getEpisodes(0, MAX_ITEMS,
                    FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD)))
            WearDataPaths.SUBSCRIPTIONS -> reply(sourceNodeId, path, WearSerializer.feedsToBytes(DBReader.getFeedList()))
            else -> {
                if (path.startsWith(WearDataPaths.PLAY_PREFIX)) {
                    try {
                        val itemId = path.substring(WearDataPaths.PLAY_PREFIX.length).toLong()
                        playItem(itemId)
                    } catch (e: NumberFormatException) {
                        Log.w(TAG, "Ignoring malformed play path: " + path, e)
                    }
                } else if (path.startsWith(WearDataPaths.FEED_EPISODES_PREFIX)) {
                    try {
                        val feedId = path.substring(WearDataPaths.FEED_EPISODES_PREFIX.length).toLong()
                        val feed = DBReader.getFeed(feedId, false, 0, MAX_ITEMS)
                        val feedItems = if (feed != null) feed.getItems()!! else Collections.emptyList()
                        reply(sourceNodeId, path, WearSerializer.episodesToBytes(feedItems))
                    } catch (e: NumberFormatException) {
                        Log.w(TAG, "Ignoring malformed feed episodes path: " + path, e)
                    }
                } else if (path.startsWith(WearDataPaths.OPEN_ON_PHONE_PREFIX)) {
                    try {
                        val itemId = path.substring(WearDataPaths.OPEN_ON_PHONE_PREFIX.length).toLong()
                        val intent = MainActivityStarter(this).withOpenEpisode(itemId).getIntent()
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    } catch (e: NumberFormatException) {
                        Log.w(TAG, "Ignoring malformed open on phone path: " + path, e)
                    }
                } else {
                    Log.d(TAG, "Ignoring unknown path: " + path)
                }
            }
        }
    }

    private fun playItem(itemId: Long) {
        val item = DBReader.getFeedItem(itemId)
        if (item == null || item.getMedia() == null) {
            Log.w(TAG, "Item or media not found for id " + itemId)
            return
        }
        Log.d(TAG, "Starting playback for: " + item.getTitle())
        PlaybackServiceStarter(this, item.getMedia())
                .callEvenIfRunning(true)
                .start()
    }

    private fun reply(nodeId: String, path: String, payload: ByteArray) {
        Log.d(TAG, "Sending reply to " + nodeId + " at " + path + " (" + payload.size + " bytes)")
        Wearable.getMessageClient(this).sendMessage(nodeId, path, payload)
                .addOnSuccessListener { id -> Log.d(TAG, "Reply sent: " + path) }
                .addOnFailureListener { e -> Log.e(TAG, "Failed to send reply to " + path, e) }
    }
}
