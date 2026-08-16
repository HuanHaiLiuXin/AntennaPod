package de.danoeh.antennapod.ui.screen.download

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.appcompat.widget.Toolbar
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.danoeh.antennapod.R
import de.danoeh.antennapod.databinding.DownloadLogFragmentBinding
import de.danoeh.antennapod.event.DownloadLogEvent
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.ui.common.EmptyViewHandler
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/**
 * Shows the download log
 */
class DownloadLogFragment : BottomSheetDialogFragment(),
        AdapterView.OnItemClickListener, Toolbar.OnMenuItemClickListener {
    companion object {
        const val TAG = "DownloadLogFragment"
    }

    private var downloadLog: List<DownloadResult> = ArrayList()
    private var adapter: DownloadLogAdapter? = null
    private var disposable: Disposable? = null
    private var viewBinding: DownloadLogFragmentBinding? = null

    override fun onStart() {
        super.onStart()
        loadDownloadLog()
    }

    override fun onStop() {
        super.onStop()
        if (disposable != null) {
            disposable!!.dispose()
        }
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewBinding = DownloadLogFragmentBinding.inflate(inflater)
        viewBinding!!.toolbar.inflateMenu(R.menu.download_log)
        viewBinding!!.toolbar.setOnMenuItemClickListener(this)

        val emptyView = EmptyViewHandler(requireActivity())
        emptyView.setIcon(R.drawable.ic_download)
        emptyView.setTitle(R.string.no_log_downloads_head_label)
        emptyView.setMessage(R.string.no_log_downloads_label)
        emptyView.attachToListView(viewBinding!!.list)

        adapter = DownloadLogAdapter(requireActivity())
        viewBinding!!.list.setAdapter(adapter!!)
        viewBinding!!.list.setOnItemClickListener(this)
        viewBinding!!.list.setNestedScrollingEnabled(true)
        EventBus.getDefault().register(this)
        return viewBinding!!.getRoot()
    }

    override fun onDestroyView() {
        EventBus.getDefault().unregister(this)
        super.onDestroyView()
        viewBinding = null
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val item = adapter!!.getItem(position)
        if (item != null) {
            DownloadLogDetailsDialog.newInstance(item, true)
                    .show(getParentFragmentManager(), DownloadLogDetailsDialog.TAG)
        }
    }

    @Subscribe
    fun onDownloadLogChanged(event: DownloadLogEvent) {
        loadDownloadLog()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.getItemId() == R.id.clear_logs_item) {
            DBWriter.clearDownloadLog()
            return true
        }
        return false
    }

    private fun loadDownloadLog() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Observable.fromCallable(DBReader::getDownloadLog)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    downloadLog = result
                    adapter!!.setDownloadLog(downloadLog)
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }
}
