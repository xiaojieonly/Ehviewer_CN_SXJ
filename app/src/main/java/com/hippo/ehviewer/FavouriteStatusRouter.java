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

package com.hippo.ehviewer;

import android.annotation.SuppressLint;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.yorozuya.IntIdGenerator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FavouriteStatusRouter {

  private static final String KEY_DATA_MAP_NEXT_ID = "data_map_next_id";

  private final IntIdGenerator idGenerator = new IntIdGenerator(Settings.getInt(KEY_DATA_MAP_NEXT_ID, 0));
  @SuppressLint("UseSparseArrays")
  private final HashMap<Integer, Map<Long, GalleryInfo>> maps = new HashMap<>();

  private List<Listener> listeners = new ArrayList<>();

  public int saveDataMap(Map<Long, GalleryInfo> map) {
    int id = idGenerator.nextId();
    maps.put(id, map);
    Settings.putInt(KEY_DATA_MAP_NEXT_ID, idGenerator.nextId());
    return id;
  }

  public Map<Long, GalleryInfo> restoreDataMap(int id) {
    return maps.remove(id);
  }

  public void modifyFavourites(long gid, int slot) {
    for (Map<Long, GalleryInfo> map : maps.values()) {
      GalleryInfo info = map.get(gid);
      if (info != null) {
        info.favoriteSlot = slot;
      }
    }

    for (Listener listener : listeners) {
      listener.onModifyFavourites(gid, slot);
    }
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  public interface Listener {
    void onModifyFavourites(long gid, int slot);
  }
}
