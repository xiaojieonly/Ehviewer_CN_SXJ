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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.hippo.anotherviewer.dao.DaoMaster;
import com.hippo.anotherviewer.dao.PageHashStore;

import org.greenrobot.greendao.database.StandardDatabase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The {@code page_file_hash} side table (plan 2026-09-19 A1, SiteDB schema
 * v10 = DaoMaster.SCHEMA_VERSION + 1):
 * <ul>
 *   <li>the v9 → v10 upgrade creates the table with the server-aligned
 *       columns, composite PK(gid, page) and column defaults;</li>
 *   <li>the v7 → v10 fall-through cascade reaches it too (and re-runs
 *       idempotently, as a re-import does);</li>
 *   <li>{@link PageHashStore} runs the full chain on an upgraded database:
 *       upsert → query → verify/bad marking → overwrite reset → delete;</li>
 *   <li>both real open paths get it: a fresh install (DBOpenHelper.onCreate)
 *       and an existing v9 database file upgraded through the new helper
 *       version, with pre-existing rows and the user_version bump surviving.</li>
 * </ul>
 */
// application 强制为朴素 Application：二进制资源模式下 manifest 的
// SiteApplication 仍会被实例化（Config.NONE 不再禁用它），其 onCreate 里的
// SiteDB.initialize 会预打开 v10 的 eh.db 并持句柄——Windows 下
// deleteDatabase 静默失败，testExistingV9File... 的旧版 helper 会撞上
// 降级保护（Can't downgrade 10→9）。本类自行初始化所需组件，不需要
// SiteApplication。
@Config(manifest = Config.NONE, application = android.app.Application.class)
@RunWith(RobolectricTestRunner.class)
public class SiteDbPageFileHashTest {

    /** The v7 HISTORY table: 12 columns, pre gallery-detail-columns. */
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

