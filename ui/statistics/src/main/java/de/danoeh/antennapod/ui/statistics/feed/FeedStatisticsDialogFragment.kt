package de.danoeh.antennapod.ui.statistics.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.google.android.material.bottomsheet.BottomSheetDialogFragment

import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.statistics.R
import de.danoeh.antennapod.ui.statistics.databinding.FeedStatisticsDialogBinding

class FeedStatisticsDialogFragment : BottomSheetDialogFragment() {
    companion object {
        private const val EXTRA_FEED_ID = "de.danoeh.antennapod.extra.feedId"
        private const val EXTRA_FEED_TITLE = "de.danoeh.antennapod.extra.feedTitle"

        @JvmStatic
        fun newInstance(feedId: Long, feedTitle: String?): FeedStatisticsDialogFragment {
            val fragment = FeedStatisticsDialogFragment()
            val arguments = Bundle()
            arguments.putLong(EXTRA_FEED_ID, feedId)
            arguments.putString(EXTRA_FEED_TITLE, feedTitle)
            fragment.setArguments(arguments)
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val binding = FeedStatisticsDialogBinding.inflate(inflater, container, false)
        binding.title.setText(getArguments()!!.getString(EXTRA_FEED_TITLE))
        binding.openPodcastButton.setOnClickListener {
            val feedId = getArguments()!!.getLong(EXTRA_FEED_ID)
            MainActivityStarter(getContext()!!).withOpenFeed(feedId).start()
            dismiss()
        }
        return binding.getRoot()
    }

    override fun onStart() {
        super.onStart()
        val feedId = getArguments()!!.getLong(EXTRA_FEED_ID)
        getChildFragmentManager().beginTransaction().replace(R.id.statisticsContainer,
                        FeedStatisticsFragment.newInstance(feedId, true), "feed_statistics_fragment")
                .commitAllowingStateLoss()
    }
}
