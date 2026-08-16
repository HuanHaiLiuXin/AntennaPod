package de.danoeh.antennapod.ui.preferences.screen.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import de.danoeh.antennapod.ui.common.IntentUtils
import de.danoeh.antennapod.ui.preferences.BuildConfig
import de.danoeh.antennapod.ui.preferences.R
import de.danoeh.antennapod.ui.preferences.screen.AnimatedPreferenceFragment

class AboutFragment : AnimatedPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_about)

        var versionName: String? = "?"
        try {
            val packageInfo: PackageInfo = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0)
            versionName = packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        if ("free" == BuildConfig.FLAVOR) {
            versionName += "f"
        }

        findPreference<androidx.preference.Preference>("about_version")!!.setSummary(String.format(
                "%s (%s)", versionName, BuildConfig.COMMIT_HASH))
        findPreference<androidx.preference.Preference>("about_version")!!.setOnPreferenceClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(getString(R.string.about_pref),
                    findPreference<androidx.preference.Preference>("about_version")!!.getSummary())
            clipboard.setPrimaryClip(clip)
            if (Build.VERSION.SDK_INT <= 32) {
                Snackbar.make(requireView(), R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT).show()
            }
            true
        }
        findPreference<androidx.preference.Preference>("about_contributors")!!.setOnPreferenceClickListener {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.settingsContainer, ContributorsPagerFragment())
                    .addToBackStack(getString(R.string.contributors)).commit()
            true
        }
        findPreference<androidx.preference.Preference>("about_privacy_policy")!!.setOnPreferenceClickListener {
            IntentUtils.openInBrowser(requireContext(), "https://antennapod.org/privacy/")
            true
        }
        findPreference<androidx.preference.Preference>("about_licenses")!!.setOnPreferenceClickListener {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.settingsContainer, LicensesFragment())
                    .addToBackStack(getString(R.string.translators)).commit()
            true
        }
    }

    override fun onStart() {
        super.onStart()
        (getActivity() as AppCompatActivity).getSupportActionBar()!!.setTitle(R.string.about_pref)
    }
}
