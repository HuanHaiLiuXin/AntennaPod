package de.danoeh.antennapod.ui.preferences.screen

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import de.danoeh.antennapod.storage.preferences.SynchronizationSettings
import de.danoeh.antennapod.ui.preferences.R

class NotificationPreferencesFragment : AnimatedPreferenceFragment() {

    companion object {
        private const val PREF_GPODNET_NOTIFICATIONS = "pref_gpodnet_notifications"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_notifications)
        setUpScreen()
    }

    override fun onStart() {
        super.onStart()
        (getActivity() as AppCompatActivity).getSupportActionBar()!!.setTitle(R.string.notification_pref_fragment)
    }

    private fun setUpScreen() {
        findPreference<Preference>(PREF_GPODNET_NOTIFICATIONS)!!.setEnabled(SynchronizationSettings.isProviderConnected())
    }
}
