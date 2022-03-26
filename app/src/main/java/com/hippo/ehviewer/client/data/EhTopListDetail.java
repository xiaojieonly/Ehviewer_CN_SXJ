package com.hippo.ehviewer.client.data;

import android.os.Parcel;
import android.os.Parcelable;

import com.hippo.ehviewer.client.data.topList.TopListInfo;

public class EhTopListDetail implements Parcelable {

    public String title;
    public TopListInfo galleryTopListInfo;
    public TopListInfo uploaderTopListInfo;
    public TopListInfo taggingTopListInfo;
    public TopListInfo hentaiHomeTopListInfo;
    public TopListInfo ehTrackerTopListInfo;
    public TopListInfo cleanUpTopListInfo;
    public TopListInfo ratingAndReviewingTopListInfo;

    private ClassLoader classLoader;


    public EhTopListDetail(){
        classLoader = TopListInfo.class.getClassLoader();
    }

    protected EhTopListDetail(Parcel in) {
        this.galleryTopListInfo = in.readParcelable(classLoader);
        this.uploaderTopListInfo = in.readParcelable(classLoader);
        this.taggingTopListInfo = in.readParcelable(classLoader);
        this.hentaiHomeTopListInfo = in.readParcelable(classLoader);
        this.ehTrackerTopListInfo = in.readParcelable(classLoader);
        this.cleanUpTopListInfo = in.readParcelable(classLoader);
        this.ratingAndReviewingTopListInfo = in.readParcelable(classLoader);
    }

    public static final Creator<EhTopListDetail> CREATOR = new Creator<EhTopListDetail>() {
        @Override
        public EhTopListDetail createFromParcel(Parcel in) {
            return new EhTopListDetail(in);
        }

        @Override
        public EhTopListDetail[] newArray(int size) {
            return new EhTopListDetail[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.galleryTopListInfo,flags);
        dest.writeParcelable(this.uploaderTopListInfo,flags);
        dest.writeParcelable(this.taggingTopListInfo,flags);
        dest.writeParcelable(this.hentaiHomeTopListInfo,flags);
        dest.writeParcelable(this.ehTrackerTopListInfo,flags);
        dest.writeParcelable(this.cleanUpTopListInfo,flags);
        dest.writeParcelable(this.ratingAndReviewingTopListInfo,flags);
    }
    public TopListInfo get(int index){
        switch (index){
            case 0:
                return galleryTopListInfo;
            case 1:
                return uploaderTopListInfo;
            case 2:
                return taggingTopListInfo;
            case 3:
                return hentaiHomeTopListInfo;
            case 4:
                return ehTrackerTopListInfo;
            case 5:
                return cleanUpTopListInfo;
            case 6:
                return ratingAndReviewingTopListInfo;
            default:
                return null;
        }
    }
}
