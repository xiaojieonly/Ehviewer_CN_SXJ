package com.hippo.ehviewer.dao;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Generated;

/**
 * 画廊版本映射实体，用于增量下载更新功能
 * 存储同一画廊不同版本之间的映射关系
 */
@Entity(nameInDb = "GALLERY_VERSION_MAP")
public class GalleryVersionMap {
    
    @Id(autoincrement = true)
    private Long id;
    
    // 当前画廊的GID
    private long currentGid;
    
    // 原始画廊的GID（第一个版本）
    private long originalGid;
    
    // 画廊标题
    private String title;
    
    // 创建时间
    private long createTime;
    
    // 更新时间
    private long updateTime;
    
    @Generated
    public GalleryVersionMap() {
    }
    
    @Generated
    public GalleryVersionMap(long currentGid, long originalGid, String title) {
        this.currentGid = currentGid;
        this.originalGid = originalGid;
        this.title = title;
        this.createTime = System.currentTimeMillis();
        this.updateTime = System.currentTimeMillis();
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public long getCurrentGid() {
        return currentGid;
    }
    
    public void setCurrentGid(long currentGid) {
        this.currentGid = currentGid;
    }
    
    public long getOriginalGid() {
        return originalGid;
    }
    
    public void setOriginalGid(long originalGid) {
        this.originalGid = originalGid;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public long getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
    
    public long getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }
}