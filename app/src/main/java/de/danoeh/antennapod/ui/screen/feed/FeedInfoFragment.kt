package de.danoeh.antennapod.ui.screen.feed

import android.content.Intent
import android.content.res.Configuration
import android.graphics.LightingColorFilter
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.databinding.FeedinfoBinding
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.common.ClipboardUtils
import de.danoeh.antennapod.ui.common.IntentUtils
import de.danoeh.antennapod.ui.share.ShareUtils
import de.danoeh.antennapod.ui.cleaner.HtmlToPlainText
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedFunding
import de.danoeh.antennapod.ui.glide.FastBlurTransformation
import de.danoeh.antennapod.ui.statistics.StatisticsFragment
import de.danoeh.antennapod.ui.statistics.feed.FeedStatisticsDialogFragment
import de.danoeh.antennapod.ui.statistics.feed.FeedStatisticsFragment
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.EventBus

import java.util.ArrayList

/**
 * Displays information about a feed.
 */
class FeedInfoFragment : Fragment(), Toolbar.OnMenuItemClickListener {

    companion object {
        private const val EXTRA_FEED_ID = "de.danoeh.antennapod.extra.feedId"
        private const val TAG = "FeedInfoActivity"

        @JvmStatic
        fun newInstance(feed: Feed): FeedInfoFragment {
            val fragment = FeedInfoFragment()
            val arguments = Bundle()
            arguments.putLong(EXTRA_FEED_ID, feed.getId())
            fragment.setArguments(arguments)
            return fragment
        }
    }

    private var feed: Feed? = null
    private var disposable: Disposable? = null
    private var viewBinding: FeedinfoBinding? = null

    private val copyUrlToClipboard = View.OnClickListener { v ->
        if (feed != null && feed!!.getDownloadUrl() != null) {
            ClipboardUtils.copyText(v, R.string.url_label, feed!!.getDownloadUrl()!!)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        viewBinding = FeedinfoBinding.inflate(inflater)
        viewBinding!!.toolbar.setTitle("")
        viewBinding!!.toolbar.inflateMenu(R.menu.feedinfo)
        viewBinding!!.toolbar.setNavigationOnClickListener { getParentFragmentManager().popBackStack() }
        viewBinding!!.toolbar.setOnMenuItemClickListener(this)
        refreshToolbarState()

        val iconTintManager =
                ToolbarIconTintManager(viewBinding!!.toolbar, viewBinding!!.collapsingToolbar)
        viewBinding!!.appBar.addOnOffsetChangedListener(iconTintManager)

        viewBinding!!.header.butShowInfo.setVisibility(View.INVISIBLE)
        viewBinding!!.header.butShowSettings.setVisibility(View.INVISIBLE)
        viewBinding!!.header.butFilter.setVisibility(View.INVISIBLE)
        // https://github.com/bumptech/glide/issues/529
        viewBinding!!.imgvBackground.setColorFilter(LightingColorFilter(0xff828282.toInt(), 0x000000))
        viewBinding!!.urlLabel.setOnClickListener(copyUrlToClipboard)

        val feedId = getArguments()!!.getLong(EXTRA_FEED_ID)
        getParentFragmentManager().beginTransaction().replace(R.id.statisticsFragmentContainer,
                        FeedStatisticsFragment.newInstance(feedId, false), "feed_statistics_fragment")
                .commitAllowingStateLoss()
        viewBinding!!.statisticsFragmentContainer.setOnClickListener {
            FeedStatisticsDialogFragment.newInstance(feedId, feed!!.getTitle())
                    .show(getChildFragmentManager().beginTransaction(), "FeedStatistics") }

        viewBinding!!.statisticsButton.setOnClickListener {
            (getActivity() as MainActivity).loadChildFragment(StatisticsFragment())
        }
        viewBinding!!.header.txtvTitle.setOnLongClickListener {
            ClipboardUtils.copyText(viewBinding!!.header.txtvTitle)
            true
        }
        viewBinding!!.header.txtvAuthor.setOnLongClickListener {
            ClipboardUtils.copyText(viewBinding!!.header.txtvAuthor)
            true
        }
        return viewBinding!!.getRoot()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val feedId = getArguments()!!.getLong(EXTRA_FEED_ID)
        disposable = Maybe.create<Feed> { emitter ->
            val loadedFeed = DBReader.getFeed(feedId, false, 0, 0)
            if (loadedFeed != null) {
                emitter.onSuccess(loadedFeed)
            } else {
                emitter.onComplete()
            }
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    feed = result
                    showFeed()
                }, { error -> Log.d(TAG, Log.getStackTraceString(error)) }, { })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (viewBinding == null) {
            return
        }
        val horizontalSpacing = getResources().getDimension(R.dimen.additional_horizontal_spacing).toInt()
        viewBinding!!.header.getRoot().setPadding(horizontalSpacing, viewBinding!!.header.getRoot().getPaddingTop(),
                horizontalSpacing, viewBinding!!.header.getRoot().getPaddingBottom())
        viewBinding!!.infoContainer.setPadding(horizontalSpacing, viewBinding!!.infoContainer.getPaddingTop(),
                horizontalSpacing, viewBinding!!.infoContainer.getPaddingBottom())
    }

