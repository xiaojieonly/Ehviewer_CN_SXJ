package com.hippo.ehviewer.client.data;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class Tag implements Parcelable {
    public String english;
    public String chinese;

    public Tag(String content){
        String[] cArray = content.split("\r");
        chinese = new String(Base64.decode(cArray[1], Base64.DEFAULT), StandardCharsets.UTF_8);
        english = content;
    }

    public Tag(String english,String chinese){
        this.chinese = chinese;
        this.english = english;
    }

    protected Tag(Parcel in) {
    }

    public static final Creator<Tag> CREATOR = new Creator<Tag>() {
        @Override
        public Tag createFromParcel(Parcel in) {
            return new Tag(in);
        }

        @Override
        public Tag[] newArray(int size) {
            return new Tag[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
    }

    public boolean involve(String chars){
        if (english.contains(chars)){
            return true;
        }
        return chinese.contains(chars);
    }
}
