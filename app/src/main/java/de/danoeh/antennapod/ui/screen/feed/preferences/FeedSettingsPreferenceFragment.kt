package de.danoeh.antennapod.ui.screen.feed.preferences

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.databinding.PlaybackSpeedFeedSettingDialogBinding
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.event.settings.SkipIntroEndingChangedEvent
import de.danoeh.antennapod.event.settings.SpeedPresetChangedEvent
import de.danoeh.antennapod.event.settings.VolumeAdaptionChangedEvent
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedFilter
import de.danoeh.antennapod.model.feed.FeedPreferences
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.preferences.screen.synchronization.AuthenticationDialog
import de.danoeh.antennapod.ui.screen.feed.RenameFeedDialog
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus

import java.util.Collections
import java.util.Locale
import java.util.concurrent.Future

class FeedSettingsPreferenceFragment : PreferenceFragmentCompat() {
    companion object {
        private const val TAG = "FeedSettingsPrefFrag"
        private const val EXTRA_FEED_ID = "de.danoeh.antennapod.extra.feedId"
        private const val PREF_EPISODE_FILTER = "episodeFilter"
        private const val PREF_AUTODOWNLOAD = "includeAutoDownload"
        private const val PREF_SCREEN = "feedSettingsScreen"
        private const val PREF_AUTHENTICATION = "authentication"
        private const val PREF_AUTO_DELETE = "autoDelete"
        private const val PREF_NEW_EPISODES_ACTION = "feedNewEpisodesAction"
        private const val PREF_FEED_PLAYBACK_SPEED = "feedPlaybackSpeed"
        private const val PREF_AUTO_SKIP = "feedAutoSkip"
        private const val PREF_NOTIFICATION = "episodeNotification"
        private const val PREF_RENAME = "rename"
        private const val PREF_TAGS = "tags"
        private const val PREF_EDIT_FEED_URL = "editFeedUrl"
        private const val PREF_RECONNECT_LOCAL_FOLDER = "reconnectLocalFolder"

        @JvmStatic
        fun newInstance(feedId: Long): FeedSettingsPreferenceFragment {
            val fragment = FeedSettingsPreferenceFragment()
            val arguments = Bundle()
            arguments.putLong(EXTRA_FEED_ID, feedId)
            fragment.setArguments(arguments)
            return fragment
        }
    }

    private var feed: Feed? = null
    private var disposable: Disposable? = null
    private var feedPreferences: FeedPreferences? = null

    private val addLocalFolderLauncher: ActivityResultLauncher<Uri?> =
            registerForActivityResult(AddLocalFolder()) { this.addLocalFolderResult(it) }

