/*
 * Copyright 2018 Hippo Seven
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

package com.hippo.app;

import android.content.Context;
import android.preference.PreferenceActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.hippo.ehviewer.R;
import java.util.ArrayList;
import java.util.List;

public abstract class PrettyPreferenceActivity extends AppCompatPreferenceActivity {

  @Override
  public void setListAdapter(ListAdapter adapter) {
    if (adapter == null) {
      super.setListAdapter(null);
      return;
    }

    int count = adapter.getCount();
    List<PreferenceActivity.Header> headers = new ArrayList<>(count);
    for (int i = 0; i < count; ++i) {
      headers.add((PreferenceActivity.Header) adapter.getItem(i));
    }

    super.setListAdapter(new HeaderAdapter(this, headers, R.layout.item_preference_header, true));
  }

  private static class HeaderAdapter extends ArrayAdapter<PreferenceActivity.Header> {
    private static class HeaderViewHolder {
      ImageView icon;
      TextView title;
      TextView summary;
    }

    private LayoutInflater mInflater;
    private int mLayoutResId;
    private boolean mRemoveIconIfEmpty;

    private HeaderAdapter(Context context, List<PreferenceActivity.Header> objects, int layoutResId,
        boolean removeIconBehavior) {
      super(context, 0, objects);
      mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      mLayoutResId = layoutResId;
      mRemoveIconIfEmpty = removeIconBehavior;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
      HeaderViewHolder holder;
      View view;

      if (convertView == null) {
        view = mInflater.inflate(mLayoutResId, parent, false);
        holder = new HeaderViewHolder();
        holder.icon = view.findViewById(android.R.id.icon);
        holder.title = view.findViewById(android.R.id.title);
        holder.summary = view.findViewById(android.R.id.summary);
        view.setTag(holder);
      } else {
        view = convertView;
        holder = (HeaderViewHolder) view.getTag();
      }

      // All view fields must be updated every time, because the view may be recycled
      PreferenceActivity.Header header = getItem(position);
      if (mRemoveIconIfEmpty) {
        if (header.iconRes == 0) {
          holder.icon.setVisibility(View.GONE);
        } else {
          holder.icon.setVisibility(View.VISIBLE);
          holder.icon.setImageResource(header.iconRes);
        }
      } else {
        holder.icon.setImageResource(header.iconRes);
      }
      holder.title.setText(header.getTitle(getContext().getResources()));
      CharSequence summary = header.getSummary(getContext().getResources());
      if (!TextUtils.isEmpty(summary)) {
        holder.summary.setVisibility(View.VISIBLE);
        holder.summary.setText(summary);
      } else {
        holder.summary.setVisibility(View.GONE);
      }

      return view;
    }
  }
}
