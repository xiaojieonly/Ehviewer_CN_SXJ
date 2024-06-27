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

package com.hippo.ehviewer.client.parser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.client.data.GalleryComment;
import com.hippo.ehviewer.client.data.GalleryCommentList;
import com.hippo.ehviewer.client.data.GalleryDetail;
import com.hippo.ehviewer.client.data.GalleryTagGroup;
import com.hippo.ehviewer.client.data.LargePreviewSet;
import com.hippo.ehviewer.client.data.NewVersion;
import com.hippo.ehviewer.client.data.NormalPreviewSet;
import com.hippo.ehviewer.client.data.PreviewSet;
import com.hippo.ehviewer.client.exception.EhException;
import com.hippo.ehviewer.client.exception.GalleryUnavailableException;
import com.hippo.ehviewer.client.exception.OffensiveException;
import com.hippo.ehviewer.client.exception.ParseException;
import com.hippo.ehviewer.client.exception.PiningException;
import com.hippo.util.ExceptionUtils;
import com.hippo.util.JsoupUtils;
import com.hippo.util.MutableBoolean;
import com.hippo.yorozuya.NumberUtils;
import com.hippo.yorozuya.StringUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 画廊数据解析
 */
public class GalleryDetailParser {

    private static final Pattern PATTERN_ERROR = Pattern.compile("<div class=\"d\">\n<p>([^<]+)</p>");
    private static final Pattern PATTERN_DETAIL = Pattern.compile("var gid = (\\d+);.+?var token = \"([a-f0-9]+)\";.+?var apiuid = ([\\-\\d]+);.+?var apikey = \"([a-f0-9]+)\";", Pattern.DOTALL);
    private static final Pattern PATTERN_TORRENT = Pattern.compile("<a[^<>]*onclick=\"return popUp\\('([^']+)'[^)]+\\)\">Torrent Download \\((\\d+)\\)</a>");
    private static final Pattern PATTERN_ARCHIVE = Pattern.compile("<a[^<>]*onclick=\"return popUp\\('([^']+)'[^)]+\\)\">Archive Download</a>");
    private static final Pattern PATTERN_COVER = Pattern.compile("width:(\\d+)px; height:(\\d+)px.+?url\\((.+?)\\)");
    private static final Pattern PATTERN_TAG_GROUP = Pattern.compile("<tr><td[^<>]+>([\\w\\s]+):</td><td>(?:<div[^<>]+><a[^<>]+>[\\w\\s]+</a></div>)+</td></tr>");
    private static final Pattern PATTERN_TAG = Pattern.compile("<div[^<>]+><a[^<>]+>([\\w\\s]+)</a></div>");
    private static final Pattern PATTERN_COMMENT = Pattern.compile("<div class=\"c3\">Posted on ([^<>]+) by: &nbsp; <a[^<>]+>([^<>]+)</a>.+?<div class=\"c6\"[^>]*>(.+?)</div><div class=\"c[78]\"");
    private static final Pattern PATTERN_PAGES = Pattern.compile("<tr><td[^<>]*>Length:</td><td[^<>]*>([\\d,]+) pages</td></tr>");
    private static final Pattern PATTERN_PREVIEW_PAGES = Pattern.compile("<td[^>]+><a[^>]+>([\\d,]+)</a></td><td[^>]+>(?:<a[^>]+>)?&gt;(?:</a>)?</td>");
    private static final Pattern PATTERN_NORMAL_PREVIEW = Pattern.compile("<div class=\"gdtm\"[^<>]*><div[^<>]*width:(\\d+)[^<>]*height:(\\d+)[^<>]*\\((.+?)\\)[^<>]*-(\\d+)px[^<>]*><a[^<>]*href=\"(.+?)\"[^<>]*><img alt=\"([\\d,]+)\"");
    private static final Pattern PATTERN_LARGE_PREVIEW = Pattern.compile("<div class=\"gdtl\".+?<a href=\"(.+?)\"><img alt=\"([\\d,]+)\".+?src=\"(.+?)\"");
    private static final Pattern PATTERN_ARCHIVE_DOWNLOAD = Pattern.compile("onclick=\"return popUp('(.*)',480,320)\">Archive Download</a>");

    private static final GalleryTagGroup[] EMPTY_GALLERY_TAG_GROUP_ARRAY = new GalleryTagGroup[0];
    private static final GalleryCommentList EMPTY_GALLERY_COMMENT_ARRAY = new GalleryCommentList(new GalleryComment[0], false);

