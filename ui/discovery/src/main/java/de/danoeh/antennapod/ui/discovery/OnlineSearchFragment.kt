package de.danoeh.antennapod.ui.discovery

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.Button
import android.widget.GridView
import android.widget.ProgressBar
import android.widget.TextView

import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment

import com.google.android.material.appbar.MaterialToolbar

import java.util.ArrayList

import de.danoeh.antennapod.net.discovery.PodcastSearchResult
import de.danoeh.antennapod.net.discovery.PodcastSearcher
import de.danoeh.antennapod.net.discovery.PodcastSearcherRegistry
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter
import de.danoeh.antennapod.ui.common.Keyboard
import io.reactivex.rxjava3.disposables.Disposable

class OnlineSearchFragment : Fragment() {

    companion object {
        private const val TAG = "FyydSearchFragment"
        private const val ARG_SEARCHER = "searcher"
        private const val ARG_QUERY = "query"

        @JvmStatic
        fun newInstance(searchProvider: Class<out PodcastSearcher>): OnlineSearchFragment {
            return newInstance(searchProvider, null)
        }

        @JvmStatic
        fun newInstance(searchProvider: Class<out PodcastSearcher>, query: String?): OnlineSearchFragment {
            val fragment = OnlineSearchFragment()
            val arguments = Bundle()
            arguments.putString(ARG_SEARCHER, searchProvider.getName())
            arguments.putString(ARG_QUERY, query)
            fragment.setArguments(arguments)
            return fragment
        }
    }

    /**
     * Adapter responsible with the search results
     */
    private var adapter: OnlineSearchAdapter? = null
    private var searchProvider: PodcastSearcher? = null
    private var gridView: GridView? = null
    private var progressBar: ProgressBar? = null
    private var txtvError: TextView? = null
    private var butRetry: Button? = null
    private var txtvEmpty: TextView? = null

    /**
     * List of podcasts retreived from the search
     */
    private var searchResults: List<PodcastSearchResult>? = null
    private var disposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        for (info in PodcastSearcherRegistry.getSearchProviders()) {
            if (info.searcher.javaClass.getName() == getArguments()!!.getString(ARG_SEARCHER)) {
                searchProvider = info.searcher
                break
            }
        }
        if (searchProvider == null) {
            throw IllegalArgumentException("Podcast searcher not found")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.fragment_online_search, container, false)
        gridView = root.findViewById(R.id.gridView)
        adapter = OnlineSearchAdapter(getActivity()!!, ArrayList())
        gridView!!.setAdapter(adapter)

        //Show information about the podcast when the list item is clicked
        gridView!!.setOnItemClickListener { parent, view1, position, id ->
            val podcast = searchResults!!.get(position)
            startActivity(OnlineFeedviewActivityStarter(getContext()!!, podcast.feedUrl!!).getIntent())
        }
        progressBar = root.findViewById(R.id.progressBar)
        txtvError = root.findViewById(R.id.txtvError)
        butRetry = root.findViewById(R.id.butRetry)
        txtvEmpty = root.findViewById(android.R.id.empty)
        val txtvPoweredBy = root.findViewById<TextView>(R.id.search_powered_by)
        txtvPoweredBy.setText(getString(R.string.search_powered_by, searchProvider!!.getName()))
        setupToolbar(root.findViewById(R.id.toolbar))

        gridView!!.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                    Keyboard.hide(getActivity())
                }
            }

            override fun onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
            }
        })
        return root
    }

    override fun onDestroy() {
        super.onDestroy()
        if (disposable != null) {
            disposable!!.dispose()
        }
        adapter = null
    }

    private fun setupToolbar(toolbar: MaterialToolbar) {
        toolbar.inflateMenu(R.menu.online_search)
        toolbar.setNavigationOnClickListener { getParentFragmentManager().popBackStack() }

        val searchItem = toolbar.getMenu().findItem(R.id.action_search)!!
        val sv = searchItem.getActionView() as SearchView
        sv.setQueryHint(getString(R.string.search_podcast_hint))
        sv.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(s: String): Boolean {
                sv.clearFocus()
                search(s)
                return true
            }

            override fun onQueryTextChange(s: String): Boolean {
                return false
            }
        })
        sv.setOnQueryTextFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                Keyboard.show(getContext(), view.findFocus())
            }
        }
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                getActivity()!!.getSupportFragmentManager().popBackStack()
                return true
            }
        })
        searchItem.expandActionView()

        if (getArguments()!!.getString(ARG_QUERY, null) != null) {
            sv.setQuery(getArguments()!!.getString(ARG_QUERY, null), true)
        }
    }

    private fun search(query: String) {
        if (disposable != null) {
            disposable!!.dispose()
        }
        showOnlyProgressBar()
        disposable = searchProvider!!.search(query).subscribe({ result ->
            searchResults = result
            progressBar!!.setVisibility(View.GONE)
            adapter!!.clear()
            adapter!!.addAll(result)
            adapter!!.notifyDataSetInvalidated()
            gridView!!.setVisibility(if (!result.isEmpty()) View.VISIBLE else View.GONE)
            txtvEmpty!!.setVisibility(if (result.isEmpty()) View.VISIBLE else View.GONE)
            txtvEmpty!!.setText(getString(R.string.no_results_for_query, query))
        }, { error ->
                Log.e(TAG, Log.getStackTraceString(error))
                progressBar!!.setVisibility(View.GONE)
                txtvError!!.setText(error.toString())
                txtvError!!.setVisibility(View.VISIBLE)
                butRetry!!.setOnClickListener { search(query) }
                butRetry!!.setVisibility(View.VISIBLE)
            })
    }

    private fun showOnlyProgressBar() {
        gridView!!.setVisibility(View.GONE)
        txtvError!!.setVisibility(View.GONE)
        butRetry!!.setVisibility(View.GONE)
        txtvEmpty!!.setVisibility(View.GONE)
        progressBar!!.setVisibility(View.VISIBLE)
    }
}
