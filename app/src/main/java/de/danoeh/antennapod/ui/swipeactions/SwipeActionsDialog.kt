package de.danoeh.antennapod.ui.swipeactions

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View

import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.gridlayout.widget.GridLayout

import java.util.ArrayList

import de.danoeh.antennapod.R
import de.danoeh.antennapod.databinding.FeeditemlistItemBinding
import de.danoeh.antennapod.databinding.SwipeactionsDialogBinding
import de.danoeh.antennapod.databinding.SwipeactionsPickerBinding
import de.danoeh.antennapod.databinding.SwipeactionsPickerItemBinding
import de.danoeh.antennapod.databinding.SwipeactionsRowBinding
import de.danoeh.antennapod.ui.screen.AllEpisodesFragment
import de.danoeh.antennapod.ui.screen.download.CompletedDownloadsFragment
import de.danoeh.antennapod.ui.screen.FavoritesFragment
import de.danoeh.antennapod.ui.screen.feed.FeedItemlistFragment
import de.danoeh.antennapod.ui.screen.InboxFragment
import de.danoeh.antennapod.ui.screen.PlaybackHistoryFragment
import de.danoeh.antennapod.ui.screen.queue.QueueFragment
import de.danoeh.antennapod.ui.common.ThemeUtils

class SwipeActionsDialog(private val context: Context, private val tag: String) {
    companion object {
        private const val LEFT = 1
        private const val RIGHT = 0
    }

    private var rightAction: SwipeAction? = null
    private var leftAction: SwipeAction? = null
    private var keys: List<SwipeAction>? = null

    fun show(prefsChanged: Callback) {
        val actions = SwipeActions.getPrefsWithDefaults(context, tag)
        leftAction = actions.left
        rightAction = actions.right

        val builder = MaterialAlertDialogBuilder(context)

        val newKeys = ArrayList<SwipeAction>()
        if (tag == QueueFragment.TAG) {
            newKeys.add(RemoveFromQueueSwipeAction())
        } else {
            newKeys.add(AddToQueueSwipeAction())
        }
        if (tag != CompletedDownloadsFragment.TAG) {
            newKeys.add(StartDownloadSwipeAction())
        }
        if (tag != CompletedDownloadsFragment.TAG
                && tag != QueueFragment.TAG
                && tag != PlaybackHistoryFragment.TAG) {
            newKeys.add(RemoveFromInboxSwipeAction())
        }
        if (tag != InboxFragment.TAG) {
            newKeys.add(DeleteSwipeAction())
        }
        if (tag == FavoritesFragment.TAG) {
            newKeys.add(RemoveFromFavoritesSwipeAction())
        } else {
            newKeys.add(MarkFavoriteSwipeAction())
        }
        if (tag == PlaybackHistoryFragment.TAG) {
            newKeys.add(RemoveFromHistorySwipeAction())
        }
        if (tag != InboxFragment.TAG) {
            newKeys.add(TogglePlaybackStateSwipeAction())
        }
        keys = newKeys

        var forFragment = ""
        when (tag) {
            InboxFragment.TAG -> forFragment = context.getString(R.string.inbox_label)
            AllEpisodesFragment.TAG -> forFragment = context.getString(R.string.episodes_label)
            CompletedDownloadsFragment.TAG -> forFragment = context.getString(R.string.downloads_label)
            FeedItemlistFragment.TAG -> forFragment = context.getString(R.string.individual_subscription)
            QueueFragment.TAG -> forFragment = context.getString(R.string.queue_label)
            PlaybackHistoryFragment.TAG -> forFragment = context.getString(R.string.playback_history_label)
            FavoritesFragment.TAG -> forFragment = context.getString(R.string.favorite_episodes_label)
            else -> Unit
        }

        builder.setTitle(context.getString(R.string.swipeactions_label) + " - " + forFragment)
        val viewBinding = SwipeactionsDialogBinding.inflate(LayoutInflater.from(context))
        builder.setView(viewBinding.getRoot())

        viewBinding.enableSwitch.setOnCheckedChangeListener { compoundButton, b ->
            viewBinding.actionLeftContainer.getRoot().setAlpha(if (b) 1.0f else 0.4f)
            viewBinding.actionRightContainer.getRoot().setAlpha(if (b) 1.0f else 0.4f)
        }

        viewBinding.enableSwitch.setChecked(SwipeActions.isSwipeActionEnabled(context, tag))

        setupSwipeDirectionView(viewBinding.actionLeftContainer, LEFT)
        setupSwipeDirectionView(viewBinding.actionRightContainer, RIGHT)

        builder.setPositiveButton(R.string.confirm_label) { dialog, which ->
            savePrefs(tag, rightAction!!.getId(), leftAction!!.getId())
            saveActionsEnabledPrefs(viewBinding.enableSwitch.isChecked())
            prefsChanged.onCall()
        }

        builder.setNegativeButton(R.string.cancel_label, null)
        builder.create().show()
    }

