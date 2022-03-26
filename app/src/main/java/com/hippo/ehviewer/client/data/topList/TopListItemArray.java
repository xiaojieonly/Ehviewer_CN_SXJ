package com.hippo.ehviewer.client.data.topList;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

public class TopListItemArray implements Parcelable {

    public TopListItem[] itemArray;
//    String name;

    public TopListItemArray(){}

    protected TopListItemArray(Parcel in) {
        this.itemArray = (TopListItem[]) in.readArray(TopListItem.class.getClassLoader());
    }

    public static final Creator<TopListItemArray> CREATOR = new Creator<TopListItemArray>() {
        @Override
        public TopListItemArray createFromParcel(Parcel in) {
            return new TopListItemArray(in);
        }

        @Override
        public TopListItemArray[] newArray(int size) {
            return new TopListItemArray[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelableArray(this.itemArray,flags);
    }

    public int length(){
        return itemArray.length;
    }

    public TopListItem get(int index){
        return itemArray[index];
    }
}
