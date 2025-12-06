package com.hippo.ehviewer.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.internal.DaoConfig;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

/**
 * DAO for table "GALLERY_VERSION_MAP".
 */
public class GalleryVersionMapDao extends AbstractDao<GalleryVersionMap, Long> {

    public static final String TABLENAME = "GALLERY_VERSION_MAP";

    /**
     * Properties of entity GalleryVersionMap.<br/>
     * Can be used for QueryBuilder and for referencing column names.
     */
    public static class Properties {
        public final static Property Id = new Property(0, Long.class, "id", true, "_id");
        public final static Property CurrentGid = new Property(1, long.class, "currentGid", false, "CURRENT_GID");
        public final static Property OriginalGid = new Property(2, long.class, "originalGid", false, "ORIGINAL_GID");
        public final static Property Title = new Property(3, String.class, "title", false, "TITLE");
        public final static Property CreateTime = new Property(4, long.class, "createTime", false, "CREATE_TIME");
        public final static Property UpdateTime = new Property(5, long.class, "updateTime", false, "UPDATE_TIME");
    }

    public GalleryVersionMapDao(DaoConfig config) {
        super(config);
    }
    
    public GalleryVersionMapDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
    }

    /** Creates the underlying database table. */
    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"GALLERY_VERSION_MAP\" (" + //
                "\"_id\" INTEGER PRIMARY KEY AUTOINCREMENT ," + // 0: id
                "\"CURRENT_GID\" INTEGER NOT NULL ," + // 1: currentGid
                "\"ORIGINAL_GID\" INTEGER NOT NULL ," + // 2: originalGid
                "\"TITLE\" TEXT," + // 3: title
                "\"CREATE_TIME\" INTEGER NOT NULL ," + // 4: createTime
                "\"UPDATE_TIME\" INTEGER NOT NULL );"); // 5: updateTime
    }

    /** Drops the underlying database table. */
    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"GALLERY_VERSION_MAP\"";
        db.execSQL(sql);
    }

    @Override
    protected GalleryVersionMap readEntity(Cursor cursor, int offset) {
        GalleryVersionMap entity = new GalleryVersionMap();
        entity.setId(cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0));
        entity.setCurrentGid(cursor.getLong(offset + 1));
        entity.setOriginalGid(cursor.getLong(offset + 2));
        entity.setTitle(cursor.isNull(offset + 3) ? null : cursor.getString(offset + 3));
        entity.setCreateTime(cursor.getLong(offset + 4));
        entity.setUpdateTime(cursor.getLong(offset + 5));
        return entity;
    }

    @Override
    protected Long readKey(Cursor cursor, int offset) {
        return cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0);
    }

    @Override
    protected void readEntity(Cursor cursor, GalleryVersionMap entity, int offset) {
        entity.setId(cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0));
        entity.setCurrentGid(cursor.getLong(offset + 1));
        entity.setOriginalGid(cursor.getLong(offset + 2));
        entity.setTitle(cursor.isNull(offset + 3) ? null : cursor.getString(offset + 3));
        entity.setCreateTime(cursor.getLong(offset + 4));
        entity.setUpdateTime(cursor.getLong(offset + 5));
    }

    @Override
    protected void bindValues(DatabaseStatement stmt, GalleryVersionMap entity) {
        stmt.clearBindings();
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
        stmt.bindLong(2, entity.getCurrentGid());
        stmt.bindLong(3, entity.getOriginalGid());
        String title = entity.getTitle();
        if (title != null) {
            stmt.bindString(4, title);
        }
        stmt.bindLong(5, entity.getCreateTime());
        stmt.bindLong(6, entity.getUpdateTime());
    }

    @Override
    protected void bindValues(SQLiteStatement stmt, GalleryVersionMap entity) {
        stmt.clearBindings();
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
        stmt.bindLong(2, entity.getCurrentGid());
        stmt.bindLong(3, entity.getOriginalGid());
        String title = entity.getTitle();
        if (title != null) {
            stmt.bindString(4, title);
        }
        stmt.bindLong(5, entity.getCreateTime());
        stmt.bindLong(6, entity.getUpdateTime());
    }

    @Override
    protected Long updateKeyAfterInsert(GalleryVersionMap entity, long rowId) {
        entity.setId(rowId);
        return rowId;
    }

    @Override
    public Long getKey(GalleryVersionMap entity) {
        return entity.getId();
    }

    public boolean hasKey(GalleryVersionMap entity) {
        return entity.getId() != null;
    }

    protected boolean isEntityUpdateable() {
        return true;
    }
}