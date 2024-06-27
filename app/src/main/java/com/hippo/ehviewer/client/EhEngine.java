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

import static com.hippo.ehviewer.client.data.ListUrlBuilder.MODE_NORMAL;

import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.GetText;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.data.ArchiverData;
import com.hippo.ehviewer.client.data.EhNewsDetail;
import com.hippo.ehviewer.client.data.EhTopListDetail;
import com.hippo.ehviewer.client.data.GalleryCommentList;
import com.hippo.ehviewer.client.data.GalleryDetail;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.data.HomeDetail;
import com.hippo.ehviewer.client.data.userTag.TagPushParam;
import com.hippo.ehviewer.client.data.userTag.UserTag;
import com.hippo.ehviewer.client.data.userTag.UserTagList;
import com.hippo.ehviewer.client.data.PreviewSet;
import com.hippo.ehviewer.client.exception.CancelledException;
import com.hippo.ehviewer.client.exception.EhException;
import com.hippo.ehviewer.client.exception.NoHAtHClientException;
import com.hippo.ehviewer.client.exception.ParseException;
import com.hippo.ehviewer.client.parser.ArchiveParser;
import com.hippo.ehviewer.client.parser.EhEventParse;
import com.hippo.ehviewer.client.parser.EhHomeParser;
import com.hippo.ehviewer.client.parser.FavoritesParser;
import com.hippo.ehviewer.client.parser.ForumsParser;
import com.hippo.ehviewer.client.parser.GalleryApiParser;
import com.hippo.ehviewer.client.parser.GalleryDetailParser;
import com.hippo.ehviewer.client.parser.GalleryListParser;
import com.hippo.ehviewer.client.parser.GalleryPageApiParser;
import com.hippo.ehviewer.client.parser.GalleryPageParser;
import com.hippo.ehviewer.client.parser.GalleryTokenApiParser;
import com.hippo.ehviewer.client.parser.MyTagLitParser;
import com.hippo.ehviewer.client.parser.ProfileParser;
import com.hippo.ehviewer.client.parser.RateGalleryParser;
import com.hippo.ehviewer.client.parser.SignInParser;
import com.hippo.ehviewer.client.parser.TopListParser;
import com.hippo.ehviewer.client.parser.TorrentParser;
import com.hippo.ehviewer.client.parser.VoteCommentParser;
import com.hippo.network.StatusCodeException;
import com.hippo.util.ExceptionUtils;
import com.hippo.util.FileUtils;
import com.hippo.yorozuya.AssertUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class EhEngine {

    private static final String TAG = EhEngine.class.getSimpleName();

    private static final String SAD_PANDA_DISPOSITION = "inline; filename=\"sadpanda.jpg\"";
    private static final String SAD_PANDA_TYPE = "image/gif";
    private static final String SAD_PANDA_LENGTH = "9615";

    private static final String KOKOMADE_URL = "https://exhentai.org/img/kokomade.jpg";

    public static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");
    public static final MediaType MEDIA_TYPE_URLENCODED = MediaType.parse("application/x-www-form-urlencoded");
    private static final MediaType MEDIA_TYPE_JPEG = MediaType.parse("image/jpeg");

    private static final Pattern PATTERN_NEED_HATH_CLIENT = Pattern.compile("(You must have a H@H client assigned to your account to use this feature\\.)");

    public static EhFilter sEhFilter;

    public static void initialize() {
        sEhFilter = EhFilter.getInstance();
    }

    private static void doThrowException(Call call, int code, @Nullable Headers headers,
                                         @Nullable String body, Throwable e) throws Throwable {
        if (call.isCanceled()) {
            throw new CancelledException();
        }

        // Check sad panda
        if (headers != null && SAD_PANDA_DISPOSITION.equals(headers.get("Content-Disposition")) &&
                SAD_PANDA_TYPE.equals(headers.get("Content-Type")) &&
                SAD_PANDA_LENGTH.equals(headers.get("Content-Length"))) {
            throw new EhException("Sad Panda");
        }

        // Check kokomade
        if (body != null && body.contains(KOKOMADE_URL)) {
            throw new EhException("今回はここまで\n\n" + GetText.getString(R.string.kokomade_tip));
        }

        if (e instanceof ParseException) {
            if (body != null && !body.contains("<")) {
                throw new EhException(body);
            } else if (TextUtils.isEmpty(body)) {
                throw new EhException(GetText.getString(R.string.error_empty_html));
            } else {
                if (Settings.getSaveParseErrorBody()) {
                    AppConfig.saveParseErrorBody((ParseException) e);
                }
                throw new EhException(GetText.getString(R.string.error_parse_error));
            }
        }
        if (e instanceof EhException){
            throw e;
        }

        if (code >= 400) {
            throw new StatusCodeException(code);
        }

        if (e != null) {
            throw e;
        }
    }

    private static void throwException(Call call, int code, @Nullable Headers headers,
                                       @Nullable String body, Throwable e) throws Throwable {
        try {
            doThrowException(call, code, headers, body, e);
        } catch (Throwable error) {
            error.printStackTrace();
            throw error;
        }
    }

    public static String signIn(@Nullable EhClient.Task task, OkHttpClient okHttpClient,
                                String username, String password) throws Throwable {
        FormBody.Builder builder = new FormBody.Builder()
                .add("UserName", username)
                .add("PassWord", password)
                .add("submit", "Log me in")
                .add("CookieDate", "1")
                .add("temporary_https", "off");
        String url = EhUrl.API_SIGN_IN;
        String referer = "https://forums.e-hentai.org/index.php?act=Login&CODE=00";
        String origin = "https://forums.e-hentai.org";
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer, origin)
                .post(builder.build())
                .build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            return SignInParser.parse(body);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }
    }

    private static void fillGalleryList(@Nullable EhClient.Task task, OkHttpClient okHttpClient, List<GalleryInfo> list, String url, boolean filter) throws Throwable {
        // Filter title and uploader
        if (filter) {
            for (int i = 0, n = list.size(); i < n; i++) {
                GalleryInfo info = list.get(i);
                if (!sEhFilter.filterTitle(info) || !sEhFilter.filterUploader(info)) {
                    list.remove(i);
                    i--;
                    n--;
                }
            }
        }

        boolean hasTags = false;
        boolean hasPages = false;
        boolean hasRated = false;
        for (GalleryInfo gi : list) {
            if (gi.simpleTags != null) {
                hasTags = true;
            }
            if (gi.pages != 0) {
                hasPages = true;
            }
            if (gi.rated) {
                hasRated = true;
            }
        }

        boolean needApi = (filter && sEhFilter.needTags() && !hasTags) ||
                (Settings.getShowGalleryPages() && !hasPages) ||
                hasRated;
        if (needApi) {
            fillGalleryListByApi(task, okHttpClient, list, url);
        }

        // Filter tag
        if (filter) {
            for (int i = 0, n = list.size(); i < n; i++) {
                GalleryInfo info = list.get(i);
                // Thumbnail mode need filter uploader again
                if (!sEhFilter.filterUploader(info) || !sEhFilter.filterTag(info) || !sEhFilter.filterTagNamespace(info)) {
                    list.remove(i);
                    i--;
                    n--;
                }
            }
        }

        for (GalleryInfo info : list) {
            info.thumb = EhUrl.getFixedPreviewThumbUrl(info.thumb);
        }
    }

    public static GalleryListParser.Result getGalleryList(@Nullable EhClient.Task task, OkHttpClient okHttpClient,
                                                          String url,int mode) throws Throwable {
        String referer = EhUrl.getReferer();
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer).build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        GalleryListParser.Result result;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            result = GalleryListParser.parse(body,mode);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }

        fillGalleryList(task, okHttpClient, result.galleryInfoList, url, true);

        return result;
    }

    // At least, GalleryInfo contain valid gid and token
    public static List<GalleryInfo> fillGalleryListByApi(@Nullable EhClient.Task task, OkHttpClient okHttpClient,
                                                         List<GalleryInfo> galleryInfoList, String referer) throws Throwable {
        // We can only request 25 items one time at most
        final int MAX_REQUEST_SIZE = 25;
        List<GalleryInfo> requestItems = new ArrayList<>(MAX_REQUEST_SIZE);
        for (int i = 0, size = galleryInfoList.size(); i < size; i++) {
            requestItems.add(galleryInfoList.get(i));
            if (requestItems.size() == MAX_REQUEST_SIZE || i == size - 1) {
                doFillGalleryListByApi(task, okHttpClient, requestItems, referer);
                requestItems.clear();
            }
        }
        return galleryInfoList;
    }

    private static void doFillGalleryListByApi(@Nullable EhClient.Task task, OkHttpClient okHttpClient,
                                               List<GalleryInfo> galleryInfoList, String referer) throws Throwable {
        JSONObject json = new JSONObject();
        json.put("method", "gdata");
        JSONArray ja = new JSONArray();
        for (int i = 0, size = galleryInfoList.size(); i < size; i++) {
            GalleryInfo gi = galleryInfoList.get(i);
            JSONArray g = new JSONArray();
            g.put(gi.gid);
            g.put(gi.token);
            ja.put(g);
        }
        json.put("gidlist", ja);
        json.put("namespace", 1);
        String url = EhUrl.getApiUrl();
        String origin = EhUrl.getOrigin();
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer, origin)
                .post(RequestBody.create(MEDIA_TYPE_JSON, json.toString()))
                .build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            GalleryApiParser.parse(body, galleryInfoList);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }
    }
