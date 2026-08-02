package com.hippo.anotherviewer.client;

public class SiteFilter {
    public static SiteFilter getInstance() { return new SiteFilter(); }
    public boolean filterGalleryInfo(com.hippo.anotherviewer.client.data.GalleryInfo gi) { return true; }
    public boolean filterTitle(com.hippo.anotherviewer.client.data.GalleryInfo gi) { return true; }
    public boolean filterUploader(com.hippo.anotherviewer.client.data.GalleryInfo gi) { return true; }
    public boolean filterTag(com.hippo.anotherviewer.client.data.GalleryInfo gi) { return true; }
    public boolean filterTagNamespace(com.hippo.anotherviewer.client.data.GalleryInfo gi) { return true; }
    public boolean needTags() { return false; }
}
