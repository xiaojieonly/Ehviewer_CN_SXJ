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

import android.os.Bundle;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.StyleRes;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.database.DatabaseManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据库查看Activity
 * 显示应用内所有数据库的版本、表、列和数据
 */
public class DatabaseViewerActivity extends EhActivity {
    private ExpandableListView mExpandableListView;
    private DatabaseManager mDatabaseManager;
    private DatabaseExpandableAdapter mAdapter;

    @Override
    protected int getThemeResId(int theme) {
        // 使用父类的默认实现，支持自适应主题切换
        return super.getThemeResId(theme);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_database_viewer);

        mExpandableListView = findViewById(R.id.expandable_list_view);
        mDatabaseManager = new DatabaseManager(this);

        loadDatabases();
    }

    private void loadDatabases() {
        new Thread(() -> {
            List<DatabaseManager.DatabaseInfo> databases = mDatabaseManager.getAllDatabases();
            runOnUiThread(() -> {
                List<String> groupList = new ArrayList<>();
                List<List<Map<String, Object>>> childList = new ArrayList<>();

                for (DatabaseManager.DatabaseInfo db : databases) {
                    groupList.add("📦 " + db.name + " (v" + db.version + ")");

                    List<Map<String, Object>> tableList = new ArrayList<>();
                    for (DatabaseManager.TableInfo table : db.tables) {
                        Map<String, Object> tableMap = new java.util.LinkedHashMap<>();
                        tableMap.put("name", table.name);
                        tableMap.put("rowCount", table.rowCount);
                        tableMap.put("columns", table.columns);
                        tableMap.put("data", table.data);
                        tableList.add(tableMap);
                    }
                    childList.add(tableList);
                }

                mAdapter = new DatabaseExpandableAdapter(this, groupList, childList);
                mExpandableListView.setAdapter(mAdapter);
            });
        }).start();
    }
}
