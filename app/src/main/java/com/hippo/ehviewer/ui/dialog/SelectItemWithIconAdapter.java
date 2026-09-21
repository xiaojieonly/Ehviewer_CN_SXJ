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

package com.hippo.ehviewer.ui.dialog;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import androidx.appcompat.content.res.AppCompatResources;
import com.hippo.ehviewer.R;

public class SelectItemWithIconAdapter extends BaseAdapter {

  private Context context;
  private LayoutInflater inflater;

  private CharSequence[] texts;
  private int[] icons;

  public SelectItemWithIconAdapter(Context context, CharSequence[] texts, int[] icons) {
    int count = texts.length;
    if (count != icons.length) {
      throw new IllegalArgumentException("Length conflict");
    }
    this.context = context;
    this.inflater = LayoutInflater.from(context);
    this.texts = texts;
    this.icons = icons;
  }

  @Override
  public int getCount() {
    return texts.length;
  }

  @Override
  public Object getItem(int position) {
    return texts[position];
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    if (convertView == null) {
      convertView = inflater.inflate(R.layout.dialog_item_select_with_icon, parent, false);
    }
    TextView view = (TextView) convertView;

    view.setText(texts[position]);

    Drawable icon = AppCompatResources.getDrawable(context, icons[position]);
    icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
    view.setCompoundDrawables(icon, null, null, null);

    return view;
  }
}
