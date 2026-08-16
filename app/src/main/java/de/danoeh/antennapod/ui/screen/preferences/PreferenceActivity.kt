package de.danoeh.antennapod.ui.screen.preferences

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.ActionBar
import androidx.preference.PreferenceFragmentCompat
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult
import com.bytehamster.lib.preferencesearch.SearchPreferenceResultListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import de.danoeh.antennapod.R
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.ui.common.Keyboard
import de.danoeh.antennapod.ui.common.ToolbarActivity
import de.danoeh.antennapod.ui.preferences.databinding.SettingsActivityBinding
import de.danoeh.antennapod.ui.preferences.screen.AutoDownloadPreferencesFragment
import de.danoeh.antennapod.ui.preferences.screen.AutomaticDeletionPreferencesFragment
import de.danoeh.antennapod.ui.preferences.screen.NotificationPreferencesFragment
import de.danoeh.antennapod.ui.preferences.screen.synchronization.SynchronizationPreferencesFragment
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * PreferenceActivity for API 11+. In order to change the behavior of the preference UI, see
 * PreferenceController.
 */
class PreferenceActivity : ToolbarActivity(), SearchPreferenceResultListener {
    private var binding: SettingsActivityBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ab: ActionBar? = getSupportActionBar()
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true)
        }

        binding = SettingsActivityBinding.inflate(getLayoutInflater())
        setContentView(binding!!.getRoot())

        if (getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(binding!!.settingsContainer.getId(), MainPreferencesFragment(), FRAGMENT_TAG)
                    .commit()
        }
        val intent = getIntent()
        if (intent.getBooleanExtra(OPEN_AUTO_DOWNLOAD_SETTINGS, false)) {
            openScreen(R.xml.preferences_autodownload)
        }
        if (intent.getBooleanExtra(OPEN_PLAYBACK_SETTINGS, false)) {
            openScreen(R.xml.preferences_playback)
        }
    }

    private fun getPreferenceScreen(screen: Int): PreferenceFragmentCompat? {
        var prefFragment: PreferenceFragmentCompat? = null

        if (screen == R.xml.preferences_user_interface) {
            prefFragment = UserInterfacePreferencesFragment()
        } else if (screen == R.xml.preferences_downloads) {
            prefFragment = DownloadsPreferencesFragment()
        } else if (screen == R.xml.preferences_import_export) {
            prefFragment = ImportExportPreferencesFragment()
        } else if (screen == R.xml.preferences_autodownload) {
            prefFragment = AutoDownloadPreferencesFragment()
        } else if (screen == R.xml.preferences_synchronization) {
            prefFragment = SynchronizationPreferencesFragment()
        } else if (screen == R.xml.preferences_playback) {
            prefFragment = PlaybackPreferencesFragment()
        } else if (screen == R.xml.preferences_notifications) {
            prefFragment = NotificationPreferencesFragment()
        } else if (screen == R.xml.preferences_swipe) {
            prefFragment = SwipePreferencesFragment()
        } else if (screen == R.xml.preferences_auto_deletion) {
            prefFragment = AutomaticDeletionPreferencesFragment()
        } else if (screen == R.xml.preferences_parental_control) {
            prefFragment = ParentalControlPreferencesFragment()
        }
        return prefFragment
    }

    fun openScreen(screen: Int): PreferenceFragmentCompat? {
        val fragment = getPreferenceScreen(screen)
        if (screen == R.xml.preferences_notifications && Build.VERSION.SDK_INT >= 26) {
            val intent = Intent()
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
            startActivity(intent)
        } else {
            getSupportFragmentManager().beginTransaction()
                    .replace(binding!!.settingsContainer.getId(), fragment!!)
                    .addToBackStack(getString(getTitleOfPage(screen))).commit()
        }


        return fragment
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.getItemId() == android.R.id.home) {
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                finish()
            } else {
                Keyboard.hide(this)
                getSupportFragmentManager().popBackStack()
            }
            return true
        }
        return false
    }

    override fun onSearchResultClicked(result: SearchPreferenceResult) {
        val screen = result.getResourceFile()
        if (screen == R.xml.feed_settings) {
            val builder = MaterialAlertDialogBuilder(this)
            builder.setTitle(R.string.feed_settings_label)
            builder.setMessage(R.string.pref_feed_settings_dialog_msg)
            builder.setPositiveButton(android.R.string.ok, null)
            builder.show()
        } else if (screen == R.xml.preferences_notifications) {
            openScreen(screen)
        } else {
            val fragment = openScreen(result.getResourceFile())
            result.highlight(fragment)
        }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: MessageEvent) {
        Log.d(FRAGMENT_TAG, "onEvent(" + event + ")")
        val s = Snackbar.make(binding!!.getRoot(), event.message, Snackbar.LENGTH_LONG)
        if (event.action != null) {
            s.setAction(event.actionText) { v -> event.action!!.accept(this) }
        }
        s.show()
    }

    companion object {
        private const val FRAGMENT_TAG = "tag_preferences"
        const val OPEN_AUTO_DOWNLOAD_SETTINGS = "OpenAutoDownloadSettings"
        const val OPEN_PLAYBACK_SETTINGS = "OpenPlaybackSettings"

        @JvmStatic
        fun getTitleOfPage(preferences: Int): Int {
            if (preferences == R.xml.preferences_downloads) {
                return R.string.downloads_pref
            } else if (preferences == R.xml.preferences_autodownload) {
                return R.string.pref_automatic_download_title
            } else if (preferences == R.xml.preferences_playback) {
                return R.string.playback_pref
            } else if (preferences == R.xml.preferences_import_export) {
                return R.string.import_export_pref
            } else if (preferences == R.xml.preferences_user_interface) {
                return R.string.user_interface_label
            } else if (preferences == R.xml.preferences_synchronization) {
                return R.string.synchronization_pref
            } else if (preferences == R.xml.preferences_notifications) {
                return R.string.notification_pref_fragment
            } else if (preferences == R.xml.feed_settings) {
                return R.string.feed_settings_label
            } else if (preferences == R.xml.preferences_swipe) {
                return R.string.swipeactions_label
            } else if (preferences == R.xml.preferences_auto_deletion) {
                return R.string.pref_auto_delete_title
            } else if (preferences == R.xml.preferences_parental_control) {
                return R.string.pref_parental_control_title
            }
            return R.string.settings_label
        }
    }
}
