package com.hippo.anotherviewer;

public class SiteDB {
    public static com.hippo.anotherviewer.dao.QuickSearch insertQuickSearch(String name, String query, String category, String advanceSearch, String searchGalleryName, String searchTags, String searchComments, String searchExpunged, int minRating, int pages) {
        return new com.hippo.anotherviewer.dao.QuickSearch();
    }
    public static java.util.List<com.hippo.anotherviewer.dao.QuickSearch> getAllQuickSearch() { return new java.util.ArrayList<>(); }
    public static boolean inBlackList(String user) { return false; }
    public static boolean containLocalFavorites(long gid) { return false; }
}
