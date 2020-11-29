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

package com.hippo.util;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class SqlUtils {

    public static void exeSQLSafely(SQLiteDatabase db, String sql) {
        try {
            db.execSQL(sql);
        } catch (SQLException e) {
            // Ignore
        }
    }

    public static void dropTable(SQLiteDatabase db, String tableName) {
        exeSQLSafely(db, "DROP TABLE IF EXISTS " + tableName);
    }

    public static void dropAllTable(SQLiteDatabase db) {
        List<String> tables = new ArrayList<String>();
        Cursor cursor = db.rawQuery("SELECT * FROM sqlite_master WHERE type='table';", null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            String tableName = cursor.getString(1);
            if (!tableName.equals("android_metadata") &&
                    !tableName.equals("sqlite_sequence"))
                tables.add(tableName);
            cursor.moveToNext();
        }
        cursor.close();

        for(String tableName : tables) {
            dropTable(db, tableName);
        }
    }

    public static String sqlEscapeString(String value) {
        StringBuilder sb = new StringBuilder();

        int length = value.length();
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            if (c == '\'') {
                sb.append('\'');
            }
            sb.append(c);
        }

        return sb.toString();
    }

    public static boolean getBoolean(Cursor cursor, String column, boolean defValue) {
        try {
            int index = cursor.getColumnIndex(column);
            if (index != -1) {
                return cursor.getInt(index) != 0;
            }
        } catch (Throwable e) { /* Ignore */ }
        return defValue;
    }

    public static int getInt(Cursor cursor, String column, int defValue) {
        try {
            int index = cursor.getColumnIndex(column);
            if (index != -1) {
                return cursor.getInt(index);
            }
        } catch (Throwable e) { /* Ignore */ }
        return defValue;
    }

    public static long getLong(Cursor cursor, String column, long defValue) {
        try {
            int index = cursor.getColumnIndex(column);
            if (index != -1) {
                return cursor.getLong(index);
            }
        } catch (Throwable e) { /* Ignore */ }
        return defValue;
    }

    public static float getFloat(Cursor cursor, String column, float defValue) {
        try {
            int index = cursor.getColumnIndex(column);
            if (index != -1) {
                return cursor.getFloat(index);
            }
        } catch (Throwable e) { /* Ignore */ }
        return defValue;
    }

    public static String getString(Cursor cursor, String column, String defValue) {
        try {
            int index = cursor.getColumnIndex(column);
            if (index != -1) {
                return cursor.getString(index);
            }
        } catch (Throwable e) { /* Ignore */ }
        return defValue;
    }
}
