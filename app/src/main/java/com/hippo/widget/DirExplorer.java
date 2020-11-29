/*
 * Copyright 2015 Hippo Seven
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

package com.hippo.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Environment;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.hippo.android.resource.AttrResources;
import com.hippo.easyrecyclerview.EasyRecyclerView;
import com.hippo.easyrecyclerview.LinearDividerItemDecoration;
import com.hippo.ehviewer.R;
import com.hippo.ripple.Ripple;
import com.hippo.yorozuya.LayoutUtils;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DirExplorer extends EasyRecyclerView implements EasyRecyclerView.OnItemClickListener {

    private static final DirFilter DIR_FILTER = new DirFilter();
    private static final FileSort FILE_SORT = new FileSort();

    private static final File PARENT_DIR = null;
    private static final String PARENT_DIR_NAME = "..";

    private File mCurrentFile;
    private final List<File> mFiles = new ArrayList<>();

    private DirAdapter mAdapter;

    private OnChangeDirListener mOnChangeDirListener;

    public DirExplorer(Context context) {
        super(context);
        init(context);
    }

    public DirExplorer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DirExplorer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        mAdapter = new DirAdapter();
        setAdapter(mAdapter);
        setLayoutManager(new LinearLayoutManager(context));
        LinearDividerItemDecoration decoration = new LinearDividerItemDecoration(
                LinearDividerItemDecoration.VERTICAL, AttrResources.getAttrColor(context, R.attr.dividerColor),
                LayoutUtils.dp2pix(context, 1));
        decoration.setShowLastDivider(true);
        addItemDecoration(decoration);
        setSelector(Ripple.generateRippleDrawable(context, !AttrResources.getAttrBoolean(context, R.attr.isLightTheme), new ColorDrawable(Color.TRANSPARENT)));
        setOnItemClickListener(this);

        mCurrentFile = Environment.getExternalStorageDirectory();
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || mCurrentFile == null) {
            mCurrentFile = new File("/");
        }
        updateFileList();
    }

    public void setOnChangeDirListener(OnChangeDirListener listener) {
        mOnChangeDirListener = listener;
    }

    public void updateFileList() {
        File[] files = mCurrentFile.listFiles(DIR_FILTER);

        mFiles.clear();
        if (mCurrentFile.getParent() != null) {
            mFiles.add(PARENT_DIR);
        }
        if (files != null) {
            Collections.addAll(mFiles, files);
        }
        // sort
        Collections.sort(mFiles, FILE_SORT);
    }

    public File getCurrentFile() {
        return mCurrentFile;
    }

    public void setCurrentFile(File file) {
        if (file != null && file.isDirectory()) {
            mCurrentFile = file;
            updateFileList();
            mAdapter.notifyDataSetChanged();

            if (mOnChangeDirListener != null) {
                mOnChangeDirListener.onChangeDir(mCurrentFile);
            }
        }
    }

    @Override
    public boolean onItemClick(EasyRecyclerView parent, View view, int position, long id) {
        File file = mFiles.get(position);
        if (file == PARENT_DIR) {
            file = mCurrentFile.getParentFile();
        }
        setCurrentFile(file);
        return true;
    }

    private class DirHolder extends ViewHolder {

        public TextView textView;

        public DirHolder(View itemView) {
            super(itemView);
            textView = (TextView) itemView;
        }
    }

    private class DirAdapter extends Adapter<DirHolder> {

        @Override
        public DirHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new DirHolder( LayoutInflater.from(getContext()).inflate(R.layout.item_dir_explorer, parent, false));
        }

        @Override
        public void onBindViewHolder(DirHolder holder, int position) {
            File file = mFiles.get(position);
            holder.textView.setText(file == PARENT_DIR ? PARENT_DIR_NAME : file.getName());
        }

        @Override
        public int getItemCount() {
            return mFiles.size();
        }
    }

    static class DirFilter implements FileFilter {
        @Override
        public boolean accept(File pathname) {
            return pathname.isDirectory();
        }
    }

    static class FileSort implements Comparator<File> {
        @Override
        public int compare(File lhs, File rhs) {
            if (lhs == null) {
                return Integer.MIN_VALUE;
            } else if (rhs == null) {
                return Integer.MAX_VALUE;
            } else {
                return lhs.getName().compareToIgnoreCase(rhs.getName());
            }
        }
    }

    public interface OnChangeDirListener {

        void onChangeDir(File dir);
    }
}
