/*
 * Copyright 2026 Hippo Seven
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

package com.hippo.anotherviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.hippo.anotherviewer.dao.DaoMaster;
import com.hippo.anotherviewer.dao.DaoSession;
import com.hippo.anotherviewer.dao.HistoryInfo;

import org.greenrobot.greendao.database.StandardDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Schema v9 reading-progress column (plan 2026-09-02, app items A1-A4):
 * the PAGE column is the 22nd (last) column of HISTORY — every bind/read in
 * {@link HistoryDao} is position-indexed, so both the fresh-install schema
 * and the v8 → v9 upgrade must keep PAGE strictly after FAVORITE_NAME and
 * leave all neighbouring columns aligned. Covers the two install paths:
 * fresh v9 createTable and SiteDB.upgradeDB (v7→v8→v9 fall-through and a
 * direct v8→v9 run) with pre-existing data preserved.
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class SiteDbHistoryPageTest {

    /** The v8 HISTORY table: 22 columns, PAGE not yet present. */
    private static final String V8_HISTORY =
            "CREATE TABLE \"HISTORY\" (" +
                    "\"GID\" INTEGER PRIMARY KEY NOT NULL ," + // 0: gid
                    "\"TOKEN\" TEXT," + // 1: token
                    "\"TITLE\" TEXT," + // 2: title
                    "\"TITLE_JPN\" TEXT," + // 3: titleJpn
                    "\"THUMB\" TEXT," + // 4: thumb
                    "\"CATEGORY\" INTEGER NOT NULL ," + // 5: category
                    "\"POSTED\" TEXT," + // 6: posted
                    "\"UPLOADER\" TEXT," + // 7: uploader
                    "\"RATING\" REAL NOT NULL ," + // 8: rating
                    "\"SIMPLE_LANGUAGE\" TEXT," + // 9: simpleLanguage
                    "\"MODE\" INTEGER NOT NULL ," + // 10: mode
                    "\"TIME\" INTEGER NOT NULL ," + // 11: time
                    "\"RATED\" INTEGER NOT NULL ," + // 12: rated
                    "\"SIMPLE_TAGS\" TEXT," + // 13: simpleTags
                    "\"PAGES\" INTEGER NOT NULL ," + // 14: pages
                    "\"THUMB_WIDTH\" INTEGER NOT NULL ," + // 15: thumbWidth
                    "\"THUMB_HEIGHT\" INTEGER NOT NULL ," + // 16: thumbHeight
                    "\"SPAN_SIZE\" INTEGER NOT NULL ," + // 17: spanSize
                    "\"SPAN_INDEX\" INTEGER NOT NULL ," + // 18: spanIndex
                    "\"SPAN_GROUP_INDEX\" INTEGER NOT NULL ," + // 19: spanGroupIndex
                    "\"FAVORITE_SLOT\" INTEGER NOT NULL ," + // 20: favoriteSlot
                    "\"FAVORITE_NAME\" TEXT);"; // 21: favoriteName

    private static SQLiteDatabase newDb() {
        return SQLiteDatabase.create(null);
    }

    private static List<String> columns(SQLiteDatabase db, String table) {
        List<String> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(\"" + table + "\")", null)) {
            while (cursor.moveToNext()) {
                result.add(cursor.getString(1));
            }
        }
        return result;
    }

    private static DaoSession newSession(SQLiteDatabase db) {
        DaoMaster.createAllTables(new StandardDatabase(db), false);
        return new DaoMaster(new StandardDatabase(db)).newSession();
    }

    @Test
    public void testFreshV9SchemaPutsPageLast() {
        SQLiteDatabase db = newDb();
        DaoMaster.createAllTables(new StandardDatabase(db), false);

        List<String> cols = columns(db, "HISTORY");
        assertEquals(23, cols.size());
        assertEquals("PAGE", cols.get(22));
        assertEquals("FAVORITE_NAME", cols.get(21));
    }

    @Test
    public void testDaoPageRoundTripOnFreshSchema() {
        SQLiteDatabase db = newDb();
        DaoSession session = newSession(db);

        HistoryInfo info = new HistoryInfo();
        info.gid = 1;
        info.token = "tok";
        info.title = "title";
        info.titleJpn = "日本語";
        info.thumb = "thumb";
        info.category = 2;
        info.posted = "2026-09-02";
        info.uploader = "up";
        info.rating = 4.5f;
        info.simpleLanguage = "EN";
        info.mode = 1;
        info.time = 1000;
        info.rated = true;
        info.simpleTags = new String[] {"a", "b"};
        info.pages = 30;
        info.thumbWidth = 100;
        info.thumbHeight = 140;
        info.spanSize = 3;
        info.spanIndex = 1;
        info.spanGroupIndex = 2;
        info.favoriteSlot = 4;
        info.favoriteName = "favName";
        info.page = 17;
        session.getHistoryDao().insert(info);

        HistoryInfo loaded = session.getHistoryDao().load(1L);
        // Every column, in order — a misplaced PAGE would shift the tail.
        assertEquals("tok", loaded.token);
        assertEquals("title", loaded.title);
        assertEquals("日本語", loaded.titleJpn);
        assertEquals("thumb", loaded.thumb);
        assertEquals(2, loaded.category);
        assertEquals("2026-09-02", loaded.posted);
        assertEquals("up", loaded.uploader);
        assertEquals(4.5f, loaded.rating, 0.001f);
        assertEquals("EN", loaded.simpleLanguage);
        assertEquals(1, loaded.mode);
        assertEquals(1000, loaded.time);
        assertTrue(loaded.rated);
        assertEquals(2, loaded.simpleTags.length);
        assertEquals(30, loaded.pages);
        assertEquals(100, loaded.thumbWidth);
        assertEquals(140, loaded.thumbHeight);
        assertEquals(3, loaded.spanSize);
        assertEquals(1, loaded.spanIndex);
        assertEquals(2, loaded.spanGroupIndex);
        assertEquals(4, loaded.favoriteSlot);
        assertEquals("favName", loaded.favoriteName);
        assertEquals(17, loaded.page);

        // Update path (the second readEntity overload + bindValues), re-read
        // through a FRESH session so the DAO actually hits the DB again
        // instead of the identity-scope cache.
        loaded.setPage(23);
        session.getHistoryDao().update(loaded);
        HistoryInfo reread = new DaoMaster(new StandardDatabase(db)).newSession()
                .getHistoryDao().load(1L);
        assertEquals(23, reread.getPage());
        // The page update must not corrupt the neighbouring string column.
        assertEquals("favName", reread.favoriteName);
    }

    @Test
    public void testUpgradeFromV8AddsPageKeepsDataAndStaysAligned() {
        SQLiteDatabase db = newDb();
        db.execSQL(V8_HISTORY);
        db.execSQL("INSERT INTO \"HISTORY\" (GID, TOKEN, TITLE, CATEGORY, RATING, MODE, TIME, RATED, PAGES, " +
                "THUMB_WIDTH, THUMB_HEIGHT, SPAN_SIZE, SPAN_INDEX, SPAN_GROUP_INDEX, FAVORITE_SLOT, FAVORITE_NAME) " +
                "VALUES (2, 'tok', 'h', 1, 3.5, 1, 2000, 1, 25, 90, 120, 2, 0, 1, -1, 'fn')");

        SiteDB.upgradeDB(db, 8);

        List<String> cols = columns(db, "HISTORY");
        assertEquals(23, cols.size());
        assertEquals("PAGE", cols.get(22));
        assertEquals("FAVORITE_NAME", cols.get(21));

        // The DAO reads the upgraded table correctly: pre-existing row keeps
        // all fields and gets the NOT NULL DEFAULT 0 page.
        DaoSession session = new DaoMaster(new StandardDatabase(db)).newSession();
        HistoryInfo loaded = session.getHistoryDao().load(2L);
        assertEquals("fn", loaded.favoriteName);
        assertEquals(1, loaded.mode);
        assertEquals(25, loaded.pages);
        assertEquals(-1, loaded.favoriteSlot);
        assertEquals(2000, loaded.time);
        assertEquals(0, loaded.page);

        // And the row is writable through the DAO after the upgrade (fresh
        // session so the reread hits the DB, not the identity scope).
        loaded.setPage(9);
        session.getHistoryDao().update(loaded);
        HistoryInfo reread = new DaoMaster(new StandardDatabase(db)).newSession()
                .getHistoryDao().load(2L);
        assertEquals(9, reread.getPage());
        assertEquals("fn", reread.favoriteName);
    }

    @Test
    public void testUpgradeFromV7CascadesThroughV8ToPage() {
        SQLiteDatabase db = newDb();
        // v7 HISTORY: 12 columns (pre gallery-detail-columns, see SiteDbSchemaV8Test).
        db.execSQL("CREATE TABLE \"HISTORY\" (" +
                "\"GID\" INTEGER PRIMARY KEY NOT NULL ," +
                "\"TOKEN\" TEXT," +
                "\"TITLE\" TEXT," +
                "\"TITLE_JPN\" TEXT," +
                "\"THUMB\" TEXT," +
                "\"CATEGORY\" INTEGER NOT NULL ," +
                "\"POSTED\" TEXT," +
                "\"UPLOADER\" TEXT," +
                "\"RATING\" REAL NOT NULL ," +
                "\"SIMPLE_LANGUAGE\" TEXT," +
                "\"MODE\" INTEGER NOT NULL ," +
                "\"TIME\" INTEGER NOT NULL );");
        db.execSQL("INSERT INTO \"HISTORY\" (GID, TITLE, CATEGORY, RATING, SIMPLE_LANGUAGE, MODE, TIME) " +
                "VALUES (3, 'h', 1, 3.5, 'EN', 1, 3000)");

        // Fall-through: case 7 runs the v7→v8 column adds, then case 8 adds PAGE.
        SiteDB.upgradeDB(db, 7);

        List<String> cols = columns(db, "HISTORY");
        assertEquals(23, cols.size());
        assertEquals("PAGE", cols.get(22));
        assertEquals("FAVORITE_NAME", cols.get(21));

        DaoSession session = new DaoMaster(new StandardDatabase(db)).newSession();
        HistoryInfo loaded = session.getHistoryDao().load(3L);
        assertEquals("h", loaded.title);
        assertEquals(3000, loaded.time);
        assertEquals(0, loaded.page);
    }
}
