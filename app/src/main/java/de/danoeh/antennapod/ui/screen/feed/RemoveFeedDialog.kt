package de.danoeh.antennapod.ui.screen.feed

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.danoeh.antennapod.R
import de.danoeh.antennapod.databinding.RemoveFeedDialogBinding
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.storage.database.DBWriter
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

import java.util.ArrayList

open class RemoveFeedDialog : BottomSheetDialogFragment {
    companion object {
        private const val TAG = "RemoveFeedDialog"
        private const val ARGUMENT_FEEDS = "feeds"
    }

    protected var feeds: List<Feed>? = null
    private var binding: RemoveFeedDialogBinding? = null
    private var disposable: Disposable? = null

    constructor() {
        // Required empty public constructor
    }

    constructor(feeds: List<Feed>) {
        val args = Bundle()
        args.putSerializable(ARGUMENT_FEEDS, ArrayList(feeds))
        setArguments(args)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = RemoveFeedDialogBinding.inflate(inflater, container, false)
        if (getArguments() == null || !requireArguments().containsKey(ARGUMENT_FEEDS)) {
            Log.e(TAG, "No feeds specified")
            dismiss()
            return binding!!.getRoot()
        }
        feeds = requireArguments().getSerializable(ARGUMENT_FEEDS) as List<Feed>
        if (feeds!!.size == 1) {
            binding!!.selectionText.setText(feeds!!.get(0).getTitle())
        } else {
            binding!!.selectionText.setText(getResources()
                    .getQuantityString(R.plurals.num_subscriptions, feeds!!.size, feeds!!.size))
        }
        var allArchived = true
        for (feed in feeds!!) {
            if (feed.getState() != Feed.STATE_ARCHIVED) {
                allArchived = false
                break
            }
        }
        if (allArchived) {
            binding!!.archiveButton.setVisibility(View.GONE)
            binding!!.restoreButton.setVisibility(View.VISIBLE)
            binding!!.explanationArchiveText.setVisibility(View.GONE)
        }
        binding!!.cancelButton.setOnClickListener { dismiss() }
        binding!!.removeButton.setOnClickListener { showRemoveConfirm() }
        binding!!.removeConfirmButton.setOnClickListener { onRemoveButtonPressed() }
        binding!!.archiveButton.setOnClickListener {
            onArchiveButtonPressed(R.string.archiving_podcast_progress, Feed.STATE_ARCHIVED) }
        binding!!.restoreButton.setOnClickListener {
            onArchiveButtonPressed(R.string.restoring_podcast_progress, Feed.STATE_SUBSCRIBED) }
        return binding!!.getRoot()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (disposable != null) {
            disposable!!.dispose()
            disposable = null
        }
        binding = null
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            setupFullHeight(bottomSheetDialog)
        }
        return dialog
    }

    private fun setupFullHeight(bottomSheetDialog: BottomSheetDialog) {
        val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            val behavior = BottomSheetBehavior.from(bottomSheet)
            val layoutParams = bottomSheet.getLayoutParams()
            bottomSheet.setLayoutParams(layoutParams)
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED)
        }
    }

    protected open fun onRemoveButtonPressed() {
        val context = getContext()
        if (context == null) {
            return
        }

        binding!!.progressBar.setVisibility(View.VISIBLE)
        binding!!.removeConfirmButton.setVisibility(View.GONE)
        binding!!.archiveButton.setVisibility(View.GONE)
        binding!!.cancelButton.setVisibility(View.GONE)

        disposable = Completable.fromAction(
                {
                    for (i in feeds!!.indices) {
                        val feed = feeds!!.get(i)
                        updateProgressText(R.string.deleting_podcast_progress, i + 1, feeds!!.size)
                        DBWriter.deleteFeed(context, feed.getId())!!.get()
                    }
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            Log.d(TAG, "Feed(s) deleted")
                            dismiss()
                        }, { error ->
                            Log.e(TAG, Log.getStackTraceString(error))
                            dismiss()
                        })
    }

    private fun onArchiveButtonPressed(progressTextResId: Int, newState: Int) {
        val context = getContext()
        if (context == null) {
            return
        }

        binding!!.progressBar.setVisibility(View.VISIBLE)
        binding!!.removeButton.setVisibility(View.GONE)
        binding!!.archiveButton.setVisibility(View.GONE)
        binding!!.cancelButton.setVisibility(View.GONE)

        disposable = Completable.fromAction(
                {
                    for (i in feeds!!.indices) {
                        val feed = feeds!!.get(i)
                        updateProgressText(progressTextResId, i + 1, feeds!!.size)
                        DBWriter.setFeedState(context, feed, newState)!!.get()
                    }
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            Log.d(TAG, "Feed(s) archived")
                            dismiss()
                        }, { error ->
                            Log.e(TAG, Log.getStackTraceString(error))
                            dismiss()
                        })
    }

    private fun updateProgressText(stringResId: Int, currentIndex: Int, total: Int) {
        // Update UI on main thread if fragment is still attached
        if (isAdded() && getActivity() != null) {
            requireActivity().runOnUiThread {
                if (binding != null) {
                    val progressText = getString(stringResId, currentIndex, total)
                    binding!!.selectionText.setText(progressText)
                }
            }
        }
    }

    private fun showRemoveConfirm() {
        binding!!.removeButton.setVisibility(View.GONE)
        binding!!.removeConfirmButton.setVisibility(View.VISIBLE)
        val params = binding!!.removeConfirmButton.getLayoutParams() as LinearLayout.LayoutParams
        val animator = ValueAnimator.ofFloat(1.0f, 2.0f)
        animator.addUpdateListener { animation ->
            if (binding != null) {
                params.weight = animation.getAnimatedValue() as Float
                binding!!.removeConfirmButton.setLayoutParams(params)
            }
        }
        animator.setDuration(400)
        animator.setInterpolator(OvershootInterpolator(3.0f))
        animator.start()
    }
}
