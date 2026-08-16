package de.danoeh.antennapod.ui.screen.subscriptions

import android.content.DialogInterface
import android.util.Log

import androidx.annotation.PluralsRes
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.util.Consumer

import java.util.ArrayList
import java.util.Locale

import de.danoeh.antennapod.R
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.databinding.PlaybackSpeedFeedSettingDialogBinding
import de.danoeh.antennapod.ui.common.ConfirmationDialog
import de.danoeh.antennapod.ui.screen.feed.RemoveFeedDialog
import de.danoeh.antennapod.ui.screen.feed.preferences.TagSettingsDialog
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.ui.preferences.screen.PreferenceListDialog
import de.danoeh.antennapod.ui.preferences.screen.PreferenceSwitchDialog
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus

import de.danoeh.antennapod.ui.share.ShareUtils

class FeedMultiSelectActionHandler(private val activity: FragmentActivity, private val selectedItems: List<Feed>) {
    fun handleAction(id: Int) {
        if (selectedItems.isEmpty()) {
            return
        }
        if (id == R.id.remove_archive_feed || id == R.id.remove_restore_feed) {
            RemoveFeedDialog(selectedItems).show(activity.getSupportFragmentManager(), null)
        } else if (id == R.id.notify_new_episodes) {
            notifyNewEpisodesPrefHandler()
        } else if (id == R.id.keep_updated) {
            keepUpdatedPrefHandler()
        } else if (id == R.id.autodownload) {
            autoDownloadPrefHandler()
        } else if (id == R.id.autoDeleteDownload) {
            autoDeleteEpisodesPrefHandler()
        } else if (id == R.id.playback_speed) {
            playbackSpeedPrefHandler()
        } else if (id == R.id.edit_tags) {
            editFeedPrefTags()
        } else if (id == R.id.remove_all_inbox_item) {
            removeAllFromInbox()
        } else if (id == R.id.share_feed) {
            if (!selectedItems.get(0).isLocalFeed()) {
                ShareUtils.shareFeedLink(activity, selectedItems.get(0))
            }
        } else {
            Log.e(TAG, "Unrecognized speed dial action item. Do nothing. id=" + id)
        }
    }

    private fun notifyNewEpisodesPrefHandler() {
        val preferenceSwitchDialog = PreferenceSwitchDialog(activity,
                activity.getString(R.string.episode_notification),
                activity.getString(R.string.episode_notification_summary))
        preferenceSwitchDialog.setOnPreferenceChangedListener(object : PreferenceSwitchDialog.OnPreferenceChangedListener {
            override fun preferenceChanged(enabled: Boolean) {
                saveFeedPreferences { feedPreferences -> feedPreferences.setShowEpisodeNotification(enabled) }
            }
        })
        preferenceSwitchDialog.openDialog()
    }

    private fun autoDownloadPrefHandler() {
        val preferenceListDialog = PreferenceListDialog(activity,
                activity.getString(R.string.auto_download_label))
        val items = activity.getResources().getStringArray(R.array.spnEnableAutoDownloadItems)
        preferenceListDialog.openDialog(items)
        preferenceListDialog.setOnPreferenceChangedListener(object : PreferenceListDialog.OnPreferenceChangedListener {
            override fun preferenceChanged(which: Int) {
                val autoDownloadSetting = when (which) {
                    1 -> FeedPreferences.AutoDownloadSetting.ENABLED
                    2 -> FeedPreferences.AutoDownloadSetting.DISABLED
                    else -> FeedPreferences.AutoDownloadSetting.GLOBAL
                }
                saveFeedPreferences { feedPreferences -> feedPreferences.setAutoDownload(autoDownloadSetting) }
            }
        })
    }

