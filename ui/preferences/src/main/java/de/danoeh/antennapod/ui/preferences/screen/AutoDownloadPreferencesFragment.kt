package de.danoeh.antennapod.ui.preferences.screen

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import de.danoeh.antennapod.ui.preferences.R

class AutoDownloadPreferencesFragment : AnimatedPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_autodownload)
    }

    override fun onStart() {
        super.onStart()
        (getActivity() as AppCompatActivity).getSupportActionBar()!!.setTitle(R.string.pref_automatic_download_title)
    }
}
