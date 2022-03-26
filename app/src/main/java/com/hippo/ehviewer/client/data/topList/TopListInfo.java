package com.hippo.ehviewer.client.data.topList;

import android.os.Parcel;
import android.os.Parcelable;

public class TopListInfo implements Parcelable {

    public TopListItemArray allTimeTopList;
    public TopListItemArray pastYearTopList;
    public TopListItemArray pastMonthTopList;
    public TopListItemArray yesterdayTopList;
    public String title;

    private ClassLoader classLoader;


    public TopListInfo() {
        classLoader = TopListItemArray.class.getClassLoader();
    }

    protected TopListInfo(Parcel in) {
        this.allTimeTopList = in.readParcelable(classLoader);
        this.pastYearTopList = in.readParcelable(classLoader);
        this.pastMonthTopList = in.readParcelable(classLoader);
        this.yesterdayTopList = in.readParcelable(classLoader);
        this.title = in.readString();
    }

    public static final Creator<TopListInfo> CREATOR = new Creator<TopListInfo>() {
        @Override
        public TopListInfo createFromParcel(Parcel in) {
            return new TopListInfo(in);
        }

        @Override
        public TopListInfo[] newArray(int size) {
            return new TopListInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.allTimeTopList, flags);
        dest.writeParcelable(this.pastYearTopList, flags);
        dest.writeParcelable(this.pastMonthTopList, flags);
        dest.writeParcelable(this.yesterdayTopList, flags);
        dest.writeString(this.title);
    }

    public TopListItemArray get(int index) {
        switch (index) {
            case 0:
                return yesterdayTopList;
            case 1:
                return pastMonthTopList;
            case 2:
                return pastYearTopList;
            case 3:
                return allTimeTopList;
            default:
                return null;
        }
    }

    public int size() {
        return 4;
    }
}
