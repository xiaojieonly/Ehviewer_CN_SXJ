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

package com.hippo.ehviewer.client;

import androidx.annotation.NonNull;
import com.hippo.ehviewer.Settings;
import com.hippo.network.UrlBuilder;
import java.util.List;
import java.util.ListIterator;
import okhttp3.HttpUrl;

/**
 * appurl请求设置
 */
public class EhUrl {

    public static final int SITE_E = 0;
    public static final int SITE_EX = 1;

    public static final String DOMAIN_EX = "exhentai.org";
    public static final String DOMAIN_E = "e-hentai.org";
    public static final String DOMAIN_LOFI = "lofi.e-hentai.org";

    public static final String REFERER_EX = "https://" + DOMAIN_EX;
    public static final String REFERER_E = "https://" + DOMAIN_E;

    public static final String HOST_EX = REFERER_EX + "/";
    public static final String HOST_E = REFERER_E + "/";

    public static final String API_SIGN_IN = "https://forums.e-hentai.org/index.php?act=Login&CODE=01";

    public static final String URL_NEWS_E = HOST_E+"news.php";

    public static final String API_E = HOST_E + "api.php";
    public static final String API_EX = HOST_EX + "api.php";

    public static final String HOME_E = HOST_E + "home.php";
    public static final String HOME_EX = HOST_EX + "home.php";

    public static final String URL_POPULAR_E = "https://e-hentai.org/popular";
    public static final String URL_POPULAR_EX = "https://exhentai.org/popular";

    public static final String URL_TOP_LIST_E = HOST_E+"toplist.php";
    public static final String URL_TOP_LIST_EX = HOST_EX+"toplist.php";

    public static final String URL_IMAGE_SEARCH_E = "https://upload.e-hentai.org/image_lookup.php";
    public static final String URL_IMAGE_SEARCH_EX = "https://exhentai.org/upload/image_lookup.php";

    public static final String URL_SIGN_IN = "https://forums.e-hentai.org/index.php?act=Login";
    public static final String URL_REGISTER = "https://forums.e-hentai.org/index.php?act=Reg&CODE=00";
    public static final String URL_FAVORITES_E = HOST_E + "favorites.php";
    public static final String URL_FAVORITES_EX = HOST_EX + "favorites.php";
    public static final String URL_FORUMS = "https://forums.e-hentai.org/";

    public static final String ORIGIN_EX = REFERER_EX;
    public static final String ORIGIN_E = REFERER_E;

    public static final String URL_UCONFIG_E = HOST_E + "uconfig.php";
    public static final String URL_UCONFIG_EX = HOST_EX + "uconfig.php";

    public static final String URL_MY_TAGS_E = HOST_E + "mytags";
    public static final String URL_MY_TAGS_EX = HOST_EX + "mytags";

    public static final String URL_WATCHED_E = HOST_E + "watched";
    public static final String URL_WATCHED_EX = HOST_EX + "watched";

    private static final String URL_PREFIX_THUMB_E = "https://ehgt.org/";
    private static final String URL_PREFIX_THUMB_EX = "https://exhentai.org/t/";

    public static String getGalleryDetailUrl(long gid, String token) {
        return getGalleryDetailUrl(gid, token, 0, false);
    }

    public static String getHost() {
        switch (Settings.getGallerySite()) {
            default:
            case SITE_E:
                return HOST_E;
            case SITE_EX:
                return HOST_EX;
        }
    }

    public static String getHomeUrl(){
        return HOME_E;
    }

    public static String getMyTag() {
        switch (Settings.getGallerySite()) {
            default:
            case SITE_E:
                return URL_MY_TAGS_E;
            case SITE_EX:
                return URL_MY_TAGS_EX;
        }
    }

    public static String getFavoritesUrl() {
        switch (Settings.getGallerySite()) {
            default:
            case SITE_E:
                return URL_FAVORITES_E;
            case SITE_EX:
                return URL_FAVORITES_EX;
        }
    }

    public static String getApiUrl() {
        switch (Settings.getGallerySite()) {
            default:
            case SITE_E:
                return API_E;
            case SITE_EX:
                return API_EX;
        }
    }

    public static String getReferer() {
        switch (Settings.getGallerySite()) {
            default:
            case SITE_E:
                return REFERER_E;
            case SITE_EX:
                return REFERER_EX;
        }
    }

    public static String getOrigin() {
        switch (Settings.getGallerySite()) {
            default:
            case SITE_E:
                return ORIGIN_E;
            case SITE_EX:
                return ORIGIN_EX;
        }
    }