    private fun setupSwipeDirectionView(view: SwipeactionsRowBinding, direction: Int) {
        val action = if (direction == LEFT) leftAction!! else rightAction!!

        view.swipeDirectionLabel.setText(if (direction == LEFT) R.string.swipe_left else R.string.swipe_right)
        view.swipeActionLabel.setText(action.getTitle(context))
        populateMockEpisode(view.mockEpisode)
        if (direction == RIGHT && view.previewContainer.getChildAt(0) !== view.swipeIcon) {
            view.previewContainer.removeView(view.swipeIcon)
            view.previewContainer.addView(view.swipeIcon, 0)
        }

        view.swipeIcon.setImageResource(action.getActionIcon())
        view.swipeIcon.setColorFilter(ThemeUtils.getColorFromAttr(context, action.getActionColor()))

        view.changeButton.setOnClickListener { showPicker(view, direction) }
        view.previewContainer.setOnClickListener { showPicker(view, direction) }
    }

    private fun showPicker(view: SwipeactionsRowBinding, direction: Int) {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(if (direction == LEFT) R.string.swipe_left else R.string.swipe_right)

        val picker = SwipeactionsPickerBinding.inflate(LayoutInflater.from(context))
        builder.setView(picker.getRoot())
        builder.setNegativeButton(R.string.cancel_label, null)
        val dialog: AlertDialog = builder.show()

        for (i in keys!!.indices) {
            val actionIndex = i
            val action = keys!!.get(actionIndex)
            val item = SwipeactionsPickerItemBinding.inflate(LayoutInflater.from(context))
            item.swipeActionLabel.setText(action.getTitle(context))

            val icon: Drawable = DrawableCompat.wrap(AppCompatResources.getDrawable(context, action.getActionIcon())!!)
            icon.mutate()
            icon.setTintMode(PorterDuff.Mode.SRC_ATOP)
            if ((direction == LEFT && leftAction === action) || (direction == RIGHT && rightAction === action)) {
                icon.setTint(ThemeUtils.getColorFromAttr(context, action.getActionColor()))
                item.swipeActionLabel.setTextColor(ThemeUtils.getColorFromAttr(context, action.getActionColor()))
            } else {
                icon.setTint(ThemeUtils.getColorFromAttr(context, R.attr.action_icon_color))
            }
            item.swipeIcon.setImageDrawable(icon)

            item.getRoot().setOnClickListener {
                if (direction == LEFT) {
                    leftAction = keys!!.get(actionIndex)
                } else {
                    rightAction = keys!!.get(actionIndex)
                }
                setupSwipeDirectionView(view, direction)
                dialog.dismiss()
            }
            val param = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, GridLayout.BASELINE),
                    GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL, 1f))
            param.width = 0
            picker.pickerGridLayout.addView(item.getRoot(), param)
        }
        picker.pickerGridLayout.setColumnCount(2)
        picker.pickerGridLayout.setRowCount((keys!!.size + 1) / 2)
    }

    private fun populateMockEpisode(view: FeeditemlistItemBinding) {
        view.container.setAlpha(0.3f)
        view.secondaryActionButton.secondaryActionButton.setVisibility(View.GONE)
        view.dragHandle.setVisibility(View.GONE)
        view.statusInbox.setVisibility(View.GONE)
        view.txtvTitle.setText("███████")
        view.txtvPosition.setText("█████")
    }

    private fun savePrefs(tag: String, right: String, left: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(SwipeActions.PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(SwipeActions.KEY_PREFIX_SWIPEACTIONS + tag, right + "," + left).apply()
    }

    private fun saveActionsEnabledPrefs(enabled: Boolean) {
        val prefs: SharedPreferences = context.getSharedPreferences(SwipeActions.PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(SwipeActions.KEY_PREFIX_NO_ACTION + tag, enabled).apply()
    }

    fun interface Callback {
        fun onCall()
    }
}
