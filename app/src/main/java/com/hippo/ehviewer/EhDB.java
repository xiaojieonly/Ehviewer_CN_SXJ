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

package com.hippo.ehviewer;

import static com.hippo.ehviewer.ui.fragment.AdvancedFragment.DB_LOADING;
import static com.hippo.ehviewer.ui.fragment.AdvancedFragment.LOADING_PROGRESS;
import static com.hippo.ehviewer.ui.fragment.AdvancedFragment.LOADING_STATUS;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.data.ListUrlBuilder;
import com.hippo.ehviewer.dao.BlackList;
import com.hippo.ehviewer.dao.BlackListDao;
import com.hippo.ehviewer.dao.DaoMaster;
import com.hippo.ehviewer.dao.DaoSession;
import com.hippo.ehviewer.dao.DownloadDirname;
import com.hippo.ehviewer.dao.DownloadDirnameDao;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.DownloadLabel;
import com.hippo.ehviewer.dao.DownloadLabelDao;
import com.hippo.ehviewer.dao.DownloadsDao;
import com.hippo.ehviewer.dao.Filter;
import com.hippo.ehviewer.dao.GalleryTags;
import com.hippo.ehviewer.dao.GalleryTagsDao;
import com.hippo.ehviewer.dao.HistoryDao;
import com.hippo.ehviewer.dao.HistoryInfo;
import com.hippo.ehviewer.dao.LocalFavoriteInfo;
import com.hippo.ehviewer.dao.LocalFavoritesDao;
import com.hippo.ehviewer.dao.QuickSearch;
import com.hippo.ehviewer.dao.QuickSearchDao;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.util.ExceptionUtils;
import com.hippo.util.SqlUtils;
import com.hippo.yorozuya.IOUtils;
import com.hippo.yorozuya.ObjectUtils;
import com.hippo.yorozuya.collect.SparseJLArray;

import org.greenrobot.greendao.AbstractDao;
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

public class EhDB {

    private static final String TAG = EhDB.class.getSimpleName();

    public static int MAX_HISTORY_COUNT = 100;

    private static DaoSession sDaoSession;

    private static boolean sHasOldDB;
    private static boolean sNewDB;

    private static class DBOpenHelper extends DaoMaster.OpenHelper {

