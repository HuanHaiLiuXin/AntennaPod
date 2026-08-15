package de.danoeh.antennapod.net.download.service.feed.local

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract

import java.util.ArrayList

/**
 * Android's DocumentFile is slow because every single method call queries the ContentResolver.
 * This queries the ContentResolver a single time with all the information.
 */
class FastDocumentFile {
    private val name: String
    private val type: String?
    private val uri: Uri
    private val length: Long
    private val lastModified: Long

    companion object {
        @JvmStatic
        fun list(context: Context, folderUri: Uri): List<FastDocumentFile> {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri,
                    DocumentsContract.getDocumentId(folderUri))
            val cursor = context.getContentResolver().query(childrenUri, arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)
            val list = ArrayList<FastDocumentFile>()
            if (cursor == null) {
                return list
            }
            try {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val uri = DocumentsContract.buildDocumentUriUsingTree(folderUri, id)
                    val name = cursor.getString(1)
                    val size = cursor.getLong(2)
                    val lastModified = cursor.getLong(3)
                    val mimeType = cursor.getString(4)
                    list.add(FastDocumentFile(name, mimeType, uri, size, lastModified))
                }
            } finally {
                cursor.close()
            }
            return list
        }
    }

    constructor(name: String, type: String?, uri: Uri, length: Long, lastModified: Long) {
        this.name = name
        this.type = type
        this.uri = uri
        this.length = length
        this.lastModified = lastModified
    }

    fun getName(): String {
        return name
    }

    fun getType(): String? {
        return type
    }

    fun getUri(): Uri {
        return uri
    }

    fun getLength(): Long {
        return length
    }

    fun getLastModified(): Long {
        return lastModified
    }
}
