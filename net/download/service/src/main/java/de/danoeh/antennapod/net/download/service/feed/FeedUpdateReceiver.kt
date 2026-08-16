package de.danoeh.antennapod.net.download.service.feed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager

/**
 * Refreshes all feeds when it receives an intent
 */
class FeedUpdateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "FeedUpdateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent")
        FeedUpdateManager.getInstance()!!.runOnce(context)
    }

}
