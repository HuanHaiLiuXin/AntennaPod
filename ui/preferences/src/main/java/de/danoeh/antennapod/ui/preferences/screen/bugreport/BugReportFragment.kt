package de.danoeh.antennapod.ui.preferences.screen.bugreport

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.view.MenuProvider
import androidx.lifecycle.ViewModelProvider

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

import java.io.File
import java.io.IOException
import java.util.Objects

import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.common.AnimatedFragment
import de.danoeh.antennapod.ui.common.ClipboardUtils
import de.danoeh.antennapod.ui.common.IntentUtils
import de.danoeh.antennapod.ui.preferences.R
import de.danoeh.antennapod.ui.preferences.databinding.BugReportFragmentBinding

/**
 * UI fragment to allow the user to submit a bug report via the AntennaPod forum or GitHub page.
 */
class BugReportFragment : AnimatedFragment() {

    private var viewBinding: BugReportFragmentBinding? = null
    private var viewModel: BugReportViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(BugReportViewModel::class.java)

        postponeEnterTransition()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        viewBinding = BugReportFragmentBinding.inflate(inflater, container, false)
        return viewBinding!!.getRoot()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewBinding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupContextMenu()

        viewModel!!.getState().observe(getViewLifecycleOwner()) { uiState ->
            refreshEnvironmentInfo(uiState.getEnvironmentInfo())
            refreshCrashLogInfo(uiState)

            startPostponedEnterTransition()
        }

        viewBinding!!.expandCrashLogButton.setOnClickListener {
            when (viewModel!!.requireCurrentState().getCrashLogState()) {
                BugReportViewModel.UiState.CrashLogState.SHOWN_COLLAPSED ->
                    viewModel!!.setCrashLogState(BugReportViewModel.UiState.CrashLogState.SHOWN_EXPANDED)
                BugReportViewModel.UiState.CrashLogState.SHOWN_EXPANDED ->
                    viewModel!!.setCrashLogState(BugReportViewModel.UiState.CrashLogState.SHOWN_COLLAPSED)
                else -> Unit    // UNAVAILABLE
            }
        }
        viewBinding!!.openForumButton.setOnClickListener {
            IntentUtils.openInBrowser(requireContext(), "https://forum.antennapod.org/search") }
        viewBinding!!.openGithubButton.setOnClickListener {
            IntentUtils.openInBrowser(requireContext(), "https://github.com/AntennaPod/AntennaPod/issues") }
        viewBinding!!.attribAppVersionLabel.setOnClickListener {
            ClipboardUtils.copyText(it as TextView, R.string.report_bug_attrib_app_version) }
        viewBinding!!.attribAndroidVersionLabel.setOnClickListener {
            ClipboardUtils.copyText(it as TextView, R.string.report_bug_attrib_android_version) }
        viewBinding!!.attribDeviceNameLabel.setOnClickListener {
            ClipboardUtils.copyText(it as TextView, R.string.report_bug_attrib_device_name) }
        viewBinding!!.crashLogContentText.setOnClickListener {
            ClipboardUtils.copyText(it, R.string.report_bug_title,
                    viewModel!!.requireCurrentState().getCrashInfoWithMarkup()!!) }
        viewBinding!!.copyToClipboardButton.setOnClickListener {
            ClipboardUtils.copyText(it, R.string.report_bug_title,
                    viewModel!!.requireCurrentState().getBugReportWithMarkup()) }
    }

    override fun onStart() {
        super.onStart()

        (requireActivity() as AppCompatActivity).getSupportActionBar()!!
                .setTitle(R.string.report_bug_title)
    }

    private fun refreshEnvironmentInfo(info: BugReportViewModel.EnvironmentInfo) {
        viewBinding!!.attribAppVersionLabel.setText(info.applicationVersion)
        viewBinding!!.attribAndroidVersionLabel.setText(info.androidVersion)
        viewBinding!!.attribDeviceNameLabel.setText(info.getFriendlyDeviceName())
    }

    private fun refreshCrashLogInfo(uiState: BugReportViewModel.UiState) {
        val state = uiState.getCrashLogState()
        val crashLogInfo = uiState.getCrashLogInfo()

        when (state) {
            BugReportViewModel.UiState.CrashLogState.SHOWN_COLLAPSED,
            BugReportViewModel.UiState.CrashLogState.SHOWN_EXPANDED -> {
                viewBinding!!.crashLogToggleGroup.setVisibility(View.VISIBLE)
                viewBinding!!.crashLogContentText.setText(crashLogInfo.getContent())
                viewBinding!!.crashLogMessageLabel.setText(getString(
                        R.string.report_bug_crash_log_message, uiState.getFormattedCrashLogTimestamp()))

                if (state == BugReportViewModel.UiState.CrashLogState.SHOWN_COLLAPSED) {
                    viewBinding!!.expandCrashLogButton.setText(R.string.general_expand_button)
                    viewBinding!!.crashLogContentText.setMaxLines(4)
                } else {
                    viewBinding!!.expandCrashLogButton.setText(R.string.general_collapse_button)
                    viewBinding!!.crashLogContentText.setMaxLines(Integer.MAX_VALUE)
                }
            }
            else -> viewBinding!!.crashLogToggleGroup.setVisibility(View.GONE)    // UNAVAILABLE
        }
    }

    private fun setupContextMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.bug_report_options, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.getItemId() == R.id.export_logcat) {
                    showExportLogcatDialog()
                    return true
                }
                return false
            }
        }, getViewLifecycleOwner())
    }

    private fun showExportLogcatDialog() {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(R.string.export_logs_menu_title)
        builder.setMessage(R.string.confirm_export_log_dialog_message)
        builder.setPositiveButton(R.string.confirm_label) { dialog, which -> exportLogcat() }
        builder.setNegativeButton(R.string.cancel_label, null)
        builder.show()
    }

    private fun exportLogcat() {
        try {
            val filename = File(UserPreferences.getDataFolder(null), "full-logs.txt")
            val cmd = "logcat -d -f " + filename.getAbsolutePath()
            Runtime.getRuntime().exec(cmd)

            //share file
            try {
                val authority = getString(R.string.provider_authority)
                val fileUri = FileProvider.getUriForFile(requireContext(), authority, filename)

                ShareCompat.IntentBuilder(requireContext())
                        .setType("text/*")
                        .addStream(fileUri)
                        .setChooserTitle(R.string.share_file_label)
                        .startChooser()

            } catch (e: Exception) {
                e.printStackTrace()
                Snackbar.make(viewBinding!!.getRoot(), R.string.log_file_share_exception, Snackbar.LENGTH_LONG).show()
            }
        } catch (e: IOException) {
            e.printStackTrace()

            Snackbar.make(viewBinding!!.getRoot(), e.message!!, Snackbar.LENGTH_LONG).show()
        }
    }
}
