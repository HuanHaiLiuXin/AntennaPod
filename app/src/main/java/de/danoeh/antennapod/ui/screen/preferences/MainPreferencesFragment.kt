package de.danoeh.antennapod.ui.screen.preferences

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.os.UserManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import com.bytehamster.lib.preferencesearch.SearchConfiguration
import com.bytehamster.lib.preferencesearch.SearchPreference
import de.danoeh.antennapod.BuildConfig
import de.danoeh.antennapod.R
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.common.IntentUtils
import de.danoeh.antennapod.ui.preferences.screen.AnimatedPreferenceFragment
import de.danoeh.antennapod.ui.preferences.screen.ParentalControlDialog
import de.danoeh.antennapod.ui.preferences.screen.about.AboutFragment
import de.danoeh.antennapod.ui.preferences.screen.bugreport.BugReportFragment

class MainPreferencesFragment : AnimatedPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)
        setupMainScreen()
        setupSearch()
        setParentalControlsVisibility()

        // If you are writing a spin-off, please update the details on screens like "About" and "Report bug"
        // and afterwards remove the following lines. Please keep in mind that AntennaPod is licensed under the GPL.
        // This means that your application needs to be open-source under the GPL, too.
        // It must also include a prominent copyright notice.
        val packageHash = requireContext().getPackageName().hashCode()
        if (packageHash != 1790437538 && packageHash != -1190467065) {
            findPreference<Preference>(PREF_CATEGORY_PROJECT)!!.setVisible(false)
            val copyrightNotice = Preference(requireContext())
            copyrightNotice.setIcon(R.drawable.ic_info_white)
            copyrightNotice.getIcon()!!.mutate()
                    .setColorFilter(PorterDuffColorFilter(0xffcc0000.toInt(), PorterDuff.Mode.MULTIPLY))
            copyrightNotice.setSummary("This application is based on AntennaPod."
                    + " The AntennaPod team does NOT provide support for this unofficial version."
                    + " If you can read this message, the developers of this modification"
                    + " violate the GNU General Public License (GPL).")
            findPreference<Preference>(PREF_CATEGORY_PROJECT)!!.getParent()!!.addPreference(copyrightNotice)
        } else if (packageHash == -1190467065) {
            val debugNotice = Preference(requireContext())
            debugNotice.setIcon(R.drawable.ic_info_white)
            debugNotice.getIcon()!!.mutate()
                    .setColorFilter(PorterDuffColorFilter(0xffcc0000.toInt(), PorterDuff.Mode.MULTIPLY))
            debugNotice.setOrder(-1)
            debugNotice.setSummary("This is a development version of AntennaPod and not meant for daily use")
            findPreference<Preference>(PREF_CATEGORY_PROJECT)!!.getParent()!!.addPreference(debugNotice)
        }
    }

    override fun onStart() {
        super.onStart()
        (getActivity() as PreferenceActivity).getSupportActionBar()!!.setTitle(R.string.settings_label)
    }

    private fun setupMainScreen() {
        findPreference<Preference>(PREF_SCREEN_USER_INTERFACE)!!.setOnPreferenceClickListener {
            (getActivity() as PreferenceActivity).openScreen(R.xml.preferences_user_interface)
            true
        }
        findPreference<Preference>(PREF_SCREEN_PLAYBACK)!!.setOnPreferenceClickListener {
            (getActivity() as PreferenceActivity).openScreen(R.xml.preferences_playback)
            true
        }
        findPreference<Preference>(PREF_SCREEN_DOWNLOADS)!!.setOnPreferenceClickListener {
            (getActivity() as PreferenceActivity).openScreen(R.xml.preferences_downloads)
            true
        }
        findPreference<Preference>(PREF_SCREEN_SYNCHRONIZATION)!!.setOnPreferenceClickListener {
            (getActivity() as PreferenceActivity).openScreen(R.xml.preferences_synchronization)
            true
        }
        findPreference<Preference>(PREF_SCREEN_IMPORT_EXPORT)!!.setOnPreferenceClickListener {
            (getActivity() as PreferenceActivity).openScreen(R.xml.preferences_import_export)
            true
        }
        findPreference<Preference>(PREF_NOTIFICATION)!!.setOnPreferenceClickListener {
            (getActivity() as PreferenceActivity).openScreen(R.xml.preferences_notifications)
            true
        }
        findPreference<Preference>(PREF_ABOUT)!!.setOnPreferenceClickListener {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.settingsContainer, AboutFragment())
                    .addToBackStack(getString(R.string.about_pref)).commit()
            true
        }
        findPreference<Preference>(PREF_DOCUMENTATION)!!.setOnPreferenceClickListener {
            IntentUtils.openInBrowser(requireContext(), "https://antennapod.org/documentation/")
            true
        }
        findPreference<Preference>(PREF_VIEW_FORUM)!!.setOnPreferenceClickListener {
            IntentUtils.openInBrowser(requireContext(), "https://forum.antennapod.org/")
            true
        }
        findPreference<Preference>(PREF_CONTRIBUTE)!!.setOnPreferenceClickListener {
            IntentUtils.openInBrowser(requireContext(), "https://antennapod.org/contribute/")
            true
        }
        findPreference<Preference>(PREF_SEND_BUG_REPORT)!!.setOnPreferenceClickListener {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.settingsContainer, BugReportFragment())
                    .addToBackStack(getString(R.string.report_bug_title)).commit()
            true
        }
        findPreference<Preference>(PREF_SCREEN_PARENTAL_CONTROL)!!.setOnPreferenceClickListener {
            if (UserPreferences.isParentalControlPasswordSet()) {
                ParentalControlDialog.show(requireContext(), {
                    (getActivity() as PreferenceActivity).openScreen(R.xml.preferences_parental_control)
                })
            } else {
                (getActivity() as PreferenceActivity).openScreen(R.xml.preferences_parental_control)
            }
            true
        }
    }

    // show the 'parental controls' preference if we're on a 'child' device (family link) or if it's a debug build
    private fun setParentalControlsVisibility() {
        val um = requireContext().getSystemService(UserManager::class.java)
        // Family Link child devices have DISALLOW_FACTORY_RESET set (among other restrictions).
        // AccountManager-based checks don't work: supervised users have no visible Google accounts.
        val isChildDevice = um.hasUserRestriction(UserManager.DISALLOW_FACTORY_RESET)
        findPreference<Preference>(PREF_SCREEN_PARENTAL_CONTROL)!!.setVisible(
                isChildDevice || BuildConfig.DEBUG || UserPreferences.isParentalControlPasswordSet())
    }

    private fun setupSearch() {
        val searchPreference = findPreference<SearchPreference>("searchPreference")!!
        val config = searchPreference.getSearchConfiguration()
        config.setActivity(getActivity() as AppCompatActivity)
        config.setFragmentContainerViewId(R.id.settingsContainer)
        config.setBreadcrumbsEnabled(true)

        config.index(R.xml.preferences_user_interface)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_user_interface))
        config.index(R.xml.preferences_playback)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_playback))
        config.index(R.xml.preferences_downloads)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_downloads))
        config.index(R.xml.preferences_import_export)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_import_export))
        config.index(R.xml.preferences_autodownload)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_downloads))
                .addBreadcrumb(R.string.automation)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_autodownload))
        config.index(R.xml.preferences_synchronization)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_synchronization))
        config.index(R.xml.preferences_notifications)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_notifications))
        config.index(R.xml.feed_settings)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.feed_settings))
        config.index(R.xml.preferences_swipe)
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_user_interface))
                .addBreadcrumb(PreferenceActivity.getTitleOfPage(R.xml.preferences_swipe))
    }

    companion object {
        private const val PREF_SCREEN_USER_INTERFACE = "prefScreenInterface"
        private const val PREF_SCREEN_PLAYBACK = "prefScreenPlayback"
        private const val PREF_SCREEN_DOWNLOADS = "prefScreenDownloads"
        private const val PREF_SCREEN_IMPORT_EXPORT = "prefScreenImportExport"
        private const val PREF_SCREEN_SYNCHRONIZATION = "prefScreenSynchronization"
        private const val PREF_DOCUMENTATION = "prefDocumentation"
        private const val PREF_VIEW_FORUM = "prefViewForum"
        private const val PREF_SEND_BUG_REPORT = "prefSendBugReport"
        private const val PREF_CATEGORY_PROJECT = "project"
        private const val PREF_ABOUT = "prefAbout"
        private const val PREF_NOTIFICATION = "notifications"
        private const val PREF_CONTRIBUTE = "prefContribute"
        private const val PREF_SCREEN_PARENTAL_CONTROL = "prefScreenParentalControl"
    }
}