//    https://e-hentai.org/g/2914213/fc8bce61d9/
    public static GalleryDetail getGalleryDetail(@Nullable EhClient.Task task, OkHttpClient okHttpClient,
                                                 String url) throws Throwable {
        String referer = EhUrl.getReferer();
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer).build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            String html = EhEventParse.parse(body);
            if (html != null) {
                EhApplication.getInstance().showEventPane(html);
            }
            return GalleryDetailParser.parse(body);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }
    }


    public static Pair<PreviewSet, Integer> getPreviewSet(
            @Nullable EhClient.Task task, OkHttpClient okHttpClient, String url) throws Throwable {
        String referer = EhUrl.getReferer();
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer).build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            return Pair.create(GalleryDetailParser.parsePreviewSet(body),
                    GalleryDetailParser.parsePreviewPages(body));
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }
    }

    public static RateGalleryParser.Result rateGallery(@Nullable EhClient.Task task,
                                                       OkHttpClient okHttpClient, long apiUid, String apiKey, long gid,
                                                       String token, float rating) throws Throwable {
        final JSONObject json = new JSONObject();
        json.put("method", "rategallery");
        json.put("apiuid", apiUid);
        json.put("apikey", apiKey);
        json.put("gid", gid);
        json.put("token", token);
        json.put("rating", (int) Math.ceil(rating * 2));
        final RequestBody requestBody = RequestBody.create(MEDIA_TYPE_JSON, json.toString());
        String url = EhUrl.getApiUrl();
        String referer = EhUrl.getGalleryDetailUrl(gid, token);
        String origin = EhUrl.getOrigin();
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer, origin)
                .post(requestBody)
                .build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            return RateGalleryParser.parse(body);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }
    }

    public static GalleryCommentList commentGallery(@Nullable EhClient.Task task,
                                                    OkHttpClient okHttpClient, String url, String comment, String id) throws Throwable {
        FormBody.Builder builder = new FormBody.Builder();
        if (id == null) {
            builder.add("commenttext_new", comment);
        } else {
            builder.add("commenttext_edit", comment);
            builder.add("edit_comment", id);
        }
        String origin = EhUrl.getOrigin();
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, url, origin)
                .post(builder.build())
                .build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            Document document = Jsoup.parse(body);

            Elements elements = document.select("#chd + p");
            if (elements.size() > 0) {
                throw new EhException(elements.get(0).text());
            }

            return GalleryDetailParser.parseComments(document);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }
    }

    public static String getGalleryToken(@Nullable EhClient.Task task, OkHttpClient okHttpClient,
                                         long gid, String gtoken, int page) throws Throwable {
        JSONObject json = new JSONObject()
                .put("method", "gtoken")
                .put("pagelist", new JSONArray().put(
                        new JSONArray().put(gid).put(gtoken).put(page + 1)));
        final RequestBody requestBody = RequestBody.create(MEDIA_TYPE_JSON, json.toString());
        String url = EhUrl.getApiUrl();
        String referer = EhUrl.getReferer();
        String origin = EhUrl.getOrigin();
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer, origin)
                .post(requestBody)
                .build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            return GalleryTokenApiParser.parse(body);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }
    }

    public static FavoritesParser.Result getAllFavorites(OkHttpClient okHttpClient, String url) throws Throwable {
        String referer = EhUrl.getReferer();
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer).build();
        Call call = okHttpClient.newCall(request);

        String body = null;
        Headers headers = null;
        FavoritesParser.Result result;
        int code = -1;

        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            result = FavoritesParser.parse(body);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }

        return result;
    }

    public static FavoritesParser.Result getFavorites(@Nullable EhClient.Task task, OkHttpClient okHttpClient,
                                                      String url, boolean callApi) throws Throwable {
        String referer = EhUrl.getReferer();
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer).build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        FavoritesParser.Result result;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            result = FavoritesParser.parse(body);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }
        fillGalleryList(task, okHttpClient, result.galleryInfoList, url, false);
        return result;
    }

    /**
     * @param dstCat -1 for delete, 0 - 9 for cloud favorite, others throw Exception
     * @param note   max 250 characters
     */
    public static Void addFavorites(@Nullable EhClient.Task task, OkHttpClient okHttpClient,
                                    long gid, String token, int dstCat, String note) throws Throwable {
        String catStr;
        if (dstCat == -1) {
            catStr = "favdel";
        } else if (dstCat >= 0 && dstCat <= 9) {
            catStr = String.valueOf(dstCat);
        } else {
            throw new EhException("Invalid dstCat: " + dstCat);
        }
        FormBody.Builder builder = new FormBody.Builder();
        builder.add("favcat", catStr);
        builder.add("favnote", note != null ? note : "");
        // submit=Add+to+Favorites is not necessary, just use submit=Apply+Changes all the time
        builder.add("submit", "Apply Changes");
        builder.add("update", "1");
        String url = EhUrl.getAddFavorites(gid, token);
        String origin = EhUrl.getOrigin();
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, url, origin)
                .post(builder.build())
                .build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            throwException(call, code, headers, body, null);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }

        return null;
    }

    public static Void addFavoritesRange(@Nullable EhClient.Task task, OkHttpClient okHttpClient,
                                         long[] gidArray, String[] tokenArray, int dstCat) throws Throwable {
        AssertUtils.assertEquals(gidArray.length, tokenArray.length);
        for (int i = 0, n = gidArray.length; i < n; i++) {
            addFavorites(task, okHttpClient, gidArray[i], tokenArray[i], dstCat, null);
        }
        return null;
    }

    public static FavoritesParser.Result modifyFavorites(@Nullable EhClient.Task task, OkHttpClient okHttpClient,
                                                         String url, long[] gidArray, int dstCat, boolean callApi) throws Throwable {
        String catStr;
        if (dstCat == -1) {
            catStr = "delete";
        } else if (dstCat >= 0 && dstCat <= 9) {
            catStr = "fav" + dstCat;
        } else {
            throw new EhException("Invalid dstCat: " + dstCat);
        }
        FormBody.Builder builder = new FormBody.Builder();
        builder.add("ddact", catStr);
        for (long gid : gidArray) {
            builder.add("modifygids[]", Long.toString(gid));
        }
        builder.add("apply", "Apply");
        String origin = EhUrl.getOrigin();
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, url, origin)
                .post(builder.build())
                .build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        FavoritesParser.Result result;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            result = FavoritesParser.parse(body);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }

        fillGalleryList(task, okHttpClient, result.galleryInfoList, url, false);

        return result;
    }

    public static Pair<String, String>[] getTorrentList(@Nullable EhClient.Task task, OkHttpClient okHttpClient,
                                                        String url, long gid, String token) throws Throwable {
        String referer = EhUrl.getGalleryDetailUrl(gid, token);
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer).build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        Pair<String, String>[] result;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            result = TorrentParser.parse(body);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }

        return result;
    }

    public static EhTopListDetail getTopList(@Nullable EhClient.Task task, OkHttpClient okHttpClient,
                                             String url) throws Throwable {
        String referer = EhUrl.getTopListUrl();
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer).build();
//        String referer = EhUrl.getGalleryDetailUrl(gid, token);
//        Log.d(TAG, url);
//        Request request = new EhRequestBuilder(url, referer).build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        EhTopListDetail result;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            result = TopListParser.parse(body);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }

        return result;
    }

    public static Pair<String, Pair<String, String>[]> getArchiveList(@Nullable EhClient.Task task, OkHttpClient okHttpClient,
                                                                      String url, long gid, String token) throws Throwable {
        String referer = EhUrl.getGalleryDetailUrl(gid, token);
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer).build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        Pair<String, Pair<String, String>[]> result;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            result = ArchiveParser.parse(body);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }

        return result;
    }

    public static ArchiverData getArchiver(@Nullable EhClient.Task task, OkHttpClient okHttpClient,
                                           String url, long gid, String token) throws Throwable {
        String referer = EhUrl.getGalleryDetailUrl(gid, token);
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer).build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        ArchiverData result;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            result = ArchiveParser.parseArchiver(body);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }

        return result;
    }

    public static Void downloadArchive(@Nullable EhClient.Task task, OkHttpClient okHttpClient,
                                       long gid, String token, String or, String res) throws Throwable {
        if (or == null || or.length() == 0) {
            throw new EhException("Invalid form param or: " + or);
        }
        if (res == null || res.length() == 0) {
            throw new EhException("Invalid res: " + res);
        }
        FormBody.Builder builder = new FormBody.Builder();
        builder.add("hathdl_xres", res);
        String url = EhUrl.getDownloadArchive(gid, token, or);
        String referer = EhUrl.getGalleryDetailUrl(gid, token);
        String origin = EhUrl.getOrigin();
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer, origin)
                .post(builder.build())
                .build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            throwException(call, code, headers, body, null);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }

        Matcher m = PATTERN_NEED_HATH_CLIENT.matcher(body);
        if (m.find()) {
            throw new NoHAtHClientException("No H@H client");
        }

        return null;
    }

    public static String downloadArchiver(
            @Nullable EhClient.Task task, OkHttpClient okHttpClient, String url, String referer, String dltype, String dlcheck)
            throws Throwable {
        if (url == null || url.length() == 0) {
            throw new EhException("Invalid form param url: " + url);
        }
        if (referer == null || referer.length() == 0) {
            throw new EhException("Invalid form param referer: " + referer);
        }

        String origin = EhUrl.getOrigin();
        FormBody.Builder builder = new FormBody.Builder();
        builder.add("dltype", dltype);
        builder.add("dlcheck", dlcheck);
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer, origin)
                .post(builder.build())
                .build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            Pattern pattern = Pattern.compile("document.location = \"(.*)\"");
            Matcher m = pattern.matcher(body);
            if (!m.find()) {
                return null;
            }
            String continueUrl = m.group(1);
