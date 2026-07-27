package com.hippo.ehviewer.client;

public class EhFilter {
    public static EhFilter getInstance() { return new EhFilter(); }
    public boolean filterGalleryInfo(com.hippo.ehviewer.client.data.GalleryInfo gi) { return true; }
    public boolean filterTitle(com.hippo.ehviewer.client.data.GalleryInfo gi) { return true; }
    public boolean filterUploader(com.hippo.ehviewer.client.data.GalleryInfo gi) { return true; }
    public boolean filterTag(com.hippo.ehviewer.client.data.GalleryInfo gi) { return true; }
    public boolean filterTagNamespace(com.hippo.ehviewer.client.data.GalleryInfo gi) { return true; }
    public boolean needTags() { return false; }
}
