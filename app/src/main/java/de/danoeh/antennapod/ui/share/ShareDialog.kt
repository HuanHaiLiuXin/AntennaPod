package de.danoeh.antennapod.ui.share

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.danoeh.antennapod.databinding.ShareEpisodeDialogBinding
import de.danoeh.antennapod.model.feed.FeedItem

class ShareDialog : BottomSheetDialogFragment() {
    companion object {
        private const val ARGUMENT_FEED_ITEM = "feedItem"
        private const val PREF_NAME = "ShareDialog"
        private const val PREF_SHARE_EPISODE_START_AT = "prefShareEpisodeStartAt"

        @JvmStatic
        fun newInstance(item: FeedItem): ShareDialog {
            val arguments = Bundle()
            arguments.putSerializable(ARGUMENT_FEED_ITEM, item)
            val dialog = ShareDialog()
            dialog.setArguments(arguments)
            return dialog
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        if (getArguments() == null) {
            return null
        }
        val item = getArguments()!!.getSerializable(ARGUMENT_FEED_ITEM) as FeedItem
        val viewBinding = ShareEpisodeDialogBinding.inflate(inflater)

        if (item.getMedia() != null && item.getMedia()!!.isDownloaded()) {
            viewBinding.mediaFileCardCard.setOnClickListener {
                ShareUtils.shareFeedItemFile(getContext()!!, item.getMedia()!!)
                dismiss()
            }
        } else {
            viewBinding.mediaFileCardCard.setVisibility(View.GONE)
        }

        if (item.getMedia() != null && item.getMedia()!!.getDownloadUrl() != null) {
            viewBinding.mediaAddressText.setText(item.getMedia()!!.getDownloadUrl())
            viewBinding.mediaAddressCard.setOnClickListener {
                ShareUtils.shareLink(getContext()!!, item.getMedia()!!.getDownloadUrl()!!)
                dismiss()
            }
        } else {
            viewBinding.mediaAddressCard.setVisibility(View.GONE)
        }

        val prefs: SharedPreferences = getContext()!!.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        viewBinding.sharePositionCheckbox.setChecked(prefs.getBoolean(PREF_SHARE_EPISODE_START_AT, false))
        viewBinding.socialMessageText.setText(ShareUtils.getSocialFeedItemShareText(
                getContext()!!, item, viewBinding.sharePositionCheckbox.isChecked(), true))
        viewBinding.sharePositionCheckbox.setOnCheckedChangeListener { buttonView, isChecked ->
            prefs.edit().putBoolean(PREF_SHARE_EPISODE_START_AT, isChecked).apply()
            viewBinding.socialMessageText.setText(
                    ShareUtils.getSocialFeedItemShareText(getContext()!!, item, isChecked, true))
        }
        viewBinding.socialMessageCard.setOnClickListener {
            ShareUtils.shareLink(getContext()!!, ShareUtils.getSocialFeedItemShareText(
                    getContext()!!, item, viewBinding.sharePositionCheckbox.isChecked(), false))
            dismiss()
        }

        return viewBinding.getRoot()
    }
}
