package de.danoeh.antennapod.ui.screen.preferences

import android.os.Build
import android.os.Bundle
import androidx.collection.ArrayMap
import androidx.preference.ListPreference
import androidx.preference.Preference
import de.danoeh.antennapod.R
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.preferences.screen.AnimatedPreferenceFragment
import de.danoeh.antennapod.ui.screen.feed.preferences.SkipPreferenceDialog
import de.danoeh.antennapod.ui.screen.playback.VariableSpeedDialog

class PlaybackPreferencesFragment : AnimatedPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_playback)

        setupPlaybackScreen()
        buildSmartMarkAsPlayedPreference()
    }

    override fun onStart() {
        super.onStart()
        (getActivity() as PreferenceActivity).getSupportActionBar()!!.setTitle(R.string.playback_pref)
    }

    private fun setupPlaybackScreen() {
        val activity = getActivity()

        findPreference<Preference>(PREF_PLAYBACK_SPEED_LAUNCHER)!!.setOnPreferenceClickListener {
            VariableSpeedDialog().show(getChildFragmentManager(), null)
            true
        }
        findPreference<Preference>(PREF_PLAYBACK_REWIND_DELTA_LAUNCHER)!!.setOnPreferenceClickListener {
            SkipPreferenceDialog.showSkipPreference(activity!!, SkipPreferenceDialog.SkipDirection.SKIP_REWIND, null)
            true
        }
        findPreference<Preference>(PREF_PLAYBACK_FAST_FORWARD_DELTA_LAUNCHER)!!.setOnPreferenceClickListener {
            SkipPreferenceDialog.showSkipPreference(activity!!, SkipPreferenceDialog.SkipDirection.SKIP_FORWARD, null)
            true
        }
        if (Build.VERSION.SDK_INT >= 31) {
            findPreference<Preference>(UserPreferences.PREF_UNPAUSE_ON_HEADSET_RECONNECT)!!.setVisible(false)
            findPreference<Preference>(UserPreferences.PREF_UNPAUSE_ON_BLUETOOTH_RECONNECT)!!.setVisible(false)
        }

        buildEnqueueLocationPreference()
    }

    private fun buildEnqueueLocationPreference() {
        val res = requireActivity().getResources()
        val options = ArrayMap<String, String>()
        val keys = res.getStringArray(R.array.enqueue_location_values)
        val values = res.getStringArray(R.array.enqueue_location_options)
        for (i in keys.indices) {
            options.put(keys[i], values[i])
        }

        val pref = requirePreference<ListPreference>(UserPreferences.PREF_ENQUEUE_LOCATION)
        pref.setSummary(res.getString(R.string.pref_enqueue_location_sum, options.get(pref.getValue())))

        pref.setOnPreferenceChangeListener { preference, newValue ->
            if (newValue !is String) {
                return@setOnPreferenceChangeListener false
            }
            val newValStr = newValue
            pref.setSummary(res.getString(R.string.pref_enqueue_location_sum, options.get(newValStr)))
            return@setOnPreferenceChangeListener true
        }
    }

    private fun <T : Preference> requirePreference(key: CharSequence): T {
        // Possibly put it to a common method in abstract base class
        val result = findPreference<T>(key)
        if (result == null) {
            throw IllegalArgumentException("Preference with key '" + key + "' is not found")

        }
        return result
    }

    private fun buildSmartMarkAsPlayedPreference() {
        val res = getActivity()!!.getResources()

        val pref = findPreference<ListPreference>(UserPreferences.PREF_SMART_MARK_AS_PLAYED_SECS)!!
        val values = res.getStringArray(R.array.smart_mark_as_played_values)
        val entries = arrayOfNulls<String>(values.size)
        for (x in values.indices) {
            if (x == 0) {
                entries[x] = res.getString(R.string.pref_smart_mark_as_played_disabled)
            } else {
                var v = Integer.parseInt(values[x])
                if (v < 60) {
                    entries[x] = res.getQuantityString(R.plurals.time_seconds_quantified, v, v)
                } else {
                    v /= 60
                    entries[x] = res.getQuantityString(R.plurals.time_minutes_quantified, v, v)
                }
            }
        }
        pref.setEntries(entries)
    }

    companion object {
        private const val PREF_PLAYBACK_SPEED_LAUNCHER = "prefPlaybackSpeedLauncher"
        private const val PREF_PLAYBACK_REWIND_DELTA_LAUNCHER = "prefPlaybackRewindDeltaLauncher"
        private const val PREF_PLAYBACK_FAST_FORWARD_DELTA_LAUNCHER = "prefPlaybackFastForwardDeltaLauncher"
    }
}
