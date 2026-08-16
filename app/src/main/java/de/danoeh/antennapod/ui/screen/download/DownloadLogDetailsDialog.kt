package de.danoeh.antennapod.ui.screen.download

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.databinding.DownloadLogDetailsDialogBinding
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter
import de.danoeh.antennapod.ui.common.ClipboardUtils
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * Shows a dialog with Feed title (and FeedItem title if possible).
 * Can show a button to jump to the feed details view.
 */
class DownloadLogDetailsDialog : DialogFragment() {
    companion object {
        const val TAG = "DownloadLogDetails"
        private const val EXTRA_IS_JUMP_TO_FEED = "isJumpToFeed"
        private const val EXTRA_DOWNLOAD_RESULT = "downloadResult"

        @JvmStatic
        fun newInstance(downloadResult: DownloadResult, isJumpToFeed: Boolean): DownloadLogDetailsDialog {
            val dialog = DownloadLogDetailsDialog()
            val args = Bundle()
            args.putSerializable(EXTRA_DOWNLOAD_RESULT, downloadResult)
            args.putBoolean(EXTRA_IS_JUMP_TO_FEED, isJumpToFeed)
            dialog.setArguments(args)
            return dialog
        }
    }

    private var viewBinding: DownloadLogDetailsDialogBinding? = null
    private var disposable: Disposable? = null
    private var isJumpToFeed = false
    private var downloadResult: DownloadResult? = null
    private var feed: Feed? = null
    private var podcastName: String? = null
    private var episodeName: String? = null
    private var url: String? = "unknown"
    private var clipboardContent = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadResult = requireArguments().getSerializable(EXTRA_DOWNLOAD_RESULT) as DownloadResult?
        isJumpToFeed = requireArguments().getBoolean(EXTRA_IS_JUMP_TO_FEED, true)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = MaterialAlertDialogBuilder(requireContext())
        dialog.setTitle(R.string.download_error_details)
        dialog.setPositiveButton(android.R.string.ok, null)
        dialog.setNeutralButton(R.string.copy_to_clipboard) { copyDialog, which ->
            ClipboardUtils.copyText(viewBinding!!.getRoot(), R.string.download_error_details, clipboardContent)
        }

        viewBinding = DownloadLogDetailsDialogBinding.inflate(getLayoutInflater())
        dialog.setView(viewBinding!!.getRoot())

        viewBinding!!.goToPodcastButton.setVisibility(View.GONE)
        viewBinding!!.goToPodcastButton.setOnClickListener {
            goToFeed()
            dismiss()
            val downloadLog = getParentFragmentManager().findFragmentByTag(DownloadLogFragment.TAG)
            if (downloadLog is DownloadLogFragment) {
                downloadLog.dismiss()
            }
        }
        viewBinding!!.fileUrlLabel.setOnClickListener {
            ClipboardUtils.copyText(viewBinding!!.fileUrlLabel, R.string.download_log_details_file_url_title)
        }
        viewBinding!!.technicalReasonLabel.setOnClickListener {
            ClipboardUtils.copyText(viewBinding!!.technicalReasonLabel,
                    R.string.download_log_details_technical_reason_title)
        }

        loadData()
        return dialog.create()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (disposable != null) {
            disposable!!.dispose()
        }
    }

    private fun loadData() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Single.create<Boolean> { emitter ->
            if (downloadResult!!.getFeedfileType() == FeedMedia.FEEDFILETYPE_FEEDMEDIA) {
                val media = DBReader.getFeedMedia(downloadResult!!.getFeedfileId())
                if (media != null) {
                    if (media.getItem() != null && media.getItem()!!.getFeed() != null) {
                        feed = media.getItem()!!.getFeed()
                        podcastName = feed!!.getTitle()
                    }
                    episodeName = media.getEpisodeTitle()
                    url = media.getDownloadUrl()
                } else {
                    episodeName = downloadResult!!.getTitle()
                }
            } else if (downloadResult!!.getFeedfileType() == Feed.FEEDFILETYPE_FEED) {
                feed = DBReader.getFeed(downloadResult!!.getFeedfileId(), false, 0, 0)
                if (feed != null) {
                    podcastName = feed!!.getTitle()
                    url = feed!!.getDownloadUrl()
                } else {
                    podcastName = downloadResult!!.getTitle()
                }
            }
            emitter.onSuccess(true)
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ obj -> updateUi() },
                        { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    private fun updateUi() {
        var message = getString(R.string.download_successful)
        if (!downloadResult!!.isSuccessful()) {
            message = downloadResult!!.getReasonDetailed()!!
        }
        viewBinding!!.goToPodcastButton.setVisibility(if (isJumpToFeed && feed != null) View.VISIBLE else View.GONE)
        viewBinding!!.podcastNameLabel.setText(podcastName)
        viewBinding!!.podcastContainer.setVisibility(if (podcastName == null) View.GONE else View.VISIBLE)
        viewBinding!!.episodeNameLabel.setText(episodeName)
        viewBinding!!.episodeContainer.setVisibility(if (episodeName == null) View.GONE else View.VISIBLE)

        val humanReadableReason = getString(DownloadErrorLabel.from(downloadResult!!.getReason()!!))
        viewBinding!!.humanReadableReasonLabel.setText(humanReadableReason)
        viewBinding!!.technicalReasonLabel.setText(message)
        viewBinding!!.fileUrlLabel.setText(url)

        val humanReadableReasonTitle = getString(R.string.download_log_details_human_readable_reason_title)
        val technicalReasonTitle = getString(R.string.download_log_details_technical_reason_title)
        val urlTitle = getString(R.string.download_log_details_file_url_title)
        clipboardContent = String.format("%s: \n%s \n\n%s: \n%s \n\n%s: \n%s",
                humanReadableReasonTitle, humanReadableReason, technicalReasonTitle, message, urlTitle, url)
    }

    private fun goToFeed() {
        if (feed == null) {
            return
        }
        val intent: Intent
        if (feed!!.getState() == Feed.STATE_SUBSCRIBED) {
            intent = MainActivityStarter(requireContext()).withOpenFeed(feed!!.getId()).getIntent()
        } else {
            intent = OnlineFeedviewActivityStarter(requireContext(), feed!!.getDownloadUrl()!!).getIntent()
        }
        requireContext().startActivity(intent)
    }
}