    private fun showFeed() {
        Log.d(TAG, "Language is " + feed!!.getLanguage())
        Log.d(TAG, "Author is " + feed!!.getAuthor())
        Log.d(TAG, "URL is " + feed!!.getDownloadUrl())
        Glide.with(this)
                .load(feed!!.getImageUrl())
                .apply(RequestOptions()
                        .placeholder(R.color.light_gray)
                        .error(R.color.light_gray)
                        .fitCenter()
                        .dontAnimate())
                .into(viewBinding!!.header.imgvCover)
        Glide.with(this)
                .load(feed!!.getImageUrl())
                .apply(RequestOptions()
                        .placeholder(R.color.image_readability_tint)
                        .error(R.color.image_readability_tint)
                        .transform(FastBlurTransformation())
                        .dontAnimate())
                .into(viewBinding!!.imgvBackground)

        viewBinding!!.header.txtvTitle.setText(feed!!.getTitle())
        viewBinding!!.header.txtvTitle.setMaxLines(6)

        val description = HtmlToPlainText.getPlainText(feed!!.getDescription())

        viewBinding!!.descriptionLabel.setText(description)

        if (!TextUtils.isEmpty(feed!!.getAuthor())) {
            viewBinding!!.header.txtvAuthor.setText(feed!!.getAuthor())
        }

        viewBinding!!.urlLabel.setText(feed!!.getDownloadUrl())
        viewBinding!!.urlLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_paperclip, 0)

        if (feed!!.getPaymentLinks() == null || feed!!.getPaymentLinks()!!.size == 0) {
            viewBinding!!.supportHeadingLabel.setVisibility(View.GONE)
            viewBinding!!.supportUrl.setVisibility(View.GONE)
        } else {
            val fundingList = feed!!.getPaymentLinks()!!

            // Filter for duplicates, but keep items in the order that they have in the feed.
            val i = fundingList.iterator()
            while (i.hasNext()) {
                val funding = i.next()
                for (other in fundingList) {
                    if (TextUtils.equals(other.url, funding.url)) {
                        if (other.content != null && funding.content != null
                                && other.content!!.length > funding.content!!.length) {
                            i.remove()
                            break
                        }
                    }
                }
            }

            var str = StringBuilder()
            for (funding in fundingList) {
                str.append(if (funding.content!!.isEmpty())
                    getContext()!!.getResources().getString(R.string.support_podcast)
                else funding.content).append(" ").append(funding.url)
                str.append("\n")
            }
            str = StringBuilder(StringUtils.trim(str.toString()))
            viewBinding!!.supportUrl.setText(str.toString())
        }

        if (feed!!.getState() == Feed.STATE_NOT_SUBSCRIBED) {
            viewBinding!!.statisticsHeading.setVisibility(View.GONE)
            viewBinding!!.statisticsFragmentContainer.setVisibility(View.GONE)
            viewBinding!!.supportHeadingLabel.setVisibility(View.GONE)
            viewBinding!!.supportUrl.setVisibility(View.GONE)
            viewBinding!!.header.butSubscribe.setVisibility(View.VISIBLE)
            viewBinding!!.header.butSubscribe.setOnClickListener {
                DBWriter.setFeedState(getContext()!!, feed!!, Feed.STATE_SUBSCRIBED)
                val mainActivityStarter = MainActivityStarter(getContext()!!)
                mainActivityStarter.withOpenFeed(feed!!.getId())
                mainActivityStarter.withClearBackStack()
                getActivity()!!.finish()
                startActivity(mainActivityStarter.getIntent())
            }
        } else {
            val feedId = getArguments()!!.getLong(EXTRA_FEED_ID)
            getParentFragmentManager().beginTransaction().replace(R.id.statisticsFragmentContainer,
                            FeedStatisticsFragment.newInstance(feedId, false), "feed_statistics_fragment")
                    .commitAllowingStateLoss()

            viewBinding!!.statisticsButton.setOnClickListener {
                (getActivity() as MainActivity).loadChildFragment(StatisticsFragment())
            }
        }

        refreshToolbarState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (disposable != null) {
            disposable!!.dispose()
        }
        viewBinding = null
    }

    private fun refreshToolbarState() {
        val isSubscribed = feed != null && feed!!.getState() == Feed.STATE_SUBSCRIBED
        viewBinding!!.toolbar.getMenu().findItem(R.id.share_item)!!.setVisible(isSubscribed && !feed!!.isLocalFeed())
        viewBinding!!.toolbar.getMenu().findItem(R.id.visit_website_item)!!.setVisible(isSubscribed
                && feed!!.getLink() != null
                && IntentUtils.isCallable(getContext()!!, Intent(Intent.ACTION_VIEW, Uri.parse(feed!!.getLink()))))
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (feed == null) {
            EventBus.getDefault().post(MessageEvent(getString(R.string.please_wait_for_data)))
            return false
        }
        if (item.getItemId() == R.id.visit_website_item) {
            IntentUtils.openInBrowser(getContext()!!, feed!!.getLink()!!)
        } else if (item.getItemId() == R.id.share_item) {
            ShareUtils.shareFeedLink(getContext()!!, feed!!)
        } else {
            return false
        }
        return true
    }

}