//            获取跳转链接
            Request requestContinue = new EhRequestBuilder(continueUrl, origin, null)
                    .build();
            Call callContinue = okHttpClient.newCall(requestContinue);
            Response responseC = callContinue.execute();
            if (responseC.body() == null) {
                return null;
            }
            body = responseC.body().string();
            String downloadPath = ArchiveParser.parseArchiverDownloadUrl(body);
            String downloadUrl = "https://" + responseC.request().url().host() + downloadPath;
            return downloadUrl;
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            return null;
        }
    }

    private static ProfileParser.Result getProfileInternal(@Nullable EhClient.Task task,
                                                           OkHttpClient okHttpClient, String url, String referer) throws Throwable {
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer).build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            return ProfileParser.parse(body);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }
    }

    public static ProfileParser.Result getProfile(@Nullable EhClient.Task task,
                                                  OkHttpClient okHttpClient) throws Throwable {
        String url = EhUrl.URL_FORUMS;
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, null).build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            return getProfileInternal(task, okHttpClient, ForumsParser.parse(body), url);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }
    }

    public static VoteCommentParser.Result voteComment(@Nullable EhClient.Task task, OkHttpClient okHttpClient,
                                                       long apiUid, String apiKey, long gid, String token, long commentId, int commentVote) throws Throwable {
        final JSONObject json = new JSONObject();
        json.put("method", "votecomment");
        json.put("apiuid", apiUid);
        json.put("apikey", apiKey);
        json.put("gid", gid);
        json.put("token", token);
        json.put("comment_id", commentId);
        json.put("comment_vote", commentVote);
        final RequestBody requestBody = RequestBody.create(MEDIA_TYPE_JSON, json.toString());
        String url = EhUrl.getApiUrl();
        String referer = EhUrl.getReferer();
        String origin = EhUrl.getOrigin();
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer, origin)
                .post(requestBody)
                .build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            return VoteCommentParser.parse(body, commentVote);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }
    }

    /**
     * @param image Must be jpeg
     */
    public static GalleryListParser.Result imageSearch(@Nullable EhClient.Task task, OkHttpClient okHttpClient,
                                                       File image, boolean uss, boolean osc, boolean se) throws Throwable {
        String imageName = image.getName();
        String fileName;
        File imageFile;
        boolean shouldDelete = false;
        if (imageName.contains(".")) {
            fileName = imageName;
            imageFile = image;
        } else {
            fileName = imageName + ".jpg";
            imageFile = new File(image.getParent()+"/"+fileName);
            FileUtils.copyFile(image,imageFile);
            shouldDelete = true;
        }
        MultipartBody.Builder builder = new MultipartBody.Builder();
        builder.setType(MultipartBody.FORM);
        builder.addPart(
                Headers.of("Content-Disposition", "form-data; name=\"sfile\"; filename=\"" + fileName + "\"; size=\"40\""),
                RequestBody.create(MediaType.parse("file"), imageFile)
        );
        if (uss) {
            builder.addPart(
                    Headers.of("Content-Disposition", "form-data; name=\"fs_similar\""),
                    RequestBody.create(null, "on")
            );
        }
        if (osc) {
            builder.addPart(
                    Headers.of("Content-Disposition", "form-data; name=\"fs_covers\""),
                    RequestBody.create(null, "on")
            );
        }
        if (se) {
            builder.addPart(
                    Headers.of("Content-Disposition", "form-data; name=\"fs_exp\""),
                    RequestBody.create(null, "on")
            );
        }
        builder.addPart(
                Headers.of("Content-Disposition", "form-data; name=\"f_sfile\""),
                RequestBody.create(MediaType.parse("submit"), "File Search")
        );
        String url = EhUrl.getImageSearchUrl();
        String referer = EhUrl.getReferer() + '/';
        String origin = EhUrl.getOrigin();
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer, origin)
                .post(builder.build())
                .build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        GalleryListParser.Result result;
        int code = -1;
        try {
            Response response = call.execute();

            Log.d(TAG, "" + response.request().url().toString());

            code = response.code();
            if (code == 302) {
                request = new EhRequestBuilder(response.headers().get("Location"), referer).build();
                call = okHttpClient.newCall(request);
                try {
                    response = call.execute();
                } catch (Throwable e) {
                    ExceptionUtils.throwIfFatal(e);
                    throwException(call, code, null, null, e);
                    throw e;
                }
            }
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            result = GalleryListParser.parse(body, MODE_NORMAL);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            if (shouldDelete){
                imageFile.delete();
            }
            throw e;
        }
        if (shouldDelete){
            imageFile.delete();
        }
        fillGalleryList(task, okHttpClient, result.galleryInfoList, url, true);

        return result;
    }

    public static GalleryPageParser.Result getGalleryPage(@Nullable EhClient.Task task,
                                                          OkHttpClient okHttpClient, String url, long gid, String token) throws Throwable {
        String referer = EhUrl.getGalleryDetailUrl(gid, token);
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer).build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            return GalleryPageParser.parse(body);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }
    }

    public static GalleryPageApiParser.Result getGalleryPageApi(@Nullable EhClient.Task task,
                                                                OkHttpClient okHttpClient, long gid, int index, String pToken, String showKey, String previousPToken) throws Throwable {
        final JSONObject json = new JSONObject();
        json.put("method", "showpage");
        json.put("gid", gid);
        json.put("page", index + 1);
        json.put("imgkey", pToken);
        json.put("showkey", showKey);
        final RequestBody requestBody = RequestBody.create(MEDIA_TYPE_JSON, json.toString());
        String url = EhUrl.getApiUrl();
        String referer = null;
        if (index > 0 && previousPToken != null) {
            referer = EhUrl.getPageUrl(gid, index - 1, previousPToken);
        }
        String origin = EhUrl.getOrigin();
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer, origin)
                .post(requestBody)
                .build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            return GalleryPageApiParser.parse(body);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }
    }

    public static UserTagList getWatchedList(@Nullable EhClient.Task task,
                                             OkHttpClient okHttpClient, String url) throws Throwable {
        if (!Settings.isLogin()) {
            return null;
        }
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url).build();
        Call call = okHttpClient.newCall(request);
        assert task != null;
        task.setCall(call);

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();

            return MyTagLitParser.parse(body);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }
    }

    public static UserTagList addTag(EhClient.Task task, OkHttpClient okHttpClient, String url, TagPushParam param) throws Throwable {

        if (!Settings.isLogin()) {
            return null;
        }
        Log.d(TAG, url);

        RequestBody requestBody = RequestBody.create(MEDIA_TYPE_URLENCODED, param.addTagParam());

        Request request = new EhRequestBuilder(url).post(requestBody).build();
        Call call = okHttpClient.newCall(request);
        assert task != null;
        task.setCall(call);

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();

            return MyTagLitParser.parse(body);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            return null;
//            throw e;
        }

    }

    public static Response editWatchedTag() throws Throwable {
        return null;
    }

    public static UserTagList deleteWatchedTag(EhClient.Task task, OkHttpClient okHttpClient, String url, UserTag param) throws Throwable {
        if (!Settings.isLogin()) {
            return null;
        }
        Log.d(TAG, url);

        RequestBody requestBody = RequestBody.create(MEDIA_TYPE_URLENCODED, param.deleteParam());

        Request request = new EhRequestBuilder(url).post(requestBody).build();
        Call call = okHttpClient.newCall(request);
        assert task != null;
        task.setCall(call);

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();

            return MyTagLitParser.parse(body);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            return null;
//            throw e;
        }
    }

    public static EhNewsDetail getEhNews(EhClient.Task task, OkHttpClient mOkHttpClient) throws Throwable {
//        return EhNewsParse.parse("");
        String url = EhUrl.getEhNewsUrl();
        Request request = new EhRequestBuilder(url, null).build();

        Call call = mOkHttpClient.newCall(request);
        if (null != task) {
            task.setCall(call);
        }
        int code = -1;
        String body;
        Headers headers = null;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            if (response.body() == null) {
                return null;
            }
            body = response.body().string();
            return new EhNewsDetail(body);
        } catch (IOException e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, null, e);
            e.printStackTrace();
        }

        return new EhNewsDetail();
    }

    public static HomeDetail getHomeDetail(@Nullable EhClient.Task task, OkHttpClient okHttpClient) throws Throwable {
        String referer = EhUrl.getReferer();
        String url = EhUrl.getHomeUrl();
        Log.d(TAG, url);
        Request request = new EhRequestBuilder(url, referer).build();
        Call call = okHttpClient.newCall(request);

        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            String html = EhEventParse.parse(body);
            if (html != null) {
                EhApplication.getInstance().showEventPane(html);
            }
            return EhHomeParser.parse(body);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }
    }

    public static HomeDetail resetLimit(@Nullable EhClient.Task task, OkHttpClient okHttpClient) throws Throwable {
        FormBody.Builder builder = new FormBody.Builder();
        builder.add("reset_imagelimit", "Reset Limit");
        String referer = EhUrl.getReferer();
        String url = EhUrl.getHomeUrl();
        Log.d(TAG, url);
        FormBody formBody = builder.build();
        Request request = new EhRequestBuilder(url, referer).post(formBody).build();
        ;
        Call call = okHttpClient.newCall(request);
        // Put call
        if (null != task) {
            task.setCall(call);
        }

        String body = null;
        Headers headers = null;
        int code = -1;
        try {
            Response response = call.execute();
            code = response.code();
            headers = response.headers();
            assert response.body() != null;
            body = response.body().string();
            String html = EhEventParse.parse(body);
            if (html != null) {
                EhApplication.getInstance().showEventPane(html);
            }
            return EhHomeParser.parse(body);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throwException(call, code, headers, body, e);
            throw e;
        }
    }
}
