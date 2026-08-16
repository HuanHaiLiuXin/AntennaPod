package de.danoeh.antennapod.ui.statistics.feed

import android.os.Bundle
import android.text.format.Formatter
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.StatisticsItem
import de.danoeh.antennapod.ui.common.Converter
import de.danoeh.antennapod.ui.common.DateFormatter
import de.danoeh.antennapod.storage.database.ReleaseScheduleGuesser
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.ui.common.ThemeUtils
import de.danoeh.antennapod.ui.statistics.R
import de.danoeh.antennapod.ui.statistics.databinding.FeedStatisticsBinding
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

import java.util.ArrayList
import java.util.Calendar
import java.util.Collections
import java.util.Date

class FeedStatisticsFragment : Fragment() {
    companion object {
        private const val EXTRA_FEED_ID = "de.danoeh.antennapod.extra.feedId"
        private const val EXTRA_DETAILED = "de.danoeh.antennapod.extra.detailed"

        @JvmStatic
        fun newInstance(feedId: Long, detailed: Boolean): FeedStatisticsFragment {
            val fragment = FeedStatisticsFragment()
            val arguments = Bundle()
            arguments.putLong(EXTRA_FEED_ID, feedId)
            arguments.putBoolean(EXTRA_DETAILED, detailed)
            fragment.setArguments(arguments)
            return fragment
        }
    }

    private var feedId = 0L
    private var disposable: Disposable? = null
    private var viewBinding: FeedStatisticsBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        feedId = requireArguments().getLong(EXTRA_FEED_ID)
        viewBinding = FeedStatisticsBinding.inflate(inflater)
        loadStatistics()

