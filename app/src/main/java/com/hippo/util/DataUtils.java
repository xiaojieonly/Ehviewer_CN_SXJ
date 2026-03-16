package com.hippo.util;

import android.os.Parcel;
import android.os.Parcelable;

import com.hippo.ehviewer.dao.DownloadInfo;

public class DataUtils {
    public static <T> T copy(Parcelable input){
        Parcel parcel = null;
        try{
            parcel = Parcel.obtain();
            parcel.writeParcelable(input,0);
            parcel.setDataPosition(0);
            return parcel.readParcelable(input.getClass().getClassLoader());
        }finally {
            parcel.recycle();
        }
    }

}
