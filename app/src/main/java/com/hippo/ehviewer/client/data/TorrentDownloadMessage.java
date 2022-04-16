package com.hippo.ehviewer.client.data;

import android.os.Parcel;
import android.os.Parcelable;

public class TorrentDownloadMessage implements Parcelable {

    public String path;
    public String name;
    public int progress;
    public boolean failed;

    public TorrentDownloadMessage() {
    }

    protected TorrentDownloadMessage(Parcel in) {
        this.failed = in.readByte() != 0;
        this.name = in.readString();
        this.path = in.readString();
        this.progress = in.readInt();
    }

    public static final Creator<TorrentDownloadMessage> CREATOR = new Creator<TorrentDownloadMessage>() {
        @Override
        public TorrentDownloadMessage createFromParcel(Parcel in) {
            return new TorrentDownloadMessage(in);
        }

        @Override
        public TorrentDownloadMessage[] newArray(int size) {
            return new TorrentDownloadMessage[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.name);
        dest.writeString(this.path);
        dest.writeByte((byte)(this.failed ? 1:0));
        dest.writeInt(this.progress);
    }
}
