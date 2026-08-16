package de.danoeh.antennapod.ui.screen.preferences

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import de.danoeh.antennapod.R
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.preferences.screen.AnimatedPreferenceFragment
import de.danoeh.antennapod.ui.preferences.screen.ProxyDialog
import de.danoeh.antennapod.ui.preferences.screen.downloads.ChooseDataFolderDialog

import java.io.File


class DownloadsPreferencesFragment : AnimatedPreferenceFragment(),
        SharedPreferences.OnSharedPreferenceChangeListener {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_downloads)
        setupNetworkScreen()
    }

    override fun onStart() {
        super.onStart()
        (getActivity() as PreferenceActivity).getSupportActionBar()!!.setTitle(R.string.downloads_pref)
        PreferenceManager.getDefaultSharedPreferences(requireContext()).registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        PreferenceManager.getDefaultSharedPreferences(requireContext()).unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onResume() {
        super.onResume()
        setDataFolderText()
    }

    private fun setupNetworkScreen() {
        findPreference<Preference>(PREF_SCREEN_AUTODL)!!.setOnPreferenceClickListener {
            (getActivity() as PreferenceActivity).openScreen(R.xml.preferences_autodownload)
            true
        }
        findPreference<Preference>(PREF_SCREEN_AUTO_DELETE)!!.setOnPreferenceClickListener {
            (getActivity() as PreferenceActivity).openScreen(R.xml.preferences_auto_deletion)
            true
        }
        // validate and set correct value: number of downloads between 1 and 50 (inclusive)
        findPreference<Preference>(PREF_PROXY)!!.setOnPreferenceClickListener {
            val dialog = ProxyDialog(requireActivity())
            dialog.show()
            true
        }
        findPreference<Preference>(PREF_CHOOSE_DATA_DIR)!!.setOnPreferenceClickListener {
            ChooseDataFolderDialog.showDialog(requireContext()) { path ->
                UserPreferences.setDataFolder(path)
                setDataFolderText()
            }
            true
        }
        setDataFolderText()
    }

    private fun setDataFolderText() {
        val f = UserPreferences.getDataFolder(null)
        if (f != null) {
            findPreference<Preference>(PREF_CHOOSE_DATA_DIR)!!.setSummary(f.getAbsolutePath())
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (UserPreferences.PREF_UPDATE_INTERVAL_MINUTES == key
                || UserPreferences.PREF_MOBILE_UPDATE == key) {
            FeedUpdateManager.getInstance()!!.restartUpdateAlarm(requireContext(), true)
        }
    }

    companion object {
        private const val PREF_SCREEN_AUTODL = "prefAutoDownloadSettings"
        private const val PREF_SCREEN_AUTO_DELETE = "prefAutoDeleteScreen"
        private const val PREF_PROXY = "prefProxy"
        private const val PREF_CHOOSE_DATA_DIR = "prefChooseDataDir"
    }
}
