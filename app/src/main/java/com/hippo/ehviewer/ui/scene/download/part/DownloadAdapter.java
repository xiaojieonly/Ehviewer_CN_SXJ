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

package com.hippo.ehviewer.ui.scene.download.part;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.hippo.android.resource.AttrResources;
import com.hippo.easyrecyclerview.EasyRecyclerView;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhCacheKeyFactory;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.download.DownloadService;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.ehviewer.ui.GalleryActivity;
import com.hippo.ehviewer.ui.scene.TransitionNameFactory;
import com.hippo.ehviewer.ui.scene.download.DownloadsScene;
import com.hippo.ehviewer.ui.scene.gallery.detail.GalleryDetailScene;
import com.hippo.ehviewer.ui.scene.gallery.list.EnterGalleryDetailTransaction;
import com.hippo.ehviewer.widget.SimpleRatingView;
import com.hippo.lib.yorozuya.AssertUtils;
import com.hippo.lib.yorozuya.ViewUtils;
import com.hippo.ripple.Ripple;
import com.hippo.scene.Announcer;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.hippo.widget.LoadImageView;

// 拖拽排序相关导入
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemAdapter;
import com.h6ah4i.android.widget.advrecyclerview.draggable.ItemDraggableRange;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractDraggableItemViewHolder;

import java.util.List;
import java.util.Map;

/**
 * 下载列表适配器
 */
