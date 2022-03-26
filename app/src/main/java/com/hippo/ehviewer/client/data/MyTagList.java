package com.hippo.ehviewer.client.data;


import android.os.Parcel;
import android.os.Parcelable;

import com.hippo.ehviewer.client.data.subscription.Subscription;

public class MyTagList implements Parcelable {

    Subscription[] subscriptions;

    public MyTagList() {
    }

    protected MyTagList(Parcel in) {
        this.subscriptions = (Subscription[]) in.readArray(Subscription.class.getClassLoader());
    }

    public static final Creator<MyTagList> CREATOR = new Creator<MyTagList>() {
        @Override
        public MyTagList createFromParcel(Parcel in) {
            return new MyTagList(in);
        }

        @Override
        public MyTagList[] newArray(int size) {
            return new MyTagList[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeArray(this.subscriptions);
    }
}
