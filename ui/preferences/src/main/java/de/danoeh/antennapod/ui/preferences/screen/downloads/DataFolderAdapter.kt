package de.danoeh.antennapod.ui.preferences.screen.downloads

import android.content.Context
import android.os.StatFs
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.util.Consumer
import androidx.recyclerview.widget.RecyclerView
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.preferences.R

import java.io.File
import java.util.ArrayList
import java.util.Arrays

class DataFolderAdapter(context: Context, private val selectionHandler: Consumer<String>) :
        RecyclerView.Adapter<DataFolderAdapter.ViewHolder>() {
    private val currentPath: String?
    private val entries: List<StoragePath>
    private val freeSpaceString: String

    init {
        this.entries = getStorageEntries(context)
        this.currentPath = getCurrentPath()
        this.freeSpaceString = context.getString(R.string.choose_data_directory_available_space)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.getContext())
        val entryView = inflater.inflate(R.layout.choose_data_folder_dialog_entry, parent, false)
        return ViewHolder(entryView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val storagePath = entries.get(position)
        val context = holder.root.getContext()
        val freeSpace = Formatter.formatShortFileSize(context, storagePath.getAvailableSpace())
        val totalSpace = Formatter.formatShortFileSize(context, storagePath.getTotalSpace())

        holder.path.setText(storagePath.getPath())
        holder.size.setText(String.format(freeSpaceString, freeSpace, totalSpace))
        holder.progressBar.setProgress(storagePath.getUsagePercentage())
        val selectListener = View.OnClickListener { selectionHandler.accept(storagePath.getPath()) }
        holder.root.setOnClickListener(selectListener)
        holder.radioButton.setOnClickListener(selectListener)

        if (storagePath.getPath() == currentPath) {
            holder.radioButton.toggle()
        }
    }

    override fun getItemCount(): Int {
        return entries.size
    }

    private fun getCurrentPath(): String? {
        val dataFolder = UserPreferences.getDataFolder(null)
        if (dataFolder != null) {
            return dataFolder.getAbsolutePath()
        }
        return null
    }

    private fun getStorageEntries(context: Context): List<StoragePath> {
        val mediaDirs: MutableList<File> = ArrayList()
        mediaDirs.addAll(Arrays.asList(*context.getExternalFilesDirs(null)))
        mediaDirs.addAll(Arrays.asList(*context.getExternalMediaDirs()))
        val entries: MutableList<StoragePath> = ArrayList(mediaDirs.size)
        for (dir in mediaDirs) {
            if (!isWritable(dir)) {
                continue
            }
            entries.add(StoragePath(dir.getAbsolutePath()))
        }
        if (entries.isEmpty() && isWritable(context.getFilesDir())) {
            entries.add(StoragePath(context.getFilesDir().getAbsolutePath()))
        }
        return entries
    }

    private fun isWritable(dir: File?): Boolean {
        return dir != null && dir.exists() && dir.canRead() && dir.canWrite()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root: View = itemView.findViewById(R.id.root)
        val path: TextView = itemView.findViewById(R.id.path)
        val size: TextView = itemView.findViewById(R.id.size)
        val radioButton: RadioButton = itemView.findViewById(R.id.radio_button)
        val progressBar: ProgressBar = itemView.findViewById(R.id.used_space)
    }

    internal class StoragePath(private val path: String) {

        fun getPath(): String {
            return this.path
        }

        fun getAvailableSpace(): Long {
            val stat = StatFs(path)
            val availableBlocks = stat.getAvailableBlocksLong()
            val blockSize = stat.getBlockSizeLong()
            return availableBlocks * blockSize
        }

        fun getTotalSpace(): Long {
            val stat = StatFs(path)
            val blockCount = stat.getBlockCountLong()
            val blockSize = stat.getBlockSizeLong()
            return blockCount * blockSize
        }

        fun getUsagePercentage(): Int {
            return 100 - (100 * getAvailableSpace() / getTotalSpace().toFloat()).toInt()
        }
    }
}
