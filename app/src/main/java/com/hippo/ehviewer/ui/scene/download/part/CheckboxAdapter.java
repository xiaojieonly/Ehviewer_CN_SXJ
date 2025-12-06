/*
 * Copyright 2024 Hippo Seven
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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hippo.ehviewer.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CheckboxAdapter extends RecyclerView.Adapter<CheckboxAdapter.ViewHolder> {
    
    private final List<String> items;
    private final List<Integer> itemIds;
    private final Set<Integer> selectedItems = new HashSet<>();
    private OnSelectionChangedListener listener;
    private boolean isMutuallyExclusive = false; // 是否互斥选择
    
    public interface OnSelectionChangedListener {
        void onSelectionChanged(Set<Integer> selectedItems);
    }
    
    public CheckboxAdapter(List<String> items, List<Integer> itemIds) {
        this.items = items;
        this.itemIds = itemIds;
    }
    
    public void setMutuallyExclusive(boolean mutuallyExclusive) {
        this.isMutuallyExclusive = mutuallyExclusive;
    }
    
    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.listener = listener;
    }
    
    public void setSelectedItems(Set<Integer> selectedItems) {
        this.selectedItems.clear();
        this.selectedItems.addAll(selectedItems);
        // 使用post方法延迟通知，避免在RecyclerView计算布局时调用
        // 这里无法直接使用post，因为没有view引用，所以先保留notifyDataSetChanged
        // 但在调用这个方法的地方确保不在RecyclerView计算布局时调用
        notifyDataSetChanged();
    }
    
    public Set<Integer> getSelectedItems() {
        return new HashSet<>(selectedItems);
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_checkbox, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String item = items.get(position);
        int itemId = itemIds.get(position);
        boolean isSelected = selectedItems.contains(itemId);
        
        // 移除旧的监听器，避免重复触发
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(isSelected);
        holder.textView.setText(item);
        
        // 统一使用itemView点击事件
        holder.itemView.setOnClickListener(v -> {
            boolean currentSelected = selectedItems.contains(itemId);
            
            if (isMutuallyExclusive) {
                // 互斥选择模式：清除其他选择，只选择当前项
                if (!currentSelected) {
                    selectedItems.clear();
                    selectedItems.add(itemId);
                    notifyDataSetChanged();
                    
                    if (listener != null) {
                        listener.onSelectionChanged(selectedItems);
                    }
                }
            } else {
                // 多选模式
                if (currentSelected) {
                    selectedItems.remove(itemId);
                } else {
                    selectedItems.add(itemId);
                }
                notifyItemChanged(position);
                
                if (listener != null) {
                    listener.onSelectionChanged(selectedItems);
                }
            }
        });
        
        // CheckBox点击也触发itemView点击
        holder.checkBox.setOnClickListener(v -> holder.itemView.performClick());
    }
    
    @Override
    public int getItemCount() {
        return items.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView textView;
        
        ViewHolder(View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkbox);
            textView = itemView.findViewById(R.id.text);
        }
    }
}