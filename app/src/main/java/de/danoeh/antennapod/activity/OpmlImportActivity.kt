package de.danoeh.antennapod.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.SparseBooleanArray
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.app.ActivityCompat
import de.danoeh.antennapod.R
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager

import de.danoeh.antennapod.storage.database.FeedDatabaseWriter
import de.danoeh.antennapod.databinding.OpmlSelectionBinding
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.storage.importexport.OpmlElement
import de.danoeh.antennapod.storage.importexport.OpmlReader
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.common.ToolbarActivity
import de.danoeh.antennapod.ui.preferences.screen.ParentalControlDialog
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import org.apache.commons.io.ByteOrderMark
import org.apache.commons.io.input.BOMInputStream

import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.util.ArrayList
import java.util.Collections
import java.util.Locale

/**
 * Activity for Opml Import.
 * */
class OpmlImportActivity : ToolbarActivity() {
    companion object {
        private const val TAG = "OpmlImportBaseActivity"
    }

    private var uri: Uri? = null
    private var viewBinding: OpmlSelectionBinding? = null
    private var listAdapter: ArrayAdapter<String>? = null
    private var selectAll: MenuItem? = null
    private var deselectAll: MenuItem? = null
    private var readElements: ArrayList<OpmlElement>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSupportActionBar()!!.setDisplayHomeAsUpEnabled(true)
        viewBinding = OpmlSelectionBinding.inflate(getLayoutInflater())
        setContentView(viewBinding!!.getRoot())