    private static final DateFormat WEB_COMMENT_DATE_FORMAT = new SimpleDateFormat("dd MMMMM yyyy, HH:mm", Locale.US);

    static {
        WEB_COMMENT_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private static final String OFFENSIVE_STRING =
            "<p>(And if you choose to ignore this warning, you lose all rights to complain about it in the future.)</p>";
    private static final String PINING_STRING =
            "<p>This gallery is pining for the fjords.</p>";
    private static final String UNAVAILABLE_STRING =
            "This gallery is unavailable";

    /**
     * 画廊详情页数据处理
     *
     * @param body 传入原始画廊html数据
     * @return 返回从html数据中提取的有用数据
     * @throws EhException
     */
    public static GalleryDetail parse(String body) throws EhException {
        if (body.contains(OFFENSIVE_STRING)) {
            throw new OffensiveException();
        }

        if (body.contains(PINING_STRING)) {
            throw new PiningException();
        }
        if (body.contains(UNAVAILABLE_STRING)) {
            throw new GalleryUnavailableException();
        }

        // Error info
        Matcher m = PATTERN_ERROR.matcher(body);
        if (m.find()) {
            throw new EhException(m.group(1));
        }


        GalleryDetail galleryDetail = new GalleryDetail();
//        GalleryDetail galleryDetailOld = new GalleryDetail();
//        galleryDetail.body = body;
//
        Document document = Jsoup.parse(body);
        parseDetail(galleryDetail, document, body);
        galleryDetail.tags = parseTagGroups(document);       //获取标签列表
        galleryDetail.comments = parseComments(document);    //获取评论内容
        galleryDetail.previewPages = parsePreviewPages(document, body);  //获取画廊图片数量
        galleryDetail.previewSet = parsePreviewSet(document, body);//获取画廊浏览参数（如：之前有观看则从上次看到的位置开始）
        galleryDetail.SpiderInfoPages = parsePages(body);
        galleryDetail.SpiderInfoPreviewPages = parsePreviewPages(body);
        galleryDetail.SpiderInfoPreviewSet = parsePreviewSet(body);
//        parseDetail(galleryDetailOld, document, body);
//        galleryDetailOld.tags = parseTagGroups(document);       //获取标签列表
//        galleryDetailOld.comments = parseComments(document);    //获取评论内容
//        galleryDetailOld.previewPages = parsePreviewPages(document, body);  //获取画廊图片数量
//        galleryDetailOld.previewSet = parsePreviewSet(document, body);//获取画廊浏览参数（如：之前有观看则从上次看到的位置开始）
//
//        galleryDetail.oldDetail = galleryDetailOld;
        return galleryDetail;
    }

    @SuppressWarnings("ConstantConditions")
    private static void parseDetail(GalleryDetail gd, Document d, String body) throws ParseException {
        Matcher matcher = PATTERN_DETAIL.matcher(body);
        if (matcher.find()) {
            gd.gid = NumberUtils.parseLongSafely(matcher.group(1), -1L);
            gd.token = matcher.group(2);
            gd.apiUid = NumberUtils.parseLongSafely(matcher.group(3), -1L);
            gd.apiKey = matcher.group(4);
        } else {
            throw new ParseException("Can't parse gallery detail", body);
        }
        if (gd.gid == -1L) {
            throw new ParseException("Can't parse gallery detail", body);
        }

        matcher = PATTERN_TORRENT.matcher(body);
        if (matcher.find()) {
            gd.torrentUrl = StringUtils.unescapeXml(StringUtils.trim(matcher.group(1)));
            gd.torrentCount = NumberUtils.parseIntSafely(matcher.group(2), 0);
        } else {
            gd.torrentCount = 0;
            gd.torrentUrl = "";
        }

        matcher = PATTERN_ARCHIVE.matcher(body);
        if (matcher.find()) {
            gd.archiveUrl = StringUtils.unescapeXml(StringUtils.trim(matcher.group(1)));
        } else {
            gd.archiveUrl = "";
        }

        try {
            Element gm = JsoupUtils.getElementByClass(d, "gm");

            // Thumb url
            Element gd1 = gm.getElementById("gd1");
            try {
                gd.thumb = parseCoverStyle(StringUtils.trim(gd1.child(0).attr("style")));
            } catch (Throwable e) {
                ExceptionUtils.throwIfFatal(e);
                gd.thumb = "";
            }

            // Title
            Element gn = gm.getElementById("gn");
            if (null != gn) {
                gd.title = StringUtils.trim(gn.text());
            } else {
                gd.title = "";
            }

            // Jpn title
            Element gj = gm.getElementById("gj");
            if (null != gj) {
                gd.titleJpn = StringUtils.trim(gj.text());
            } else {
                gd.titleJpn = "";
            }

            // Category
            Element gdc = gm.getElementById("gdc");
            try {
                Element ce = JsoupUtils.getElementByClass(gdc, "cn");
                if (ce == null) {
                    ce = JsoupUtils.getElementByClass(gdc, "cs");
                }
                gd.category = EhUtils.getCategory(ce.text());
            } catch (Throwable e) {
                ExceptionUtils.throwIfFatal(e);
                gd.category = EhUtils.UNKNOWN;
            }

            // Uploader
            Element gdn = gm.getElementById("gdn");
            if (null != gdn) {
                gd.uploader = StringUtils.trim(gdn.text());
            } else {
                gd.uploader = "";
            }

            Element gdd = gm.getElementById("gdd");
            gd.posted = "";
            gd.parent = "";
            gd.visible = "";
            gd.size = "";
            gd.pages = 0;
            gd.favoriteCount = 0;
            try {
                Elements es = gdd.child(0).child(0).children();
                for (int i = 0, n = es.size(); i < n; i++) {
                    parseDetailInfo(gd, es.get(i), body);
                }
            } catch (Throwable e) {
                ExceptionUtils.throwIfFatal(e);
                // Ignore
            }

            // Rating count
            Element rating_count = gm.getElementById("rating_count");
            if (null != rating_count) {
                gd.ratingCount = NumberUtils.parseIntSafely(
                        StringUtils.trim(rating_count.text()), 0);
            } else {
                gd.ratingCount = 0;
            }

            // Rating
            Element rating_label = gm.getElementById("rating_label");
            if (null != rating_label) {
                String ratingStr = StringUtils.trim(rating_label.text());
                if ("Not Yet Rated".equals(ratingStr)) {
                    gd.rating = -1.0f;
                } else {
                    int index = ratingStr.indexOf(' ');
                    if (index == -1 || index >= ratingStr.length()) {
                        gd.rating = 0f;
                    } else {
                        gd.rating = NumberUtils.parseFloatSafely(ratingStr.substring(index + 1), 0f);
                    }
                }
            } else {
                gd.rating = -1.0f;
            }

            // isFavorited
            Element gdf = gm.getElementById("gdf");
            gd.isFavorited = null != gdf && !StringUtils.trim(gdf.text()).equals("Add to Favorites");
            if (gdf != null) {
                final String favoriteName = StringUtils.trim(gdf.text());
                if (favoriteName.equals("Add to Favorites")) {
                    gd.favoriteName = null;
                } else {
                    gd.favoriteName = StringUtils.trim(gdf.text());
                }
            }
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            throw new ParseException("Can't parse gallery detail", body);
        }
        try {
            Element updateElement = d.getElementById("gnd");
            List<NewVersion> versionList = new ArrayList<>();
            List<TextNode> textNodes = updateElement.textNodes();
            for (int i = 0; i < updateElement.childrenSize(); i++) {
                Element urlE = updateElement.child(i);
                String url = urlE.absUrl("href");
                if (url != null && !url.isEmpty()) {
                    NewVersion version = new NewVersion();
                    version.versionUrl = url;
                    String nameS = urlE.text();
                    TextNode textNode = textNodes.get(versionList.size());
                    String timeS = textNode.text();
                    if (timeS != null && !timeS.isEmpty()) {
                        version.versionName = nameS + timeS;
                    } else {
                        version.versionName = nameS;
                    }
                    versionList.add(version);
                }
            }
            if (versionList.isEmpty()) {
                gd.newVersions = null;
                return;
            }
            gd.newVersions = versionList.toArray(new NewVersion[0]);
        } catch (Throwable ignore) {
            gd.newVersions = null;
        }
    }

    // width:250px; height:356px; background:transparent url(https://exhentai.org/t/fe/1f/fe1fcfa9bf8fba2f03982eda0aa347cc9d6a6372-145921-1050-1492-jpg_250.jpg) 0 0 no-repeat
    private static String parseCoverStyle(String str) {
        Matcher matcher = PATTERN_COVER.matcher(str);
        if (matcher.find()) {
            return EhUtils.handleThumbUrlResolution(matcher.group(3));
        } else {
            return "";
        }
    }

    private static void parseDetailInfo(GalleryDetail gd, Element e, String body) {
        Elements es = e.children();
        if (es.size() < 2) {
            return;
        }

        String key = StringUtils.trim(es.get(0).text());
        String value = StringUtils.trim(es.get(1).ownText());
        if (key.startsWith("Posted")) {
            gd.posted = value;
        } else if (key.startsWith("Parent")) {
            Element a = es.get(1).children().first();
            if (a != null) {
                gd.parent = a.attr("href");
            }
        } else if (key.startsWith("Visible")) {
            gd.visible = value;
        } else if (key.startsWith("Language")) {
            gd.language = value;
        } else if (key.startsWith("File Size")) {
            gd.size = value;
        } else if (key.startsWith("Length")) {
            int index = value.indexOf(' ');
            if (index >= 0) {
                gd.pages = NumberUtils.parseIntSafely(value.substring(0, index), 1);
            } else {
                gd.pages = 1;
            }
        } else if (key.startsWith("Favorited")) {
            switch (value) {
                case "Never":
                    gd.favoriteCount = 0;
                    break;
                case "Once":
                    gd.favoriteCount = 1;
                    break;
                default:
                    int index = value.indexOf(' ');
                    if (index == -1) {
                        gd.favoriteCount = 0;
                    } else {
                        gd.favoriteCount = NumberUtils.parseIntSafely(value.substring(0, index), 0);
                    }
                    break;
            }
        }
    }

    @Nullable
    private static GalleryTagGroup parseTagGroup(Element element) {
        try {
            GalleryTagGroup group = new GalleryTagGroup();

            String nameSpace = element.child(0).text();
            // Remove last ':'
            nameSpace = nameSpace.substring(0, nameSpace.length() - 1);
            group.groupName = nameSpace;

            Elements tags = element.child(1).children();
            for (int i = 0, n = tags.size(); i < n; i++) {
                String tag = tags.get(i).text();
                // Sometimes parody tag is followed with '|' and english translate, just remove them
                int index = tag.indexOf('|');
                if (index >= 0) {
                    tag = tag.substring(0, index).trim();
                }
                group.addTag(tag);
            }

            return group.size() > 0 ? group : null;
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Parse tag groups with html parser
     */
    @NonNull
    public static GalleryTagGroup[] parseTagGroups(Document document) {
        try {
            Element taglist = document.getElementById("taglist");
            Elements tagGroups = taglist.child(0).child(0).children();
            return parseTagGroups(tagGroups);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            e.printStackTrace();
            return EMPTY_GALLERY_TAG_GROUP_ARRAY;
        }
    }

    /**
     * 从HTML元素中提取tag
     *
     * @param trs
     * @return
     */
    @NonNull
    public static GalleryTagGroup[] parseTagGroups(Elements trs) {
        try {
            List<GalleryTagGroup> list = new ArrayList<>(trs.size());
            for (int i = 0, n = trs.size(); i < n; i++) {
                GalleryTagGroup group = parseTagGroup(trs.get(i));
                if (null != group) {
                    list.add(group);
                }
            }
            return list.toArray(new GalleryTagGroup[list.size()]);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            e.printStackTrace();
            return EMPTY_GALLERY_TAG_GROUP_ARRAY;
        }
    }

    /**
     * Parse tag groups with regular expressions
     */
    @NonNull
    private static GalleryTagGroup[] parseTagGroups(String body) throws EhException {
        List<GalleryTagGroup> list = new LinkedList<>();

        Matcher m = PATTERN_TAG_GROUP.matcher(body);
        while (m.find()) {
            GalleryTagGroup tagGroup = new GalleryTagGroup();
            tagGroup.groupName = ParserUtils.trim(m.group(1));
            parseGroup(tagGroup, m.group(0));
            list.add(tagGroup);
        }

        return list.toArray(new GalleryTagGroup[list.size()]);
    }

    private static void parseGroup(GalleryTagGroup tagGroup, String body) {
        Matcher m = PATTERN_TAG.matcher(body);
        while (m.find()) {
            tagGroup.addTag(ParserUtils.trim(m.group(1)));
        }
    }

    @Nullable
    @SuppressWarnings("ConstantConditions")
    public static GalleryComment parseComment(Element element) {
        try {
            GalleryComment comment = new GalleryComment();
            // Id
            Element a = element.previousElementSibling();
            String name = a.attr("name");
            comment.id = Integer.parseInt(StringUtils.trim(name).substring(1));
            // Editable, vote up and vote down
            Element c4 = JsoupUtils.getElementByClass(element, "c4");
            if (null != c4) {
                for (Element e : c4.children()) {
                    switch (e.text()) {
                        case "Vote+":
                            comment.voteUpAble = true;
                            comment.voteUpEd = !StringUtils.trim(e.attr("style")).isEmpty();
                            break;
                        case "Vote-":
                            comment.voteDownAble = true;
                            comment.voteDownEd = !StringUtils.trim(e.attr("style")).isEmpty();
                            break;
                        case "Edit":
                            comment.editable = true;
                            break;
                    }
                }
            }
            // Vote state
            Element c7 = JsoupUtils.getElementByClass(element, "c7");
            if (null != c7) {
                comment.voteState = StringUtils.trim(c7.text());
            }
            // Score
            Element c5 = JsoupUtils.getElementByClass(element, "c5");
            if (null != c5) {
                Elements es = c5.children();
                if (!es.isEmpty()) {
                    comment.score = NumberUtils.parseIntSafely(StringUtils.trim(es.get(0).text()), 0);
                }
            }
            // time
            Element c3 = JsoupUtils.getElementByClass(element, "c3");
            String temp = c3.ownText();
            temp = temp.substring("Posted on ".length(), temp.length() - " by:".length());
            comment.time = WEB_COMMENT_DATE_FORMAT.parse(temp).getTime();
            // user
            comment.user = c3.child(0).text();
            // comment
            comment.comment = JsoupUtils.getElementByClass(element, "c6").html();
            // last edited
            Element c8 = JsoupUtils.getElementByClass(element, "c8");
            if (c8 != null) {
                Element e = c8.children().first();
                if (e != null) {
                    comment.lastEdited = WEB_COMMENT_DATE_FORMAT.parse(temp).getTime();
                }
            }
            return comment;
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Parse comments with html parser
     */
    @NonNull
    public static GalleryCommentList parseComments(Document document) {
        try {
            Element cdiv = document.getElementById("cdiv");
            Elements c1s = cdiv.getElementsByClass("c1");

            List<GalleryComment> list = new ArrayList<>(c1s.size());
            for (int i = 0, n = c1s.size(); i < n; i++) {
                GalleryComment comment = parseComment(c1s.get(i));
                if (null != comment && !EhDB.inBlackList(comment.user)) {
                    list.add(comment);
                }
            }

            Element chd = cdiv.getElementById("chd");
            MutableBoolean hasMore = new MutableBoolean(false);
            NodeTraversor.traverse(new NodeVisitor() {
                @Override
                public void head(Node node, int depth) {
                    if (node instanceof Element && ((Element) node).text().equals("click to show all")) {
                        hasMore.value = true;
                    }
                }

                @Override
                public void tail(Node node, int depth) {
                }
            }, chd);

            return new GalleryCommentList(list.toArray(new GalleryComment[list.size()]), hasMore.value);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            e.printStackTrace();
            return EMPTY_GALLERY_COMMENT_ARRAY;
        }
    }

    /**
     * Parse comments with regular expressions
     */
    @NonNull
    public static GalleryComment[] parseComments(String body) {
        List<GalleryComment> list = new LinkedList<>();

        Matcher m = PATTERN_COMMENT.matcher(body);
        while (m.find()) {
            String webDateString = ParserUtils.trim(m.group(1));
            Date date;
            try {
                date = WEB_COMMENT_DATE_FORMAT.parse(webDateString);
            } catch (java.text.ParseException e) {
                date = new Date(0L);
            }
            GalleryComment comment = new GalleryComment();
            comment.time = date.getTime();
            comment.user = ParserUtils.trim(m.group(2));
            comment.comment = m.group(3);
            list.add(comment);
        }

        return list.toArray(new GalleryComment[list.size()]);
    }

    /**
     * Parse preview pages with html parser
     */
    public static int parsePreviewPages(Document document, String body) throws ParseException {
        try {
            Elements elements = document.getElementsByClass("ptt").first().child(0).child(0).children();
            return Integer.parseInt(elements.get(elements.size() - 2).text());
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            e.printStackTrace();
            throw new ParseException("Can't parse preview pages", body);
        }
    }

    /**
     * Parse preview pages with regular expressions
     */
    public static int parsePreviewPages(String body) throws ParseException {
        Matcher m = PATTERN_PREVIEW_PAGES.matcher(body);
        int previewPages = -1;
        if (m.find()) {
            previewPages = ParserUtils.parseInt(m.group(1), -1);
        }

        if (previewPages <= 0) {
            throw new ParseException("Parse preview page count error", body);
        }

        return previewPages;
    }

    /**
     * Parse pages with regular expressions
     */
    public static int parsePages(String body) throws ParseException {
        int pages = -1;

        Matcher m = PATTERN_PAGES.matcher(body);
        if (m.find()) {
            pages = ParserUtils.parseInt(m.group(1), -1);
        }

        if (pages < 0) {
            throw new ParseException("Parse pages error", body);
        }

        return pages;
    }

    public static PreviewSet parsePreviewSet(Document d, String body) throws ParseException {
        PreviewSet previewSet;
        try {
            previewSet = parseLargePreviewSet(d, body);
            if (previewSet == null) {
                previewSet = parseNormalPreviewSet(body);
            }
            if (previewSet == null) {
                throw new ParseException("加载预览图失败", body);
            }
            return previewSet;
//            return parseLargePreviewSet(d, body);
        } catch (ParseException e) {
            ExceptionUtils.throwIfFatal(e);
            return new NormalPreviewSet();
//            return parseNormalPreviewSet(body);
        }
    }

    public static PreviewSet parsePreviewSet(String body) throws ParseException {
        try {
            return parseLargePreviewSet(body);
        } catch (ParseException e) {
            return parseNormalPreviewSet(body);
        }
    }

    /**
     * Parse large previews with regular expressions
     */
    private static LargePreviewSet parseLargePreviewSet(Document d, String body) throws ParseException {
        try {
            LargePreviewSet largePreviewSet = new LargePreviewSet();
            Element gdt = d.getElementById("gdt");
            Elements gdtls = gdt.getElementsByClass("gdtl");
            int n = gdtls.size();
            if (n <= 0) {
                return null;
//                throw new ParseException("Can't parse large preview", body);
            }
            for (int i = 0; i < n; i++) {
                Element element = gdtls.get(i).child(0);
                String pageUrl = element.attr("href");
                element = element.child(0);
                String imageUrl = element.attr("src");
                if (Settings.getFixThumbUrl()) {
                    imageUrl = EhUrl.getFixedPreviewThumbUrl(imageUrl);
                }
                int index = Integer.parseInt(element.attr("alt")) - 1;
                largePreviewSet.addItem(index, imageUrl, pageUrl);
            }
            return largePreviewSet;
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            e.printStackTrace();
            throw new ParseException("Can't parse large preview", body);
        }
    }

    /**
     * Parse large previews with regular expressions
     */
    private static LargePreviewSet parseLargePreviewSet(String body) throws ParseException {
        Matcher m = PATTERN_LARGE_PREVIEW.matcher(body);
        LargePreviewSet largePreviewSet = new LargePreviewSet();

        while (m.find()) {
            int index = ParserUtils.parseInt(m.group(2), 0) - 1;
            if (index < 0) {
                continue;
            }
            String imageUrl = ParserUtils.trim(m.group(3));
            String pageUrl = ParserUtils.trim(m.group(1));
            if (Settings.getFixThumbUrl()) {
                imageUrl = EhUrl.getFixedPreviewThumbUrl(imageUrl);
            }
            largePreviewSet.addItem(index, imageUrl, pageUrl);
        }

        if (largePreviewSet.size() == 0) {
            throw new ParseException("Can't parse large preview", body);
        }

        return largePreviewSet;
    }

    /**
     * Parse normal previews with regular expressions
     */
    private static NormalPreviewSet parseNormalPreviewSet(String body) throws ParseException {
        Matcher m = PATTERN_NORMAL_PREVIEW.matcher(body);
        NormalPreviewSet normalPreviewSet = new NormalPreviewSet();
        while (m.find()) {
            int position = ParserUtils.parseInt(m.group(6), 0) - 1;
            if (position < 0) {
                continue;
            }
            String imageUrl = ParserUtils.trim(m.group(3));
            int xOffset = ParserUtils.parseInt(m.group(4), 0);
            int yOffset = 0;
            int width = ParserUtils.parseInt(m.group(1), 0);
            if (width <= 0) {
                continue;
            }
            int height = ParserUtils.parseInt(m.group(2), 0);
            if (height <= 0) {
                continue;
            }
            String pageUrl = ParserUtils.trim(m.group(5));
            normalPreviewSet.addItem(position, imageUrl, xOffset, yOffset, width, height, pageUrl);
        }

        if (normalPreviewSet.size() == 0) {
            throw new ParseException("Can't parse normal preview", body);
        }

        return normalPreviewSet;
    }

}
