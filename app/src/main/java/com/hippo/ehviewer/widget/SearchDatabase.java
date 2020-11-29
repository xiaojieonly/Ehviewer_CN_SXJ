/*
 * Copyright (C) 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.widget;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;

import com.hippo.util.SqlUtils;

import java.util.LinkedList;
import java.util.List;

public final class SearchDatabase {

    private static final String TAG = SearchDatabase.class.getSimpleName();

    public static final String COLUMN_QUERY = "query";
    public static final String COLUMN_DATE = "date";

    private static final String DATABASE_NAME = "search_database.db";
    private static final String TABLE_SUGGESTIONS = "suggestions";

    private static final int MAX_HISTORY = 100;

    private final SQLiteDatabase mDatabase;

    private static SearchDatabase sInstance;

    public static SearchDatabase getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new SearchDatabase(context.getApplicationContext());
        }
        return sInstance;
    }

    private SearchDatabase(Context context) {
        DatabaseHelper databaseHelper = new DatabaseHelper(context);
        mDatabase = databaseHelper.getWritableDatabase();
    }

    public String[] getSuggestions(String prefix, int limit) {
        List<String> queryList = new LinkedList<>();
        limit = Math.max(0, limit);

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM ").append(TABLE_SUGGESTIONS);
        if (!TextUtils.isEmpty(prefix)) {
            sb.append(" WHERE ").append(COLUMN_QUERY).append(" LIKE '")
                    .append(SqlUtils.sqlEscapeString(prefix)).append("%'");
        }
        sb.append(" ORDER BY ").append(COLUMN_DATE).append(" DESC")
            .append(" LIMIT ").append(limit);

        try {
            Cursor cursor = mDatabase.rawQuery(sb.toString(), null);
            int queryIndex = cursor.getColumnIndex(COLUMN_QUERY);
            if (cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    String suggestion = cursor.getString(queryIndex);
                    if (!prefix.equals(suggestion)) {
                        queryList.add(suggestion);
                    }
                    cursor.moveToNext();
                }
            }
            cursor.close();
            return queryList.toArray(new String[queryList.size()]);
        } catch (SQLException e) {
            return new String[0];
        }
    }

    public void addQuery(final String query) {
        if (!TextUtils.isEmpty(query)) {
            // Delete old first
            deleteQuery(query);
            // Add it to database
            ContentValues values = new ContentValues();
            values.put(COLUMN_QUERY, query);
            values.put(COLUMN_DATE, System.currentTimeMillis());
            mDatabase.insert(TABLE_SUGGESTIONS, null, values);
            // Remove history if more than max
            truncateHistory(MAX_HISTORY);
        }
    }

    public void deleteQuery(final String query) {
        mDatabase.delete(TABLE_SUGGESTIONS, COLUMN_QUERY + "=?", new String[]{query});
    }

    public void clearQuery() {
        truncateHistory(0);
    }

    /**
     * Reduces the length of the history table, to prevent it from growing too large.
     *
     * @param maxEntries Max entries to leave in the table. 0 means remove all entries.
     */
    protected void truncateHistory(int maxEntries) {
        if (maxEntries < 0) {
            throw new IllegalArgumentException();
        }

        try {
            // null means "delete all".  otherwise "delete but leave n newest"
            String selection = null;
            if (maxEntries > 0) {
                selection = "_id IN " +
                        "(SELECT _id FROM " + TABLE_SUGGESTIONS +
                        " ORDER BY " + COLUMN_DATE + " DESC" +
                        " LIMIT -1 OFFSET " + String.valueOf(maxEntries) + ")";
            }
            mDatabase.delete(TABLE_SUGGESTIONS, selection, null);
        } catch (RuntimeException e) {
            Log.e(TAG, "truncateHistory", e);
        }
    }

    /**
     * Builds the database.  This version has extra support for using the version field
     * as a mode flags field, and configures the database columns depending on the mode bits
     * (features) requested by the extending class.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_SUGGESTIONS + " (" +
                    "_id INTEGER PRIMARY KEY" +
                    "," + COLUMN_QUERY + " TEXT" +
                    "," + COLUMN_DATE + " LONG" +
                    ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SUGGESTIONS);
            onCreate(db);
        }
    }
}
