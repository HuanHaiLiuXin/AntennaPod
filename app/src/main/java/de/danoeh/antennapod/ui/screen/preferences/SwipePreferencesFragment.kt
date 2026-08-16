package de.danoeh.antennapod.ui.screen.preferences

import android.os.Bundle
import androidx.preference.Preference
import de.danoeh.antennapod.R
import de.danoeh.antennapod.ui.preferences.screen.AnimatedPreferenceFragment
import de.danoeh.antennapod.ui.screen.AllEpisodesFragment
import de.danoeh.antennapod.ui.screen.FavoritesFragment
import de.danoeh.antennapod.ui.screen.InboxFragment
import de.danoeh.antennapod.ui.screen.PlaybackHistoryFragment
import de.danoeh.antennapod.ui.screen.download.CompletedDownloadsFragment
import de.danoeh.antennapod.ui.screen.feed.FeedItemlistFragment
import de.danoeh.antennapod.ui.screen.queue.QueueFragment
import de.danoeh.antennapod.ui.swipeactions.SwipeActionsDialog

class SwipePreferencesFragment : AnimatedPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_swipe)

        findPreference<Preference>(PREF_SWIPE_QUEUE)!!.setOnPreferenceClickListener {
            SwipeActionsDialog(requireContext(), QueueFragment.TAG).show { }
            true
        }
        findPreference<Preference>(PREF_SWIPE_INBOX)!!.setOnPreferenceClickListener {
            SwipeActionsDialog(requireContext(), InboxFragment.TAG).show { }
            true
        }
        findPreference<Preference>(PREF_SWIPE_EPISODES)!!.setOnPreferenceClickListener {
            SwipeActionsDialog(requireContext(), AllEpisodesFragment.TAG).show { }
            true
        }
        findPreference<Preference>(PREF_SWIPE_DOWNLOADS)!!.setOnPreferenceClickListener {
            SwipeActionsDialog(requireContext(), CompletedDownloadsFragment.TAG).show { }
            true
        }
        findPreference<Preference>(PREF_SWIPE_FEED)!!.setOnPreferenceClickListener {
            SwipeActionsDialog(requireContext(), FeedItemlistFragment.TAG).show { }
            true
        }
        findPreference<Preference>(PREF_SWIPE_HISTORY)!!.setOnPreferenceClickListener {
            SwipeActionsDialog(requireContext(), PlaybackHistoryFragment.TAG).show { }
            true
        }
        findPreference<Preference>(PREF_SWIPE_FAVORITES)!!.setOnPreferenceClickListener {
            SwipeActionsDialog(requireContext(), FavoritesFragment.TAG).show { }
            true
        }
    }

    override fun onStart() {
        super.onStart()
        (getActivity() as PreferenceActivity).getSupportActionBar()!!.setTitle(R.string.swipeactions_label)
    }

    companion object {
        private const val PREF_SWIPE_QUEUE = "prefSwipeQueue"
        private const val PREF_SWIPE_INBOX = "prefSwipeInbox"
        private const val PREF_SWIPE_EPISODES = "prefSwipeEpisodes"
        private const val PREF_SWIPE_DOWNLOADS = "prefSwipeDownloads"
        private const val PREF_SWIPE_FEED = "prefSwipeFeed"
        private const val PREF_SWIPE_HISTORY = "prefSwipeHistory"
        private const val PREF_SWIPE_FAVORITES = "prefSwipeFavorites"
    }
}
