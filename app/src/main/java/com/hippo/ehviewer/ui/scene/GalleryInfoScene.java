/*
 * Copyright 2016 Hippo Seven
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

package com.hippo.ehviewer.ui.scene;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.hippo.android.resource.AttrResources;
import com.hippo.easyrecyclerview.EasyRecyclerView;
import com.hippo.easyrecyclerview.LinearDividerItemDecoration;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.UrlOpener;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.client.data.GalleryDetail;
import com.hippo.ripple.Ripple;
import com.hippo.yorozuya.AssertUtils;
import com.hippo.yorozuya.LayoutUtils;
import com.hippo.yorozuya.ViewUtils;
import java.util.ArrayList;

public final class GalleryInfoScene extends ToolbarScene implements EasyRecyclerView.OnItemClickListener {

    public static final String KEY_GALLERY_DETAIL = "gallery_detail";
    public static final String KEY_KEYS = "keys";
    public static final String KEY_VALUES = "values";

    private static final int INDEX_URL = 3;
    private static final int INDEX_PARENT = 10;

    /*---------------
     Whole life cycle
     ---------------*/
    @Nullable
    private ArrayList<String> mKeys;
    @Nullable
    private ArrayList<String> mValues;

    @Nullable
    private EasyRecyclerView mRecyclerView;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            onInit();
        } else {
            onRestore(savedInstanceState);
        }
    }

    private void handlerArgs(Bundle args) {
        if (args == null) {
            return;
        }
        GalleryDetail gd = args.getParcelable(KEY_GALLERY_DETAIL);
        if (gd == null) {
            return;
        }
        if (mKeys == null || mValues == null) {
            return;
        }

        Resources resources = getResources2();
        AssertUtils.assertNotNull(resources);
        mKeys.add(resources.getString(R.string.header_key));
        mValues.add(resources.getString(R.string.header_value));
        mKeys.add(resources.getString(R.string.key_gid));
        mValues.add(Long.toString(gd.gid));
        mKeys.add(resources.getString(R.string.key_token));
        mValues.add(gd.token);
        mKeys.add(resources.getString(R.string.key_url));
        mValues.add(EhUrl.getGalleryDetailUrl(gd.gid, gd.token));
        mKeys.add(resources.getString(R.string.key_title));
        mValues.add(gd.title);
        mKeys.add(resources.getString(R.string.key_title_jpn));
        mValues.add(gd.titleJpn);
        mKeys.add(resources.getString(R.string.key_thumb));
        mValues.add(gd.thumb);
        mKeys.add(resources.getString(R.string.key_category));
        mValues.add(EhUtils.getCategory(gd.category));
        mKeys.add(resources.getString(R.string.key_uploader));
        mValues.add(gd.uploader);
        mKeys.add(resources.getString(R.string.key_posted));
        mValues.add(gd.posted);
        mKeys.add(resources.getString(R.string.key_parent));
        mValues.add(gd.parent);
        mKeys.add(resources.getString(R.string.key_visible));
        mValues.add(gd.visible);
        mKeys.add(resources.getString(R.string.key_language));
        mValues.add(gd.language);
        mKeys.add(resources.getString(R.string.key_pages));
        mValues.add(Integer.toString(gd.pages));
        mKeys.add(resources.getString(R.string.key_size));
        mValues.add(gd.size);
        mKeys.add(resources.getString(R.string.key_favorite_count));
        mValues.add(Integer.toString(gd.favoriteCount));
        mKeys.add(resources.getString(R.string.key_favorited));
        mValues.add(Boolean.toString(gd.isFavorited));
        mKeys.add(resources.getString(R.string.key_rating_count));
        mValues.add(Integer.toString(gd.ratingCount));
        mKeys.add(resources.getString(R.string.key_rating));
        mValues.add(Float.toString(gd.rating));
        mKeys.add(resources.getString(R.string.key_torrents));
        mValues.add(Integer.toString(gd.torrentCount));
        mKeys.add(resources.getString(R.string.key_torrent_url));
        mValues.add(gd.torrentUrl);
        mKeys.add(resources.getString(R.string.favorite_name));
        mValues.add(gd.favoriteName);
    }

    protected void onInit() {
        mKeys = new ArrayList<>();
        mValues = new ArrayList<>();
        handlerArgs(getArguments());
    }

    protected void onRestore(@NonNull Bundle savedInstanceState) {
        mKeys = savedInstanceState.getStringArrayList(KEY_KEYS);
        mValues = savedInstanceState.getStringArrayList(KEY_VALUES);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(KEY_KEYS, mKeys);
        outState.putStringArrayList(KEY_VALUES, mValues);
    }

    @SuppressWarnings("deprecation")
    @Nullable
    @Override
    public View onCreateView3(LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.scene_gallery_info, container, false);

        Context context = getContext2();
        AssertUtils.assertNotNull(context);

        mRecyclerView = (EasyRecyclerView) ViewUtils.$$(view, R.id.recycler_view);
        InfoAdapter adapter = new InfoAdapter();
        mRecyclerView.setAdapter(adapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
        LinearDividerItemDecoration decoration = new LinearDividerItemDecoration(
                LinearDividerItemDecoration.VERTICAL,
                AttrResources.getAttrColor(context, R.attr.dividerColor),
                LayoutUtils.dp2pix(context, 1));
        decoration.setPadding(context.getResources().getDimensionPixelOffset(R.dimen.keyline_margin));
        mRecyclerView.addItemDecoration(decoration);
        mRecyclerView.setSelector(Ripple.generateRippleDrawable(context, !AttrResources.getAttrBoolean(context, R.attr.isLightTheme), new ColorDrawable(Color.TRANSPARENT)));
        mRecyclerView.setClipToPadding(false);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setOnItemClickListener(this);
        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setTitle(R.string.gallery_info);
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (null != mRecyclerView) {
            mRecyclerView.stopScroll();
            mRecyclerView = null;
        }
    }

    @Override
    public boolean onItemClick(EasyRecyclerView parent, View view, int position, long id) {
        Context context = getContext2();
        if (null != context && 0 != position && null != mValues) {
            if (position == INDEX_PARENT) {
                UrlOpener.openUrl(context, mValues.get(position), true);
            } else {
                ClipboardManager cmb = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                cmb.setPrimaryClip(ClipData.newPlainText(null, mValues.get(position)));

                if (position == INDEX_URL) {
                    // Save it to avoid detect the gallery
                    Settings.putClipboardTextHashCode(mValues.get(position).hashCode());
                }

                showTip(R.string.copied_to_clipboard, LENGTH_SHORT);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onNavigationClick() {
        onBackPressed();
    }

    private static class InfoHolder extends RecyclerView.ViewHolder {

        private final TextView key;
        private final TextView value;

        public InfoHolder(View itemView) {
            super(itemView);

            key = (TextView) itemView.findViewById(R.id.key);
            value = (TextView) itemView.findViewById(R.id.value);
        }
    }

    private class InfoAdapter extends RecyclerView.Adapter<InfoHolder> {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_DATA = 1;

        private final LayoutInflater mInflater;

        public InfoAdapter() {
            mInflater = getLayoutInflater2();
            AssertUtils.assertNotNull(mInflater);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return TYPE_HEADER;
            } else {
                return TYPE_DATA;
            }
        }

        @Override
        public InfoHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new InfoHolder(mInflater.inflate(viewType == TYPE_HEADER ?
                    R.layout.item_gallery_info_header : R.layout.item_gallery_info_data, parent, false));
        }

        @Override
        public void onBindViewHolder(InfoHolder holder, int position) {
            if (mKeys != null && mValues != null) {
                holder.key.setText(mKeys.get(position));
                holder.value.setText(mValues.get(position));
                holder.itemView.setEnabled(position != 0);
            }
        }

        @Override
        public int getItemCount() {
            return mKeys == null || mValues == null ? 0 : Math.min(mKeys.size(), mValues.size());
        }
    }
}
