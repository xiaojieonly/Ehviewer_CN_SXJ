package com.hippo.ehviewer.client;

public class HomeDetail {

    private long used = 0L;
    private long total = 0L;
    private long resetCost = 0L;
    private long fromGalleryVisits = 0L;
    private long fromTorrentCompletions = 0L;
    private long fromArchiveDownloads = 0L;
    private long fromHentaiAtHome = 0L;
    private long currentModerationPower = 0L;

    public HomeDetail() {

    }

    public long getUsed() {
        return used;
    }

    public void setUsed(long used) {
        this.used = used;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public String getResetCost() {
        return withGp(resetCost);
    }

    public long resetCost() {
        return resetCost;
    }


    public void setResetCost(long resetCost) {
        this.resetCost = resetCost;
    }

    public String getFromGalleryVisits() {
        return withGp(fromGalleryVisits);
    }

    public void setFromGalleryVisits(long fromGalleryVisits) {
        this.fromGalleryVisits = fromGalleryVisits;
    }

    public String getFromTorrentCompletions() {
        return withGp(fromTorrentCompletions);
    }

    public void setFromTorrentCompletions(long fromTorrentCompletions) {
        this.fromTorrentCompletions = fromTorrentCompletions;
    }

    public String getFromArchiveDownloads() {
        return withGp(fromArchiveDownloads);
    }

    public void setFromArchiveDownloads(long fromArchiveDownloads) {
        this.fromArchiveDownloads = fromArchiveDownloads;
    }

    public String getFromHentaiAtHome() {
        return withGp(fromHentaiAtHome);
    }

    public void setFromHentaiAtHome(long fromHentaiAtHome) {
        this.fromHentaiAtHome = fromHentaiAtHome;
    }

    public String getCurrentModerationPower() {
        return Long.toString(currentModerationPower);
    }

    public void setCurrentModerationPower(long currentModerationPower) {
        this.currentModerationPower = currentModerationPower;
    }

    public String getImageLimits() {
        return used + "/" + total;
    }

    private String withGp(long l) {
        return l + " Gp";
    }
}