    internal var notificationPermissionDenied = false
    private val enableNotificationsRequestPermissionLauncher: ActivityResultLauncher<String> =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    val pref = findPreference<SwitchPreferenceCompat>(PREF_NOTIFICATION)!!
                    pref.setChecked(true)
                    pref.callChangeListener(true)
                    return@registerForActivityResult
                }
                if (notificationPermissionDenied) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", getContext()!!.getPackageName(), null)
                    intent.setData(uri)
                    startActivity(intent)
                    return@registerForActivityResult
                }
                Toast.makeText(getContext(), R.string.notification_permission_denied, Toast.LENGTH_LONG).show()
                notificationPermissionDenied = true
            }

    override fun onCreateRecyclerView(inflater: LayoutInflater, parent: ViewGroup, state: Bundle?): RecyclerView {
        val view = super.onCreateRecyclerView(inflater, parent, state)
        // To prevent transition animation because of summary update
        view.setItemAnimator(null)
        view.setLayoutAnimation(null)
        return view
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.feed_settings)
        // To prevent displaying partially loaded data
        findPreference<Preference>(PREF_SCREEN)!!.setVisible(false)

        val feedId = getArguments()!!.getLong(EXTRA_FEED_ID)
        disposable = Maybe.create<Feed> { emitter ->
            val loadedFeed = DBReader.getFeed(feedId, false, 0, 0)
            if (loadedFeed != null) {
                emitter.onSuccess(loadedFeed)
            } else {
                emitter.onComplete()
            }
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    feed = result
                    feedPreferences = feed!!.getPreferences()

                    setupPreferences()
                    updateAutoDeleteSummary()
                    updateAutoDownloadEnabledSummary()
                    updateNewEpisodesActionSummary()

                    findPreference<Preference>(PREF_RECONNECT_LOCAL_FOLDER)!!.setVisible(feed!!.isLocalFeed())
                    if (feed!!.isLocalFeed()) {
                        findPreference<Preference>(PREF_AUTHENTICATION)!!.setVisible(false)
                        findPreference<Preference>(PREF_AUTODOWNLOAD)!!.setVisible(false)
                        findPreference<Preference>(PREF_EPISODE_FILTER)!!.setVisible(false)
                        findPreference<Preference>(PREF_EDIT_FEED_URL)!!.setVisible(false)
                    }

                    findPreference<Preference>(PREF_SCREEN)!!.setVisible(true)
                }, { error -> Log.d(TAG, Log.getStackTraceString(error)) }, { })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (disposable != null) {
            disposable!!.dispose()
        }
    }

    private fun setupPreferences() {
        findPreference<Preference>(PREF_AUTO_SKIP)!!.setOnPreferenceClickListener {
            object : FeedPreferenceSkipDialog(getContext()!!,
                    feedPreferences!!.getFeedSkipIntro(), feedPreferences!!.getFeedSkipEnding()) {
                override fun onConfirmed(skipIntro: Int, skipEnding: Int) {
                    feedPreferences!!.setFeedSkipIntro(skipIntro)
                    feedPreferences!!.setFeedSkipEnding(skipEnding)
                    DBWriter.setFeedPreferences(feedPreferences!!)
                    EventBus.getDefault().post(
                            SkipIntroEndingChangedEvent(feedPreferences!!.getFeedSkipIntro(),
                                    feedPreferences!!.getFeedSkipEnding(), feed!!.getId()))
                }
            }.show()
            false
        }
        findPreference<Preference>(PREF_FEED_PLAYBACK_SPEED)!!.setOnPreferenceClickListener { showPlaybackSpeedDialog(it) }
        findPreference<Preference>(PREF_EPISODE_FILTER)!!.setOnPreferenceClickListener {
            object : EpisodeFilterDialog(getContext()!!, feedPreferences!!.getFilter()) {
                override fun onConfirmed(filter: FeedFilter) {
                    feedPreferences!!.setFilter(filter)
                    DBWriter.setFeedPreferences(feedPreferences!!)
                }
            }.show()
            false
        }
        findPreference<Preference>(PREF_AUTHENTICATION)!!.setOnPreferenceClickListener {
            object : AuthenticationDialog(getContext()!!,
                    R.string.authentication_label, true,
                    feedPreferences!!.getUsername(), feedPreferences!!.getPassword()) {
                override fun onConfirmed(username: String, password: String) {
                    feedPreferences!!.setUsername(username)
                    feedPreferences!!.setPassword(password)
                    val setPreferencesFuture = DBWriter.setFeedPreferences(feedPreferences!!)

                    Thread({
                        try {
                            setPreferencesFuture!!.get()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        FeedUpdateManager.getInstance()!!.runOnce(getContext()!!, feed!!)
                    }, "RefreshAfterCredentialChange").start()
                }
            }.show()
            false
        }
        findPreference<Preference>(PREF_AUTO_DELETE)!!.setOnPreferenceChangeListener { preference, newValue ->
            feedPreferences!!.setAutoDeleteAction(
                    FeedPreferences.AutoDeleteAction.fromCode(Integer.parseInt(newValue as String)))
            DBWriter.setFeedPreferences(feedPreferences!!)
            updateAutoDeleteSummary()
            false
        }
        val volumeAdaptationPreference = findPreference<ListPreference>("volumeReduction")!!
        volumeAdaptationPreference.setValue("" + feedPreferences!!.getVolumeAdaptionSetting().toInteger())
        volumeAdaptationPreference.setOnPreferenceChangeListener { preference, newValue ->
            val newSetting = VolumeAdaptionSetting.fromInteger(Integer.parseInt(newValue as String))
            feedPreferences!!.setVolumeAdaptionSetting(newSetting)
            DBWriter.setFeedPreferences(feedPreferences!!)
            volumeAdaptationPreference.setValue("" + feedPreferences!!.getVolumeAdaptionSetting().toInteger())
            EventBus.getDefault().post(VolumeAdaptionChangedEvent(newSetting, feed!!.getId()))
            false
        }
        findPreference<Preference>(PREF_NEW_EPISODES_ACTION)!!.setOnPreferenceClickListener {
            val isAutoDownload = feed!!.getPreferences()!!.isAutoDownload(UserPreferences.isEnableAutodownloadGlobal())
            if (isAutoDownload && !feed!!.isLocalFeed()) {
                EventBus.getDefault().post(MessageEvent(getString(R.string.feed_new_episodes_action_snackbar)))
                return@setOnPreferenceClickListener true
            }
            return@setOnPreferenceClickListener false
        }
        findPreference<Preference>(PREF_NEW_EPISODES_ACTION)!!.setOnPreferenceChangeListener { preference, newValue ->
            val code = Integer.parseInt(newValue as String)
            feedPreferences!!.setNewEpisodesAction(FeedPreferences.NewEpisodesAction.fromCode(code))
            DBWriter.setFeedPreferences(feedPreferences!!)
            updateNewEpisodesActionSummary()
            false
        }
        val keepUpdated = findPreference<SwitchPreferenceCompat>("keepUpdated")!!
        keepUpdated.setChecked(feedPreferences!!.getKeepUpdated())
        keepUpdated.setOnPreferenceChangeListener { preference, newValue ->
            val checked = java.lang.Boolean.TRUE == newValue
            feedPreferences!!.setKeepUpdated(checked)
            DBWriter.setFeedPreferences(feedPreferences!!)
            keepUpdated.setChecked(checked)
            false
        }
        findPreference<Preference>(PREF_AUTODOWNLOAD)!!.setOnPreferenceChangeListener { preference, newValue ->
            feedPreferences!!.setAutoDownload(
                    FeedPreferences.AutoDownloadSetting.fromInteger(Integer.parseInt(newValue as String)))
            DBWriter.setFeedPreferences(feedPreferences!!)
            updateAutoDownloadEnabledSummary()
            updateNewEpisodesActionSummary()
            false
        }
        findPreference<Preference>(PREF_TAGS)!!.setOnPreferenceClickListener {
            TagSettingsDialog.newInstance(Collections.singletonList(feedPreferences))
                    .show(getChildFragmentManager(), TagSettingsDialog.TAG)
            true
        }
        val notificationPreference = findPreference<SwitchPreferenceCompat>(PREF_NOTIFICATION)!!
        notificationPreference.setChecked(feedPreferences!!.getShowEpisodeNotification())
        notificationPreference.setOnPreferenceChangeListener { preference, newValue ->
            val checked = java.lang.Boolean.TRUE == newValue
            if (checked && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(getContext()!!,
                            Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                enableNotificationsRequestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return@setOnPreferenceChangeListener false
            }
            feedPreferences!!.setShowEpisodeNotification(checked)
            DBWriter.setFeedPreferences(feedPreferences!!)
            notificationPreference.setChecked(checked)
            false
        }
        findPreference<Preference>(PREF_RENAME)!!.setOnPreferenceClickListener {
            RenameFeedDialog(getActivity()!!, feed!!).show()
            true
        }
        findPreference<Preference>(PREF_EDIT_FEED_URL)!!.setOnPreferenceClickListener {
            object : EditUrlSettingsDialog(getActivity()!!, feed!!) {
                override fun setUrl(url: String) {
                    feed!!.setDownloadUrl(url)
                }
            }.show()
            true
        }
        findPreference<Preference>(PREF_RECONNECT_LOCAL_FOLDER)!!.setOnPreferenceClickListener {
            val alert = MaterialAlertDialogBuilder(getContext()!!)
            alert.setMessage(R.string.reconnect_local_folder_warning)
            alert.setPositiveButton(android.R.string.ok) { dialog, which ->
                try {
                    addLocalFolderLauncher.launch(null)
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "No activity found. Should never happen...")
                }
            }
            alert.setNegativeButton(android.R.string.cancel, null)
            alert.show()
            true
        }
    }

    private fun updateAutoDeleteSummary() {
        val autoDeletePreference = findPreference<ListPreference>(PREF_AUTO_DELETE)!!
        val isEnabledGlobally = if (feed!!.isLocalFeed())
            UserPreferences.isAutoDeleteLocal() else UserPreferences.isAutoDelete()
        val globalStringResource = if (isEnabledGlobally)
            R.string.feed_auto_download_always else R.string.feed_auto_download_never
        val summary = when (feedPreferences!!.getAutoDeleteAction()) {
            FeedPreferences.AutoDeleteAction.GLOBAL ->
                getString(R.string.global_default_with_value, getString(globalStringResource))
            FeedPreferences.AutoDeleteAction.ALWAYS ->
                getString(R.string.feed_auto_download_always)
            else ->
                getString(R.string.feed_auto_download_never)
        }
        autoDeletePreference.setSummary(summary)
        autoDeletePreference.setValue("" + feedPreferences!!.getAutoDeleteAction().code)
    }

    private fun updateNewEpisodesActionSummary() {
        if (feed == null || feed!!.getPreferences() == null) {
            return
        }
        val newEpisodesAction = findPreference<ListPreference>(PREF_NEW_EPISODES_ACTION)!!
        val isAutoDownload = feed!!.getPreferences()!!.isAutoDownload(UserPreferences.isEnableAutodownloadGlobal())
        if (isAutoDownload && !feed!!.isLocalFeed()) {
            newEpisodesAction.setSummary(R.string.feed_new_episodes_action_summary_autodownload)
            return
        }
        newEpisodesAction.setEnabled(true)
        newEpisodesAction.setValue("" + feedPreferences!!.getNewEpisodesAction().code)
        val globalStringResource = when (UserPreferences.getNewEpisodesAction()) {
            FeedPreferences.NewEpisodesAction.ADD_TO_INBOX ->
                R.string.feed_new_episodes_action_add_to_inbox
            FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE ->
                R.string.feed_new_episodes_action_add_to_queue
            else ->
                R.string.feed_new_episodes_action_nothing
        }
        val summary = when (feedPreferences!!.getNewEpisodesAction()) {
            FeedPreferences.NewEpisodesAction.GLOBAL ->
                getString(R.string.global_default_with_value, getString(globalStringResource))
            FeedPreferences.NewEpisodesAction.ADD_TO_INBOX ->
                getString(R.string.feed_new_episodes_action_add_to_inbox)
            FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE ->
                getString(R.string.feed_new_episodes_action_add_to_queue)
            else ->
                getString(R.string.feed_new_episodes_action_nothing)
        }
        newEpisodesAction.setSummary(summary)
    }

    private fun updateAutoDownloadEnabledSummary() {
        if (feed == null || feed!!.getPreferences() == null) {
            return
        }
        val enabled = feed!!.getPreferences()!!.isAutoDownload(UserPreferences.isEnableAutodownloadGlobal())
        findPreference<Preference>(PREF_EPISODE_FILTER)!!.setVisible(enabled)
        val autoDownloadPreference = findPreference<ListPreference>(PREF_AUTODOWNLOAD)!!
        val summary = when (feedPreferences!!.getAutoDownload()) {
            FeedPreferences.AutoDownloadSetting.GLOBAL -> getString(R.string.global_default_with_value,
                    getString(if (enabled) R.string.enabled else R.string.disabled))
            FeedPreferences.AutoDownloadSetting.ENABLED -> getString(R.string.enabled)
            FeedPreferences.AutoDownloadSetting.DISABLED -> getString(R.string.disabled)
        }
        autoDownloadPreference.setSummary(summary)
        autoDownloadPreference.setValue("" + feedPreferences!!.getAutoDownload().code)
    }

    private fun addLocalFolderResult(uri: Uri?) {
        if (uri == null) {
            return
        }
        if (feed == null) {
            return
        }
        Completable.fromAction {
            getActivity()!!.getContentResolver()
                    .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val documentFile = DocumentFile.fromTreeUri(getContext()!!, uri)
            if (documentFile == null) {
                throw IllegalArgumentException("Unable to retrieve document tree")
            }
            feed!!.setDownloadUrl(Feed.PREFIX_LOCAL_FOLDER + uri.toString())
            FeedDatabaseWriter.updateFeed(getContext()!!, feed!!, false)
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { EventBus.getDefault().post(MessageEvent(getString(android.R.string.ok))) },
                        { error -> EventBus.getDefault().post(MessageEvent(error.getLocalizedMessage())) })
    }

    private class AddLocalFolder : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent {
            return super.createIntent(context, input)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }

    private fun showPlaybackSpeedDialog(preference: Preference): Boolean {
        val viewBinding =
                PlaybackSpeedFeedSettingDialogBinding.inflate(getLayoutInflater())
        viewBinding.seekBar.setProgressChangedListener { speed ->
            viewBinding.currentSpeedLabel.setText(String.format(Locale.getDefault(), "%.2fx", speed)) }
        viewBinding.useGlobalCheckbox.setOnCheckedChangeListener { buttonView, isChecked ->
            viewBinding.seekBar.setEnabled(!isChecked)
            viewBinding.seekBar.setAlpha(if (isChecked) 0.4f else 1f)
            viewBinding.currentSpeedLabel.setAlpha(if (isChecked) 0.4f else 1f)

            viewBinding.skipSilenceFeed.setEnabled(!isChecked)
            viewBinding.skipSilenceFeed.setAlpha(if (isChecked) 0.4f else 1f)
        }
        val speed = feedPreferences!!.getFeedPlaybackSpeed()
        val isGlobal = speed == FeedPreferences.SPEED_USE_GLOBAL
        viewBinding.useGlobalCheckbox.setChecked(isGlobal)
        viewBinding.seekBar.updateSpeed(if (isGlobal) 1f else speed)
        viewBinding.currentSpeedLabel.setText(String.format(Locale.getDefault(), "%.2fx", if (isGlobal) 1 else speed))
        val skipSilence = feedPreferences!!.getFeedSkipSilence()
        viewBinding.skipSilenceFeed.setChecked(!isGlobal
                && skipSilence == FeedPreferences.SkipSilence.AGGRESSIVE)
        MaterialAlertDialogBuilder(getContext()!!)
                .setTitle(R.string.playback_speed)
                .setView(viewBinding.getRoot())
                .setPositiveButton(android.R.string.ok) { dialog, which ->
                    val newSpeed = if (viewBinding.useGlobalCheckbox.isChecked())
                        FeedPreferences.SPEED_USE_GLOBAL else viewBinding.seekBar.getCurrentSpeed()
                    feedPreferences!!.setFeedPlaybackSpeed(newSpeed)
                    val newSkipSilence: FeedPreferences.SkipSilence
                    if (viewBinding.useGlobalCheckbox.isChecked()) {
                        newSkipSilence = FeedPreferences.SkipSilence.GLOBAL
                    } else if (viewBinding.skipSilenceFeed.isChecked()) {
                        newSkipSilence = FeedPreferences.SkipSilence.AGGRESSIVE
                    } else {
                        newSkipSilence = FeedPreferences.SkipSilence.OFF
                    }
                    feedPreferences!!.setFeedSkipSilence(newSkipSilence)
                    DBWriter.setFeedPreferences(feedPreferences!!)
                    EventBus.getDefault().post(SpeedPresetChangedEvent(feedPreferences!!.getFeedPlaybackSpeed(),
                            feed!!.getId(), feedPreferences!!.getFeedSkipSilence()))
                }
                .setNegativeButton(R.string.cancel_label, null)
                .show()
        return true
    }
}
