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

class SpecialThanksFragment : ListFragment() {
    private var translatorsLoader: Disposable? = null

    private val specialMembers = ArrayList<SpecialMemberItem>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        getListView().setDivider(null)

        translatorsLoader = Single.create<ArrayList<SpecialMemberItem>> { emitter ->
            specialMembers.clear()
            try {
                BufferedReader(InputStreamReader(
                        getContext()!!.getAssets().open("special_thanks.csv"), "UTF-8")).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val info = line!!.split(";")
                        specialMembers.add(SpecialMemberItem(info[0], info[1], info[2], info[3]))
                    }
                    emitter.onSuccess(specialMembers)
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

    private class SpecialMemberItem(title: String, subtitle: String, imageUrl: String?,
                                    val githubUsername: String) :
            SimpleIconListAdapter.ListItem(title, subtitle, imageUrl)

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        super.onListItemClick(l, v, position, id)

        IntentUtils.openInBrowser(getContext()!!, "https://github.com/" + specialMembers.get(position).githubUsername)
    }

    override fun onStop() {
        super.onStop()
        if (translatorsLoader != null) {
            translatorsLoader!!.dispose()
        }
    }
}
