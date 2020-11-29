/*
 * Copyright 2017 Hippo Seven
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

package com.hippo.network;

/*
 * Created by Hippo on 2017/9/4.
 */

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;
import androidx.annotation.Nullable;
import com.hippo.database.MSQLiteBuilder;
import com.hippo.util.SqlUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.Cookie;

class CookieDatabase {

  private static final String LOG_TAG = CookieDatabase.class.getSimpleName();

  private static final int VERSION_1 = 1;
  private static final String TABLE_COOKIE = "OK_HTTP_3_COOKIE";
  private static final String COLUMN_ID = "_id";
  private static final String COLUMN_NAME = "NAME";
  private static final String COLUMN_VALUE = "VALUE";
  private static final String COLUMN_EXPIRES_AT = "EXPIRES_AT";
  private static final String COLUMN_DOMAIN = "DOMAIN";
  private static final String COLUMN_PATH = "PATH";
  private static final String COLUMN_SECURE = "SECURE";
  private static final String COLUMN_HTTP_ONLY = "HTTP_ONLY";
  private static final String COLUMN_PERSISTENT = "PERSISTENT";
  private static final String COLUMN_HOST_ONLY = "HOST_ONLY";

  private static final int DB_VERSION = VERSION_1;

  private final Map<Cookie, Long> cookieIdMap = new HashMap<>();
  private final SQLiteOpenHelper helper;
  private final SQLiteDatabase db;

  public CookieDatabase(Context context, String name) {
    helper = new MSQLiteBuilder()
        .version(VERSION_1)
        .createTable(TABLE_COOKIE)
        .insertColumn(TABLE_COOKIE, COLUMN_NAME, String.class)
        .insertColumn(TABLE_COOKIE, COLUMN_VALUE, String.class)
        .insertColumn(TABLE_COOKIE, COLUMN_EXPIRES_AT, long.class)
        .insertColumn(TABLE_COOKIE, COLUMN_DOMAIN, String.class)
        .insertColumn(TABLE_COOKIE, COLUMN_PATH, String.class)
        .insertColumn(TABLE_COOKIE, COLUMN_SECURE, boolean.class)
        .insertColumn(TABLE_COOKIE, COLUMN_HTTP_ONLY, boolean.class)
        .insertColumn(TABLE_COOKIE, COLUMN_PERSISTENT, boolean.class)
        .insertColumn(TABLE_COOKIE, COLUMN_HOST_ONLY, boolean.class)
        .build(context, name, DB_VERSION);
    db = helper.getWritableDatabase();
  }

  @Nullable
  private static Cookie getCookie(Cursor cursor, long now) {
    String name = SqlUtils.getString(cursor, COLUMN_NAME, null);
    String value = SqlUtils.getString(cursor, COLUMN_VALUE, null);
    long expiresAt = SqlUtils.getLong(cursor, COLUMN_EXPIRES_AT, 0);
    String domain = SqlUtils.getString(cursor, COLUMN_DOMAIN, null);
    String path = SqlUtils.getString(cursor, COLUMN_PATH, null);
    boolean secure = SqlUtils.getBoolean(cursor, COLUMN_SECURE, false);
    boolean httpOnly = SqlUtils.getBoolean(cursor, COLUMN_HTTP_ONLY, false);
    boolean persistent = SqlUtils.getBoolean(cursor, COLUMN_PERSISTENT, false);
    boolean hostOnly = SqlUtils.getBoolean(cursor, COLUMN_HOST_ONLY, false);

    if (name == null || domain == null || path == null) {
      return null;
    }

    // Check non-persistent or expired
    if (!persistent || expiresAt <= now) {
      return null;
    }

    Cookie.Builder builder = new Cookie.Builder();
    builder.name(name);
    builder.value(value);
    if (hostOnly) {
      builder.hostOnlyDomain(domain);
    } else {
      builder.domain(domain);
    }
    builder.path(path);
    builder.expiresAt(expiresAt);
    if (secure) builder.secure();
    if (httpOnly) builder.httpOnly();
    return builder.build();
  }