    public static String getUConfigUrl() {
        switch (Settings.getGallerySite()) {
            default:
            case SITE_E:
                return URL_UCONFIG_E;
            case SITE_EX:
                return URL_UCONFIG_EX;
        }
    }

    public static String getMyTagsUrl() {
        switch (Settings.getGallerySite()) {
            default:
            case SITE_E:
                return URL_MY_TAGS_E;
            case SITE_EX:
                return URL_MY_TAGS_EX;
        }
    }

    /**
     * 获取画廊详情地址
     * @param gid
     * @param token
     * @param index
     * @param allComment
     * @return
     */
    public static String getGalleryDetailUrl(long gid, String token, int index, boolean allComment) {
        UrlBuilder builder = new UrlBuilder(getHost() + "g/" + gid + '/' + token + '/');
        if (index != 0) {
            builder.addQuery("p", index);
        }
        if (allComment) {
            builder.addQuery("hc", 1);
        }
        return builder.build();
    }

    public static String getPageUrl(long gid, int index, String pToken) {
        return getHost() + "s/" + pToken + '/' + gid + '-' + (index + 1);
    }

    public static String getAddFavorites(long gid, String token) {
        return getHost() + "gallerypopups.php?gid=" + gid + "&t=" + token + "&act=addfav";
    }

    public static String getDownloadArchive(long gid, String token, String or) {
        return getHost() + "archiver.php?gid=" + gid + "&token=" + token + "&or=" + or;
    }

    public static String getTagDefinitionUrl(String tag) {
        return "https://ehwiki.org/wiki/" + tag.replace(' ', '_');
    }

    /**
     * 获取‘favorites’连接
     * @return
     */
    @NonNull
    public static String getPopularUrl() {
        switch (Settings.getGallerySite()) {
            default:
            case SITE_E:
                return URL_POPULAR_E;
            case SITE_EX:
                return URL_POPULAR_EX;
        }
    }

    /**
     * 获取排行榜‘top list’连接
     * @return
     */
    @NonNull
    public static String getTopListUrl() {
        return URL_TOP_LIST_E;
        /**
         * 里站没排行榜入口？？？
         * 妈的绝了
         */
//        switch (Settings.getGallerySite()) {
//            default:
//            case SITE_E:
//                return URL_TOP_LIST_E;
//            case SITE_EX:
//                return URL_TOP_LIST_EX;
//        }
    }

    /**
     * 获取排行榜‘top list’连接
     * @return
     */
    @NonNull
    public static String getEhNewsUrl() {
        return URL_NEWS_E;
    }

    @NonNull
    public static String getImageSearchUrl() {
        switch (Settings.getGallerySite()) {
            default:
            case SITE_E:
                return URL_IMAGE_SEARCH_E;
            case SITE_EX:
                return URL_IMAGE_SEARCH_EX;
        }
    }

    @NonNull
    public static String getWatchedUrl() {
        switch (Settings.getGallerySite()) {
            default:
            case SITE_E:
                return URL_WATCHED_E;
            case SITE_EX:
                return URL_WATCHED_EX;
        }
    }

    public static String getThumbUrlPrefix() {
        switch (Settings.getGallerySite()) {
            default:
            //case SITE_E:
                return URL_PREFIX_THUMB_E;
            //case SITE_EX:
            //    return URL_PREFIX_THUMB_EX;
        }
    }

    public static String getFixedPreviewThumbUrl(String originUrl) {
        HttpUrl url = HttpUrl.parse(originUrl);
        if (url == null) return originUrl;
        List<String> pathSegments = url.pathSegments();
        if (pathSegments == null || pathSegments.size() < 3) return originUrl;

        ListIterator<String> iterator = pathSegments.listIterator(pathSegments.size());
        // The last segments, like
        // 317a1a254cd9c3269e71b2aa2671fe8d28c91097-260198-640-480-png_250.jpg
        if (!iterator.hasPrevious()) return originUrl;
        String lastSegment = iterator.previous();
        // The second last segments, like
        // 7a
        if (!iterator.hasPrevious()) return originUrl;
        String secondLastSegment = iterator.previous();
        // The third last segments, like
        // 31
        if (!iterator.hasPrevious()) return originUrl;
        String thirdLastSegment = iterator.previous();
        // Check path segments
        if (lastSegment != null && secondLastSegment != null
                && thirdLastSegment != null
                && lastSegment.startsWith(thirdLastSegment)
                && lastSegment.startsWith(secondLastSegment, thirdLastSegment.length())) {
            return getThumbUrlPrefix() + thirdLastSegment + "/" + secondLastSegment + "/" + lastSegment;
        } else {
            return originUrl;
        }
    }
}