    private static final List<String> PAGE_FILE_HASH_COLUMNS = Arrays.asList(
            "gid", "page", "ext", "size", "hash", "algo", "origin",
            "created_at", "last_verified_at", "verdict");

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.application;
        AppConfig.initialize(context);
        Settings.initialize(context);
    }

    @After
    public void tearDown() {
        // 释放 eh.db 句柄：用例里 SiteDB.initialize 打开的连接在此 JVM 内常驻，
        // Windows 下句柄未释放时 deleteDatabase 静默失败，下一个用例的旧版
        // helper 会撞上遗留的 v10 文件（Can't downgrade 10→9）。
        SiteDB.closeForTest();
    }

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

    /** PRAGMA table_info pk flag of one column (0 = not part of the PK). */
    private static int pkFlag(SQLiteDatabase db, String table, String column) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(\"" + table + "\")", null)) {
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(1))) {
                    return cursor.getInt(5);
                }
            }
        }
        return 0;
    }

    /** A valid 64-hex string alternating the two characters. */
    private static String hexHash(char a, char b) {
        StringBuilder builder = new StringBuilder(64);
        for (int i = 0; i < 64; i++) {
            builder.append(i % 2 == 0 ? a : b);
        }
        return builder.toString();
    }

    private static void assertTableExists(SQLiteDatabase db, String table) {
        assertTrue("missing table " + table, !columns(db, table).isEmpty());
    }

    @Test
    public void testUpgradeFromV9CreatesAlignedSideTable() {
        SQLiteDatabase db = newDb();
        // The v9 (= current SCHEMA_VERSION) schema: greenDAO tables only.
        DaoMaster.createAllTables(new StandardDatabase(db), false);
        assertTrue(columns(db, PageHashStore.TABLE).isEmpty());

        SiteDB.upgradeDB(db, 9);

        assertEquals(PAGE_FILE_HASH_COLUMNS, columns(db, PageHashStore.TABLE));
        // Composite PK(gid, page): first and second PK members respectively.
        assertEquals(1, pkFlag(db, PageHashStore.TABLE, "gid"));
        assertEquals(2, pkFlag(db, PageHashStore.TABLE, "page"));

        // Server-aligned column defaults apply when only the key is supplied.
        db.execSQL("INSERT INTO page_file_hash (gid, page) VALUES (7, 3)");
        try (Cursor cursor = db.rawQuery(
                "SELECT ext, size, hash, algo, origin, created_at, last_verified_at, verdict " +
                        "FROM page_file_hash", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals("", cursor.getString(0));
            assertEquals(0, cursor.getLong(1));
            assertEquals("", cursor.getString(2));
            assertEquals("sha256", cursor.getString(3));
            assertEquals("downloader", cursor.getString(4));
            assertEquals(0, cursor.getLong(5));
            assertTrue(cursor.isNull(6));
            assertTrue(cursor.isNull(7));
        }

        // The composite key really is enforced.
        try {
            db.execSQL("INSERT INTO page_file_hash (gid, page) VALUES (7, 3)");
            fail("duplicate (gid, page) must violate the composite PK");
        } catch (SQLiteException expected) {
        }
    }

    @Test
    public void testUpgradeFromV7CascadesToSideTableAndIsIdempotent() {
        SQLiteDatabase db = newDb();
        db.execSQL(V7_HISTORY);
        db.execSQL("INSERT INTO \"HISTORY\" (GID, TITLE, CATEGORY, RATING, SIMPLE_LANGUAGE, MODE, TIME) " +
                "VALUES (3, 'h', 1, 3.5, 'EN', 1, 3000)");

        // Fall-through: cases 7 and 8 migrate HISTORY, case 9 adds the side table.
        SiteDB.upgradeDB(db, 7);

        assertEquals(23, columns(db, "HISTORY").size());
        assertEquals("PAGE", columns(db, "HISTORY").get(22));
        assertTableExists(db, PageHashStore.TABLE);

        try (Cursor cursor = db.rawQuery("SELECT GID, TIME, PAGE FROM \"HISTORY\"", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(3, cursor.getLong(0));
            assertEquals(3000, cursor.getLong(1));
            assertEquals(0, cursor.getInt(2));
        }

        // Re-run (a re-import of an already-migrated database) must not throw.
        SiteDB.upgradeDB(db, 7);
        assertTableExists(db, PageHashStore.TABLE);
        assertEquals(23, columns(db, "HISTORY").size());
    }

    @Test
    public void testPageHashStoreFullChainOnUpgradedDb() {
        SQLiteDatabase db = newDb();
        DaoMaster.createAllTables(new StandardDatabase(db), false);
        SiteDB.upgradeDB(db, 9);
        PageHashStore.init(db);

        String hashA = hexHash('a', 'b');
        String hashB = hexHash('c', 'd');

        assertNull(PageHashStore.queryPage(1, 0));

        PageHashStore.upsert(1, 0, "jpg", 12345L, hashA, PageHashStore.ORIGIN_DOWNLOADER);
        PageHashStore.upsert(1, 1, "gif", 999L, hashB.toUpperCase(Locale.US),
                PageHashStore.ORIGIN_READ_SYNC);
        PageHashStore.upsert(2, 0, "png", 2048L, hashA, PageHashStore.ORIGIN_DOWNLOADER);

        PageHashStore.PageFileHash row = PageHashStore.queryPage(1, 0);
        assertNotNull(row);
        assertEquals(1, row.gid);
        assertEquals(0, row.page);
        assertEquals("jpg", row.ext);
        assertEquals(12345L, row.size);
        assertEquals(hashA, row.hash);
        assertEquals("sha256", row.algo);
        assertEquals(PageHashStore.ORIGIN_DOWNLOADER, row.origin);
        assertTrue(row.createdAt > 0);
        assertEquals(0, row.lastVerifiedAt);
        assertNull(row.verdict);

        // Uppercase input hashes are normalized to lowercase.
        assertEquals(hashB, PageHashStore.queryPage(1, 1).hash);

        // Verification pass: verdict=ok + the caller's timestamp.
        PageHashStore.markVerified(1, 0, 5000L);
        row = PageHashStore.queryPage(1, 0);
        assertEquals(PageHashStore.VERDICT_OK, row.verdict);
        assertEquals(5000L, row.lastVerifiedAt);

        // Failed pass: struct_bad, stamped with the scan time.
        long beforeBad = System.currentTimeMillis() - 1;
        PageHashStore.markBad(1, 1, PageHashStore.VERDICT_STRUCT_BAD);
        row = PageHashStore.queryPage(1, 1);
        assertEquals(PageHashStore.VERDICT_STRUCT_BAD, row.verdict);
        assertTrue(row.lastVerifiedAt >= beforeBad);

        // Marking a page without a baseline is a silent no-op, not an insert.
        PageHashStore.markVerified(404, 0, 1L);
        PageHashStore.markBad(404, 0, PageHashStore.VERDICT_STRUCT_BAD);
        assertNull(PageHashStore.queryPage(404, 0));

        // Same (gid, page) again = new bytes = full baseline reset (heal path).
        PageHashStore.upsert(1, 0, "jpg", 222L, hashB, PageHashStore.ORIGIN_HEAL);
        row = PageHashStore.queryPage(1, 0);
        assertEquals(222L, row.size);
        assertEquals(hashB, row.hash);
        assertEquals(PageHashStore.ORIGIN_HEAL, row.origin);
        assertEquals(0, row.lastVerifiedAt);
        assertNull(row.verdict);

        // Per-gallery reads, page ascending; other galleries untouched.
        List<PageHashStore.PageFileHash> rows = PageHashStore.queryByGallery(1);
        assertEquals(2, rows.size());
        assertEquals(0, rows.get(0).page);
        assertEquals(1, rows.get(1).page);
        assertEquals(1, PageHashStore.queryByGallery(2).size());

        assertEquals(2, PageHashStore.deleteByGallery(1));
        assertTrue(PageHashStore.queryByGallery(1).isEmpty());
        assertEquals(1, PageHashStore.queryByGallery(2).size());
        assertEquals(0, PageHashStore.deleteByGallery(1));

        // A malformed hash is rejected before any write happens.
        try {
            PageHashStore.upsert(1, 5, "jpg", 1L, "not-a-hash", PageHashStore.ORIGIN_DOWNLOADER);
            fail("non-64-hex hash must be rejected");
        } catch (IllegalArgumentException expected) {
        }
        assertNull(PageHashStore.queryPage(1, 5));
    }

    @Test
    public void testFreshInstallCreatesSideTableAndWiresStore() {
        Context context = RuntimeEnvironment.application;
        // The database file survives between test methods in this JVM; make
        // this a true fresh install regardless of execution order.
        context.deleteDatabase("eh.db");
        SiteDB.initialize(context);

        // No extra wiring: SiteDB.initialize hands its connection to the store.
        String hash = hexHash('1', 'f');
        PageHashStore.upsert(11, 2, "jpg", 4096L, hash, PageHashStore.ORIGIN_DOWNLOADER);
        PageHashStore.PageFileHash row = PageHashStore.queryPage(11, 2);
        assertNotNull(row);
        assertEquals(hash, row.hash);
        assertEquals("jpg", row.ext);
        assertEquals(4096L, row.size);

        // The file carries SiteDB's own version (greenDAO SCHEMA_VERSION + 1).
        SQLiteDatabase reopened = SQLiteDatabase.openDatabase(
                context.getDatabasePath("eh.db").getPath(), null,
                SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        try (Cursor cursor = reopened.rawQuery("PRAGMA user_version", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(SiteDB.DB_VERSION, cursor.getInt(0));
        } finally {
            reopened.close();
        }
    }

    @Test
    public void testExistingV9FileUpgradesThroughOpenHelper() {
        Context context = RuntimeEnvironment.application;
        // The database file survives between test methods in this JVM; clear
        // any leftover so the old v9 helper below does not see a downgrade.
        context.deleteDatabase("eh.db");

        // Simulate the pre-side-table install: a greenDAO-only v9 database
        // file, declared at SCHEMA_VERSION by the old helper.
        DaoMaster.OpenHelper oldHelper = new DaoMaster.OpenHelper(context, "eh.db", null) {
        };
        SQLiteDatabase oldDb = oldHelper.getWritableDatabase();
        assertEquals(DaoMaster.SCHEMA_VERSION, oldDb.getVersion());
        oldDb.execSQL("INSERT INTO \"QUICK_SEARCH\" " +
                "(NAME, MODE, CATEGORY, KEYWORD, ADVANCE_SEARCH, MIN_RATING, PAGE_FROM, PAGE_TO, TIME) " +
                "VALUES ('kw', 0, 1, 'tag', 0, 0, -1, -1, 1000)");
        oldHelper.close();

        // The new helper declares DB_VERSION: opening the same file must run
        // upgradeDB(9) and land at v10 with the side table ready.
        SiteDB.initialize(context);

        String hash = hexHash('9', 'e');
        PageHashStore.upsert(21, 0, "png", 777L, hash, PageHashStore.ORIGIN_READ_SYNC);
        assertNotNull(PageHashStore.queryPage(21, 0));

        SQLiteDatabase reopened = SQLiteDatabase.openDatabase(
                context.getDatabasePath("eh.db").getPath(), null,
                SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        try {
            try (Cursor cursor = reopened.rawQuery("PRAGMA user_version", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(SiteDB.DB_VERSION, cursor.getInt(0));
            }
            // Pre-existing rows survive the upgrade untouched.
            try (Cursor cursor = reopened.rawQuery(
                    "SELECT NAME, TIME FROM \"QUICK_SEARCH\"", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals("kw", cursor.getString(0));
                assertEquals(1000, cursor.getLong(1));
            }
            assertTableExists(reopened, PageHashStore.TABLE);
        } finally {
            reopened.close();
        }
    }
}
