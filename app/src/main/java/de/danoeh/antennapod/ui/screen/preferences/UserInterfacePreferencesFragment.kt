package de.danoeh.antennapod.ui.screen.preferences

import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import de.danoeh.antennapod.R
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.storage.preferences.UsageStatistics
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.preferences.screen.AnimatedPreferenceFragment
import de.danoeh.antennapod.ui.screen.drawer.DrawerPreferencesDialog
import de.danoeh.antennapod.ui.screen.subscriptions.EpisodeListGlobalDefaultSortDialog

import org.greenrobot.eventbus.EventBus

import java.util.Collections

class UserInterfacePreferencesFragment : AnimatedPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_user_interface)
        setupInterfaceScreen()
        backOpensDrawerToggle(UserPreferences.isBottomNavigationEnabled())
    }

    override fun onStart() {
        super.onStart()
        (getActivity() as PreferenceActivity).getSupportActionBar()!!.setTitle(R.string.user_interface_label)
    }

    private fun setupInterfaceScreen() {
        val restartApp = Preference.OnPreferenceChangeListener { preference, newValue ->
            ActivityCompat.recreate(getActivity()!!)
            true
        }
        findPreference<Preference>(UserPreferences.PREF_THEME)!!.setOnPreferenceChangeListener(restartApp)
        findPreference<Preference>(UserPreferences.PREF_THEME_BLACK)!!.setOnPreferenceChangeListener(restartApp)
        findPreference<Preference>(UserPreferences.PREF_TINTED_COLORS)!!.setOnPreferenceChangeListener(restartApp)
        if (Build.VERSION.SDK_INT < 31) {
            findPreference<Preference>(UserPreferences.PREF_TINTED_COLORS)!!.setVisible(false)
        }

        findPreference<Preference>(UserPreferences.PREF_SHOW_TIME_LEFT)!!
                .setOnPreferenceChangeListener { preference, newValue ->
                    UserPreferences.setShowRemainTimeSetting(newValue as Boolean)
                    EventBus.getDefault().post(FeedItemEvent(Collections.emptyList(), true))
                    EventBus.getDefault().post(PlayerStatusEvent())
                    return@setOnPreferenceChangeListener true
                }

        findPreference<Preference>(UserPreferences.PREF_HIDDEN_DRAWER_ITEMS)!!
                .setOnPreferenceClickListener {
                    DrawerPreferencesDialog(getContext()!!, null).show()
                    true
                }

        findPreference<Preference>(UserPreferences.PREF_FULL_NOTIFICATION_BUTTONS)!!
                .setOnPreferenceClickListener {
                    showFullNotificationButtonsDialog()
                    true
                }
        findPreference<Preference>(UserPreferences.PREF_GLOBAL_DEFAULT_SORTED_ORDER)!!
                .setOnPreferenceClickListener {
                    val dialog = EpisodeListGlobalDefaultSortDialog.newInstance()
                    dialog.show(getChildFragmentManager(), "SortDialog")
                    true
                }
        findPreference<Preference>(PREF_SWIPE)!!
                .setOnPreferenceClickListener {
                    (getActivity() as PreferenceActivity).openScreen(R.xml.preferences_swipe)
                    true
                }
        findPreference<Preference>(UserPreferences.PREF_STREAM_OVER_DOWNLOAD)!!
                .setOnPreferenceChangeListener { preference, newValue ->
                    // Update all visible lists to reflect new streaming action button
                    EventBus.getDefault().post(FeedItemEvent(Collections.emptyList(), true))
                    // User consciously decided whether to prefer the streaming button, disable suggestion
                    UsageStatistics.doNotAskAgain(UsageStatistics.ACTION_STREAM)
                    return@setOnPreferenceChangeListener true
                }

        if (Build.VERSION.SDK_INT >= 26) {
            findPreference<Preference>(UserPreferences.PREF_EXPANDED_NOTIFICATION)!!.setVisible(false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            findPreference<Preference>(UserPreferences.PREF_PERSISTENT_NOTIFICATION)!!.setVisible(false)
        }

        findPreference<Preference>(UserPreferences.PREF_BOTTOM_NAVIGATION)!!
                .setOnPreferenceChangeListener { preference, newValue ->
                    if (newValue is Boolean && !newValue) {
                        MaterialAlertDialogBuilder(getContext()!!)
                                .setMessage(R.string.bottom_navigation_deprecation_warning)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                    }
                    if (newValue is Boolean) {
                        backOpensDrawerToggle(newValue)
                    }
                    return@setOnPreferenceChangeListener true
                }
    }

    private fun backOpensDrawerToggle(bottomNavigationEnabled: Boolean) {
        findPreference<Preference>(UserPreferences.PREF_BACK_OPENS_DRAWER)!!.setEnabled(!bottomNavigationEnabled)
    }

    private fun showFullNotificationButtonsDialog() {
        val context = getActivity()!!

        val preferredButtons = UserPreferences.getFullNotificationButtons() as MutableList<Int>
        val allButtonNames = context.getResources().getStringArray(
                R.array.full_notification_buttons_options)
        val buttonIds = intArrayOf(
                UserPreferences.NOTIFICATION_BUTTON_SKIP,
                UserPreferences.NOTIFICATION_BUTTON_NEXT_CHAPTER,
                UserPreferences.NOTIFICATION_BUTTON_PLAYBACK_SPEED,
                UserPreferences.NOTIFICATION_BUTTON_SLEEP_TIMER)
        val completeListener = DialogInterface.OnClickListener { dialog, which ->
            UserPreferences.setFullNotificationButtons(preferredButtons)
        }
        val title = context.getResources().getString(R.string.pref_full_notification_buttons_title)

        val checked = BooleanArray(allButtonNames.size) // booleans default to false in java

        // Clear buttons that are not part of the setting anymore
        for (i in preferredButtons.size - 1 downTo 0) {
            var isValid = false
            for (j in checked.indices) {
                if (buttonIds[j] == preferredButtons.get(i)) {
                    isValid = true
                    break
                }
            }

            if (!isValid) {
                preferredButtons.removeAt(i)
            }
        }

        for (i in checked.indices) {
            if (preferredButtons.contains(buttonIds[i])) {
                checked[i] = true
            }
        }

        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(title)
        builder.setMultiChoiceItems(allButtonNames, checked) { dialog, which, isChecked ->
            checked[which] = isChecked
            if (isChecked) {
                preferredButtons.add(buttonIds[which])
            } else {
                preferredButtons.remove(buttonIds[which])
            }
        }
        builder.setPositiveButton(R.string.confirm_label, null)
        builder.setNegativeButton(R.string.cancel_label, null)
        val dialog = builder.create()

        dialog.show()

        val positiveButton: Button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

        positiveButton.setOnClickListener {
            if (preferredButtons.size != 2) {
                val selectionView: ListView = dialog.getListView()
                Snackbar.make(
                    selectionView,
                    context.getResources().getString(R.string.pref_compact_notification_buttons_dialog_error_exact),
                    Snackbar.LENGTH_SHORT).show()

            } else {
                completeListener.onClick(dialog, AlertDialog.BUTTON_POSITIVE)
                dialog.cancel()
            }
        }
    }

    companion object {
        private const val PREF_SWIPE = "prefSwipe"
    }
}
