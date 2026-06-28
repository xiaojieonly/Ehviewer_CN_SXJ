package com.hippo.ehviewer;

public class EhDB {
    public static com.hippo.ehviewer.dao.QuickSearch insertQuickSearch(String name, String query, String category, String advanceSearch, String searchGalleryName, String searchTags, String searchComments, String searchExpunged, int minRating, int pages) {
        return new com.hippo.ehviewer.dao.QuickSearch();
    }
    public static java.util.List<com.hippo.ehviewer.dao.QuickSearch> getAllQuickSearch() { return new java.util.ArrayList<>(); }
    public static boolean inBlackList(String user) { return false; }
    public static boolean containLocalFavorites(long gid) { return false; }
}
