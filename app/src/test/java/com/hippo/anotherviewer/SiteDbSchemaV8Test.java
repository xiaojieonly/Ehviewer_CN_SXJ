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

import com.hippo.anotherviewer.dao.BookmarkInfo;
import com.hippo.anotherviewer.dao.DaoMaster;
import com.hippo.anotherviewer.dao.DaoSession;
import com.hippo.anotherviewer.dao.DownloadInfo;
import com.hippo.anotherviewer.dao.HistoryInfo;
import com.hippo.anotherviewer.dao.LocalFavoriteInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.greenrobot.greendao.database.StandardDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Schema v8 tests: the v7 → v8 migration (new columns on DOWNLOADS, HISTORY,
 * LOCAL_FAVORITES, BOOKMARKS) and entity round trips through the v8 DAOs,
 * asserting the new columns survive insert → read.
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class SiteDbSchemaV8Test {

    private static final String V7_DOWNLOADS =
            "CREATE TABLE \"DOWNLOADS\" (" +
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
                    "\"STATE\" INTEGER NOT NULL ," +
                    "\"LEGACY\" INTEGER NOT NULL ," +
                    "\"TIME\" INTEGER NOT NULL ," +
                    "\"LABEL\" TEXT," +
                    "\"ARCHIVE_URI\" TEXT);";

    private static final String V6_DOWNLOADS = V7_DOWNLOADS.replace(",\"ARCHIVE_URI\" TEXT", "");

    private static final String V7_HISTORY =
            "CREATE TABLE \"HISTORY\" (" +
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
                    "\"TIME\" INTEGER NOT NULL );";

    private static final String V7_LOCAL_FAVORITES =
            "CREATE TABLE \"LOCAL_FAVORITES\" (" +
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
                    "\"TIME\" INTEGER NOT NULL );";

    private static final String V7_BOOKMARKS =
            "CREATE TABLE \"BOOKMARKS\" (" +
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
                    "\"PAGE\" INTEGER NOT NULL ," +
                    "\"TIME\" INTEGER NOT NULL );";

    private static final String[] DOWNLOAD_COLUMNS = {
            "FINISHED", "TOTAL", "FILE_SIZE", "LAST_MODIFIED", "RATED", "SIMPLE_TAGS",
            "PAGES", "THUMB_WIDTH", "THUMB_HEIGHT", "SPAN_SIZE", "SPAN_INDEX",
            "SPAN_GROUP_INDEX", "FAVORITE_SLOT", "FAVORITE_NAME"
    };

    private static final String[] GALLERY_COLUMNS = {
            "RATED", "SIMPLE_TAGS", "PAGES", "THUMB_WIDTH", "THUMB_HEIGHT",
            "SPAN_SIZE", "SPAN_INDEX", "SPAN_GROUP_INDEX", "FAVORITE_SLOT", "FAVORITE_NAME"
    };

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

    private static void assertHasColumns(SQLiteDatabase db, String table, String[] expected) {
        List<String> actual = columns(db, table);
        for (String column : expected) {
            assertTrue("missing column " + table + "." + column, actual.contains(column));
        }
    }

    @Test
    public void testUpgradeFromV7AddsColumnsAndKeepsData() {
        SQLiteDatabase db = newDb();
        db.execSQL(V7_DOWNLOADS);
        db.execSQL(V7_HISTORY);
        db.execSQL(V7_LOCAL_FAVORITES);
        db.execSQL(V7_BOOKMARKS);

        db.execSQL("INSERT INTO \"DOWNLOADS\" (GID, TOKEN, TITLE, CATEGORY, RATING, STATE, LEGACY, TIME, LABEL, ARCHIVE_URI) " +
                "VALUES (1, 'tok', 'title', 2, 4.5, 3, 1, 123456, 'lbl', 'content://x')");
        db.execSQL("INSERT INTO \"HISTORY\" (GID, TITLE, CATEGORY, RATING, SIMPLE_LANGUAGE, MODE, TIME) " +
                "VALUES (2, 'h', 1, 3.5, 'EN', 1, 2000)");
        db.execSQL("INSERT INTO \"LOCAL_FAVORITES\" (GID, TITLE, CATEGORY, RATING, TIME) VALUES (3, 'f', 1, 4.0, 3000)");
        db.execSQL("INSERT INTO \"BOOKMARKS\" (GID, TITLE, CATEGORY, RATING, PAGE, TIME) VALUES (4, 'b', 1, 2.5, 7, 4000)");

        SiteDB.upgradeDB(db, 7);

        assertHasColumns(db, "DOWNLOADS", DOWNLOAD_COLUMNS);
        assertHasColumns(db, "HISTORY", GALLERY_COLUMNS);
        assertHasColumns(db, "LOCAL_FAVORITES", GALLERY_COLUMNS);
        assertHasColumns(db, "BOOKMARKS", GALLERY_COLUMNS);

        // Old data preserved, new NOT NULL columns defaulted.
        try (Cursor cursor = db.rawQuery("SELECT GID, TOKEN, TITLE, STATE, TIME, FINISHED, TOTAL, RATED, SIMPLE_TAGS FROM \"DOWNLOADS\"", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(1, cursor.getLong(0));
            assertEquals("tok", cursor.getString(1));
            assertEquals("title", cursor.getString(2));
            assertEquals(3, cursor.getInt(3));
            assertEquals(123456, cursor.getLong(4));
            assertEquals(0, cursor.getInt(5));
            assertEquals(0, cursor.getInt(6));
            assertEquals(0, cursor.getInt(7));
            assertTrue(cursor.isNull(8));
        }
        try (Cursor cursor = db.rawQuery("SELECT GID, MODE, TIME FROM \"HISTORY\"", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(2, cursor.getLong(0));
            assertEquals(1, cursor.getInt(1));
            assertEquals(2000, cursor.getLong(2));
        }
        try (Cursor cursor = db.rawQuery("SELECT GID, TIME FROM \"LOCAL_FAVORITES\"", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(3, cursor.getLong(0));
            assertEquals(3000, cursor.getLong(1));
        }
        try (Cursor cursor = db.rawQuery("SELECT GID, PAGE, TIME FROM \"BOOKMARKS\"", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(4, cursor.getLong(0));
            assertEquals(7, cursor.getInt(1));
            assertEquals(4000, cursor.getLong(2));
        }
    }

    @Test
    public void testUpgradeFromV6AddsArchiveUriAndNewColumns() {
        SQLiteDatabase db = newDb();
        db.execSQL(V6_DOWNLOADS);
        db.execSQL("INSERT INTO \"DOWNLOADS\" (GID, TITLE, CATEGORY, RATING, STATE, LEGACY, TIME) " +
                "VALUES (1, 'old', 1, 4.0, 0, 0, 1000)");

        SiteDB.upgradeDB(db, 6);

        assertHasColumns(db, "DOWNLOADS", new String[] {"ARCHIVE_URI"});
        assertHasColumns(db, "DOWNLOADS", DOWNLOAD_COLUMNS);
    }

    @Test
    public void testUpgradeIsIdempotentOnAlreadyMigratedColumns() {
        SQLiteDatabase db = newDb();
        db.execSQL(V7_DOWNLOADS);
        db.execSQL(V7_HISTORY);
        db.execSQL(V7_LOCAL_FAVORITES);
        db.execSQL(V7_BOOKMARKS);
        SiteDB.upgradeDB(db, 7);
        // A second run (e.g. re-import of an already-migrated DB) must not throw.
        SiteDB.upgradeDB(db, 7);
        assertHasColumns(db, "DOWNLOADS", DOWNLOAD_COLUMNS);
        assertHasColumns(db, "HISTORY", GALLERY_COLUMNS);
    }

    @Test
    public void testFreshV8SchemaHasAllColumns() {
        SQLiteDatabase db = newDb();
        DaoMaster.createAllTables(new StandardDatabase(db), false);
        assertHasColumns(db, "DOWNLOADS", DOWNLOAD_COLUMNS);
        assertHasColumns(db, "HISTORY", GALLERY_COLUMNS);
        assertHasColumns(db, "LOCAL_FAVORITES", GALLERY_COLUMNS);
        assertHasColumns(db, "BOOKMARKS", GALLERY_COLUMNS);
    }

    @Test
    public void testDownloadInfoRoundTripWithNewColumns() {
        SQLiteDatabase db = newDb();
        DaoMaster.createAllTables(new StandardDatabase(db), false);
        DaoMaster daoMaster = new DaoMaster(new StandardDatabase(db));
        DaoSession session = daoMaster.newSession();

        DownloadInfo info = new DownloadInfo();
        info.gid = 42;
        info.token = "tok";
        info.title = "title";
        info.category = 2;
        info.rating = 4.5f;
        info.simpleLanguage = "EN";
        info.state = DownloadInfo.STATE_WAIT;
        info.legacy = 1;
        info.time = 123456;
        info.label = "lbl";
        info.archiveUri = "content://archive";
        info.finished = 3;
        info.total = 10;
        info.fileSize = 999L;
        info.lastModified = 8888L;
        info.rated = true;
        info.simpleTags = new String[] {"a", "b"};
        info.pages = 10;
        info.thumbWidth = 100;
        info.thumbHeight = 200;
        info.spanSize = 3;
        info.spanIndex = 1;
        info.spanGroupIndex = 2;
        info.favoriteSlot = 4;
        info.favoriteName = "favName";
        session.getDownloadsDao().insert(info);

        DownloadInfo loaded = session.getDownloadsDao().load(42L);
        assertEquals("title", loaded.title);
        assertEquals(DownloadInfo.STATE_WAIT, loaded.state);
        assertEquals(3, loaded.finished);
        assertEquals(10, loaded.total);
        assertEquals(999L, loaded.fileSize);
        assertEquals(8888L, loaded.lastModified);
        assertTrue(loaded.rated);
        assertTrue(Arrays.equals(new String[] {"a", "b"}, loaded.simpleTags));
        assertEquals(10, loaded.pages);
        assertEquals(100, loaded.thumbWidth);
        assertEquals(200, loaded.thumbHeight);
        assertEquals(3, loaded.spanSize);
        assertEquals(1, loaded.spanIndex);
        assertEquals(2, loaded.spanGroupIndex);
        assertEquals(4, loaded.favoriteSlot);
        assertEquals("favName", loaded.favoriteName);
    }

    @Test
    public void testGalleryEntityRoundTripsWithNewColumns() {
        SQLiteDatabase db = newDb();
        DaoMaster.createAllTables(new StandardDatabase(db), false);
        DaoMaster daoMaster = new DaoMaster(new StandardDatabase(db));
        DaoSession session = daoMaster.newSession();

        HistoryInfo history = new HistoryInfo();
        history.gid = 1;
        history.title = "h";
        history.mode = 1;
        history.time = 1000;
        history.rated = true;
        history.simpleTags = new String[] {"t1"};
        history.pages = 9;
        history.favoriteSlot = 3;
        history.favoriteName = "fn";
        session.getHistoryDao().insert(history);

        LocalFavoriteInfo fav = new LocalFavoriteInfo();
        fav.gid = 2;
        fav.title = "f";
        fav.time = 2000;
        fav.thumbWidth = 50;
        fav.thumbHeight = 60;
        fav.spanSize = 2;
        session.getLocalFavoritesDao().insert(fav);

        BookmarkInfo bookmark = new BookmarkInfo();
        bookmark.gid = 3;
        bookmark.title = "b";
        bookmark.page = 5;
        bookmark.time = 3000;
        bookmark.spanIndex = 0;
        bookmark.spanGroupIndex = 1;
        session.getBookmarksDao().insert(bookmark);

        HistoryInfo loadedHistory = session.getHistoryDao().load(1L);
        assertTrue(loadedHistory.rated);
        assertTrue(Arrays.equals(new String[] {"t1"}, loadedHistory.simpleTags));
        assertEquals(9, loadedHistory.pages);
        assertEquals(3, loadedHistory.favoriteSlot);
        assertEquals("fn", loadedHistory.favoriteName);

        LocalFavoriteInfo loadedFav = session.getLocalFavoritesDao().load(2L);
        assertEquals(50, loadedFav.thumbWidth);
        assertEquals(60, loadedFav.thumbHeight);
        assertEquals(2, loadedFav.spanSize);

        BookmarkInfo loadedBookmark = session.getBookmarksDao().load(3L);
        assertEquals(5, loadedBookmark.page);
        assertEquals(0, loadedBookmark.spanIndex);
        assertEquals(1, loadedBookmark.spanGroupIndex);
    }
}
