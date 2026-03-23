package com.hippo.util

import android.os.Parcel
import android.os.Parcelable

object DataUtils {
    @JvmStatic
    fun <T> copy(input: Parcelable): T? {
        var parcel: Parcel? = null
        try {
            parcel = Parcel.obtain()
            parcel.writeParcelable(input, 0)
            parcel.setDataPosition(0)
            return parcel.readParcelable(input.javaClass.getClassLoader())
        } finally {
            parcel!!.recycle()
        }
    }
}
