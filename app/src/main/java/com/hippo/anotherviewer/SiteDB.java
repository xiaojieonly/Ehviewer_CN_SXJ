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

package com.hippo.anotherviewer;

import static com.hippo.anotherviewer.ui.fragment.AdvancedFragment.DB_LOADING;
import static com.hippo.anotherviewer.ui.fragment.AdvancedFragment.LOADING_PROGRESS;
import static com.hippo.anotherviewer.ui.fragment.AdvancedFragment.LOADING_STATUS;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.anotherviewer.client.data.GalleryInfo;
import com.hippo.anotherviewer.client.data.ListUrlBuilder;
import com.hippo.anotherviewer.dao.BlackList;
import com.hippo.anotherviewer.dao.BlackListDao;
import com.hippo.anotherviewer.dao.BookmarkInfo;
import com.hippo.anotherviewer.dao.DaoMaster;
import com.hippo.anotherviewer.dao.DaoSession;
import com.hippo.anotherviewer.dao.DownloadDirname;
import com.hippo.anotherviewer.dao.DownloadDirnameDao;
import com.hippo.anotherviewer.dao.DownloadInfo;
import com.hippo.anotherviewer.dao.DownloadLabel;
import com.hippo.anotherviewer.dao.DownloadLabelDao;
import com.hippo.anotherviewer.dao.DownloadsDao;
import com.hippo.anotherviewer.dao.Filter;
import com.hippo.anotherviewer.dao.FilterDao;
import com.hippo.anotherviewer.dao.GalleryTags;
import com.hippo.anotherviewer.dao.GalleryTagsDao;
import com.hippo.anotherviewer.dao.HistoryDao;
import com.hippo.anotherviewer.dao.HistoryInfo;
import com.hippo.anotherviewer.dao.LocalFavoriteInfo;
import com.hippo.anotherviewer.dao.LocalFavoritesDao;
import com.hippo.anotherviewer.dao.PageHashStore;
import com.hippo.anotherviewer.dao.QuickSearch;
import com.hippo.anotherviewer.dao.QuickSearchDao;
import com.hippo.anotherviewer.download.DownloadManager;
import com.hippo.util.ExceptionUtils;
import com.hippo.util.SqlUtils;
import com.hippo.lib.yorozuya.IOUtils;
import com.hippo.lib.yorozuya.ObjectUtils;
import com.hippo.lib.yorozuya.collect.SparseJLArray;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.database.StandardDatabase;
import org.greenrobot.greendao.query.CloseableListIterator;
import org.greenrobot.greendao.query.LazyList;
import org.greenrobot.greendao.query.QueryBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class SiteDB {

    private static final String TAG = SiteDB.class.getSimpleName();

    /**
     * 同步范围内任一公开写方法的「脏」标志（WP5-4 无脏快路径判据）。
     * WebUiSyncEngine 在每个周期头部 getAndSet(false) 取走：false 且 8 个
     * pending 集合全空时走快路径（只 pull+apply，不发 push）。所有会改变
     * 同步实体集合/内容的写路径都必须置位，否则对应的本地变更会被快路径
     * 漏推一个周期（删除检测靠快照差分，快路径跳过该差分）。置位用
     * set(true) 保持粘性——周期进行中的并发写入不会被周期尾的读取吞掉。
     */
    public static final AtomicBoolean SYNC_DIRTY = new AtomicBoolean(false);

    /** 置位同步脏标志；引擎据此决定下个周期走全量差分还是快路径。 */
    public static void markSyncDirty() {
        SYNC_DIRTY.set(true);
    }

    /**
     * 因 MAX_HISTORY_COUNT 上限被裁剪的历史 gid。这些行本地已不存在但并非
     * 用户主动删除：WebUiSyncEngine 同步删除检测时必须把它们并入「本地现存」，
     * 否则上限裁剪会被当作删除推给服务器，把配对设备的历史逐条硬删
     * （行被裁剪后本地不再存在，不可能再被用户删除，故永久排除是安全的；
     * 重新阅读同一画廊时行会回到本地现存集，排除项只是冗余不再生效）。
     */
    private static final Set<Long> sCapPrunedHistoryGids = new HashSet<>();

    /**
     * 白名单的持久化通道（WP4-1）。sCapPrunedHistoryGids 是进程内存活集合，
     * 进程重启即清零——若不落盘，重启后「上限裁剪」会被删除检测当成用户删除
     * 推墓碑给服务器。由 SiteDbWebUiSyncStore.wireCapPrunedPersistence 注册
     * 生产实现（webui_sync_state prefs）；测试注册内存假实现。
     * {@link #save} 全量落盘（幂等）；{@link #load} 在注册时被调用一次。
     */
    public interface CapPrunedPersistence {
        void save(Set<Long> gids);

        Set<Long> load();
    }

    private static volatile CapPrunedPersistence sCapPrunedPersistence;

    /**
     * 注册白名单持久化回调；传 {@code null} 撤销（回到纯内存行为）。
     * 注册时立即 load 一次并把结果<b>并集灌入</b>内存集合：不覆盖会话内
     * 已登记的新条目（注册可能发生在若干裁剪之后），异常静默——持久化通道
     * 故障只损失重启恢复能力，不得影响主流程。
     */
    public static void setCapPrunedPersistence(@Nullable CapPrunedPersistence persistence) {
        synchronized (SiteDB.class) {
            sCapPrunedPersistence = persistence;
            if (persistence == null) {
                return;
            }
            try {
                Set<Long> persisted = persistence.load();
                if (persisted != null) {
                    sCapPrunedHistoryGids.addAll(persisted);
                }
            } catch (Exception e) {
                // 静默：load 失败保持内存现状（等价旧版无持久化行为）。
            }
        }
    }

    /** 清空内存白名单。仅供测试模拟进程重启后的空集起点。 */
    public static void clearCapPrunedHistoryGids() {
        synchronized (SiteDB.class) {
            sCapPrunedHistoryGids.clear();
        }
    }

    /**
     * 全量落盘当前白名单。必须在持有 SiteDB.class 锁的写点调用（与登记
     * 原子）；失败静默——内存白名单仍然有效，下次登记会重试落盘。
     */
    private static void saveCapPrunedHistoryGidsLocked() {
        CapPrunedPersistence persistence = sCapPrunedPersistence;
        if (persistence == null) {
            return;
        }
        try {
            persistence.save(new HashSet<>(sCapPrunedHistoryGids));
        } catch (Exception e) {
            // 静默：落盘失败不影响内存白名单与裁剪本身。
        }
    }

    /** 供同步引擎把上限裁剪的 gid 并入删除检测的「本地现存」集合。 */
    public static Set<Long> getCapPrunedHistoryGids() {
        synchronized (SiteDB.class) {
            return new HashSet<>(sCapPrunedHistoryGids);
        }
    }

    public static int MAX_HISTORY_COUNT = 100;

    private static DaoSession sDaoSession;

    private static boolean sHasOldDB;
    private static boolean sNewDB;

    /**
     * SiteDB's own schema version. The greenDAO tables track
     * {@link DaoMaster#SCHEMA_VERSION} (generated code, not to be edited), so
     * the raw side tables ({@code page_file_hash}, plan 2026-09-19 A1) ride
     * one version above it: {@link DBOpenHelper} declares this version and
     * {@link #upgradeDB} migrates any older database. When greenDAO's schema
     * eventually regenerates at 10, this becomes 11 and the new greenDAO
     * migration is added as the next case — {@code upgradeDB} stays the single
     * migration funnel.
     */
    static final int DB_VERSION = DaoMaster.SCHEMA_VERSION + 1;

    private static class DBOpenHelper extends SQLiteOpenHelper {

        public DBOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory) {
            super(context, name, factory, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // Same table creation DaoMaster.OpenHelper used to do, plus the
            // side tables (fresh installs never pass through upgradeDB).
            DaoMaster.createAllTables(new StandardDatabase(db), false);
            createPageFileHash(db);
            sNewDB = true;
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            upgradeDB(db, oldVersion);
        }
    }

    /**
     * Adds a column to a table, tolerating the column already existing
     * (e.g. a partially applied previous migration or a re-run). Matches the
     * style of the v6→v7 ARCHIVE_URI migration.
     */
    private static void addColumn(SQLiteDatabase db, String table, String column, String definition) {
        try {
            db.execSQL("ALTER TABLE \"" + table + "\" ADD COLUMN \"" + column + "\" " + definition);
        } catch (Exception e) {
            // Column might already exist, ignore the error
            Log.w("SiteDB", "Failed to add " + column + " column to " + table + ", might already exist", e);
            Analytics.recordException(e);
        }
    }

    static void upgradeDB(SQLiteDatabase db, int oldVersion) {
        switch (oldVersion) {
//            case 1: // 1 to 2, add FILTER
//                FilterDao.createTable(db, true);
            case 2: // 2 to 3, add ENABLE column to table FILTER
                db.execSQL("CREATE TABLE " + "\"FILTER2\" (" +
                        "\"_id\" INTEGER PRIMARY KEY ," +
                        "\"MODE\" INTEGER NOT NULL ," +
                        "\"TEXT\" TEXT," +
                        "\"ENABLE\" INTEGER);");
                db.execSQL("INSERT INTO \"FILTER2\" (" +
                        "_id, MODE, TEXT, ENABLE)" +
                        "SELECT _id, MODE, TEXT, 1 FROM [FILTER];");
                db.execSQL("DROP TABLE [FILTER]");
                db.execSQL("ALTER TABLE FILTER2 RENAME TO [FILTER]");
            case 3: // 3 to 4, add PAGE_FROM and PAGE_TO column to QUICK_SEARCH
                db.execSQL("CREATE TABLE " + "\"QUICK_SEARCH2\" (" +
                        "\"_id\" INTEGER PRIMARY KEY ," +
                        "\"NAME\" TEXT," +
                        "\"MODE\" INTEGER NOT NULL ," +
                        "\"CATEGORY\" INTEGER NOT NULL ," +
                        "\"KEYWORD\" TEXT," +
                        "\"ADVANCE_SEARCH\" INTEGER NOT NULL ," +
                        "\"MIN_RATING\" INTEGER NOT NULL ," +
                        "\"PAGE_FROM\" INTEGER NOT NULL ," +
                        "\"PAGE_TO\" INTEGER NOT NULL ," +
                        "\"TIME\" INTEGER NOT NULL );");
                db.execSQL("INSERT INTO \"QUICK_SEARCH2\" (" +
                        "_id, NAME, MODE, CATEGORY, KEYWORD, ADVANCE_SEARCH, MIN_RATING, PAGE_FROM, PAGE_TO, TIME)" +
                        "SELECT _id, NAME, MODE, CATEGORY, KEYWORD, ADVANCE_SEARCH, MIN_RATING, -1, -1, TIME FROM QUICK_SEARCH;");
                db.execSQL("DROP TABLE QUICK_SEARCH");
                db.execSQL("ALTER TABLE QUICK_SEARCH2 RENAME TO QUICK_SEARCH");
            case 4:
                db.execSQL("DROP TABLE IF EXISTS \"Black_List\"");
                db.execSQL("CREATE TABLE " + "\"Black_List\" (" + //
                        "\"_id\" INTEGER PRIMARY KEY AUTOINCREMENT ," + // 0: id
                        "\"BADGAYNAME\" TEXT," + // 1: badgayname
                        "\"REASON\" TEXT," + // 2: reason
                        "\"ANGRYWITH\" TEXT," + // 3: angrywith
                        "\"ADD_TIME\" TEXT," + // 4: add_time
                        "\"MODE\" INTEGER);");
            case 5:
                db.execSQL("DROP TABLE IF EXISTS \"Gallery_Tags\"");
                db.execSQL("CREATE TABLE " + "\"Gallery_Tags\" (" + //
                        "\"GID\" INTEGER PRIMARY KEY NOT NULL ," + // 0: gid
                        "\"ROWS\" TEXT," + // 1: rows
                        "\"ARTIST\" TEXT," + // 2: artist
                        "\"COSPLAYER\" TEXT," + // 3: cosplayer
                        "\"CHARACTER\" TEXT," + // 4: character
                        "\"FEMALE\" TEXT," + // 5: female
                        "\"GROUP\" TEXT," + // 6: group
                        "\"LANGUAGE\" TEXT," + // 7: language
                        "\"MALE\" TEXT," + // 8: male
                        "\"MISC\" TEXT," + // 9: misc
                        "\"MIXED\" TEXT," + // 10: mixed
                        "\"OTHER\" TEXT," + // 11: other
                        "\"PARODY\" TEXT," + // 12: parody
                        "\"RECLASS\" TEXT," + // 13: reclass
                        "\"CREATE_TIME\" INTEGER," + // 14: create_time
                        "\"UPDATE_TIME\" INTEGER);"); // 15: update_time
            case 6: // 6 to 7, add ARCHIVE_URI column to DOWNLOADS table
                try {
                    db.execSQL("ALTER TABLE \"DOWNLOADS\" ADD COLUMN \"ARCHIVE_URI\" TEXT");
                } catch (Exception e) {
                    // Column might already exist, ignore the error
                    Log.w("SiteDB", "Failed to add ARCHIVE_URI column, might already exist", e);
                    Analytics.recordException(e);
                }
            case 7: // 7 to 8, add sync metadata + gallery detail columns to the four gallery tables
                // DOWNLOADS (14 new columns; NOT NULL columns need a DEFAULT so
                // ALTER TABLE succeeds on tables that already contain rows)
                addColumn(db, "DOWNLOADS", "FINISHED", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "DOWNLOADS", "TOTAL", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "DOWNLOADS", "FILE_SIZE", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "DOWNLOADS", "LAST_MODIFIED", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "DOWNLOADS", "RATED", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "DOWNLOADS", "SIMPLE_TAGS", "TEXT");
                addColumn(db, "DOWNLOADS", "PAGES", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "DOWNLOADS", "THUMB_WIDTH", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "DOWNLOADS", "THUMB_HEIGHT", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "DOWNLOADS", "SPAN_SIZE", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "DOWNLOADS", "SPAN_INDEX", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "DOWNLOADS", "SPAN_GROUP_INDEX", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "DOWNLOADS", "FAVORITE_SLOT", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "DOWNLOADS", "FAVORITE_NAME", "TEXT");
                // HISTORY
                addColumn(db, "HISTORY", "RATED", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "HISTORY", "SIMPLE_TAGS", "TEXT");
                addColumn(db, "HISTORY", "PAGES", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "HISTORY", "THUMB_WIDTH", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "HISTORY", "THUMB_HEIGHT", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "HISTORY", "SPAN_SIZE", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "HISTORY", "SPAN_INDEX", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "HISTORY", "SPAN_GROUP_INDEX", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "HISTORY", "FAVORITE_SLOT", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "HISTORY", "FAVORITE_NAME", "TEXT");
                // LOCAL_FAVORITES
                addColumn(db, "LOCAL_FAVORITES", "RATED", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "LOCAL_FAVORITES", "SIMPLE_TAGS", "TEXT");
                addColumn(db, "LOCAL_FAVORITES", "PAGES", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "LOCAL_FAVORITES", "THUMB_WIDTH", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "LOCAL_FAVORITES", "THUMB_HEIGHT", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "LOCAL_FAVORITES", "SPAN_SIZE", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "LOCAL_FAVORITES", "SPAN_INDEX", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "LOCAL_FAVORITES", "SPAN_GROUP_INDEX", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "LOCAL_FAVORITES", "FAVORITE_SLOT", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "LOCAL_FAVORITES", "FAVORITE_NAME", "TEXT");
                // BOOKMARKS
                addColumn(db, "BOOKMARKS", "RATED", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "BOOKMARKS", "SIMPLE_TAGS", "TEXT");
                addColumn(db, "BOOKMARKS", "PAGES", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "BOOKMARKS", "THUMB_WIDTH", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "BOOKMARKS", "THUMB_HEIGHT", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "BOOKMARKS", "SPAN_SIZE", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "BOOKMARKS", "SPAN_INDEX", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "BOOKMARKS", "SPAN_GROUP_INDEX", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "BOOKMARKS", "FAVORITE_SLOT", "INTEGER NOT NULL DEFAULT 0");
                addColumn(db, "BOOKMARKS", "FAVORITE_NAME", "TEXT");
            case 8: // 8 to 9, add reading-progress PAGE column to HISTORY
                addColumn(db, "HISTORY", "PAGE", "INTEGER NOT NULL DEFAULT 0");
            case 9: // 9 to 10, add page_file_hash side table (file-integrity baseline, plan 2026-09-19 A1)
                createPageFileHash(db);
        }
    }

    /**
     * DDL of the {@code page_file_hash} side table (download file-integrity
     * baseline, plan 2026-09-19 A1): PK(gid, page), columns aligned with the
     * server entity {@code PageFileHashEntity} — algo defaults to
     * {@code sha256}, origin defaults to {@code downloader}, timestamps are
     * epoch millis, and last_verified_at/verdict stay nullable (never
     * scanned). Intentionally outside greenDAO: created on fresh installs by
     * {@code DBOpenHelper.onCreate} and on upgrades/imports by
     * {@code upgradeDB} case 9. IF NOT EXISTS keeps re-runs (a re-import of
     * an already-migrated database) harmless.
     */
    private static void createPageFileHash(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS \"page_file_hash\" (" +
                "\"gid\" INTEGER NOT NULL ," +
                "\"page\" INTEGER NOT NULL ," +
                "\"ext\" TEXT NOT NULL DEFAULT '' ," +
                "\"size\" INTEGER NOT NULL DEFAULT 0 ," +
                "\"hash\" TEXT NOT NULL DEFAULT '' ," +
                "\"algo\" TEXT NOT NULL DEFAULT 'sha256' ," +
                "\"origin\" TEXT NOT NULL DEFAULT 'downloader' ," +
                "\"created_at\" INTEGER NOT NULL DEFAULT 0 ," +
                "\"last_verified_at\" INTEGER ," +
                "\"verdict\" TEXT," +
                "PRIMARY KEY (\"gid\", \"page\"));");
    }

    private static class OldDBHelper extends SQLiteOpenHelper {

        private static final String DB_NAME = "data";
        private static final int VERSION = 6;

        private static final String TABLE_GALLERY = "gallery";
        private static final String TABLE_LOCAL_FAVOURITE = "local_favourite";
        private static final String TABLE_TAG = "tag";
        private static final String TABLE_DOWNLOAD = "download";
        private static final String TABLE_HISTORY = "history";

        public OldDBHelper(Context context) {
            super(context, DB_NAME, null, VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }

    public static void initialize(Context context) {
        sHasOldDB = context.getDatabasePath("data").exists();

        DBOpenHelper helper = new DBOpenHelper(
                context.getApplicationContext(), "eh.db", null);

        SQLiteDatabase db = helper.getWritableDatabase();
        // Side-table stores share this one connection (the greenDAO session
        // keeps its own wrapper view of the same database).
        PageHashStore.init(db);
        DaoMaster daoMaster = new DaoMaster(db);

        sDaoSession = daoMaster.newSession();
        MAX_HISTORY_COUNT = Settings.getHistoryInfoSize();
    }

    /**
     * 仅供测试：关闭并丢弃共享 DaoSession 与底层连接，释放 eh.db 的文件句柄。
     * Windows 下打开中的文件无法被 deleteDatabase 删除，遗留的 v10 文件会让
     * 「模拟旧版 v9」的 helper 走进降级路径（Can't downgrade 10→9）。生产路径
     * 不调用（单进程常驻会话，随进程退出释放）。
     */
    static synchronized void closeForTest() {
        DaoSession session = sDaoSession;
        sDaoSession = null;
        if (session != null) {
            try {
                session.getDatabase().close();
            } catch (Exception ignored) {
                // 释放失败只影响测试清理，不影响任何生产语义。
            }
        }
    }

    public static boolean needMerge() {
        return sNewDB && sHasOldDB;
    }

    public static void mergeOldDB(Context context) {
        sNewDB = false;

        OldDBHelper oldDBHelper = new OldDBHelper(context);
        SQLiteDatabase oldDB;
        try {
            oldDB = oldDBHelper.getReadableDatabase();
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            return;
        }

        // Get GalleryInfo list
        SparseJLArray<GalleryInfo> map = new SparseJLArray<>();
        try {
            Cursor cursor = oldDB.rawQuery("select * from " + OldDBHelper.TABLE_GALLERY, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        GalleryInfo gi = new GalleryInfo();
                        gi.gid = cursor.getInt(0);
                        gi.token = cursor.getString(1);
                        gi.title = cursor.getString(2);
                        gi.posted = cursor.getString(3);
                        gi.category = cursor.getInt(4);
                        gi.thumb = cursor.getString(5);
                        gi.uploader = cursor.getString(6);
                        try {
                            // In 0.6.x version, NaN is stored
                            gi.rating = cursor.getFloat(7);
                        } catch (Throwable e) {
                            ExceptionUtils.throwIfFatal(e);
                            gi.rating = -1.0f;
                        }

                        map.put(gi.gid, gi);

                        cursor.moveToNext();
                    }
                }
                cursor.close();
            }
        } catch (Throwable i) {
            ExceptionUtils.throwIfFatal(i);
        }

        // Merge local favorites
        try {
            Cursor cursor = oldDB.rawQuery("select * from " + OldDBHelper.TABLE_LOCAL_FAVOURITE, null);
            if (cursor != null) {
                LocalFavoritesDao dao = sDaoSession.getLocalFavoritesDao();
                if (cursor.moveToFirst()) {
                    long i = 0L;
                    while (!cursor.isAfterLast()) {
                        // Get GalleryInfo first
                        long gid = cursor.getInt(0);
                        GalleryInfo gi = map.get(gid);
                        if (gi == null) {
                            Log.e(TAG, "Can't get GalleryInfo with gid: " + gid);
                            cursor.moveToNext();
                            continue;
                        }

                        LocalFavoriteInfo info = new LocalFavoriteInfo(gi);
                        info.setTime(i);
                        dao.insert(info);
                        cursor.moveToNext();
                        i++;
                    }
                }
                cursor.close();
            }
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            // Ignore
        }


        // Merge quick search
        try {
            Cursor cursor = oldDB.rawQuery("select * from " + OldDBHelper.TABLE_TAG, null);
            if (cursor != null) {
                QuickSearchDao dao = sDaoSession.getQuickSearchDao();
                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        QuickSearch quickSearch = new QuickSearch();

                        int mode = cursor.getInt(2);
                        String search = cursor.getString(4);
                        String tag = cursor.getString(7);
                        if (mode == ListUrlBuilder.MODE_UPLOADER && search != null &&
                                search.startsWith("uploader:")) {
                            search = search.substring("uploader:".length());
                        }

                        quickSearch.setTime((long) cursor.getInt(0));
                        quickSearch.setName(cursor.getString(1));
                        quickSearch.setMode(mode);
                        quickSearch.setCategory(cursor.getInt(3));
                        quickSearch.setKeyword(mode == ListUrlBuilder.MODE_TAG ? tag : search);
                        quickSearch.setAdvanceSearch(cursor.getInt(5));
                        quickSearch.setMinRating(cursor.getInt(6));

                        dao.insert(quickSearch);
                        cursor.moveToNext();
                    }
                }
                cursor.close();
            }
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            // Ignore
        }

        // Merge download info
        try {
            Cursor cursor = oldDB.rawQuery("select * from " + OldDBHelper.TABLE_DOWNLOAD, null);
            if (cursor != null) {
                DownloadsDao dao = sDaoSession.getDownloadsDao();
                if (cursor.moveToFirst()) {
                    long i = 0L;
                    while (!cursor.isAfterLast()) {
                        // Get GalleryInfo first
                        long gid = cursor.getInt(0);
                        GalleryInfo gi = map.get(gid);
                        if (gi == null) {
                            Log.e(TAG, "Can't get GalleryInfo with gid: " + gid);
                            cursor.moveToNext();
                            continue;
                        }

                        DownloadInfo info = new DownloadInfo(gi);
                        int state = cursor.getInt(2);
                        int legacy = cursor.getInt(3);
                        if (state == DownloadInfo.STATE_FINISH && legacy > 0) {
                            state = DownloadInfo.STATE_FAILED;
                        }
                        info.setState(state);
                        info.setLegacy(legacy);
                        if (cursor.getColumnCount() == 5) {
                            info.setTime(cursor.getLong(4));
                        } else {
                            info.setTime(i);
                        }
                        dao.insert(info);
                        cursor.moveToNext();
                        i++;
                    }
                }
                cursor.close();
            }
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            // Ignore
        }

        try {
            // Merge history info
            Cursor cursor = oldDB.rawQuery("select * from " + OldDBHelper.TABLE_HISTORY, null);
            if (cursor != null) {
                HistoryDao dao = sDaoSession.getHistoryDao();
                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        // Get GalleryInfo first
                        long gid = cursor.getInt(0);
                        GalleryInfo gi = map.get(gid);
                        if (gi == null) {
                            Log.e(TAG, "Can't get GalleryInfo with gid: " + gid);
                            cursor.moveToNext();
                            continue;
                        }

                        HistoryInfo info = new HistoryInfo(gi);
                        info.setMode(cursor.getInt(1));
                        info.setTime(cursor.getLong(2));
                        dao.insert(info);
                        cursor.moveToNext();
                    }
                }
                cursor.close();
            }
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            // Ignore
        }

        try {
            oldDBHelper.close();
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            // Ignore
        }
    }

    public static synchronized List<DownloadInfo> getAllDownloadInfo() {
        DownloadsDao dao = sDaoSession.getDownloadsDao();
        // No ARCHIVE_URI fallback ALTER here anymore: since schema v8 the column
        // always exists (v8 createTable includes it, and upgradeDB case 7 adds
        // it to any older DB before this is reachable). The raw ALTER used to
        // live in a SQLiteException catch around this query.
        // The WAIT/DOWNLOAD states are returned as-is (no "Fix state" pass):
        // DownloadManager owns state transitions, and the WebUI sync engine
        // needs the real states to propagate them (B3).
        return dao.queryBuilder().orderDesc(DownloadsDao.Properties.Time).list();
    }

    public static synchronized void moveDownloadInfo(List<DownloadInfo> infos, int fromPosition, int toPosition){
        if (fromPosition == toPosition) {
            return;
        }
        markSyncDirty(); // 顺序调整会改 time（推送 lastModified），同步范围写
        DownloadsDao dao = sDaoSession.getDownloadsDao();
        boolean reverse = fromPosition > toPosition;
        int offset = reverse ? toPosition : fromPosition;
        int limit = reverse ? fromPosition - toPosition + 1 : toPosition - fromPosition + 1;

        List<DownloadInfo> list = infos.subList(offset, offset + limit);

        int step = reverse ? 1 : -1;
        int start = reverse ? limit - 1 : 0;
        int end = reverse ? 0 : limit - 1;
        long toTime = list.get(end).time;
        for(int i = end; reverse ? i < start : i > start; i += step) {
            list.get(i).setTime(list.get(i + step).getTime());
        }
        list.get(start).setTime(toTime);
        dao.updateInTx(list);
    }

    // Insert or update
    public static synchronized void putDownloadInfo(DownloadInfo downloadInfo) {
        markSyncDirty(); // 下载行新增/状态变化是同步实体的核心写路径
        DownloadsDao dao = sDaoSession.getDownloadsDao();
        if (null != dao.load(downloadInfo.gid)) {
            // Update
            dao.update(downloadInfo);
        } else {
            // Insert
            dao.insert(downloadInfo);
        }
    }

    public static synchronized void removeDownloadInfo(long gid) {
        markSyncDirty(); // 删除必须进入快照差分 → 墓碑推送
        sDaoSession.getDownloadsDao().deleteByKey(gid);
    }

    @Nullable
    public static synchronized String getDownloadDirname(long gid) {
        DownloadDirnameDao dao = sDaoSession.getDownloadDirnameDao();
        DownloadDirname raw = dao.load(gid);
        if (raw != null) {
            return raw.getDirname();
        } else {
            return null;
        }
    }

    /**
     * Insert or update
     */
    public static synchronized void putDownloadDirname(long gid, String dirname) {
        markSyncDirty(); // 同步范围写（下载目录名随下载行走线）
        DownloadDirnameDao dao = sDaoSession.getDownloadDirnameDao();
        DownloadDirname raw = dao.load(gid);
        if (raw != null) { // Update
            raw.setDirname(dirname);
            dao.update(raw);
        } else { // Insert
            raw = new DownloadDirname();
            raw.setGid(gid);
            raw.setDirname(dirname);
            dao.insert(raw);
        }
    }

    public static synchronized void removeDownloadDirname(long gid) {
        markSyncDirty();
        DownloadDirnameDao dao = sDaoSession.getDownloadDirnameDao();
        dao.deleteByKey(gid);
    }

    public static synchronized void updateDownloadDirname(long removeGid, long newGid, String dirname) {
        markSyncDirty();
        DownloadDirnameDao dao = sDaoSession.getDownloadDirnameDao();
        dao.deleteByKey(removeGid);
        DownloadDirname raw = dao.load(newGid);
        if (raw != null) { // Update
            raw.setDirname(dirname);
            dao.update(raw);
        } else { // Insert
            raw = new DownloadDirname();
            raw.setGid(newGid);
            raw.setDirname(dirname);
            dao.insert(raw);
        }
    }

    public static synchronized void clearDownloadDirname() {
        markSyncDirty();
        DownloadDirnameDao dao = sDaoSession.getDownloadDirnameDao();
        dao.deleteAll();
    }

    @NonNull
    public static synchronized List<DownloadLabel> getAllDownloadLabelList() {
        DownloadLabelDao dao = sDaoSession.getDownloadLabelDao();
        return dao.queryBuilder().orderAsc(DownloadLabelDao.Properties.Time).list();
    }

    public static synchronized DownloadLabel addDownloadLabel(String label) {
        DownloadLabelDao dao = sDaoSession.getDownloadLabelDao();

        QueryBuilder<DownloadLabel> queryBuilder = dao.queryBuilder().where(DownloadLabelDao.Properties.Label.eq(label));
        List<DownloadLabel> result = queryBuilder.list();
        if (!result.isEmpty()) {
            return result.get(0);
        }

        markSyncDirty(); // 新标签是同步实体写
        DownloadLabel raw = new DownloadLabel();
        raw.setLabel(label);
        raw.setTime(System.currentTimeMillis());
        raw.setId(dao.insert(raw));
        return raw;
    }

    public static synchronized DownloadLabel addDownloadLabel(DownloadLabel raw) {
        // Reset id
        raw.setId(null);
        DownloadLabelDao dao = sDaoSession.getDownloadLabelDao();
        DownloadLabel label = dao.load(raw.getId());
        if (label != null) {
            return label;
        }
        markSyncDirty();
        raw.setId(dao.insert(raw));
        return raw;
    }

    public static synchronized void updateDownloadLabel(DownloadLabel raw) {
        markSyncDirty();
        DownloadLabelDao dao = sDaoSession.getDownloadLabelDao();
        dao.update(raw);
    }

    public static synchronized void moveDownloadLabel(int fromPosition, int toPosition) {
        if (fromPosition == toPosition) {
            return;
        }
        markSyncDirty(); // 顺序调整改 time

        boolean reverse = fromPosition > toPosition;
        int offset = reverse ? toPosition : fromPosition;
        int limit = reverse ? fromPosition - toPosition + 1 : toPosition - fromPosition + 1;

        DownloadLabelDao dao = sDaoSession.getDownloadLabelDao();
        List<DownloadLabel> list = dao.queryBuilder().orderAsc(DownloadLabelDao.Properties.Time)
                .offset(offset).limit(limit).list();

        int step = reverse ? 1 : -1;
        int start = reverse ? limit - 1 : 0;
        int end = reverse ? 0 : limit - 1;
        long toTime = list.get(end).getTime();
        for (int i = end; reverse ? i < start : i > start; i += step) {
            list.get(i).setTime(list.get(i + step).getTime());
        }
        list.get(start).setTime(toTime);

        dao.updateInTx(list);
    }

    public static synchronized void removeDownloadLabel(DownloadLabel raw) {
        markSyncDirty(); // 删除必须进墓碑推送
        DownloadLabelDao dao = sDaoSession.getDownloadLabelDao();
        dao.delete(raw);
    }

    public static synchronized List<GalleryInfo> getAllLocalFavorites() {
        LocalFavoritesDao dao = sDaoSession.getLocalFavoritesDao();
        List<LocalFavoriteInfo> list = dao.queryBuilder().orderDesc(LocalFavoritesDao.Properties.Time).list();
        List<GalleryInfo> result = new ArrayList<>();
        result.addAll(list);
        return result;
    }

    /**
     * Cheap row count for UI labels (R4-7): the local favorites count without
     * materializing every row. The DB is the source of truth; the Settings
     * fav_local cache lags whenever favorites change outside the favorites
     * scene (e.g. WebUI sync adoption).
     */
    public static synchronized int countLocalFavorites() {
        Cursor cursor = sDaoSession.getDatabase()
                .rawQuery("SELECT COUNT(*) FROM " + LocalFavoritesDao.TABLENAME, null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    public static synchronized List<GalleryInfo> searchLocalFavorites(String query) {
        query = SqlUtils.sqlEscapeString("%" + query + "%");
        LocalFavoritesDao dao = sDaoSession.getLocalFavoritesDao();
        List<LocalFavoriteInfo> list = dao.queryBuilder().orderDesc(LocalFavoritesDao.Properties.Time)
                .where(LocalFavoritesDao.Properties.Title.like(query)).list();
        return new ArrayList<>(list);
    }

    public static synchronized GalleryInfo searchLocalFavorites(long query) {
        //        query = SqlUtils.sqlEscapeString("%" + query+ "%");
        LocalFavoritesDao dao = sDaoSession.getLocalFavoritesDao();
        List<LocalFavoriteInfo> list = dao.queryBuilder().orderDesc(LocalFavoritesDao.Properties.Time)
                .where(LocalFavoritesDao.Properties.Gid.eq(query)).list();
        return list.get(0);
    }

    public static synchronized void removeLocalFavorites(long gid) {
        markSyncDirty(); // 删除必须进墓碑推送
        sDaoSession.getLocalFavoritesDao().deleteByKey(gid);
    }

    public static synchronized void removeLocalFavorites(long[] gidArray) {
        if (gidArray.length == 0) {
            return;
        }
        markSyncDirty();
        LocalFavoritesDao dao = sDaoSession.getLocalFavoritesDao();
        for (long gid : gidArray) {
            dao.deleteByKey(gid);
        }
    }

    public static synchronized boolean containLocalFavorites(long gid) {
        LocalFavoritesDao dao = sDaoSession.getLocalFavoritesDao();
        return null != dao.load(gid);
    }

    public static synchronized void putLocalFavorite(GalleryInfo galleryInfo) {
        LocalFavoritesDao dao = sDaoSession.getLocalFavoritesDao();
        if (null == dao.load(galleryInfo.gid)) {
            markSyncDirty(); // 收藏为 union 合并实体：新增必须推送
            LocalFavoriteInfo info;
            if (galleryInfo instanceof LocalFavoriteInfo) {
                info = (LocalFavoriteInfo) galleryInfo;
            } else {
                info = new LocalFavoriteInfo(galleryInfo);
                info.time = System.currentTimeMillis();
            }
            dao.insert(info);
        }
    }

    public static synchronized void putLocalFavorites(List<GalleryInfo> galleryInfoList) {
        for (GalleryInfo gi : galleryInfoList) {
            putLocalFavorite(gi);
        }
    }


    public static synchronized List<BlackList> getAllBlackList() {
        BlackListDao dao = sDaoSession.getBlackListDao();
        return dao.queryBuilder().orderAsc(BlackListDao.Properties.Add_time).list();
    }

    public static synchronized boolean inBlackList(String Badgayname) {
        BlackListDao dao = sDaoSession.getBlackListDao();
        return dao.queryRaw("where Badgayname ='" + Badgayname + "'").size() != 0;
    }

    public static synchronized void insertBlackList(BlackList blackList) {
        BlackListDao dao = sDaoSession.getBlackListDao();
        blackList.id = null;
        if (blackList.badgayname == null) {
            return;
        }
        dao.insert(blackList);
    }

    public static synchronized void updateBlackList(BlackList blackList) {
        BlackListDao dao = sDaoSession.getBlackListDao();
        dao.update(blackList);
    }

    public static synchronized void deleteBlackList(BlackList blackList) {
        BlackListDao dao = sDaoSession.getBlackListDao();
        dao.delete(blackList);
    }

    public static synchronized List<GalleryTags> getAllGalleryTags() {
        GalleryTagsDao dao = sDaoSession.getGalleryTagsDao();
        return dao.queryBuilder().orderAsc(GalleryTagsDao.Properties.Gid).list();
    }

    public static synchronized boolean inGalleryTags(long gid) {
        GalleryTagsDao dao = sDaoSession.getGalleryTagsDao();
        return dao.queryRaw("where gid =" + gid).size() != 0;
    }

    public static synchronized GalleryTags queryGalleryTags(long gid) {
        GalleryTagsDao dao = sDaoSession.getGalleryTagsDao();
        List<GalleryTags> list = dao.queryRaw("where gid =" + gid);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public static synchronized void insertGalleryTags(GalleryTags galleryTags) {
        GalleryTagsDao dao = sDaoSession.getGalleryTagsDao();
        galleryTags.create_time = new Date();
        galleryTags.update_time = galleryTags.create_time;
        dao.insert(galleryTags);
    }

    public static synchronized void updateGalleryTags(GalleryTags galleryTags) {
        GalleryTagsDao dao = sDaoSession.getGalleryTagsDao();
        galleryTags.update_time = new Date();
        dao.update(galleryTags);
    }

    public static synchronized void deleteGalleryTags(GalleryTags galleryTags) {
        GalleryTagsDao dao = sDaoSession.getGalleryTagsDao();
        dao.delete(galleryTags);
    }

    public static synchronized List<QuickSearch> getAllQuickSearch() {
        QuickSearchDao dao = sDaoSession.getQuickSearchDao();
        return dao.queryBuilder().orderAsc(QuickSearchDao.Properties.Time).list();
    }

    public static synchronized void insertQuickSearch(QuickSearch quickSearch) {
        markSyncDirty();
        QuickSearchDao dao = sDaoSession.getQuickSearchDao();
        quickSearch.id = null;
        if (quickSearch.time==0L){
            quickSearch.time = System.currentTimeMillis();
        }
        quickSearch.id = dao.insert(quickSearch);
    }

    public static synchronized void insertQuickSearchList(List<QuickSearch> quickSearchList) {
        if (quickSearchList.isEmpty()) {
            return;
        }
        markSyncDirty();
        QuickSearchDao dao = sDaoSession.getQuickSearchDao();
        for (int i = 0; i < quickSearchList.size(); i++) {
            QuickSearch search = quickSearchList.get(i);
            search.id = null;
            search.time = System.currentTimeMillis();
            search.id = dao.insert(search);
        }
    }

    public static synchronized void takeOverQuickSearchList(List<QuickSearch> quickSearchList) {
        QuickSearchDao dao = sDaoSession.getQuickSearchDao();
        List<QuickSearch> allList = dao.queryBuilder().orderAsc(QuickSearchDao.Properties.Time).list();
        boolean inserted = false;
        for (int i = 0; i < quickSearchList.size(); i++) {
            QuickSearch newSearch = quickSearchList.get(i);
            if (newSearch == null) {
                continue;
            }
            boolean insert = true;
            for (int j = 0; j < allList.size(); j++) {
                QuickSearch exist = allList.get(j);
                if (exist != null && Objects.equals(exist.keyword, newSearch.keyword)) {
                    insert = false;
                    break;
                }
            }
            if (insert) {
                inserted = true;
                newSearch.id = null;
                newSearch.time = System.currentTimeMillis();
                newSearch.id = dao.insert(newSearch);
            }
        }
        // 接管路径直接 dao.insert（不经 insertQuickSearch），必须显式置脏。
        if (inserted) {
            markSyncDirty();
        }
    }

    public static synchronized void updateQuickSearch(QuickSearch quickSearch) {
        markSyncDirty();
        QuickSearchDao dao = sDaoSession.getQuickSearchDao();
        dao.update(quickSearch);
    }

    public static synchronized void deleteQuickSearch(QuickSearch quickSearch) {
        markSyncDirty(); // 删除必须进墓碑推送
        QuickSearchDao dao = sDaoSession.getQuickSearchDao();
        dao.delete(quickSearch);
    }

    /**
     * 快速搜索项移动方法
     * 用于调整快速搜索项的顺序，通过时间戳来实现位置调整
     *
     * @param fromPosition 起始位置
     * @param toPosition   目标位置
     */
    public static synchronized void moveQuickSearch(int fromPosition, int toPosition) {
    // 如果起始位置和目标位置相同，则直接返回
        if (fromPosition == toPosition) {
            return;
        }
    // 顺序调整改 time（推送 lastModified）
        markSyncDirty();

    // 判断是否需要反向移动
        boolean reverse = fromPosition > toPosition;
    // 计算偏移量，用于查询数据库时的起始位置
        int offset = reverse ? toPosition : fromPosition;
    // 计算需要移动的项目数量
        int limit = reverse ? fromPosition - toPosition + 1 : toPosition - fromPosition + 1;

    // 获取QuickSearchDao对象
        QuickSearchDao dao = sDaoSession.getQuickSearchDao();
    // 查询需要移动的搜索项列表，按时间升序排列
        List<QuickSearch> list = dao.queryBuilder().orderAsc(QuickSearchDao.Properties.Time)
                .offset(offset).limit(limit).list();

    // 设置移动方向和起始、结束位置
        int step = reverse ? 1 : -1;
        int start = reverse ? limit - 1 : 0;
        int end = reverse ? 0 : limit - 1;
    // 获取目标位置的时间戳
        long toTime = list.get(end).getTime();
    // 遍历列表，交换相邻项的时间戳，实现位置移动
        for (int i = end; reverse ? i < start : i > start; i += step) {
            list.get(i).setTime(list.get(i + step).getTime());
        }
    // 将起始项的时间设置为目标时间
        list.get(start).setTime(toTime);

    // 在事务中更新所有更改
        dao.updateInTx(list);
    }

    public static synchronized LazyList<HistoryInfo> getHistoryLazyList() {
        return sDaoSession.getHistoryDao().queryBuilder().orderDesc(HistoryDao.Properties.Time).listLazy();
    }

    /** Loads one history record by gid, or {@code null} when absent. */
    @Nullable
    public static synchronized HistoryInfo loadHistoryInfo(long gid) {
        return sDaoSession.getHistoryDao().load(gid);
    }

    /**
     * Bridges the reading progress into the HISTORY row (plan D2): stores
     * {@code page} and refreshes {@code time} so the "currently reading"
     * row stays at the top of the history list and the sync push ledger
     * (keyed on {@code time}) re-pushes it next cycle. Skips silently when
     * the row does not exist yet — history rows are created by the detail
     * scene on visit, so reading always has a row to update.
     */
    public static synchronized void updateHistoryPage(long gid, int page) {
        HistoryDao dao = sDaoSession.getHistoryDao();
        HistoryInfo info = dao.load(gid);
        if (info == null) {
            return;
        }
        markSyncDirty(); // 阅读进度刷新会改 time（推送 lastModified）
        info.page = page;
        info.time = System.currentTimeMillis();
        dao.update(info);
    }

    public static synchronized void putHistoryInfo(GalleryInfo galleryInfo) {
        HistoryDao dao = sDaoSession.getHistoryDao();
        HistoryInfo info = dao.load(galleryInfo.gid);
        if (null != info) {
            markSyncDirty(); // 重读：time 前移即推送 lastModified 前移
            info.time = System.currentTimeMillis();
            dao.update(info);
        } else {
            markSyncDirty();
            // New history
            info = new HistoryInfo(galleryInfo);
            info.time = System.currentTimeMillis();
            dao.insert(info);
            List<HistoryInfo> list;
            if (MAX_HISTORY_COUNT < 1) {
                list = dao.queryBuilder().orderDesc(HistoryDao.Properties.Time)
                        .limit(-1).offset(100).list();
            } else {
                list = dao.queryBuilder().orderDesc(HistoryDao.Properties.Time)
                        .limit(-1).offset(MAX_HISTORY_COUNT).list();
            }
            // 记录因上限被裁剪的 gid：这些行本地已不存在但并非用户删除，
            // 同步引擎不得把它们当作删除推给服务器（否则配对后历史被裁到
            // 上限并逐条硬删服务器端，表现为「历史被清空」）。
            synchronized (SiteDB.class) {
                for (HistoryInfo pruned : list) {
                    sCapPrunedHistoryGids.add(pruned.gid);
                }
                // WP4-1：登记后立即全量落盘（幂等），否则进程重启后白名单
                // 清零，被裁剪的行仍会被当成删除推送。
                saveCapPrunedHistoryGidsLocked();
            }
            dao.deleteInTx(list);
        }
    }

    public static synchronized void putHistoryInfo(List<HistoryInfo> historyInfoList) {
        if (historyInfoList.isEmpty()) {
            return;
        }
        markSyncDirty(); // 批量导入的新历史行是同步实体写（与裁剪分支无关）
        HistoryDao dao = sDaoSession.getHistoryDao();
        for (HistoryInfo info : historyInfoList) {
            if (null == dao.load(info.gid)) {
                dao.insert(info);
            }
        }

        List<HistoryInfo> list = dao.queryBuilder().orderDesc(HistoryDao.Properties.Time)
                .limit(-1).offset(MAX_HISTORY_COUNT).list();
        if (list.isEmpty()) {
            return;
        }
        // 修复既有 bug：批量导入路径（导入 DB）此前只裁剪不登记白名单——
        // 导入大库后超出上限的历史会在下个同步周期被快照差分当成用户删除，
        // 逐条硬删服务器端。与单条版同一处理：登记 + 立即落盘。
        synchronized (SiteDB.class) {
            for (HistoryInfo pruned : list) {
                sCapPrunedHistoryGids.add(pruned.gid);
            }
            saveCapPrunedHistoryGidsLocked();
        }
        dao.deleteInTx(list);
    }

    public static synchronized void deleteHistoryInfo(HistoryInfo info) {
        markSyncDirty(); // 删除必须进墓碑推送
        HistoryDao dao = sDaoSession.getHistoryDao();
        dao.delete(info);
    }

    public static synchronized void clearHistoryInfo() {
        markSyncDirty();
        HistoryDao dao = sDaoSession.getHistoryDao();
        dao.deleteAll();
    }

    // --- WebUI sync helpers (additive; used by com.hippo.anotherviewer.webui.WebUiSyncEngine) ---

    /**
     * Materialized snapshot of all history records for building a sync push.
     * The model has no lastModified field; the wire DTO requires one
     * (sync-schemas.json syncMetadata), so the caller (WebUiSyncEngine)
     * fabricates {@code lastModified = time}, which {@code time} already
     * exposes here.
     */
    public static synchronized List<HistoryInfo> getAllHistoryForSync() {
        HistoryDao dao = sDaoSession.getHistoryDao();
        LazyList<HistoryInfo> lazy = dao.queryBuilder().orderDesc(HistoryDao.Properties.Time).listLazy();
        try {
            return new ArrayList<>(lazy);
        } finally {
            lazy.close();
        }
    }

    /**
     * Last-write-wins upsert of a history record pulled from the sync server.
     * Inserts when absent; updates only when the incoming record wins the LWW
     * comparison of sync-conflict-rules.md §3.2. The comparator keys on
     * {@code lastModified} when both sides carry it, falling back to
     * {@code time} when lastModified is absent or 0.
     */
    public static synchronized void applySyncedHistory(HistoryInfo incoming) {
        // apply 也是写路径：即使 LWW 落选也置脏代价极小（下个周期多一次全量
        // 差分），而落选判定在 dao 层不可见，宁可保守。
        markSyncDirty();
        HistoryDao dao = sDaoSession.getHistoryDao();
        HistoryInfo existing = dao.load(incoming.gid);
        if (existing == null) {
            dao.insert(incoming);
            return;
        }
        // LWW on lastModified per sync-conflict-rules.md §3.2. HistoryInfo has
        // no lastModified field, so the rule's absence/0 fallback applies: the
        // effective LWW timestamp is `time`, which is exactly the value the
        // sync engine sends as lastModified on push (WebUiSyncEngine.buildPush
        // fabricates lastModified = time), keeping both sides comparable.
        long incomingLastModified = incoming.time;
        long existingLastModified = existing.time;
        if (incomingLastModified > existingLastModified) {
            existing.token = incoming.token;
            existing.title = incoming.title;
            existing.titleJpn = incoming.titleJpn;
            existing.thumb = incoming.thumb;
            existing.category = incoming.category;
            existing.posted = incoming.posted;
            existing.uploader = incoming.uploader;
            existing.rating = incoming.rating;
            existing.rated = incoming.rated;
            existing.simpleTags = incoming.simpleTags;
            existing.pages = incoming.pages;
            existing.thumbWidth = incoming.thumbWidth;
            existing.thumbHeight = incoming.thumbHeight;
            existing.spanSize = incoming.spanSize;
            existing.spanIndex = incoming.spanIndex;
            existing.spanGroupIndex = incoming.spanGroupIndex;
            existing.tgList = incoming.tgList;
            existing.simpleLanguage = incoming.simpleLanguage;
            existing.favoriteSlot = incoming.favoriteSlot;
            existing.favoriteName = incoming.favoriteName;
            existing.mode = incoming.mode;
            existing.page = incoming.page;
            existing.time = incoming.time;
            dao.update(existing);
        }
    }

    /** Hard-delete a history record by gid (history uses hard-delete per sync rules). */
    public static synchronized void removeHistoryByKey(long gid) {
        sDaoSession.getHistoryDao().deleteByKey(gid);
    }

    // --- WebUI sync helpers: favorite tombstones & metadata LWW (additive) ---

    /** Loads one local favorite by gid, or {@code null} when absent. */
    public static synchronized LocalFavoriteInfo loadLocalFavorite(long gid) {
        return sDaoSession.getLocalFavoritesDao().load(gid);
    }

    /**
     * Replaces an existing local favorite with the given (loaded) record,
     * keeping its original add {@code time} so list ordering is preserved.
     * Inserts instead when the row no longer exists (removed concurrently).
     */
    public static synchronized void updateLocalFavorite(LocalFavoriteInfo info) {
        markSyncDirty(); // 收藏元数据刷新（同步 apply 与本地编辑共用入口）
        LocalFavoritesDao dao = sDaoSession.getLocalFavoritesDao();
        LocalFavoriteInfo existing = dao.load(info.gid);
        if (existing == null) {
            dao.insert(info);
            return;
        }
        info.time = existing.time;
        dao.update(info);
    }

    public static synchronized List<Filter> getAllFilter() {
        return sDaoSession.getFilterDao().queryBuilder().list();
    }

    public static synchronized void addFilter(Filter filter) {
        filter.setId(null);
        filter.setId(sDaoSession.getFilterDao().insert(filter));
    }

    public static synchronized void deleteFilter(Filter filter) {
        sDaoSession.getFilterDao().delete(filter);
    }

    public static synchronized void triggerFilter(Filter filter) {
        filter.setEnable(!filter.enable);
        sDaoSession.getFilterDao().update(filter);
    }

    // --- WebUI sync helpers: bookmarks & filter composite-key access (additive) ---

    /** Materialized snapshot of all bookmark records for building a sync push. */
    public static synchronized List<BookmarkInfo> getAllBookmark() {
        return sDaoSession.getBookmarksDao().queryBuilder().list();
    }

    /** Insert or replace a bookmark record (gid is the primary key). */
    public static synchronized void putBookmark(BookmarkInfo bookmark) {
        sDaoSession.getBookmarksDao().insertOrReplace(bookmark);
    }

    /** Hard-delete a bookmark record by gid. */
    public static synchronized void removeBookmarkByGid(long gid) {
        sDaoSession.getBookmarksDao().deleteByKey(gid);
    }

    /** Finds the filter identified by its composite (mode, text) key, or {@code null} when absent. */
    public static synchronized Filter findFilterByKey(int mode, String text) {
        return sDaoSession.getFilterDao().queryBuilder()
                .where(FilterDao.Properties.Mode.eq(mode), FilterDao.Properties.Text.eq(text))
                .unique();
    }

    /** Deletes the filter identified by (mode, text), if present. */
    public static synchronized void deleteFilterByKey(int mode, String text) {
        Filter f = findFilterByKey(mode, text);
        if (f != null) sDaoSession.getFilterDao().delete(f);
    }

    private static <T> boolean copyDao(AbstractDao<T, ?> from, AbstractDao<T, ?> to) {
        try (CloseableListIterator<T> iterator = from.queryBuilder().listIterator()) {
            while (iterator.hasNext()) {
                to.insert(iterator.next());
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public static synchronized boolean exportDB(Context context, File file) {
        final String ehExportName = "eh.export.db";

        // Ensure source database has ARCHIVE_URI column. Since schema v8 the
        // column is always present (created by the v8 createTable or added by
        // upgradeDB case 7), so this is a harmless defensive no-op that would
        // only matter for a database that predates v7 and skipped the upgrade.
        try {
            sDaoSession.getDatabase().execSQL("ALTER TABLE \"DOWNLOADS\" ADD COLUMN \"ARCHIVE_URI\" TEXT");
        } catch (Exception e) {
            // Column might already exist, ignore the error
            Log.d(TAG, "ARCHIVE_URI column already exists or failed to add", e);
        }

        // Delete old export db
        context.deleteDatabase(ehExportName);

        DBOpenHelper helper = new DBOpenHelper(context.getApplicationContext(), ehExportName, null);

        try {
            // Copy data to a export db
            try (SQLiteDatabase db = helper.getWritableDatabase()) {
                DaoMaster daoMaster = new DaoMaster(db);
                DaoSession exportSession = daoMaster.newSession();
                if (! copyDao(sDaoSession.getDownloadsDao(), exportSession.getDownloadsDao()))
                    return false;
                if (!copyDao(sDaoSession.getDownloadLabelDao(), exportSession.getDownloadLabelDao()))
                    return false;
                if (!copyDao(sDaoSession.getDownloadDirnameDao(), exportSession.getDownloadDirnameDao()))
                    return false;
                if (!copyDao(sDaoSession.getHistoryDao(), exportSession.getHistoryDao()))
                    return false;
                if (!copyDao(sDaoSession.getQuickSearchDao(), exportSession.getQuickSearchDao()))
                    return false;
                if (!copyDao(sDaoSession.getLocalFavoritesDao(), exportSession.getLocalFavoritesDao()))
                    return false;
                if (!copyDao(sDaoSession.getBookmarksDao(), exportSession.getBookmarksDao()))
                    return false;
                if (!copyDao(sDaoSession.getFilterDao(), exportSession.getFilterDao()))
                    return false;
            }

            // Copy export db to data dir
            File dbFile = context.getDatabasePath(ehExportName);
            if (dbFile == null || !dbFile.isFile()) {
                return false;
            }
            InputStream is = null;
            OutputStream os = null;
            try {
                is = new FileInputStream(dbFile);
                os = new FileOutputStream(file);
                IOUtils.copy(is, os);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                IOUtils.closeQuietly(is);
                IOUtils.closeQuietly(os);
            }
            // Delete failed file
            file.delete();
            return false;
        } finally {
            context.deleteDatabase(ehExportName);
        }
    }

    /**
     * @param file The db file
     * @return error string, null for no error
     */
    public static synchronized String importDB(Context context, File file, Handler handler) {
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(
                    file.getPath(), null, SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            // SiteDB's own version (side tables included), not
            // DaoMaster.SCHEMA_VERSION: a pre-side-table import upgrades, a
            // current export (carrying DB_VERSION) imports unchanged, and a
            // newer export still fails loudly.
            int newVersion = DB_VERSION;
            int oldVersion = db.getVersion();
            if (oldVersion < newVersion) {
                upgradeDB(db, oldVersion);
                db.setVersion(newVersion);
            } else if (oldVersion > newVersion) {
                return context.getString(R.string.cant_read_the_file);
            }

            DaoMaster daoMaster = new DaoMaster(db);
            DaoSession session = daoMaster.newSession();
            sendImportProgress(handler, 10);
            DownloadManager manager = SiteApplication.getDownloadManager(context);
            // Download label
            List<DownloadLabel> downloadLabelList = session.getDownloadLabelDao().queryBuilder().list();
            manager.addDownloadLabel(downloadLabelList);
            // Downloads
            List<DownloadInfo> downloadInfoList = session.getDownloadsDao().queryBuilder().list();
            manager.addDownload(downloadInfoList);

            sendImportProgress(handler, 50);
            // Download dirname
            List<DownloadDirname> downloadDirnameList = session.getDownloadDirnameDao().queryBuilder().list();
            for (DownloadDirname dirname : downloadDirnameList) {
                putDownloadDirname(dirname.getGid(), dirname.getDirname());
            }
            sendImportProgress(handler, 90);

            // History
            List<HistoryInfo> historyInfoList = session.getHistoryDao().queryBuilder().list();
            putHistoryInfo(historyInfoList);

            // QuickSearch
            List<QuickSearch> quickSearchList = session.getQuickSearchDao().queryBuilder().list();
            List<QuickSearch> currentQuickSearchList = sDaoSession.getQuickSearchDao().queryBuilder().list();
            for (QuickSearch quickSearch : quickSearchList) {
                String name = quickSearch.name;
                for (QuickSearch q : currentQuickSearchList) {
                    if (ObjectUtils.equal(q.name, name)) {
                        // The same name
                        name = null;
                        break;
                    }
                }
                if (null == name) {
                    continue;
                }
                insertQuickSearch(quickSearch);
            }

            // LocalFavorites
            List<LocalFavoriteInfo> localFavoriteInfoList = session.getLocalFavoritesDao().queryBuilder().list();
            for (LocalFavoriteInfo info : localFavoriteInfoList) {
                putLocalFavorite(info);
            }

            // Bookmarks
            List<BookmarkInfo> bookmarkInfoList = session.getBookmarksDao().queryBuilder().list();
            for (BookmarkInfo info : bookmarkInfoList) {
                putBookmark(info);
            }

            // Filter
            List<Filter> filterList = session.getFilterDao().queryBuilder().list();
            List<Filter> currentFilterList = sDaoSession.getFilterDao().queryBuilder().list();
            for (Filter filter : filterList) {
                if (!currentFilterList.contains(filter)) {
                    addFilter(filter);
                }
            }

            List<BlackList> blackList = session.getBlackListDao().queryBuilder().list();
            List<BlackList> currentBlackList = sDaoSession.getBlackListDao().queryBuilder().list();
            for (BlackList black : blackList) {
                if (!currentBlackList.contains(black)) {
                    insertBlackList(black);
                }
            }

            List<GalleryTags> galleryTagsList = session.getGalleryTagsDao().queryBuilder().list();
            List<GalleryTags> currentGalleryTags = sDaoSession.getGalleryTagsDao().queryBuilder().list();
            for (GalleryTags tags : galleryTagsList) {
                if (!currentGalleryTags.contains(tags)) {
                    insertGalleryTags(tags);
                }
            }

            return null;
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            // Ignore
            return context.getString(R.string.cant_read_the_file);
        }
    }

    private static void sendImportProgress(Handler handler, int progress) {
        Message message = new Message();
        Bundle bundle = new Bundle();
        bundle.putInt(LOADING_PROGRESS, progress);
        bundle.putInt(LOADING_STATUS, DB_LOADING);
        message.setData(bundle);
        handler.sendMessage(message);
    }
}
