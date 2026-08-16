package de.danoeh.antennapod.ui.preferences.screen.about

import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.ListFragment
import com.google.android.material.transition.MaterialSharedAxis
import de.danoeh.antennapod.ui.common.IntentUtils
import de.danoeh.antennapod.ui.preferences.R
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.NodeList

import javax.xml.parsers.DocumentBuilderFactory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.ArrayList

class LicensesFragment : ListFragment() {
    private var licensesLoader: Disposable? = null
    private val licenses = ArrayList<LicenseItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setEnterTransition(MaterialSharedAxis(MaterialSharedAxis.X, true))
        setReturnTransition(MaterialSharedAxis(MaterialSharedAxis.X, false))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        getListView().setDivider(null)

        licensesLoader = Single.create<ArrayList<LicenseItem>> { emitter ->
            licenses.clear()
            try {
                getContext()!!.getAssets().open("licenses.xml").use { stream ->
                    val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    val libraryList = docBuilder.parse(stream).getElementsByTagName("library")
                    for (i in 0 until libraryList.getLength()) {
                        val lib: NamedNodeMap = libraryList.item(i).getAttributes()
                        licenses.add(LicenseItem(
                                lib.getNamedItem("name").getTextContent(),
                                String.format("By %s, %s license",
                                        lib.getNamedItem("author").getTextContent(),
                                        lib.getNamedItem("license").getTextContent()),
                                null,
                                lib.getNamedItem("website").getTextContent(),
                                lib.getNamedItem("licenseText").getTextContent()))
                    }
                    emitter.onSuccess(licenses)
                }
            } catch (e: Exception) {
                emitter.onError(e)
            }
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { loadedDevelopers -> setListAdapter(SimpleIconListAdapter(getContext()!!, loadedDevelopers)) },
                        { error -> Toast.makeText(getContext(), error.message, Toast.LENGTH_LONG).show() }
                )
    }

    private class LicenseItem(title: String, subtitle: String, imageUrl: String?,
                              val licenseUrl: String, val licenseTextFile: String) :
            SimpleIconListAdapter.ListItem(title, subtitle, imageUrl)

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        super.onListItemClick(l, v, position, id)

        val item = licenses.get(position)
        val items = arrayOf("View website", "View license")
        MaterialAlertDialogBuilder(getContext()!!)
                .setTitle(item.title)
                .setItems(items) { dialog, which ->
                    if (which == 0) {
                        IntentUtils.openInBrowser(getContext()!!, item.licenseUrl)
                    } else if (which == 1) {
                        showLicenseText(item.licenseTextFile)
                    }
                }.show()
    }

    private fun showLicenseText(licenseTextFile: String) {
        try {
            BufferedReader(InputStreamReader(
                    getContext()!!.getAssets().open(licenseTextFile), "UTF-8")).use { reader ->
                val licenseText = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    licenseText.append(line).append("\n")
                }

                MaterialAlertDialogBuilder(getContext()!!)
                        .setMessage(licenseText)
                        .show()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onStop() {
        super.onStop()
        if (licensesLoader != null) {
            licensesLoader!!.dispose()
        }
    }

    override fun onStart() {
        super.onStart()
        (getActivity() as AppCompatActivity).getSupportActionBar()!!.setTitle(R.string.licenses)
    }
}
