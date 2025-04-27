package com.hippo.ehviewer.client.data

import android.os.Parcel
import android.os.Parcelable

class TorrentDownloadMessage : Parcelable {
    @JvmField
    var path: String? = null
    @JvmField
    var dir: String? = null
    @JvmField
    var name: String? = null
    @JvmField
    var progress: Int = 0
    @JvmField
    var failed: Boolean = false

    constructor()

    protected constructor(`in`: Parcel) {
        this.failed = `in`.readByte().toInt() != 0
        this.name = `in`.readString()
        this.path = `in`.readString()
        this.dir = `in`.readString()
        this.progress = `in`.readInt()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(this.name)
        dest.writeString(this.path)
        dest.writeString(this.dir)
        dest.writeByte((if (this.failed) 1 else 0).toByte())
        dest.writeInt(this.progress)
    }

    companion object CREATOR : Parcelable.Creator<TorrentDownloadMessage> {
        override fun createFromParcel(parcel: Parcel): TorrentDownloadMessage {
            return TorrentDownloadMessage(parcel)
        }

        override fun newArray(size: Int): Array<TorrentDownloadMessage?> {
            return arrayOfNulls(size)
        }
    }
}
