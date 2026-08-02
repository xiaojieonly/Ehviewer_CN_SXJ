package com.hippo.anotherviewer;

public class SiteCoreConfig {
    private String downloadPath = "./data/downloads";
    private String cachePath = "./data/cache";
    private long cacheSizeBytes = 10L * 1024 * 1024 * 1024;
    private int workerCount = 3;
    private int downloadDelay = 0;
    private int downloadTimeout = 60000;
    private int maxConcurrentGalleries = 3;
    private int maxConcurrentImages = 3;
    private boolean enableLogging = true;

    public String getDownloadPath() { return downloadPath; }
    public void setDownloadPath(String downloadPath) { this.downloadPath = downloadPath; }
    public String getCachePath() { return cachePath; }
    public void setCachePath(String cachePath) { this.cachePath = cachePath; }
    public long getCacheSizeBytes() { return cacheSizeBytes; }
    public void setCacheSizeBytes(long cacheSizeBytes) { this.cacheSizeBytes = cacheSizeBytes; }
    public int getWorkerCount() { return workerCount; }
    public void setWorkerCount(int workerCount) { this.workerCount = workerCount; }
    public int getDownloadDelay() { return downloadDelay; }
    public void setDownloadDelay(int downloadDelay) { this.downloadDelay = downloadDelay; }
    public int getDownloadTimeout() { return downloadTimeout; }
    public void setDownloadTimeout(int downloadTimeout) { this.downloadTimeout = downloadTimeout; }
    public int getMaxConcurrentGalleries() { return maxConcurrentGalleries; }
    public void setMaxConcurrentGalleries(int maxConcurrentGalleries) { this.maxConcurrentGalleries = maxConcurrentGalleries; }
    public int getMaxConcurrentImages() { return maxConcurrentImages; }
    public void setMaxConcurrentImages(int maxConcurrentImages) { this.maxConcurrentImages = maxConcurrentImages; }
    public boolean isEnableLogging() { return enableLogging; }
    public void setEnableLogging(boolean enableLogging) { this.enableLogging = enableLogging; }
}