        if (requireArguments().getBoolean(EXTRA_DETAILED)) {
            viewBinding!!.secondRowContainer.setVisibility(View.VISIBLE)
            val color = ThemeUtils.getColorFromAttr(requireContext(), R.attr.colorSurfaceContainerHighest)
            viewBinding!!.playbackTime.getRoot().setBackgroundColor(color)
            viewBinding!!.episodesStarted.getRoot().setBackgroundColor(color)
            viewBinding!!.spaceDownloaded.getRoot().setBackgroundColor(color)
            viewBinding!!.episodesTotal.getRoot().setBackgroundColor(color)
            viewBinding!!.durationTotal.getRoot().setBackgroundColor(color)
            viewBinding!!.episodesDownloaded.getRoot().setBackgroundColor(color)
            viewBinding!!.expectedNextEpisode.getRoot().setBackgroundColor(color)
            viewBinding!!.episodeSchedule.getRoot().setBackgroundColor(color)
        }
        return viewBinding!!.getRoot()
    }

    private fun loadStatistics() {
        disposable =
                Observable.fromCallable<Pair<StatisticsItem, ReleaseScheduleGuesser.Guess?>> {
                    val statisticsData = DBReader.getStatistics(true, 0L, Long.MAX_VALUE)
                    Collections.sort(statisticsData.feedTime) { item1, item2 ->
                        java.lang.Long.compare(item2.timePlayed, item1.timePlayed) }

                    for (statisticsItem in statisticsData.feedTime) {
                        if (statisticsItem.feed.getId() == feedId) {
                            val items = DBReader.getFeedItemList(statisticsItem.feed,
                                    FeedItemFilter.unfiltered(), SortOrder.DATE_OLD_NEW, 0, Integer.MAX_VALUE)
                            val dates: MutableList<Date> = ArrayList()
                            for (item in items) {
                                dates.add(item.getPubDate()!!)
                            }
                            var guess: ReleaseScheduleGuesser.Guess? = null
                            if (dates.size > 1) {
                                guess = ReleaseScheduleGuesser.performGuess(dates)
                            }
                            return@fromCallable Pair(statisticsItem, guess)
                        }
                    }
                    throw NullPointerException()
                }
                        .subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ showStats(it) }, { it.printStackTrace() })
    }

    private fun getReadableDay(day: Int): String {
        return when (day) {
            Calendar.MONDAY -> getString(R.string.release_schedule_monday)
            Calendar.TUESDAY -> getString(R.string.release_schedule_tuesday)
            Calendar.WEDNESDAY -> getString(R.string.release_schedule_wednesday)
            Calendar.THURSDAY -> getString(R.string.release_schedule_thursday)
            Calendar.FRIDAY -> getString(R.string.release_schedule_friday)
            Calendar.SATURDAY -> getString(R.string.release_schedule_saturday)
            Calendar.SUNDAY -> getString(R.string.release_schedule_sunday)
            else -> "error"
        }
    }

    private fun getReadableSchedule(guess: ReleaseScheduleGuesser.Guess): String {
        val prefix = if (guess.multipleReleasesPerDay) getString(R.string.release_schedule_multiple_per_day) +
                ", " else ""
        when (guess.schedule) {
            ReleaseScheduleGuesser.Schedule.DAILY ->
                return prefix + getString(R.string.release_schedule_daily)
            ReleaseScheduleGuesser.Schedule.WEEKDAYS ->
                return prefix + getString(R.string.release_schedule_weekdays)
            ReleaseScheduleGuesser.Schedule.WEEKLY ->
                return prefix + getString(R.string.release_schedule_weekly) + ", " + getReadableDay(guess.days!!.get(0))
            ReleaseScheduleGuesser.Schedule.BIWEEKLY ->
                return prefix + getString(R.string.release_schedule_biweekly) + ", " +
                        getReadableDay(guess.days!!.get(0))
            ReleaseScheduleGuesser.Schedule.MONTHLY ->
                return prefix + getString(R.string.release_schedule_monthly)
            ReleaseScheduleGuesser.Schedule.FOURWEEKLY ->
                return prefix + getString(R.string.release_schedule_monthly) + ", " + getReadableDay(guess.days!!.get(0))
            ReleaseScheduleGuesser.Schedule.SPECIFIC_DAYS -> {
                val days = StringBuilder()
                for (i in guess.days!!.indices) {
                    if (i != 0) {
                        days.append(", ")
                    }
                    days.append(getReadableDay(guess.days!!.get(i)))
                }
                return prefix + days.toString()
            }
            else ->
                return prefix + getString(R.string.statistics_expected_next_episode_unknown)
        }
    }

    private fun showStats(p: Pair<StatisticsItem, ReleaseScheduleGuesser.Guess?>) {
        val s = p.first
        viewBinding!!.episodesStarted.mainLabel.setText(getResources()
                .getQuantityString(R.plurals.num_episodes, s.episodesStarted.toInt(), s.episodesStarted))
        viewBinding!!.episodesStarted.subtitleLabel.setText(getResources()
                .getQuantityString(R.plurals.statistics_episodes_started, s.episodesStarted.toInt()))

        viewBinding!!.episodesTotal.mainLabel.setText(getResources()
                .getQuantityString(R.plurals.num_episodes, s.episodes.toInt(), s.episodes))
        viewBinding!!.episodesTotal.subtitleLabel.setText(getResources()
                .getQuantityString(R.plurals.statistics_episodes_total, s.episodes.toInt()))

        viewBinding!!.playbackTime.mainLabel.setText(Converter.shortLocalizedDuration(requireContext(), s.timePlayed))
        viewBinding!!.playbackTime.subtitleLabel.setText(R.string.statistics_time_played)

        viewBinding!!.durationTotal.mainLabel.setText(Converter.shortLocalizedDuration(requireContext(), s.time))
        viewBinding!!.durationTotal.subtitleLabel.setText(R.string.statistics_time_total)

        viewBinding!!.episodesDownloaded.mainLabel.setText(getResources()
                .getQuantityString(R.plurals.num_episodes, s.episodesDownloadCount.toInt(), s.episodesDownloadCount))
        viewBinding!!.episodesDownloaded.subtitleLabel.setText(getResources()
                .getQuantityString(R.plurals.statistics_episodes_downloaded, s.episodesDownloadCount.toInt()))

        viewBinding!!.spaceDownloaded.mainLabel.setText(Formatter.formatShortFileSize(getContext(), s.totalDownloadSize))
        viewBinding!!.spaceDownloaded.subtitleLabel.setText(R.string.statistics_episodes_space)

        viewBinding!!.expectedNextEpisode.subtitleLabel.setText(R.string.statistics_release_next)
        viewBinding!!.episodeSchedule.subtitleLabel.setText(R.string.statistics_release_schedule)
        val guess = p.second
        if (s.feed.isLocalFeed()) {
            viewBinding!!.expectedNextEpisode.mainLabel.setText(R.string.local_folder)
            viewBinding!!.episodeSchedule.mainLabel.setText(R.string.local_folder)
        } else if (!s.feed.getPreferences()!!.getKeepUpdated()) {
            viewBinding!!.expectedNextEpisode.mainLabel.setText(R.string.updates_disabled_label)
            viewBinding!!.episodeSchedule.mainLabel.setText(R.string.updates_disabled_label)
        } else if (guess == null || guess.nextExpectedDate!!.getTime() <= Date().getTime() - 7 * 24 * 3600000L) {            // More than 30 days delayed
            viewBinding!!.expectedNextEpisode.mainLabel.setText(R.string.statistics_expected_next_episode_unknown)
            viewBinding!!.episodeSchedule.mainLabel.setText(R.string.statistics_expected_next_episode_unknown)
        } else {
            if (guess.nextExpectedDate!!.getTime() <= Date().getTime()) {
                viewBinding!!.expectedNextEpisode.mainLabel.setText(
                        if (guess.multipleReleasesPerDay)
                            R.string.statistics_expected_next_episode_any_time
                        else R.string.statistics_expected_next_episode_any_day)
            } else {
                viewBinding!!.expectedNextEpisode.mainLabel.setText(
                        DateFormatter.formatAbbrev(requireContext(), guess.nextExpectedDate))
            }
            if (guess.schedule == ReleaseScheduleGuesser.Schedule.UNKNOWN) {
                viewBinding!!.episodeSchedule.mainLabel.setText(R.string.statistics_expected_next_episode_unknown)
            } else {
                viewBinding!!.episodeSchedule.mainLabel.setText(getReadableSchedule(guess))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (disposable != null) {
            disposable!!.dispose()
        }
    }
}
