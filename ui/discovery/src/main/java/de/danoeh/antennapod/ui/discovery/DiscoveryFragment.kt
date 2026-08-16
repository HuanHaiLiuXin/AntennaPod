package de.danoeh.antennapod.ui.discovery

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import de.danoeh.antennapod.net.discovery.BuildConfig
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.event.DiscoveryDefaultUpdateEvent
import de.danoeh.antennapod.net.discovery.ItunesTopListLoader
import de.danoeh.antennapod.net.discovery.PodcastSearchResult
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus

import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.HashMap
import java.util.Locale

/**
 * Searches iTunes store for top podcasts and displays results in a list.
 */
class DiscoveryFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    companion object {
        const val TAG = "DiscoveryFragment"
        private const val NUM_OF_TOP_PODCASTS = 25
    }

    private lateinit var prefs: SharedPreferences

    /**
     * Adapter responsible with the search results.
     */
    private var adapter: OnlineSearchAdapter? = null
    private var gridView: GridView? = null
    private var progressBar: ProgressBar? = null
    private var txtvError: TextView? = null
    private var butRetry: Button? = null
    private var txtvEmpty: TextView? = null

    /**
     * List of podcasts retreived from the search.
     */
    private var searchResults: List<PodcastSearchResult>? = null
    private var topList: List<PodcastSearchResult>? = null
    private var disposable: Disposable? = null
    private var countryCode = "US"
    private var hidden = false
    private var needsConfirm = false
    private var toolbar: MaterialToolbar? = null

    /**
     * Replace adapter data with provided search results from SearchTask.
     *
     * @param result List of Podcast objects containing search results
     */
    private fun updateData(result: List<PodcastSearchResult>?) {
        this.searchResults = result
        adapter!!.clear()
        if (result != null && result.size > 0) {
            gridView!!.setVisibility(View.VISIBLE)
            txtvEmpty!!.setVisibility(View.GONE)
            for (p in result) {
                adapter!!.add(p)
            }
            adapter!!.notifyDataSetInvalidated()
        } else {
            gridView!!.setVisibility(View.GONE)
            txtvEmpty!!.setVisibility(View.VISIBLE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = requireActivity().getSharedPreferences(ItunesTopListLoader.PREFS, Context.MODE_PRIVATE)
        countryCode = prefs.getString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, Locale.getDefault().getCountry())!!
        hidden = prefs.getBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, false)
        needsConfirm = prefs.getBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.fragment_online_search, container, false)
        gridView = root.findViewById(R.id.gridView)
        adapter = OnlineSearchAdapter(requireActivity(), ArrayList())
        gridView!!.setAdapter(adapter)

        toolbar = root.findViewById(R.id.toolbar)
        toolbar!!.setNavigationOnClickListener { getParentFragmentManager().popBackStack() }
        toolbar!!.inflateMenu(R.menu.countries_menu)
        val discoverHideItem = toolbar!!.getMenu().findItem(R.id.discover_hide_item)!!
        discoverHideItem.setChecked(hidden)
        toolbar!!.setOnMenuItemClickListener(this)

        //Show information about the podcast when the list item is clicked
        gridView!!.setOnItemClickListener { parent, view1, position, id ->
            val podcast = searchResults!!.get(position)
            if (podcast.feedUrl == null) {
                return@setOnItemClickListener
            }
            startActivity(OnlineFeedviewActivityStarter(requireContext(), podcast.feedUrl!!).getIntent())
        }

        progressBar = root.findViewById(R.id.progressBar)
        txtvError = root.findViewById(R.id.txtvError)
        butRetry = root.findViewById(R.id.butRetry)
        txtvEmpty = root.findViewById(android.R.id.empty)

        loadToplist(countryCode)
        return root
    }

    override fun onDestroy() {
        super.onDestroy()
        if (disposable != null) {
            disposable!!.dispose()
        }
        adapter = null
    }

    private fun loadToplist(country: String) {
        if (disposable != null) {
            disposable!!.dispose()
        }

        gridView!!.setVisibility(View.GONE)
        txtvError!!.setVisibility(View.GONE)
        butRetry!!.setVisibility(View.GONE)
        butRetry!!.setText(R.string.retry_label)
        txtvEmpty!!.setVisibility(View.GONE)
        progressBar!!.setVisibility(View.VISIBLE)

        if (hidden) {
            gridView!!.setVisibility(View.GONE)
            txtvError!!.setVisibility(View.VISIBLE)
            txtvError!!.setText(getResources().getString(R.string.discover_is_hidden))
            butRetry!!.setVisibility(View.GONE)
            txtvEmpty!!.setVisibility(View.GONE)
            progressBar!!.setVisibility(View.GONE)
            return
        }
        if (BuildConfig.FLAVOR == "free" && needsConfirm) {
            txtvError!!.setVisibility(View.VISIBLE)
            txtvError!!.setText("")
            butRetry!!.setVisibility(View.VISIBLE)
            butRetry!!.setText(R.string.discover_confirm)
            butRetry!!.setOnClickListener {
                prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, false).apply()
                needsConfirm = false
                loadToplist(country)
            }
            txtvEmpty!!.setVisibility(View.GONE)
            progressBar!!.setVisibility(View.GONE)
            return
        }

        val loader = ItunesTopListLoader(requireContext())
        disposable = Observable.fromCallable<List<PodcastSearchResult>> {
                    loader.loadToplist(country, NUM_OF_TOP_PODCASTS, DBReader.getFeedList()) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { podcasts ->
                        progressBar!!.setVisibility(View.GONE)
                        topList = podcasts
                        updateData(topList)
                    }, { error ->
                        Log.e(TAG, Log.getStackTraceString(error))
                        progressBar!!.setVisibility(View.GONE)
                        txtvError!!.setText(error.message)
                        txtvError!!.setVisibility(View.VISIBLE)
                        butRetry!!.setOnClickListener { loadToplist(country) }
                        butRetry!!.setVisibility(View.VISIBLE)
                    })
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.getItemId()
        if (itemId == R.id.discover_hide_item) {
            item.setChecked(!item.isChecked())
            hidden = item.isChecked()
            prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, hidden).apply()

            EventBus.getDefault().post(DiscoveryDefaultUpdateEvent())
            loadToplist(countryCode)
            return true
        } else if (itemId == R.id.discover_countries_item) {

            val inflater = getLayoutInflater()
            val selectCountryDialogView = inflater.inflate(R.layout.select_country_dialog, null)
            val builder = MaterialAlertDialogBuilder(requireContext())
            builder.setView(selectCountryDialogView)

            val countryCodeArray: List<String> = ArrayList(Arrays.asList(*Locale.getISOCountries()))
            val countryCodeNames = HashMap<String, String>()
            val countryNameCodes = HashMap<String, String>()
            for (code in countryCodeArray) {
                val locale = Locale("", code)
                val countryName = locale.getDisplayCountry()
                countryCodeNames.put(code, countryName)
                countryNameCodes.put(countryName, code)
            }

            val countryNamesSort: List<String> = ArrayList(countryCodeNames.values)
            Collections.sort(countryNamesSort)

            val dataAdapter =
                    ArrayAdapter(this.requireContext(), android.R.layout.simple_list_item_1, countryNamesSort)
            val textInput = selectCountryDialogView.findViewById<TextInputLayout>(R.id.country_text_input)
            val editText = textInput.getEditText() as MaterialAutoCompleteTextView
            editText.setAdapter(dataAdapter)
            editText.setText(countryCodeNames.get(countryCode))
            editText.setOnClickListener {
                if (editText.getText().length != 0) {
                    editText.setText("")
                    editText.postDelayed({ editText.showDropDown() }, 100)
                }
            }
            editText.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    editText.setText("")
                    editText.postDelayed({ editText.showDropDown() }, 100)
                }
            }

            builder.setPositiveButton(android.R.string.ok) { dialogInterface, i ->
                val countryName = editText.getText().toString()
                if (countryNameCodes.containsKey(countryName)) {
                    countryCode = countryNameCodes.get(countryName)!!
                    val discoverHideItem = toolbar!!.getMenu().findItem(R.id.discover_hide_item)!!
                    discoverHideItem.setChecked(false)
                    hidden = false
                }

                prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, hidden).apply()
                prefs.edit().putString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, countryCode).apply()

                EventBus.getDefault().post(DiscoveryDefaultUpdateEvent())
                loadToplist(countryCode)
            }
            builder.setNegativeButton(R.string.cancel_label, null)
            builder.show()
            return true
        }
        return false
    }
}
