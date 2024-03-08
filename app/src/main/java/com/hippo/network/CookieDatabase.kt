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
package com.hippo.network

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.hippo.database.MSQLiteBuilder
import com.hippo.ehviewer.Settings
import com.hippo.util.SqlUtils
import okhttp3.Cookie

/*
 * Created by Hippo on 2017/9/4.
 */internal class CookieDatabase(context: Context?, name: String?) {
    private val cookieIdMap: MutableMap<Cookie, Long> = HashMap()
    private val helper: SQLiteOpenHelper
    private val db: SQLiteDatabase

    init {
        helper = MSQLiteBuilder()
            .version(VERSION_1)
            .createTable(TABLE_COOKIE)
            .insertColumn(TABLE_COOKIE, COLUMN_NAME, String::class.java)
            .insertColumn(TABLE_COOKIE, COLUMN_VALUE, String::class.java)
            .insertColumn(TABLE_COOKIE, COLUMN_EXPIRES_AT, Long::class.javaPrimitiveType)
            .insertColumn(TABLE_COOKIE, COLUMN_DOMAIN, String::class.java)
            .insertColumn(TABLE_COOKIE, COLUMN_PATH, String::class.java)
            .insertColumn(TABLE_COOKIE, COLUMN_SECURE, Boolean::class.javaPrimitiveType)
            .insertColumn(TABLE_COOKIE, COLUMN_HTTP_ONLY, Boolean::class.javaPrimitiveType)
            .insertColumn(TABLE_COOKIE, COLUMN_PERSISTENT, Boolean::class.javaPrimitiveType)
            .insertColumn(TABLE_COOKIE, COLUMN_HOST_ONLY, Boolean::class.javaPrimitiveType)
            .build(context, name, DB_VERSION)
        db = helper.writableDatabase
    }// Mark to remove the cookie

    // Remove invalid or expired cookie
    // Save id of the cookie in db

    // Put cookie to set
    val allCookies: MutableMap<String, CookieSet>
        get() {
            val now = System.currentTimeMillis()
            val map: MutableMap<String, CookieSet> = HashMap()
            val toRemove: MutableList<Long> = ArrayList()
            val cursor = db.rawQuery("SELECT * FROM " + TABLE_COOKIE + ";", null)
            try {
                while (cursor.moveToNext()) {
                    val id = SqlUtils.getLong(cursor, COLUMN_ID, 0)
                    val cookie = getCookie(cursor, now)
                    if (cookie != null) {
                        // Save id of the cookie in db
                        cookieIdMap[cookie] = id

                        // Put cookie to set
                        var set = map[cookie.domain()]
                        if (set == null) {
                            set = CookieSet()
                            map[cookie.domain()] = set
                        }
                        set.add(cookie)
                    } else {
                        // Mark to remove the cookie
                        toRemove.add(id)
                    }
                }
            } finally {
                cursor.close()
            }

            // Remove invalid or expired cookie
            if (!toRemove.isEmpty()) {
                val statement = db.compileStatement(
                    "DELETE FROM " + TABLE_COOKIE + " WHERE " + COLUMN_ID + " = ?;"
                )
                db.beginTransaction()
                try {
                    for (id in toRemove) {
                        statement.bindLong(1, id)
                        statement.executeUpdateDelete()
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            return map
        }

    fun toContentValues(cookie: Cookie): ContentValues {
        val contentValues = ContentValues(9)
        contentValues.put(COLUMN_NAME, cookie.name())
        contentValues.put(COLUMN_VALUE, cookie.value())
        contentValues.put(COLUMN_EXPIRES_AT, cookie.expiresAt())
        contentValues.put(COLUMN_DOMAIN, cookie.domain())
        contentValues.put(COLUMN_PATH, cookie.path())
        contentValues.put(COLUMN_SECURE, cookie.secure())
        contentValues.put(COLUMN_HTTP_ONLY, cookie.httpOnly())
        contentValues.put(COLUMN_PERSISTENT, cookie.persistent())
        contentValues.put(COLUMN_HOST_ONLY, cookie.hostOnly())
        return contentValues
    }

    fun add(cookie: Cookie) {
        val id = db.insert(TABLE_COOKIE, null, toContentValues(cookie))
        if (id != -1L) {
            val oldId = cookieIdMap.put(cookie, id)
            if (oldId != null) Log.e(LOG_TAG, "Add a duplicate cookie")
        } else {
            Log.e(LOG_TAG, "An error occurred when insert a cookie")
        }
    }

    fun update(from: Cookie, to: Cookie) {
        val id = cookieIdMap[from]
        if (id == null) {
            Log.e(LOG_TAG, "Can't get id when update the cookie")
            return
        }
        if (from.name() == "igneous") {
            if (Settings.getLockCookieIgneous()) {
                return
            }
        }
        val values = toContentValues(to)
        val whereClause = COLUMN_ID + " = ?"
        val whereArgs = arrayOf(id.toString())
        val count = db.update(TABLE_COOKIE, values, whereClause, whereArgs)
        if (count != 1) {
            Log.e(LOG_TAG, "Bad result when update cookie: $count")
        }

        // Update it in cookie-id map
        cookieIdMap.remove(from)
        cookieIdMap[to] = id
    }

    fun remove(cookie: Cookie) {
        val id = cookieIdMap[cookie]
        if (id == null) {
            Log.e(LOG_TAG, "Can't get id when remove the cookie")
            return
        }
        val whereClause = COLUMN_ID + " = ?"
        val whereArgs = arrayOf(id.toString())
        val count = db.delete(TABLE_COOKIE, whereClause, whereArgs)
        if (count != 1) {
            Log.e(LOG_TAG, "Bad result when remove cookie: $count")
        }

        // Remove it from cookie-id map
        cookieIdMap.remove(cookie)
    }

    fun clear() {
        db.delete(TABLE_COOKIE, null, null)
        cookieIdMap.clear()
    }

    fun close() {
        db.close()
        helper.close()
    }

    companion object {
        private val LOG_TAG = CookieDatabase::class.java.simpleName
        private const val VERSION_1 = 1
        private const val TABLE_COOKIE = "OK_HTTP_3_COOKIE"
        private const val COLUMN_ID = "_id"
        private const val COLUMN_NAME = "NAME"
        private const val COLUMN_VALUE = "VALUE"
        private const val COLUMN_EXPIRES_AT = "EXPIRES_AT"
        private const val COLUMN_DOMAIN = "DOMAIN"
        private const val COLUMN_PATH = "PATH"
        private const val COLUMN_SECURE = "SECURE"
        private const val COLUMN_HTTP_ONLY = "HTTP_ONLY"
        private const val COLUMN_PERSISTENT = "PERSISTENT"
        private const val COLUMN_HOST_ONLY = "HOST_ONLY"
        private const val DB_VERSION = VERSION_1
        private fun getCookie(cursor: Cursor, now: Long): Cookie? {
            val name = SqlUtils.getString(cursor, COLUMN_NAME, null)
            val value = SqlUtils.getString(cursor, COLUMN_VALUE, null)
            val expiresAt = SqlUtils.getLong(cursor, COLUMN_EXPIRES_AT, 0)
            val domain = SqlUtils.getString(cursor, COLUMN_DOMAIN, null)
            val path = SqlUtils.getString(cursor, COLUMN_PATH, null)
            val secure = SqlUtils.getBoolean(cursor, COLUMN_SECURE, false)
            val httpOnly = SqlUtils.getBoolean(cursor, COLUMN_HTTP_ONLY, false)
            val persistent = SqlUtils.getBoolean(cursor, COLUMN_PERSISTENT, false)
            val hostOnly = SqlUtils.getBoolean(cursor, COLUMN_HOST_ONLY, false)
            if (name == null || domain == null || path == null) {
                return null
            }

            // Check non-persistent or expired
            if (!persistent || expiresAt <= now) {
                return null
            }
            val builder = Cookie.Builder()
            builder.name(name)
            builder.value(value)
            if (hostOnly) {
                builder.hostOnlyDomain(domain)
            } else {
                builder.domain(domain)
            }
            builder.path(path)
            builder.expiresAt(expiresAt)
            if (secure) builder.secure()
            if (httpOnly) builder.httpOnly()
            return builder.build()
        }
    }
}