package de.danoeh.antennapod.ui.screen.preferences

import android.app.Activity
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View

import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import com.google.android.material.snackbar.Snackbar
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.OpmlImportActivity
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.storage.importexport.AutomaticDatabaseExportWorker
import de.danoeh.antennapod.storage.importexport.DatabaseExporter
import de.danoeh.antennapod.storage.importexport.FavoritesWriter
import de.danoeh.antennapod.storage.importexport.HtmlWriter
import de.danoeh.antennapod.storage.importexport.OpmlWriter
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.preferences.screen.AnimatedPreferenceFragment
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImportExportPreferencesFragment : AnimatedPreferenceFragment() {
    private val chooseOpmlExportPathLauncher: ActivityResultLauncher<Intent> =
            registerForActivityResult(StartActivityForResult(),
                    { result -> exportToDocument(result, Export.OPML) })
    private val chooseHtmlExportPathLauncher: ActivityResultLauncher<Intent> =
            registerForActivityResult(StartActivityForResult(),
                    { result -> exportToDocument(result, Export.HTML) })
    private val chooseFavoritesExportPathLauncher: ActivityResultLauncher<Intent> =
            registerForActivityResult(StartActivityForResult(),
                    { result -> exportToDocument(result, Export.FAVORITES) })
    private val restoreDatabaseLauncher: ActivityResultLauncher<Intent> =
            registerForActivityResult(StartActivityForResult(), this::restoreDatabaseResult)
    private val backupDatabaseLauncher: ActivityResultLauncher<String> =
            registerForActivityResult(BackupDatabase(), this::backupDatabaseResult)
    private val chooseOpmlImportPathLauncher: ActivityResultLauncher<String> =
            registerForActivityResult(GetContent(), { uri ->
                if (uri != null) {
                    val intent = Intent(getContext(), OpmlImportActivity::class.java)
                    intent.setData(uri)
                    startActivity(intent)
                }
            })
    private val automaticBackupLauncher: ActivityResultLauncher<Uri?> =
            registerForActivityResult(PickWritableFolder(), this::setupAutomaticBackup)

    private var disposable: Disposable? = null
    private var progressDialog: ProgressDialog? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_import_export)
        setupStorageScreen()
        progressDialog = ProgressDialog(getContext()!!)
        progressDialog!!.setIndeterminate(true)
        progressDialog!!.setMessage(getContext()!!.getString(R.string.please_wait))
    }

    override fun onStart() {
        super.onStart()
        (getActivity() as PreferenceActivity).getSupportActionBar()!!.setTitle(R.string.import_export_pref)
    }

    override fun onStop() {
        super.onStop()
        if (disposable != null) {
            disposable!!.dispose()
        }
    }

    private fun setupStorageScreen() {
        findPreference<Preference>(PREF_OPML_EXPORT)!!.setOnPreferenceClickListener {
            openExportPathPicker(Export.OPML, chooseOpmlExportPathLauncher)
            true
        }
        findPreference<Preference>(PREF_HTML_EXPORT)!!.setOnPreferenceClickListener {
            openExportPathPicker(Export.HTML, chooseHtmlExportPathLauncher)
            true
        }
        findPreference<Preference>(PREF_OPML_IMPORT)!!.setOnPreferenceClickListener {
            try {
                chooseOpmlImportPathLauncher.launch("*/*")
            } catch (e: ActivityNotFoundException) {
                Snackbar.make(getView()!!, R.string.unable_to_start_system_file_manager, Snackbar.LENGTH_LONG)
                        .show()
            }
            true
        }
        findPreference<Preference>(PREF_DATABASE_IMPORT)!!.setOnPreferenceClickListener {
            importDatabase()
            true
        }
        findPreference<Preference>(PREF_DATABASE_EXPORT)!!.setOnPreferenceClickListener {
            try {
                backupDatabaseLauncher.launch(dateStampFilename(DATABASE_EXPORT_FILENAME))
            } catch (e: ActivityNotFoundException) {
                Snackbar.make(getView()!!, R.string.unable_to_start_system_file_manager, Snackbar.LENGTH_LONG)
                        .show()
            }
            true
        }
        findPreference<SwitchPreferenceCompat>(PREF_AUTOMATIC_DATABASE_EXPORT)!!
                .setChecked(UserPreferences.getAutomaticExportFolder() != null)
        findPreference<Preference>(PREF_AUTOMATIC_DATABASE_EXPORT)!!.setOnPreferenceChangeListener { preference, newValue ->
            if (java.lang.Boolean.TRUE == newValue) {
                try {
                    automaticBackupLauncher.launch(null)
                } catch (e: ActivityNotFoundException) {
                    Snackbar.make(getView()!!, R.string.unable_to_start_system_file_manager, Snackbar.LENGTH_LONG)
                            .show()
                }
                return@setOnPreferenceChangeListener false
            } else {
                UserPreferences.setAutomaticExportFolder(null)
                AutomaticDatabaseExportWorker.enqueueIfNeeded(getContext()!!, false)
            }
            return@setOnPreferenceChangeListener true
        }
        findPreference<Preference>(PREF_FAVORITE_EXPORT)!!.setOnPreferenceClickListener {
            openExportPathPicker(Export.FAVORITES, chooseFavoritesExportPathLauncher)
            true
        }
    }

    private fun dateStampFilename(fname: String): String {
        return String.format(fname, SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    }

    private fun importDatabase() {
        // setup the alert builder
        val builder = MaterialAlertDialogBuilder(getActivity()!!)
        builder.setTitle(R.string.database_import_label)
        builder.setMessage(R.string.database_import_warning)

        // add a button
        builder.setNegativeButton(R.string.no, null)
        builder.setPositiveButton(R.string.confirm_label) { dialog, which ->
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.setType("*/*")
            try {
                restoreDatabaseLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                Snackbar.make(getView()!!, R.string.unable_to_start_system_file_manager, Snackbar.LENGTH_LONG)
                        .show()
            }
        }

        // create and show the alert dialog
        builder.show()
    }

    private fun showDatabaseImportSuccessDialog() {
        val builder = MaterialAlertDialogBuilder(getContext()!!)
        builder.setTitle(R.string.successful_import_label)
        builder.setMessage(R.string.import_ok)
        builder.setCancelable(false)
        builder.setPositiveButton(android.R.string.ok) { dialogInterface, i -> forceRestart() }
        builder.show()
    }

    internal fun showExportSuccessSnackbar(uri: Uri, mimeType: String) {
        val view = getView()
        if (view == null) {
            return
        }

        Snackbar.make(view, R.string.export_success_title, Snackbar.LENGTH_LONG)
                .setAction(R.string.share_label) { v ->
                    ShareCompat.IntentBuilder(v.getContext())
                            .setType(mimeType)
                            .addStream(uri)
                            .setChooserTitle(R.string.share_label)
                            .startChooser()
                }
                .show()
    }

    private fun showExportErrorDialog(error: Throwable) {
        progressDialog!!.dismiss()
        val alert = MaterialAlertDialogBuilder(getContext()!!)
        alert.setPositiveButton(android.R.string.ok) { dialog, which -> dialog.dismiss() }
        alert.setTitle(R.string.export_error_label)
        alert.setMessage(error.message)
        alert.show()
    }

    private fun showImportErrorDialog(error: Throwable) {
        progressDialog!!.dismiss()
        val alert = MaterialAlertDialogBuilder(getContext()!!)
        alert.setPositiveButton(android.R.string.ok) { dialog, which -> dialog.dismiss() }
        alert.setTitle(R.string.import_error_label)
        alert.setMessage(error.message)
        alert.show()
    }

    private fun restoreDatabaseResult(result: ActivityResult) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            return
        }
        val uri = result.getData()!!.getData()
        progressDialog!!.show()
        disposable = Completable.fromAction { DatabaseExporter.importBackup(uri!!, getContext()!!) }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    showDatabaseImportSuccessDialog()
                    progressDialog!!.dismiss()
                }, this::showImportErrorDialog)
    }

    private fun backupDatabaseResult(uri: Uri?) {
        if (uri == null) {
            return
        }
        progressDialog!!.show()
        disposable = Completable.fromAction { DatabaseExporter.exportToDocument(uri, getContext()!!) }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    showExportSuccessSnackbar(uri, "application/x-sqlite3")
                    progressDialog!!.dismiss()
                }, this::showExportErrorDialog)
    }

    private fun openExportPathPicker(exportType: Export, result: ActivityResultLauncher<Intent>) {
        val title = dateStampFilename(exportType.outputNameTemplate)

        val intentPickAction = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(exportType.contentType)
                .putExtra(Intent.EXTRA_TITLE, title)

        // Creates an implicit intent to launch a file manager which lets
        // the user choose a specific directory to export to.
        try {
            result.launch(intentPickAction)
            return
        } catch (e: ActivityNotFoundException) {
            Snackbar.make(getView()!!, R.string.unable_to_start_system_file_manager, Snackbar.LENGTH_LONG)
                    .show()
        }

        // If we are using a SDK lower than API 21 or the implicit intent failed
        // fallback to the legacy export process
        val output = File(UserPreferences.getDataFolder("export/")!!, title)
        exportToFile(exportType, output)
    }

    private fun exportToFile(exportType: Export, output: File) {
        progressDialog!!.show()
        disposable = Observable.create<File> { subscriber ->
            if (output.exists()) {
                val success = output.delete()
                Log.w(TAG, "Overwriting previously exported file: " + success)
            }
            try {
                FileOutputStream(output).use { fileOutputStream ->
                    writeToStream(fileOutputStream, exportType)
                    subscriber.onNext(output)
                }
            } catch (e: IOException) {
                subscriber.onError(e)
            }
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ outputFile ->
                    progressDialog!!.dismiss()
                    val fileUri = FileProvider.getUriForFile(getActivity()!!.getApplicationContext(),
                            getString(R.string.provider_authority), output)
                    showExportSuccessSnackbar(fileUri, exportType.contentType)
                }, this::showExportErrorDialog, { progressDialog!!.dismiss() })
    }

    private fun exportToDocument(result: ActivityResult, exportType: Export) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            return
        }
        progressDialog!!.show()
        val output = DocumentFile.fromSingleUri(getContext()!!, result.getData()!!.getData()!!)
        disposable = Observable.create<DocumentFile> { subscriber ->
            try {
                getContext()!!.getContentResolver().openOutputStream(output!!.getUri(), "wt")!!.use { outputStream ->
                    writeToStream(outputStream, exportType)
                    subscriber.onNext(output!!)
                }
            } catch (e: IOException) {
                subscriber.onError(e)
            }
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ ignore ->
                    progressDialog!!.dismiss()
                    showExportSuccessSnackbar(output!!.getUri(), exportType.contentType)
                }, this::showExportErrorDialog, { progressDialog!!.dismiss() })
    }

    @Throws(IOException::class)
    private fun writeToStream(outputStream: OutputStream, type: Export) {
        OutputStreamWriter(outputStream, Charset.forName("UTF-8")).use { writer ->
            when (type) {
                Export.HTML -> HtmlWriter.writeDocument(DBReader.getFeedList(), writer, getContext()!!)
                Export.OPML -> OpmlWriter.writeDocument(DBReader.getFeedList(), writer)
                Export.FAVORITES -> {
                    val allFavorites = DBReader.getEpisodes(0, Integer.MAX_VALUE,
                            FeedItemFilter(FeedItemFilter.IS_FAVORITE), SortOrder.DATE_NEW_OLD)
                    FavoritesWriter.writeDocument(allFavorites, writer, getContext()!!)
                }
                else -> showExportErrorDialog(Exception("Invalid export type"))
            }
        }
    }

    private fun setupAutomaticBackup(uri: Uri?) {
        if (uri == null) {
            return
        }
        getActivity()!!.getContentResolver().takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        UserPreferences.setAutomaticExportFolder(uri.toString())
        AutomaticDatabaseExportWorker.enqueueIfNeeded(getContext()!!, true)
        findPreference<SwitchPreferenceCompat>(PREF_AUTOMATIC_DATABASE_EXPORT)!!.setChecked(true)
    }

    private fun forceRestart() {
        val pm: PackageManager = getContext()!!.getPackageManager()
        val intent = pm.getLaunchIntentForPackage(getContext()!!.getPackageName())!!
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getContext()!!.getApplicationContext().startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    private class BackupDatabase : ActivityResultContracts.CreateDocument("application/x-sqlite3") {
        override fun createIntent(context: Context, input: String): Intent {
            return super.createIntent(context, input)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/x-sqlite3")
        }
    }

    private class PickWritableFolder : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent {
            return super.createIntent(context, input)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }

    private enum class Export(val contentType: String, val outputNameTemplate: String,
                              @StringRes val labelResId: Int) {
        OPML(CONTENT_TYPE_OPML, DEFAULT_OPML_OUTPUT_NAME, R.string.opml_export_label),
        HTML(CONTENT_TYPE_HTML, DEFAULT_HTML_OUTPUT_NAME, R.string.html_export_label),
        FAVORITES(CONTENT_TYPE_HTML, DEFAULT_FAVORITES_OUTPUT_NAME, R.string.favorites_export_label)
    }

    companion object {
        private const val TAG = "ImportExPrefFragment"
        private const val PREF_OPML_EXPORT = "prefOpmlExport"
        private const val PREF_OPML_IMPORT = "prefOpmlImport"
        private const val PREF_HTML_EXPORT = "prefHtmlExport"
        private const val PREF_DATABASE_IMPORT = "prefDatabaseImport"
        private const val PREF_DATABASE_EXPORT = "prefDatabaseExport"
        private const val PREF_AUTOMATIC_DATABASE_EXPORT = "prefAutomaticDatabaseExport"
        private const val PREF_FAVORITE_EXPORT = "prefFavoritesExport"
        private const val DEFAULT_OPML_OUTPUT_NAME = "antennapod-feeds-%s.opml"
        private const val CONTENT_TYPE_OPML = "text/x-opml"
        private const val DEFAULT_HTML_OUTPUT_NAME = "antennapod-feeds-%s.html"
        private const val CONTENT_TYPE_HTML = "text/html"
        private const val DEFAULT_FAVORITES_OUTPUT_NAME = "antennapod-favorites-%s.html"
        private const val DATABASE_EXPORT_FILENAME = "AntennaPodBackup-%s.db"
    }
}
