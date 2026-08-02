package com.hippo.anotherviewer.client.data;

public class TorrentInfo {
    public long gid;
    public String token;
    public String posted;
    public String title;
    public String subtitle;
    public int downloads;
    public String size;
    public String hash;

    public TorrentInfo() {}
    public TorrentInfo(String url, String title, String posted) {
        this.title = title;
        this.posted = posted;
    }
}
