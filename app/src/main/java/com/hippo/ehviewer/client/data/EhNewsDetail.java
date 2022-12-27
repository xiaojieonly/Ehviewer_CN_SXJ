package com.hippo.ehviewer.client.data;

import android.os.Parcel;
import android.os.Parcelable;

public class EhNewsDetail implements Parcelable {
    public String eventPane;

    public EhNewsDetail(){
        eventPane = null;
    }

    protected EhNewsDetail(Parcel in) {
        this.eventPane = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.eventPane);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<EhNewsDetail> CREATOR = new Creator<EhNewsDetail>() {
        @Override
        public EhNewsDetail createFromParcel(Parcel in) {
            return new EhNewsDetail(in);
        }

        @Override
        public EhNewsDetail[] newArray(int size) {
            return new EhNewsDetail[size];
        }
    };


}
