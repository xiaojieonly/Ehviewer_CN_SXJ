package com.hippo.ehviewer.client.data.subscription;

import android.os.Parcel;
import android.os.Parcelable;

public class Subscription implements Parcelable {

    String tagName;
    String color;
    boolean watch;
    boolean hide;
    int tagWeight;

    public Subscription(){}

    protected Subscription(Parcel in) {
        this.tagName = in.readString();
        this.color = in.readString();
        this.watch = in.readByte()!= 0;
        this.hide = in.readByte()!= 0;
        this.tagWeight = in.readInt();
    }

    public static final Creator<Subscription> CREATOR = new Creator<Subscription>() {
        @Override
        public Subscription createFromParcel(Parcel in) {
            return new Subscription(in);
        }

        @Override
        public Subscription[] newArray(int size) {
            return new Subscription[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.tagName);
        dest.writeString(this.color);
        dest.writeByte(this.hide ? (byte) 1 : (byte) 0);
        dest.writeByte(this.watch ? (byte) 1 : (byte) 0);
        dest.writeInt(this.tagWeight);
    }
}