        viewBinding!!.feedlist.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE)
        viewBinding!!.feedlist.setOnItemClickListener { parent, view, position, id ->
            val checked = viewBinding!!.feedlist.getCheckedItemPositions()
            var checkedCount = 0
            for (i in 0 until checked.size()) {
                if (checked.valueAt(i)) {
                    checkedCount++
                }
            }
            if (checkedCount == listAdapter!!.getCount()) {
                selectAll!!.setVisible(false)
                deselectAll!!.setVisible(true)
            } else {
                deselectAll!!.setVisible(false)
                selectAll!!.setVisible(true)
            }
        }
        viewBinding!!.butCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        viewBinding!!.butConfirm.setOnClickListener {
            if (UserPreferences.isParentalControlPasswordSet()
                    && UserPreferences.isParentalControlRequireSubscribeSet()) {
                ParentalControlDialog.show(this) { doImport() }
                return@setOnClickListener
            }
            doImport()
        }

        var importUri = getIntent().getData()
        if (importUri != null && importUri.toString().startsWith("/")) {
            importUri = Uri.parse("file://" + importUri.toString())
        } else {
            val extraText = getIntent().getStringExtra(Intent.EXTRA_TEXT)
            if (extraText != null) {
                importUri = Uri.parse(extraText)
            }
        }
        importUri(importUri)
    }

    private fun doImport() {
        viewBinding!!.progressBar.setVisibility(View.VISIBLE)
        Completable.fromAction {
            val checked = viewBinding!!.feedlist.getCheckedItemPositions()
            for (i in 0 until checked.size()) {
                if (!checked.valueAt(i)) {
                    continue
                }
                val element = readElements!!.get(checked.keyAt(i))
                val feed = Feed(element.getXmlUrl(), null,
                        if (element.getText() != null) element.getText() else "Unknown podcast")
                feed.setItems(Collections.emptyList())
                FeedDatabaseWriter.updateFeed(this@OpmlImportActivity, feed, false)
            }
            FeedUpdateManager.getInstance()!!.runOnce(this@OpmlImportActivity)
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            viewBinding!!.progressBar.setVisibility(View.GONE)
                            val intent = Intent(this@OpmlImportActivity, MainActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                            finish()
                        }, { e ->
                            e.printStackTrace()
                            viewBinding!!.progressBar.setVisibility(View.GONE)
                            Toast.makeText(this@OpmlImportActivity, e.message, Toast.LENGTH_LONG).show()
                        })
    }

    fun importUri(uri: Uri?) {
        if (uri == null) {
            MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.opml_import_error_no_file)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            return
        }
        this.uri = uri
        startImport()
    }

    private fun getTitleList(): List<String> {
        val result: MutableList<String> = ArrayList()
        if (readElements != null) {
            for (element in readElements!!) {
                result.add(element.getText()!!)
            }
        }
        return result
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater = getMenuInflater()
        inflater.inflate(R.menu.opml_selection_options, menu)
        selectAll = menu.findItem(R.id.select_all_item)
        deselectAll = menu.findItem(R.id.deselect_all_item)
        deselectAll!!.setVisible(false)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.getItemId()
        if (itemId == R.id.select_all_item) {
            selectAll!!.setVisible(false)
            selectAllItems(true)
            deselectAll!!.setVisible(true)
            return true
        } else if (itemId == R.id.deselect_all_item) {
            deselectAll!!.setVisible(false)
            selectAllItems(false)
            selectAll!!.setVisible(true)
            return true
        } else if (itemId == android.R.id.home) {
            finish()
        }
        return false
    }

    private fun selectAllItems(b: Boolean) {
        for (i in 0 until viewBinding!!.feedlist.getCount()) {
            viewBinding!!.feedlist.setItemChecked(i, b)
        }
    }

    private fun requestPermission() {
        requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private val requestPermissionLauncher: ActivityResultLauncher<String> =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    startImport()
                } else {
                    MaterialAlertDialogBuilder(this)
                            .setMessage(R.string.opml_import_ask_read_permission)
                            .setPositiveButton(android.R.string.ok) { dialog, which ->
                                requestPermission()
                            }
                            .setNegativeButton(R.string.cancel_label) { dialog, which ->
                                finish()
                            }
                            .show()
                }
            }

    /** Starts the import process. */
    private fun startImport() {
        viewBinding!!.progressBar.setVisibility(View.VISIBLE)

        Observable.fromCallable<ArrayList<OpmlElement>> {
            val opmlFileStream: InputStream? = getContentResolver().openInputStream(uri!!)
            val bomInputStream = BOMInputStream(opmlFileStream)
            val bom: ByteOrderMark? = bomInputStream.getBOM()
            val charsetName = if (bom == null) "UTF-8" else bom.getCharsetName()
            val reader: Reader = InputStreamReader(bomInputStream, charsetName)
            val opmlReader = OpmlReader()
            val result = opmlReader.readDocument(reader)
            reader.close()
            result
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { result ->
                            viewBinding!!.progressBar.setVisibility(View.GONE)
                            Log.d(TAG, "Parsing was successful")
                            readElements = result
                            listAdapter = ArrayAdapter(this@OpmlImportActivity,
                                    android.R.layout.simple_list_item_multiple_choice,
                                    getTitleList())
                            viewBinding!!.feedlist.setAdapter(listAdapter)
                        }, { e ->
                            Log.d(TAG, Log.getStackTraceString(e))
                            val message = if (e.message == null) "" else e.message!!
                            if (message.lowercase(Locale.ROOT).contains("permission")) {
                                val permission = ActivityCompat.checkSelfPermission(this,
                                        android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                if (permission != PackageManager.PERMISSION_GRANTED) {
                                    requestPermission()
                                    return@subscribe
                                }
                            }
                            viewBinding!!.progressBar.setVisibility(View.GONE)
                            val alert = MaterialAlertDialogBuilder(this)
                            alert.setTitle(R.string.error_label)
                            val userReadable = getString(R.string.opml_reader_error)
                            val details = e.message
                            val total = userReadable + "\n\n" + details
                            val errorMessage = SpannableString(total)
                            errorMessage.setSpan(ForegroundColorSpan(0x88888888.toInt()),
                                    userReadable.length, total.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            alert.setMessage(errorMessage)
                            alert.setPositiveButton(android.R.string.ok) { dialog, which -> finish() }
                            alert.show()
                        })
    }
}
