package de.danoeh.antennapod.model.download

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils

class DownloadRequest : Parcelable {
    private var destination: String = ""
    private var source: String = ""
    private var title: String = ""
    private var username: String? = null
    private var password: String? = null
    private var lastModified: String? = null
    private var feedfileId: Long = 0
    private var feedfileType: Int = 0
    private var arguments: Bundle? = null

    private var progressPercent: Int = 0
    private var soFar: Long = 0
    private var size: Long = 0
    private var statusMsg: Int = 0
    private var mediaEnqueued: Boolean = false
    private var initiatedByUser: Boolean = false

    constructor(destination: String, source: String, title: String, feedfileId: Long,
                feedfileType: Int, username: String?, password: String?,
                arguments: Bundle, initiatedByUser: Boolean) : this(destination, source, title,
            feedfileId, feedfileType, null, username, password, false,
            arguments, initiatedByUser) {
    }

    private constructor(source: Parcel) : this(source.readString()!!, source.readString()!!,
        source.readString()!!, source.readLong(), source.readInt(), source.readString(),
        nullIfEmpty(source.readString()), nullIfEmpty(source.readString()), source.readByte() > 0,
        source.readBundle()!!, source.readByte() > 0) {
    }

    constructor(destination: String, source: String, title: String, feedfileId: Long,
                feedfileType: Int, lastModified: String?, username: String?, password: String?,
                mediaEnqueued: Boolean, arguments: Bundle, initiatedByUser: Boolean) {
        this.destination = destination
        this.source = source
        this.title = title
        this.feedfileId = feedfileId
        this.feedfileType = feedfileType
        this.lastModified = lastModified
        this.username = username
        this.password = password
        this.mediaEnqueued = mediaEnqueued
        this.arguments = arguments
        this.initiatedByUser = initiatedByUser
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(destination)
        dest.writeString(source)
        dest.writeString(title)
        dest.writeLong(feedfileId)
        dest.writeInt(feedfileType)
        dest.writeString(lastModified)
        // in case of null username/password, still write an empty string
        // (rather than skipping it). Otherwise, unmarshalling  a collection
        // of them from a Parcel (from an Intent extra to submit a request to DownloadService) will fail.
        //
        // see: https://stackoverflow.com/a/22926342
        dest.writeString(nonNullString(username))
        dest.writeString(nonNullString(password))
        dest.writeByte((if (mediaEnqueued) 1 else 0).toByte())
        dest.writeBundle(arguments)
        dest.writeByte((if (initiatedByUser) 1 else 0).toByte())
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is DownloadRequest) return false

        val that = o as DownloadRequest

        if (lastModified != that.lastModified) return false
        if (feedfileId != that.feedfileId) return false
        if (feedfileType != that.feedfileType) return false
        if (progressPercent != that.progressPercent) return false
        if (size != that.size) return false
        if (soFar != that.soFar) return false
        if (statusMsg != that.statusMsg) return false
        if (destination != that.destination) return false
        if (password != that.password) return false
        if (source != that.source) return false
        if (title != that.title) return false
        if (username != that.username) return false
        if (mediaEnqueued != that.mediaEnqueued) return false
        if (initiatedByUser != that.initiatedByUser) return false
        return true
    }

    override fun hashCode(): Int {
        var result = destination.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (username?.hashCode() ?: 0)
        result = 31 * result + (password?.hashCode() ?: 0)
        result = 31 * result + (lastModified?.hashCode() ?: 0)
        result = 31 * result + (feedfileId xor (feedfileId ushr 32)).toInt()
        result = 31 * result + feedfileType
        result = 31 * result + arguments!!.hashCode()
        result = 31 * result + progressPercent
        result = 31 * result + (soFar xor (soFar ushr 32)).toInt()
        result = 31 * result + (size xor (size ushr 32)).toInt()
        result = 31 * result + statusMsg
        result = 31 * result + (if (mediaEnqueued) 1 else 0)
        return result
    }

    fun getDestination(): String {
        return destination
    }

    fun getSource(): String {
        return source
    }

    fun getTitle(): String {
        return title
    }

    fun getFeedfileId(): Long {
        return feedfileId
    }

    fun getFeedfileType(): Int {
        return feedfileType
    }

    fun getProgressPercent(): Int {
        return progressPercent
    }

    fun setProgressPercent(progressPercent: Int) {
        this.progressPercent = progressPercent
    }

    fun getSoFar(): Long {
        return soFar
    }

    fun setSoFar(soFar: Long) {
        this.soFar = soFar
    }

    fun getSize(): Long {
        return size
    }

    fun setSize(size: Long) {
        this.size = size
    }

    fun setStatusMsg(statusMsg: Int) {
        this.statusMsg = statusMsg
    }

    fun getUsername(): String? {
        return username
    }

    fun getPassword(): String? {
        return password
    }

    fun setUsername(username: String?) {
        this.username = username
    }

    fun setPassword(password: String?) {
        this.password = password
    }

    fun setLastModified(lastModified: String?): DownloadRequest {
        this.lastModified = lastModified
        return this
    }

    fun getLastModified(): String? {
        return lastModified
    }

    fun getArguments(): Bundle? {
        return arguments
    }

    companion object {
        const val REQUEST_ARG_PAGE_NR = "page"

        private fun nonNullString(str: String?): String {
            return str ?: ""
        }

        private fun nullIfEmpty(str: String?): String? {
            return if (TextUtils.isEmpty(str)) null else str
        }

        @JvmField
        val CREATOR: Parcelable.Creator<DownloadRequest> = object : Parcelable.Creator<DownloadRequest> {
            override fun createFromParcel(source: Parcel): DownloadRequest {
                return DownloadRequest(source)
            }

            override fun newArray(size: Int) = arrayOfNulls<DownloadRequest>(size)
        }
    }
}
