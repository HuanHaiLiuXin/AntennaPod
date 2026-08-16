package de.danoeh.antennapod.ui.screen.feed

import android.app.Activity

import java.lang.ref.WeakReference
import java.util.HashSet

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.storage.database.NavDrawerData
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.ui.common.databinding.EditTextDialogBinding
import de.danoeh.antennapod.model.feed.FeedPreferences

class RenameFeedDialog {

    private val activityRef: WeakReference<Activity>
    private var feed: Feed? = null
    private var tag: NavDrawerData.TagItem? = null

    constructor(activity: Activity, feed: Feed) {
        this.activityRef = WeakReference(activity)
        this.feed = feed
    }

    constructor(activity: Activity, drawerItem: NavDrawerData.TagItem) {
        this.activityRef = WeakReference(activity)
        this.tag = drawerItem
    }

    fun show() {
        val activity = activityRef.get()
        if (activity == null) {
            return
        }

        val binding = EditTextDialogBinding.inflate(LayoutInflater.from(activity))
        val title = if (feed != null) feed!!.getTitle() else tag!!.getTitle()

        binding.textInput.setText(title)
        val dialog = MaterialAlertDialogBuilder(activity)
                .setView(binding.getRoot())
                .setTitle(if (feed != null) R.string.rename_feed_label else R.string.rename_tag_label)
                .setPositiveButton(android.R.string.ok) { d, input ->
                    val newTitle = binding.textInput.getText().toString()
                    if (feed != null) {
                        feed!!.setCustomTitle(newTitle)
                        DBWriter.setFeedCustomTitle(feed!!)
                    } else {
                        renameTag(newTitle)
                    }
                }
                .setNeutralButton(R.string.reset, null)
                .setNegativeButton(R.string.cancel_label, null)
                .show()

        // To prevent cancelling the dialog on button click
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            binding.textInput.setText(title) }
    }

    private fun renameTag(title: String) {
        for (feed in tag!!.getFeeds()) {
            val preferences = feed.getPreferences()
            (preferences!!.getTags() as HashSet<String>).remove(tag!!.getTitle())
            (preferences.getTags() as HashSet<String>).add(title)
            DBWriter.setFeedPreferences(preferences)
        }
    }

}
