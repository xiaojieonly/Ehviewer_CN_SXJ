package com.hippo.ehviewer.client.data.userTag;


import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

public class UserTagList implements Parcelable {

    public List<UserTag> userTags;
    public int stageId;
    public UserTagList() {
    }

    protected UserTagList(Parcel in) {
        this.userTags = in.createTypedArrayList(UserTag.CREATOR);
    }

    public static final Creator<UserTagList> CREATOR = new Creator<UserTagList>() {
        @Override
        public UserTagList createFromParcel(Parcel in) {
            return new UserTagList(in);
        }

        @Override
        public UserTagList[] newArray(int size) {
            return new UserTagList[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(this.userTags);
    }

    public UserTag get(int index){
        return userTags.get(index);
    }
}
