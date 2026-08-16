package de.danoeh.antennapod.ui.preferences.screen

import android.content.res.Resources
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.TwoStatePreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.ui.preferences.R
import de.danoeh.antennapod.storage.preferences.UserPreferences


class AutomaticDeletionPreferencesFragment : AnimatedPreferenceFragment() {
    companion object {
        private const val PREF_AUTO_DELETE_LOCAL = "prefAutoDeleteLocal"
    }

    private var blockAutoDeleteLocal = true

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_auto_deletion)
        setupScreen()
        buildEpisodeCleanupPreference()
        checkItemVisibility(UserPreferences.isAutoDelete())
    }

    override fun onStart() {
        super.onStart()
        (getActivity() as AppCompatActivity).getSupportActionBar()!!.setTitle(R.string.pref_auto_delete_title)
    }

    private fun checkItemVisibility(autoDeleteEnabled: Boolean) {
        findPreference<androidx.preference.Preference>(UserPreferences.PREF_FAVORITE_KEEPS_EPISODE)!!.setEnabled(autoDeleteEnabled)
        findPreference<androidx.preference.Preference>(PREF_AUTO_DELETE_LOCAL)!!.setEnabled(autoDeleteEnabled)
    }

    private fun setupScreen() {
        findPreference<androidx.preference.Preference>(PREF_AUTO_DELETE_LOCAL)!!.setOnPreferenceChangeListener { preference, newValue ->
            if (blockAutoDeleteLocal && newValue == true) {
                showAutoDeleteEnableDialog()
                false
            } else {
                true
            }
        }
        findPreference<androidx.preference.Preference>(UserPreferences.PREF_AUTO_DELETE)!!.setOnPreferenceChangeListener { preference, newValue ->
            if (newValue is Boolean) {
                checkItemVisibility(newValue)
            }
            true
        }
    }

    private fun showAutoDeleteEnableDialog() {
        MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.pref_auto_local_delete_dialog_body)
                .setPositiveButton(R.string.yes) { dialog, which ->
                    blockAutoDeleteLocal = false
                    (findPreference<androidx.preference.Preference>(PREF_AUTO_DELETE_LOCAL) as TwoStatePreference).setChecked(true)
                    blockAutoDeleteLocal = true
                }
                .setNegativeButton(R.string.cancel_label, null)
                .show()
    }


    private fun buildEpisodeCleanupPreference() {
        val res: Resources = requireActivity().getResources()

        val pref = findPreference<ListPreference>(UserPreferences.PREF_EPISODE_CLEANUP)!!
        val values = res.getStringArray(
                de.danoeh.antennapod.ui.preferences.R.array.episode_cleanup_values)
        val entries = arrayOfNulls<String>(values.size)
        for (x in values.indices) {
            val v = Integer.parseInt(values[x])
            if (v == UserPreferences.EPISODE_CLEANUP_EXCEPT_FAVORITE) {
                entries[x] = res.getString(R.string.episode_cleanup_except_favorite_removal)
            } else if (v == UserPreferences.EPISODE_CLEANUP_QUEUE) {
                entries[x] = res.getString(R.string.episode_cleanup_queue_removal)
            } else if (v == UserPreferences.EPISODE_CLEANUP_NULL) {
                entries[x] = res.getString(R.string.episode_cleanup_never)
            } else if (v == 0) {
                entries[x] = res.getString(R.string.episode_cleanup_after_listening)
            } else if (v > 0 && v < 24) {
                entries[x] = res.getQuantityString(R.plurals.episode_cleanup_hours_after_listening, v, v)
            } else {
                val numDays = v / 24 // assume underlying value will be NOT fraction of days, e.g., 36 (hours)
                entries[x] = res.getQuantityString(R.plurals.episode_cleanup_days_after_listening, numDays, numDays)
            }
        }
        pref.setEntries(entries)
    }
}
