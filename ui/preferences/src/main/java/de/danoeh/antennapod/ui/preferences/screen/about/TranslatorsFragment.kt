package de.danoeh.antennapod.ui.preferences.screen.about

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.ListFragment
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.ArrayList

class TranslatorsFragment : ListFragment() {
    private var translatorsLoader: Disposable? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        getListView().setDivider(null)
        getListView().setSelector(android.R.color.transparent)

        translatorsLoader = Single.create<ArrayList<SimpleIconListAdapter.ListItem>> { emitter ->
            val translators = ArrayList<SimpleIconListAdapter.ListItem>()
            try {
                BufferedReader(InputStreamReader(
                        getContext()!!.getAssets().open("translators.csv"), "UTF-8")).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val info = line!!.split(";")
                        translators.add(SimpleIconListAdapter.ListItem(info[0], info[1], null))
                    }
                    emitter.onSuccess(translators)
                }
            } catch (e: Exception) {
                emitter.onError(e)
            }
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { translators -> setListAdapter(SimpleIconListAdapter(getContext()!!, translators)) },
                        { error -> Toast.makeText(getContext(), error.message, Toast.LENGTH_LONG).show() }
                )
    }

    override fun onStop() {
        super.onStop()
        if (translatorsLoader != null) {
            translatorsLoader!!.dispose()
        }
    }
}
