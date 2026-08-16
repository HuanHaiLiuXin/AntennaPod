package de.danoeh.antennapod.ui.screen.feed.preferences

import android.app.Activity
import android.os.CountDownTimer
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.ui.common.databinding.EditTextDialogBinding
import de.danoeh.antennapod.model.feed.Feed

import java.lang.ref.WeakReference
import java.util.Locale

abstract class EditUrlSettingsDialog {
    companion object {
        const val TAG = "EditUrlSettingsDialog"
    }

    private val activityRef: WeakReference<Activity>
    private val feed: Feed

    constructor(activity: Activity, feed: Feed) {
        this.activityRef = WeakReference(activity)
        this.feed = feed
    }

    fun show() {
        val activity = activityRef.get()
        if (activity == null) {
            return
        }

        val binding = EditTextDialogBinding.inflate(LayoutInflater.from(activity))

        binding.textInput.setText(feed.getDownloadUrl())

        MaterialAlertDialogBuilder(activity)
                .setView(binding.getRoot())
                .setTitle(R.string.edit_url_menu)
                .setPositiveButton(android.R.string.ok) { d, input ->
                    showConfirmAlertDialog(binding.textInput.getText().toString()) }
                .setNegativeButton(R.string.cancel_label, null)
                .show()
    }

    private fun onConfirmed(original: String, updated: String) {
        try {
            DBWriter.updateFeedDownloadURL(original, updated)!!.get()
            feed.setDownloadUrl(updated)
            FeedUpdateManager.getInstance()!!.runOnce(activityRef.get()!!, feed)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun showConfirmAlertDialog(url: String) {
        val activity = activityRef.get()!!

        val alertDialog = MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.edit_url_menu)
                .setMessage(R.string.edit_url_confirmation_msg)
                .setPositiveButton(android.R.string.ok) { d, input ->
                    onConfirmed(feed.getDownloadUrl()!!, url)
                    setUrl(url)
                }
                .setNegativeButton(R.string.cancel_label, null)
                .show()
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false)

        object : CountDownTimer(15000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(
                        String.format(Locale.getDefault(), "%s (%d)",
                                activity.getString(android.R.string.ok), millisUntilFinished / 1000 + 1))
            }

            override fun onFinish() {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true)
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(android.R.string.ok)
            }
        }.start()
    }

    protected abstract fun setUrl(url: String)
}
