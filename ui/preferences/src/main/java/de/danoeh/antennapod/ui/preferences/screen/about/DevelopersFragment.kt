package de.danoeh.antennapod.ui.preferences.screen.about

import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.Toast
import androidx.fragment.app.ListFragment

import de.danoeh.antennapod.ui.common.IntentUtils
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.ArrayList

class DevelopersFragment : ListFragment() {
    private var developersLoader: Disposable? = null
    private val developers = ArrayList<SimpleIconListAdapter.ListItem>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        getListView().setDivider(null)

        developersLoader = Single.create<ArrayList<SimpleIconListAdapter.ListItem>> { emitter ->
            developers.clear()
            try {
                BufferedReader(InputStreamReader(
                        requireContext().getAssets().open("developers.csv"), "UTF-8")).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val info = line!!.split(";")
                        developers.add(SimpleIconListAdapter.ListItem(info[0], info[2],
                                "https://avatars2.githubusercontent.com/u/" + info[1] + "?s=60&v=4"))
                    }
                    emitter.onSuccess(developers)
                }
            } catch (e: Exception) {
                emitter.onError(e)
            }
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { loadedDevelopers -> setListAdapter(SimpleIconListAdapter(requireContext(), loadedDevelopers)) },
                        { error -> Toast.makeText(getContext(), error.message, Toast.LENGTH_LONG).show() }
                )
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        super.onListItemClick(l, v, position, id)
        IntentUtils.openInBrowser(requireContext(), "https://github.com/" + developers.get(position).title)
    }

    override fun onStop() {
        super.onStop()
        if (developersLoader != null) {
            developersLoader!!.dispose()
        }
    }
}
