package de.danoeh.antennapod.ui.view

import android.content.Context
import android.os.Handler
import android.os.Looper

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.ui.i18n.R
import de.danoeh.antennapod.model.feed.FeedItem

class LocalDeleteModal {
    companion object {
        @JvmStatic
        fun showLocalFeedDeleteWarningIfNecessary(context: Context, items: Iterable<FeedItem>,
                                                  deleteCommand: Runnable) {
            var anyLocalFeed = false
            for (item in items) {
                if (item.getFeed()!!.isLocalFeed()) {
                    anyLocalFeed = true
                    break
                }
            }

            if (!anyLocalFeed) {
                deleteCommand.run()
                return
            }

            Handler(Looper.getMainLooper()).post {
                MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.delete_label)
                        .setMessage(R.string.delete_local_feed_confirmation_dialog_message)
                        .setPositiveButton(R.string.delete_label) { dialog, which -> deleteCommand.run() }
                        .setNegativeButton(R.string.cancel_label, null)
                        .show()
            }
        }
    }
}
