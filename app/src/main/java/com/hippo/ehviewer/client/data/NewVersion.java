package com.hippo.ehviewer.client.data;

import android.os.Parcel;
import android.os.Parcelable;

public class NewVersion implements Parcelable {
    public String versionUrl;
    public String versionName;

    public NewVersion() {

    }

    protected NewVersion(Parcel in) {
        this.versionName = in.readString();
        this.versionUrl = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.versionName);
        dest.writeString(this.versionUrl);
    }

    public static final Creator<NewVersion> CREATOR = new Creator<>() {
        @Override
        public NewVersion createFromParcel(Parcel in) {
            return new NewVersion(in);
        }

        @Override
        public NewVersion[] newArray(int size) {
            return new NewVersion[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }
}
