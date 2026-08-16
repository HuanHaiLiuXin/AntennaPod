package de.danoeh.antennapod.ui.swipeactions

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.fragment.app.Fragment
import de.danoeh.antennapod.R
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus

class RemoveFromQueueSwipeAction : SwipeAction {

    companion object {
        private const val TAG = "RemoveFromQueueSwipeAction"
    }

    override fun getId(): String {
        return SwipeAction.REMOVE_FROM_QUEUE
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_playlist_remove
    }

    override fun getActionColor(): Int {
        return R.attr.colorAccent
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.remove_from_queue_label)
    }

    override fun performAction(item: FeedItem, fragment: Fragment, filter: FeedItemFilter?) {
        Single.fromCallable { DBReader.getQueueIDList().indexOf(item.getId()) }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ position ->
                    val activity: Activity? = fragment.getActivity()
                    if (activity == null) {
                        return@subscribe
                    }

                    DBWriter.removeQueueItem(activity, true, item)
                    if (willRemove(filter, item)) {
                        EventBus.getDefault().post(MessageEvent(
                                fragment.getResources().getQuantityString(R.plurals.removed_from_queue_message, 1, 1),
                                { DBWriter.addQueueItemAt(activity, item.getId(), position) },
                                fragment.getString(R.string.undo)))
                    }
                }, { throwable -> Log.e(TAG, "Failed to get queue position", throwable) })
    }

    override fun willRemove(filter: FeedItemFilter?, item: FeedItem): Boolean {
        return filter!!.showQueued || filter.showNotQueued
    }
}
