package de.danoeh.antennapod.ui.screen.rating

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.ui.common.IntentUtils
import de.danoeh.antennapod.databinding.RatingDialogBinding
import de.danoeh.antennapod.ui.common.DateFormatter

import java.util.Date

class RatingDialogFragment : DialogFragment() {
    companion object {
        private const val EXTRA_TOTAL_TIME = "totalTime"
        private const val EXTRA_OLDEST_DATE = "oldestDate"

        @JvmStatic
        fun newInstance(totalTime: Long, oldestDate: Long): RatingDialogFragment {
            val fragment = RatingDialogFragment()
            val arguments = Bundle()
            arguments.putLong(EXTRA_TOTAL_TIME, totalTime)
            arguments.putLong(EXTRA_OLDEST_DATE, oldestDate)
            fragment.setArguments(arguments)
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
                .setView(onCreateView(getLayoutInflater(), null, savedInstanceState))
                .create()
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val viewBinding = RatingDialogBinding.inflate(inflater)
        val totalTime = requireArguments().getLong(EXTRA_TOTAL_TIME, 0)
        val oldestDate = requireArguments().getLong(EXTRA_OLDEST_DATE, 0)

        viewBinding.headerLabel.setText(HtmlCompat.fromHtml(getString(R.string.rating_tagline,
                DateFormatter.formatAbbrev(requireContext(), Date(oldestDate)),
                "<br/><b><big><big><big><big><big>", totalTime / 3600L,
                "</big></big></big></big></big></b><br/>"), HtmlCompat.FROM_HTML_MODE_LEGACY))
        viewBinding.neverAgainButton.setOnClickListener {
            RatingDialogManager(requireActivity()).saveRated()
            dismiss()
        }
        viewBinding.showLaterButton.setOnClickListener {
            RatingDialogManager(requireActivity()).resetStartDate()
            dismiss()
        }
        viewBinding.rateButton.setOnClickListener {
            IntentUtils.openInBrowser(requireContext(),
                    "https://play.google.com/store/apps/details?id=de.danoeh.antennapod")
            RatingDialogManager(requireActivity()).saveRated()
        }
        viewBinding.contibuteButton.setOnClickListener {
            IntentUtils.openInBrowser(requireContext(), "https://antennapod.org/contribute/")
            RatingDialogManager(requireActivity()).saveRated()
        }
        return viewBinding.getRoot()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        RatingDialogManager(requireActivity()).resetStartDate()
    }
}
