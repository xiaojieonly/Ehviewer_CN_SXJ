/*
 * Copyright 2019 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.client.data;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;

public class GalleryCommentList implements Parcelable {

  public GalleryComment[] comments;
  public boolean hasMore;

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelableArray(comments, flags);
    dest.writeByte(hasMore ? (byte) 1 : (byte) 0);
  }

  public GalleryCommentList(GalleryComment[] comments, boolean hasMore) {
    this.comments = comments;
    this.hasMore = hasMore;
  }

  protected GalleryCommentList(Parcel in) {
    Parcelable[] array = in.readParcelableArray(getClass().getClassLoader());
    if (array != null) {
      comments = Arrays.copyOf(array, array.length, GalleryComment[].class);
    } else {
      comments = null;
    }
    hasMore = in.readByte() != 0;
  }

  public static final Creator<GalleryCommentList> CREATOR = new Creator<GalleryCommentList>() {
    @Override
    public GalleryCommentList createFromParcel(Parcel in) {
      return new GalleryCommentList(in);
    }

    @Override
    public GalleryCommentList[] newArray(int size) {
      return new GalleryCommentList[size];
    }
  };
}
