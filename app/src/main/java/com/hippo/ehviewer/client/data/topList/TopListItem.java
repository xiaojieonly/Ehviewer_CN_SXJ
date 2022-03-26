package com.hippo.ehviewer.client.data.topList;

import android.os.Parcel;
import android.os.Parcelable;

public class TopListItem implements Parcelable {

    public String href;
    public String gid;
    public String token;
    public String tag;
    public String value;

    public TopListItem() {

    }

    protected TopListItem(Parcel in) {
        this.gid = in.readString();
        this.href = in.readString();
        this.token = in.readString();
        this.tag = in.readString();
        this.value = in.readString();
    }

    public static final Creator<TopListItem> CREATOR = new Creator<TopListItem>() {
        @Override
        public TopListItem createFromParcel(Parcel in) {
            return new TopListItem(in);
        }

        @Override
        public TopListItem[] newArray(int size) {
            return new TopListItem[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.gid);
        dest.writeString(this.href);
        dest.writeString(this.tag);
        dest.writeString(this.token);
        dest.writeString(this.value);
    }
}
