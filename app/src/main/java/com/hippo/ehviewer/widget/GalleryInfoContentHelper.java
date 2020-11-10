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

package com.hippo.ehviewer.widget;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Parcelable;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.FavouriteStatusRouter;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.widget.ContentLayout;
import com.hippo.yorozuya.IntIdGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class GalleryInfoContentHelper extends ContentLayout.ContentHelper<GalleryInfo> {

  private static final String KEY_DATA_MAP = "data_map";

  @SuppressLint("UseSparseArrays")
  private Map<Long, GalleryInfo> map = new HashMap<>();
  private FavouriteStatusRouter.Listener listener;

  public GalleryInfoContentHelper() {
    listener = (gid, slot) -> {
      GalleryInfo info = map.get(gid);
      if (info != null) {
        info.favoriteSlot = slot;
      }
    };
    EhApplication.getFavouriteStatusRouter().addListener(listener);
  }

  public void destroy() {
    EhApplication.getFavouriteStatusRouter().removeListener(listener);
  }

  @Override
  protected void onAddData(GalleryInfo data) {
    map.put(data.gid, data);
  }

  @Override
  protected void onAddData(List<GalleryInfo> data) {
    for (GalleryInfo info : data) {
      map.put(info.gid, info);
    }
  }

  @Override
  protected void onRemoveData(GalleryInfo data) {
    map.remove(data.gid);
  }

  @Override
  protected void onRemoveData(List<GalleryInfo> data) {
    for (GalleryInfo info : data) {
      map.remove(info.gid);
    }
  }

  @Override
  protected void onClearData() {
    map.clear();
  }

  @Override
  protected Parcelable saveInstanceState(Parcelable superState) {
    Bundle bundle = (Bundle) super.saveInstanceState(superState);

    // TODO It's a bad design
    FavouriteStatusRouter router = EhApplication.getFavouriteStatusRouter();
    int id = router.saveDataMap(map);
    bundle.putInt(KEY_DATA_MAP, id);

    return bundle;
  }

  @Override
  protected Parcelable restoreInstanceState(Parcelable state) {
    Bundle bundle = (Bundle) state;

    int id = bundle.getInt(KEY_DATA_MAP, IntIdGenerator.INVALID_ID);
    if (id != IntIdGenerator.INVALID_ID) {
      FavouriteStatusRouter router = EhApplication.getFavouriteStatusRouter();
      Map<Long, GalleryInfo> map = router.restoreDataMap(id);
      if (map != null) {
        this.map = map;
      }
    }

    return super.restoreInstanceState(state);
  }
}
