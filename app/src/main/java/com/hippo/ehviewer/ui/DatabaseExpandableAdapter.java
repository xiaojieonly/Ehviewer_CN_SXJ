/*
 * Copyright 2025 Hippo Seven
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

package com.hippo.ehviewer.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.database.DatabaseManager;
import java.util.List;
import java.util.Map;

/**
 * 数据库信息展开列表适配器
 */
public class DatabaseExpandableAdapter extends BaseExpandableListAdapter {
    private final Context mContext;
    private final List<String> mGroupList;
    private final List<List<Map<String, Object>>> mChildList;
    private final LayoutInflater mInflater;

    public DatabaseExpandableAdapter(Context context, List<String> groupList, List<List<Map<String, Object>>> childList) {
        mContext = context;
        mGroupList = groupList;
        mChildList = childList;
        mInflater = LayoutInflater.from(context);
    }

    @Override
    public int getGroupCount() {
        return mGroupList.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return mChildList.get(groupPosition).size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        return mGroupList.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return mChildList.get(groupPosition).get(childPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mInflater.inflate(android.R.layout.simple_expandable_list_item_1, parent, false);
        }

        TextView textView = convertView.findViewById(android.R.id.text1);
        textView.setText(mGroupList.get(groupPosition));
        textView.setTextSize(16);
        textView.setPadding(40, 20, 20, 20);

        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        Map<String, Object> tableMap = mChildList.get(groupPosition).get(childPosition);
        String tableName = (String) tableMap.get("name");
        int rowCount = (int) tableMap.get("rowCount");
        
        @SuppressWarnings("unchecked")
        List<DatabaseManager.ColumnInfo> columns = (List<DatabaseManager.ColumnInfo>) tableMap.get("columns");
        
        @SuppressWarnings("unchecked")
        List<Map<String, String>> data = (List<Map<String, String>>) tableMap.get("data");

        View view = new ScrollView(mContext);
        LinearLayout layout = new LinearLayout(mContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 20, 20);

        // 表名和行数
        TextView tableNameView = new TextView(mContext);
        tableNameView.setText("表: " + tableName + " (共 " + rowCount + " 行)");
        tableNameView.setTextSize(14);
        tableNameView.setTextColor(0xFF1976D2);
        layout.addView(tableNameView);

        // 列信息表格
        TextView columnHeaderView = new TextView(mContext);
        columnHeaderView.setText("\n列信息:");
        columnHeaderView.setTextSize(12);
        columnHeaderView.setTextColor(0xFF424242);
        layout.addView(columnHeaderView);

        TableLayout columnTable = new TableLayout(mContext);
        columnTable.setLayoutParams(new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT));

        for (DatabaseManager.ColumnInfo column : columns) {
            TableRow row = new TableRow(mContext);
            row.setLayoutParams(new TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT));

            String columnInfo = String.format("• %s (%s)%s%s",
                    column.name,
                    column.type,
                    column.primaryKey ? " [PK]" : "",
                    column.notnull ? " [NOT NULL]" : "");

            TextView columnView = new TextView(mContext);
            columnView.setText(columnInfo);
            columnView.setTextSize(11);
            columnView.setLayoutParams(new TableRow.LayoutParams());
            columnView.setPadding(10, 5, 10, 5);
            row.addView(columnView);

            columnTable.addView(row);
        }
        layout.addView(columnTable);

        // 数据样本（最多显示前10行）
        if (!data.isEmpty()) {
            TextView dataHeaderView = new TextView(mContext);
            dataHeaderView.setText("\n数据样本 (前10行):");
            dataHeaderView.setTextSize(12);
            dataHeaderView.setTextColor(0xFF424242);
            layout.addView(dataHeaderView);

            TableLayout dataTable = new TableLayout(mContext);
            dataTable.setLayoutParams(new TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT));

            // 表头
            TableRow headerRow = new TableRow(mContext);
            headerRow.setLayoutParams(new TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT));
            headerRow.setBackgroundColor(0xFFEEEEEE);

            for (DatabaseManager.ColumnInfo column : columns) {
                TextView headerView = new TextView(mContext);
                headerView.setText(column.name);
                headerView.setTextSize(10);
                headerView.setTextAppearance(android.graphics.Typeface.BOLD);
                headerView.setPadding(10, 8, 10, 8);
                headerView.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1));
                headerRow.addView(headerView);
            }
            dataTable.addView(headerRow);

            // 数据行
            int rowIndex = 0;
            for (Map<String, String> row : data) {
                if (rowIndex >= 10) break;

                TableRow dataRow = new TableRow(mContext);
                dataRow.setLayoutParams(new TableLayout.LayoutParams(
                        TableLayout.LayoutParams.MATCH_PARENT,
                        TableLayout.LayoutParams.WRAP_CONTENT));

                if (rowIndex % 2 == 1) {
                    dataRow.setBackgroundColor(0xFFFAFAFA);
                }

                for (DatabaseManager.ColumnInfo column : columns) {
                    String value = row.get(column.name);
                    TextView cellView = new TextView(mContext);
                    cellView.setText(value != null ? truncate(value, 50) : "NULL");
                    cellView.setTextSize(9);
                    cellView.setPadding(10, 6, 10, 6);
                    cellView.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1));
                    dataRow.addView(cellView);
                }
                dataTable.addView(dataRow);
                rowIndex++;
            }
            layout.addView(dataTable);
        }

        ((ScrollView) view).addView(layout);
        return view;
    }

    /**
     * 截断长文本
     */
    private String truncate(String text, int maxLength) {
        if (text.length() > maxLength) {
            return text.substring(0, maxLength) + "...";
        }
        return text;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return false;
    }
}