        public DBOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory) {
            super(context, name, factory);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            super.onCreate(db);
            sNewDB = true;
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            upgradeDB(db, oldVersion);
        }
    }

    private static void upgradeDB(SQLiteDatabase db, int oldVersion) {
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
                        "SELECT _id, MODE, TEXT, 1 FROM FILTER;");
                db.execSQL("DROP TABLE FILTER");
                db.execSQL("ALTER TABLE FILTER2 RENAME TO FILTER");
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
        }
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
        DaoMaster daoMaster = new DaoMaster(db);

        sDaoSession = daoMaster.newSession();
        MAX_HISTORY_COUNT = Settings.getHistoryInfoSize();
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
        List<DownloadInfo> list = dao.queryBuilder().orderDesc(DownloadsDao.Properties.Time).list();
        // Fix state
        for (DownloadInfo info : list) {
            if (info.state == DownloadInfo.STATE_WAIT || info.state == DownloadInfo.STATE_DOWNLOAD) {
                info.state = DownloadInfo.STATE_NONE;
            }
        }
        return list;
    }

    // Insert or update
    public static synchronized void putDownloadInfo(DownloadInfo downloadInfo) {
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
        DownloadDirnameDao dao = sDaoSession.getDownloadDirnameDao();
        dao.deleteByKey(gid);
    }

    public static synchronized void updateDownloadDirname(long removeGid, long newGid, String dirname) {
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
        raw.setId(dao.insert(raw));
        return raw;
    }

    public static synchronized void updateDownloadLabel(DownloadLabel raw) {
        DownloadLabelDao dao = sDaoSession.getDownloadLabelDao();
        dao.update(raw);
    }

    public static synchronized void moveDownloadLabel(int fromPosition, int toPosition) {
        if (fromPosition == toPosition) {
            return;
        }

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
        sDaoSession.getLocalFavoritesDao().deleteByKey(gid);
    }

    public static synchronized void removeLocalFavorites(long[] gidArray) {
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
        QuickSearchDao dao = sDaoSession.getQuickSearchDao();
        quickSearch.id = null;
        if (quickSearch.time==0L){
            quickSearch.time = System.currentTimeMillis();
        }
        quickSearch.id = dao.insert(quickSearch);
    }

    public static synchronized void insertQuickSearchList(List<QuickSearch> quickSearchList) {
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
        for (int i = 0; i < quickSearchList.size(); i++) {
            QuickSearch newSearch = quickSearchList.get(i);
            boolean insert = true;
            for (int j = 0; j < allList.size(); j++) {
                QuickSearch exist = allList.get(j);
                if (exist.keyword.equals(newSearch.keyword)) {
                    insert = false;
                    break;
                }
            }
            if (insert) {
                newSearch.id = null;
                newSearch.time = System.currentTimeMillis();
                newSearch.id = dao.insert(newSearch);
            }
        }
    }

    public static synchronized void updateQuickSearch(QuickSearch quickSearch) {
        QuickSearchDao dao = sDaoSession.getQuickSearchDao();
        dao.update(quickSearch);
    }

    public static synchronized void deleteQuickSearch(QuickSearch quickSearch) {
        QuickSearchDao dao = sDaoSession.getQuickSearchDao();
        dao.delete(quickSearch);
    }

    public static synchronized void moveQuickSearch(int fromPosition, int toPosition) {
        if (fromPosition == toPosition) {
            return;
        }

        boolean reverse = fromPosition > toPosition;
        int offset = reverse ? toPosition : fromPosition;
        int limit = reverse ? fromPosition - toPosition + 1 : toPosition - fromPosition + 1;

        QuickSearchDao dao = sDaoSession.getQuickSearchDao();
        List<QuickSearch> list = dao.queryBuilder().orderAsc(QuickSearchDao.Properties.Time)
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

    public static synchronized LazyList<HistoryInfo> getHistoryLazyList() {
        return sDaoSession.getHistoryDao().queryBuilder().orderDesc(HistoryDao.Properties.Time).listLazy();
    }

    public static synchronized void putHistoryInfo(GalleryInfo galleryInfo) {
        HistoryDao dao = sDaoSession.getHistoryDao();
        HistoryInfo info = dao.load(galleryInfo.gid);
        if (null != info) {
            // Update time
            info.time = System.currentTimeMillis();
            dao.update(info);
        } else {
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
            dao.deleteInTx(list);
        }
    }

    public static synchronized void putHistoryInfo(List<HistoryInfo> historyInfoList) {
        HistoryDao dao = sDaoSession.getHistoryDao();
        for (HistoryInfo info : historyInfoList) {
            if (null == dao.load(info.gid)) {
                dao.insert(info);
            }
        }

        List<HistoryInfo> list = dao.queryBuilder().orderDesc(HistoryDao.Properties.Time)
                .limit(-1).offset(MAX_HISTORY_COUNT).list();
        dao.deleteInTx(list);
    }

    public static synchronized void deleteHistoryInfo(HistoryInfo info) {
        HistoryDao dao = sDaoSession.getHistoryDao();
        dao.delete(info);
    }

    public static synchronized void clearHistoryInfo() {
        HistoryDao dao = sDaoSession.getHistoryDao();
        dao.deleteAll();
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

        // Delete old export db
        context.deleteDatabase(ehExportName);

        DBOpenHelper helper = new DBOpenHelper(context.getApplicationContext(), ehExportName, null);

        try {
            // Copy data to a export db
            try (SQLiteDatabase db = helper.getWritableDatabase()) {
                DaoMaster daoMaster = new DaoMaster(db);
                DaoSession exportSession = daoMaster.newSession();
                if (!copyDao(sDaoSession.getDownloadsDao(), exportSession.getDownloadsDao()))
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
                if (!copyDao(sDaoSession.getBookmarksBao(), exportSession.getBookmarksBao()))
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
            int newVersion = DaoMaster.SCHEMA_VERSION;
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
            DownloadManager manager = EhApplication.getDownloadManager(context);
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
            // TODO

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
