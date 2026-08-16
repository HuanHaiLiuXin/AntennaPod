package de.danoeh.antennapod.ui.preferences.screen.downloads

import android.content.Context

import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.util.Consumer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.danoeh.antennapod.ui.preferences.R

class ChooseDataFolderDialog {
    companion object {
        @JvmStatic
        fun showDialog(context: Context, handlerFunc: Consumer<String>) {

            val content = View.inflate(context, R.layout.choose_data_folder_dialog, null)
            val dialog = MaterialAlertDialogBuilder(context)
                    .setView(content)
                    .setTitle(R.string.choose_data_directory)
                    .setMessage(R.string.choose_data_directory_message)
                    .setNegativeButton(R.string.cancel_label, null)
                    .create()
            (content.findViewById<RecyclerView>(R.id.recyclerView)).setLayoutManager(LinearLayoutManager(context))

            val adapter = DataFolderAdapter(context) { path ->
                dialog.dismiss()
                handlerFunc.accept(path)
            }
            (content.findViewById<RecyclerView>(R.id.recyclerView)).setAdapter(adapter)

            if (adapter.getItemCount() != 0) {
                dialog.show()
            }
        }
    }
}