  public Map<String, CookieSet> getAllCookies() {
    long now = System.currentTimeMillis();
    Map<String, CookieSet> map = new HashMap<>();
    List<Long> toRemove = new ArrayList<>();

    Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_COOKIE + ";", null);
    try {
      while (cursor.moveToNext()) {
        long id = SqlUtils.getLong(cursor, COLUMN_ID, 0);
        Cookie cookie = getCookie(cursor, now);

        if (cookie != null) {
          // Save id of the cookie in db
          cookieIdMap.put(cookie, id);

          // Put cookie to set
          CookieSet set = map.get(cookie.domain());
          if (set == null) {
            set = new CookieSet();
            map.put(cookie.domain(), set);
          }
          set.add(cookie);
        } else {
          // Mark to remove the cookie
          toRemove.add(id);
        }
      }
    } finally {
      cursor.close();
    }

    // Remove invalid or expired cookie
    if (!toRemove.isEmpty()) {
      SQLiteStatement statement = db.compileStatement(
          "DELETE FROM " + TABLE_COOKIE + " WHERE " + COLUMN_ID + " = ?;");
      db.beginTransaction();
      try {
        for (long id : toRemove) {
          statement.bindLong(1, id);
          statement.executeUpdateDelete();
        }
        db.setTransactionSuccessful();
      } finally {
        db.endTransaction();
      }
    }

    return map;
  }

  public ContentValues toContentValues(Cookie cookie) {
    ContentValues contentValues = new ContentValues(9);
    contentValues.put(COLUMN_NAME, cookie.name());
    contentValues.put(COLUMN_VALUE, cookie.value());
    contentValues.put(COLUMN_EXPIRES_AT, cookie.expiresAt());
    contentValues.put(COLUMN_DOMAIN, cookie.domain());
    contentValues.put(COLUMN_PATH, cookie.path());
    contentValues.put(COLUMN_SECURE, cookie.secure());
    contentValues.put(COLUMN_HTTP_ONLY, cookie.httpOnly());
    contentValues.put(COLUMN_PERSISTENT, cookie.persistent());
    contentValues.put(COLUMN_HOST_ONLY, cookie.hostOnly());
    return contentValues;
  }

  public void add(Cookie cookie) {
    long id = db.insert(TABLE_COOKIE, null, toContentValues(cookie));
    if (id != -1L) {
      Long oldId = cookieIdMap.put(cookie, id);
      if (oldId != null) Log.e(LOG_TAG, "Add a duplicate cookie");
    } else {
      Log.e(LOG_TAG, "An error occurred when insert a cookie");
    }
  }

  public void update(Cookie from, Cookie to) {
    Long id = cookieIdMap.get(from);
    if (id == null) {
      Log.e(LOG_TAG, "Can't get id when update the cookie");
      return;
    }

    ContentValues values = toContentValues(to);
    String whereClause = COLUMN_ID + " = ?";
    String[] whereArgs = { id.toString() };
    int count = db.update(TABLE_COOKIE, values, whereClause, whereArgs);
    if (count != 1) {
      Log.e(LOG_TAG, "Bad result when update cookie: " + count);
    }

    // Update it in cookie-id map
    cookieIdMap.remove(from);
    cookieIdMap.put(to, id);
  }

  public void remove(Cookie cookie) {
    Long id = cookieIdMap.get(cookie);
    if (id == null) {
      Log.e(LOG_TAG, "Can't get id when remove the cookie");
      return;
    }

    String whereClause = COLUMN_ID + " = ?";
    String[] whereArgs = { id.toString() };
    int count = db.delete(TABLE_COOKIE, whereClause, whereArgs);
    if (count != 1) {
      Log.e(LOG_TAG, "Bad result when remove cookie: " + count);
    }

    // Remove it from cookie-id map
    cookieIdMap.remove(cookie);
  }

  public void clear() {
    db.delete(TABLE_COOKIE, null, null);
    cookieIdMap.clear();
  }

  public void close() {
    db.close();
    helper.close();
  }
}