    private fun playbackSpeedPrefHandler() {
        val viewBinding =
                PlaybackSpeedFeedSettingDialogBinding.inflate(activity.getLayoutInflater())
        viewBinding.seekBar.setProgressChangedListener { speed ->
            viewBinding.currentSpeedLabel.setText(String.format(Locale.getDefault(), "%.2fx", speed))
        }
        viewBinding.useGlobalCheckbox.setOnCheckedChangeListener { buttonView, isChecked ->
            viewBinding.seekBar.setEnabled(!isChecked)
            viewBinding.seekBar.setAlpha(if (isChecked) 0.4f else 1f)
            viewBinding.currentSpeedLabel.setAlpha(if (isChecked) 0.4f else 1f)
        }
        viewBinding.seekBar.updateSpeed(1.0f)
        MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.playback_speed)
                .setView(viewBinding.getRoot())
                .setPositiveButton(android.R.string.ok) { dialog, which ->
                    val newSpeed = if (viewBinding.useGlobalCheckbox.isChecked())
                        FeedPreferences.SPEED_USE_GLOBAL else viewBinding.seekBar.getCurrentSpeed()
                    saveFeedPreferences { feedPreferences -> feedPreferences.setFeedPlaybackSpeed(newSpeed) }
                }
                .setNegativeButton(R.string.cancel_label, null)
                .show()
    }

    private fun autoDeleteEpisodesPrefHandler() {
        val preferenceListDialog = PreferenceListDialog(activity,
                activity.getString(R.string.pref_auto_delete_playback_title))
        val items = activity.getResources().getStringArray(R.array.spnAutoDeleteItems)
        preferenceListDialog.openDialog(items)
        preferenceListDialog.setOnPreferenceChangedListener(object : PreferenceListDialog.OnPreferenceChangedListener {
            override fun preferenceChanged(which: Int) {
                val autoDeleteAction = FeedPreferences.AutoDeleteAction.fromCode(which)
                saveFeedPreferences { feedPreferences -> feedPreferences.setAutoDeleteAction(autoDeleteAction) }
            }
        })
    }

    private fun keepUpdatedPrefHandler() {
        val preferenceSwitchDialog = PreferenceSwitchDialog(activity,
                activity.getString(R.string.kept_updated),
                activity.getString(R.string.keep_updated_summary))
        preferenceSwitchDialog.setOnPreferenceChangedListener(object : PreferenceSwitchDialog.OnPreferenceChangedListener {
            override fun preferenceChanged(keepUpdated: Boolean) {
                saveFeedPreferences { feedPreferences -> feedPreferences.setKeepUpdated(keepUpdated) }
            }
        })
        preferenceSwitchDialog.openDialog()
    }

    private fun showMessage(@PluralsRes msgId: Int, numItems: Int) {
        EventBus.getDefault().post(MessageEvent(activity.getResources()
                .getQuantityString(msgId, numItems, numItems)))
    }

    private fun saveFeedPreferences(preferencesConsumer: Consumer<FeedPreferences>) {
        for (feed in selectedItems) {
            preferencesConsumer.accept(feed.getPreferences()!!)
            DBWriter.setFeedPreferences(feed.getPreferences()!!)
        }
        showMessage(R.plurals.updated_feeds_batch_label, selectedItems.size)
    }

    private fun editFeedPrefTags() {
        val preferencesList = ArrayList<FeedPreferences>()
        for (feed in selectedItems) {
            preferencesList.add(feed.getPreferences()!!)
        }
        TagSettingsDialog.newInstance(preferencesList).show(activity.getSupportFragmentManager(),
                TagSettingsDialog.TAG)
    }

    private fun removeAllFromInbox() {
        object : ConfirmationDialog(activity, R.string.remove_all_inbox_label, R.string.remove_all_inbox_confirmation_msg) {
            override fun onConfirmButtonPressed(clickedDialog: DialogInterface) {
                clickedDialog.dismiss()
                Observable.fromAction<Any> {
                    for (selectedFeed in selectedItems) {
                        DBWriter.removeFeedNewFlag(selectedFeed.getId())
                    }
                }
                        .subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
            }
        }.createNewDialog().show()
    }

    companion object {
        private const val TAG = "FeedSelectHandler"
    }
}