public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.DownloadHolder>
        implements DraggableItemAdapter<DownloadAdapter.DownloadHolder> {

    private final LayoutInflater mInflater;
    private final int mListThumbWidth;
    private final int mListThumbHeight;
    private final DownloadsScene mScene;
    private final DownloadAdapterCallback mCallback;

    private View movedItem = null;

    public interface DownloadAdapterCallback {
        int getIndexPage();
        int getPageSize();
        int getPaginationSize();
        boolean isCanPagination();
        int positionInList(int position);
        int listIndexInPage(int position);
        List<DownloadInfo> getList();
        Map<Long, SpiderInfo> getSpiderInfoMap();
        DownloadManager getDownloadManager();
        EasyRecyclerView getRecyclerView();
    }

    public DownloadAdapter(DownloadsScene scene, DownloadAdapterCallback callback) {
        this.mScene = scene;
        this.mCallback = callback;
        
        LayoutInflater mInflater1;
        try {
            mInflater1 = scene.getLayoutInflater2();
        } catch (NullPointerException e) {
            mInflater1 = scene.getLayoutInflater();
        }
        mInflater = mInflater1;
        AssertUtils.assertNotNull(mInflater);

        View calculator = mInflater.inflate(R.layout.item_gallery_list_thumb_height, null);
        ViewUtils.measureView(calculator, 1024, ViewGroup.LayoutParams.WRAP_CONTENT);
        mListThumbHeight = calculator.getMeasuredHeight();
        mListThumbWidth = mListThumbHeight * 2 / 3;
    }

    @Override
    public long getItemId(int position) {
        int posInList = mCallback.positionInList(position);
        List<DownloadInfo> list = mCallback.getList();
        if (list == null || posInList < 0 || posInList >= list.size()) {
            return 0;
        }
        return list.get(posInList).gid;
    }

    @NonNull
    @Override
    public DownloadHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        DownloadHolder holder = new DownloadHolder(mInflater.inflate(R.layout.item_download, parent, false));

        ViewGroup.LayoutParams lp = holder.thumb.getLayoutParams();
        lp.width = mListThumbWidth;
        lp.height = mListThumbHeight;
        holder.thumb.setLayoutParams(lp);

        return holder;
    }

    @Override
    public void onBindViewHolder(DownloadHolder holder, int position) {
        List<DownloadInfo> list = mCallback.getList();
        if (list == null) {
            return;
        }

        try {
            int pos = mCallback.positionInList(position);
            DownloadInfo info = list.get(pos);

            String title = EhUtils.getSuitableTitle(info);

            holder.thumb.load(EhCacheKeyFactory.getThumbKey(info.gid), info.thumb, new ThumbDataContainer(info), true);

            holder.title.setText(title);
            holder.uploader.setText(info.uploader);
            holder.rating.setRating(info.rating);

            SpiderInfo spiderInfo = mCallback.getSpiderInfoMap().get(info.gid);

            if (spiderInfo != null) {
                int startPage = spiderInfo.startPage + 1;
                String readText = startPage + "/" + spiderInfo.pages;
                holder.readProgress.setText(readText);
            }

            TextView category = holder.category;
            String newCategoryText = EhUtils.getCategory(info.category);
            if (!newCategoryText.equals(category.getText())) {
                category.setText(newCategoryText);
                category.setBackgroundColor(EhUtils.getCategoryColor(info.category));
            }
            bindForState(holder, info);

            // Update transition name
            ViewCompat.setTransitionName(holder.thumb, TransitionNameFactory.getThumbTransitionName(info.gid));
        } catch (Exception e) {
            FirebaseCrashlytics.getInstance().recordException(e);
        }
    }

    @Override
    public int getItemCount() {
        List<DownloadInfo> list = mCallback.getList();
        if (list == null) {
            return 0;
        }
        int listSize = list.size();
        if (listSize < mCallback.getPaginationSize() || !mCallback.isCanPagination()) {
            return listSize;
        }
        int count = listSize - mCallback.getPageSize() * (mCallback.getIndexPage() - 1);
        return Math.min(count, mCallback.getPageSize());
    }

    private void bindForState(DownloadHolder holder, DownloadInfo info) {
        Resources resources = mScene.getResources2();
        if (null == resources) {
            return;
        }

        switch (info.state) {
            case DownloadInfo.STATE_NONE:
                bindState(holder, info, resources.getString(R.string.download_state_none));
                break;
            case DownloadInfo.STATE_WAIT:
                bindState(holder, info, resources.getString(R.string.download_state_wait));
                break;
            case DownloadInfo.STATE_DOWNLOAD:
                bindProgress(holder, info);
                break;
            case DownloadInfo.STATE_FAILED:
                String text;
                if (info.legacy <= 0) {
                    text = resources.getString(R.string.download_state_failed);
                } else {
                    text = resources.getString(R.string.download_state_failed_2, info.legacy);
                }
                bindState(holder, info, text);
                break;
            case DownloadInfo.STATE_FINISH:
                bindState(holder, info, resources.getString(R.string.download_state_finish));
                break;
        }
    }

    private void bindState(DownloadHolder holder, DownloadInfo info, String state) {
        holder.uploader.setVisibility(View.VISIBLE);
        holder.rating.setVisibility(View.VISIBLE);
        holder.category.setVisibility(View.VISIBLE);
        holder.readProgress.setVisibility(View.VISIBLE);
        holder.state.setVisibility(View.VISIBLE);
        holder.progressBar.setVisibility(View.GONE);
        holder.percent.setVisibility(View.GONE);
        holder.speed.setVisibility(View.GONE);
        if (info.state == DownloadInfo.STATE_WAIT || info.state == DownloadInfo.STATE_DOWNLOAD) {
            holder.start.setVisibility(View.GONE);
            holder.stop.setVisibility(View.VISIBLE);
        } else {
            holder.start.setVisibility(View.VISIBLE);
            holder.stop.setVisibility(View.GONE);
        }

        holder.state.setText(state);
    }

    @SuppressLint("SetTextI18n")
    private void bindProgress(DownloadHolder holder, DownloadInfo info) {
        holder.uploader.setVisibility(View.GONE);
        holder.rating.setVisibility(View.GONE);
        holder.category.setVisibility(View.GONE);
        holder.readProgress.setVisibility(View.GONE);
        holder.state.setVisibility(View.GONE);
        holder.progressBar.setVisibility(View.VISIBLE);
        holder.percent.setVisibility(View.VISIBLE);
        holder.speed.setVisibility(View.VISIBLE);
        if (info.state == DownloadInfo.STATE_WAIT || info.state == DownloadInfo.STATE_DOWNLOAD) {
            holder.start.setVisibility(View.GONE);
            holder.stop.setVisibility(View.VISIBLE);
        } else {
            holder.start.setVisibility(View.VISIBLE);
            holder.stop.setVisibility(View.GONE);
        }

        if (info.total <= 0 || info.finished < 0) {
            holder.percent.setText(null);
            holder.progressBar.setIndeterminate(true);
        } else {
            holder.percent.setText(info.finished + "/" + info.total);
            holder.progressBar.setIndeterminate(false);
            holder.progressBar.setMax(info.total);
            holder.progressBar.setProgress(info.finished);
        }
        long speed = info.speed;
        if (speed < 0) {
            speed = 0;
        }
        holder.speed.setText(com.hippo.lib.yorozuya.FileUtils.humanReadableByteCount(speed, false) + "/S");
    }

    public class DownloadHolder extends AbstractDraggableItemViewHolder implements View.OnClickListener {

        public final LoadImageView thumb;
        public final TextView title;
        public final TextView uploader;
        public final SimpleRatingView rating;
        public final TextView category;
        public final TextView readProgress;
        public final View start;
        public final View stop;
        public final TextView state;
        public final android.widget.ProgressBar progressBar;
        public final TextView percent;
        public final TextView speed;

        public DownloadHolder(View itemView) {
            super(itemView);

            thumb = itemView.findViewById(R.id.thumb);
            title = itemView.findViewById(R.id.title);
            uploader = itemView.findViewById(R.id.uploader);
            rating = itemView.findViewById(R.id.rating);
            category = itemView.findViewById(R.id.category);
            readProgress = itemView.findViewById(R.id.read_progress);
            start = itemView.findViewById(R.id.start);
            stop = itemView.findViewById(R.id.stop);
            state = itemView.findViewById(R.id.state);
            progressBar = itemView.findViewById(R.id.progress_bar);
            percent = itemView.findViewById(R.id.percent);
            speed = itemView.findViewById(R.id.speed);

            // TODO cancel on click listener when select items
            thumb.setOnClickListener(this);
            start.setOnClickListener(this);
            stop.setOnClickListener(this);

            boolean isDarkTheme = !AttrResources.getAttrBoolean(mScene.getEHContext(), androidx.appcompat.R.attr.isLightTheme);
            Ripple.addRipple(start, isDarkTheme);
            Ripple.addRipple(stop, isDarkTheme);
        }

        @Override
        public void onClick(View v) {
            Context context = mScene.getEHContext();
            EasyRecyclerView recyclerView = mCallback.getRecyclerView();
            if (null == context || null == recyclerView || recyclerView.isInCustomChoice()) {
                return;
            }
            List<DownloadInfo> list = mCallback.getList();
            if (list == null) {
                return;
            }
            int size = list.size();
            int index = recyclerView.getChildAdapterPosition(itemView);
            if (index < 0 || index >= size) {
                return;
            }

            if (thumb == v) {
                Bundle args = new Bundle();
                args.putString(GalleryDetailScene.KEY_ACTION, GalleryDetailScene.ACTION_DOWNLOAD_GALLERY_INFO);
                args.putParcelable(GalleryDetailScene.KEY_GALLERY_INFO, list.get(mCallback.positionInList(index)));
                Announcer announcer = new Announcer(GalleryDetailScene.class).setArgs(args);
                announcer.setTranHelper(new EnterGalleryDetailTransaction(thumb));
                mScene.startScene(announcer);
            } else if (start == v) {
                final DownloadInfo info = list.get(mCallback.positionInList(index));
                Intent intent = new Intent(context, DownloadService.class);
                intent.setAction(DownloadService.ACTION_START);
                intent.putExtra(DownloadService.KEY_GALLERY_INFO, info);
                context.startService(intent);
            } else if (stop == v) {
                DownloadManager downloadManager = mCallback.getDownloadManager();
                if (null != downloadManager) {
                    downloadManager.stopDownload(list.get(mCallback.positionInList(index)).gid);
                }
            }
        }
    }

    // 拖拽排序相关方法实现
    @Override
    public boolean onCheckCanStartDrag(DownloadHolder holder, int position, int x, int y) {
        // 检查是否点击在thumb上
        return ViewUtils.isViewUnder(holder.thumb, x, y, 0);
    }

    @Override
    public ItemDraggableRange onGetItemDraggableRange(DownloadHolder holder, int position) {
        return null;
    }

    @Override
    public void onMoveItem(int fromPosition, int toPosition) {
        if (fromPosition == toPosition) {
            return;
        }
        List<DownloadInfo> list = mCallback.getList();
        if (list == null) {
            return;
        }

        // 计算在完整列表中的位置
        int fromPosInList = mCallback.positionInList(fromPosition);
        int toPosInList = mCallback.positionInList(toPosition);
        
        if (fromPosInList >= 0 && fromPosInList < list.size() && 
            toPosInList >= 0 && toPosInList < list.size()) {
            // 获取下载项信息
            EhDB.moveDownloadInfo(list,fromPosInList, toPosInList);

            // 移动下载项
            final DownloadInfo item = list.remove(fromPosInList);
            list.add(toPosInList, item);
            
            // 通知适配器数据已更改
            notifyDataSetChanged();
        }
    }

    @Override
    public boolean onCheckCanDrop(int draggingPosition, int dropPosition) {
        return true;
    }

    @Override
    public void onItemDragStarted(int position) {
        // 拖拽开始时的处理
        try {
            // 设置RecyclerView为软件渲染模式以避免硬件位图问题
            if (mCallback.getRecyclerView() != null) {
                movedItem = mCallback.getRecyclerView().getChildAt(position);
                movedItem.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                Log.d("DownloadAdapter", "onItemDragStarted: " + position);
            }
        } catch (Exception e) {
            // 忽略硬件位图相关错误
            android.util.Log.e("DownloadAdapter", "Error in onItemDragStarted: " + e.getMessage());
        }
    }

    @Override
    public void onItemDragFinished(int fromPosition, int toPosition, boolean result) {
        // 拖拽结束时的处理
        try {
            // 恢复RecyclerView为硬件加速模式
            RecyclerView recyclerView = mCallback.getRecyclerView();
            if (recyclerView != null) {
//                if (recyclerView.getChildCount() >= toPosition + 1) {
//                    recyclerView.getChildAt(toPosition + 1).setLayerType(View.LAYER_TYPE_HARDWARE, null);
//                    Log.d("DownloadAdapter", "toPosition+1: " + (toPosition + 1));
//                }

//                if (toPosition >= 1) {
//                    recyclerView.getChildAt(toPosition - 1).setLayerType(View.LAYER_TYPE_HARDWARE, null);
//                    Log.d("DownloadAdapter", "toPosition-1: " + (toPosition - 1));
//                }
//                recyclerView.getChildAt(fromPosition).setLayerType(View.LAYER_TYPE_HARDWARE, null);
                if (movedItem != null) {
                    movedItem.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    Log.d("DownloadAdapter", "movedItem: " + movedItem);
                } else {
                    recyclerView.getChildAt(toPosition).setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    Log.d("DownloadAdapter", "onItemDragFinished: " + toPosition);
                }


            }
        } catch (Exception e) {
            // 忽略硬件位图相关错误
            android.util.Log.e("DownloadAdapter", "Error in onItemDragFinished: " + e.getMessage());
        }
    }
}
