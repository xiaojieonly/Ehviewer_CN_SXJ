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

package com.hippo.ehviewer.daogenerator;


import org.greenrobot.greendao.generator.DaoGenerator;
import org.greenrobot.greendao.generator.Entity;
import org.greenrobot.greendao.generator.Schema;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;

import java.io.File;
import java.io.FileWriter;

public class EhDaoGenerator {

    private static final String PACKAGE = "com.hippo.ehviewer.dao";
    private static final String OUT_DIR = "app/src/main/java-gen";
    private static final String DELETE_DIR = "app/src/main/java-gen/com/hippo/ehviewer/dao";

    private static final int VERSION = 6;

    private static final String DOWNLOAD_INFO_PATH = "app/src/main/java-gen/com/hippo/ehviewer/dao/DownloadInfo.java";
    private static final String HISTORY_INFO_PATH = "app/src/main/java-gen/com/hippo/ehviewer/dao/HistoryInfo.java";
    private static final String QUICK_SEARCH_PATH = "app/src/main/java-gen/com/hippo/ehviewer/dao/QuickSearch.java";
    private static final String LOCAL_FAVORITE_INFO_PATH = "app/src/main/java-gen/com/hippo/ehviewer/dao/LocalFavoriteInfo.java";
    private static final String BOOKMARK_INFO_PATH = "app/src/main/java-gen/com/hippo/ehviewer/dao/BookmarkInfo.java";
    private static final String FILTER_PATH = "app/src/main/java-gen/com/hippo/ehviewer/dao/Filter.java";
    private static final String BLACKLIST_PATH = "app/src/main/java-gen/com/hippo/ehviewer/dao/BlackList.java";
    private static final String GALLERY_TAG_PATH = "app/src/main/java-gen/com/hippo/ehviewer/dao/GalleryTags.java";


    public static void generate() throws Exception {
        Utilities.deleteContents(new File(DELETE_DIR));
        File absFile = new File("");
        String absPath = absFile.getAbsolutePath()+"/"+OUT_DIR;
        File outDir = new File(absPath);
        if(!outDir.delete()){
            outDir.deleteOnExit();
        }
        if (!outDir.mkdirs()){
            throw new Exception("创建文件失败");
        }

        Schema schema = new Schema(VERSION, PACKAGE);
        addGalleryTags(schema);
        addDownloads(schema);
        addDownloadLabel(schema);
        addDownloadDirname(schema);
        addHistoryInfo(schema);
        addQuickSearch(schema);
        addBlackList(schema);
        addLocalFavorites(schema);
        addBookmarks(schema);
        addFilter(schema);
        new DaoGenerator().generateAll(schema, OUT_DIR);

        adjustGalleryTags();
        adjustDownloadInfo();
        adjustHistoryInfo();
        adjustQuickSearch();
        adjustBlackList();
        adjustLocalFavoriteInfo();
        adjustBookmarkInfo();
        adjustFilter();
    }


    private static void addGalleryTags(Schema schema) {
        Entity entity = schema.addEntity("GalleryTags");
        entity.setTableName("Gallery_Tags");
        entity.setClassNameDao("GalleryTagsDao");
        entity.addLongProperty("gid").primaryKey().notNull();
        entity.addStringProperty("rows");
        entity.addStringProperty("artist");
        entity.addStringProperty("cosplayer");
        entity.addStringProperty("character");
        entity.addStringProperty("female");
        entity.addStringProperty("group");
        entity.addStringProperty("language");
        entity.addStringProperty("male");
        entity.addStringProperty("misc");
        entity.addStringProperty("mixed");
        entity.addStringProperty("other");
        entity.addStringProperty("parody");
        entity.addStringProperty("reclass");
        entity.addDateProperty("create_time");
        entity.addDateProperty("update_time");
    }


    private static void adjustGalleryTags() throws Exception {
        JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, new File(GALLERY_TAG_PATH));

        // Set all field public
        javaClass.getField("gid").setPublic();
        javaClass.getField("rows").setPublic();
        javaClass.getField("artist").setPublic();
        javaClass.getField("cosplayer").setPublic();
        javaClass.getField("character").setPublic();
        javaClass.getField("female").setPublic();
        javaClass.getField("group").setPublic();
        javaClass.getField("language").setPublic();
        javaClass.getField("male").setPublic();
        javaClass.getField("misc").setPublic();
        javaClass.getField("mixed").setPublic();
        javaClass.getField("other").setPublic();
        javaClass.getField("parody").setPublic();
        javaClass.getField("reclass").setPublic();
        javaClass.getField("create_time").setPublic();
        javaClass.getField("update_time").setPublic();

        javaClass.addMethod("\t@Override\n" +
                "\tpublic String toString() {\n" +
                "\t\tJSONObject jsonObject = (JSONObject) JSONObject.toJSON(this);\n" +
                "\t\treturn jsonObject.toJSONString();\n" +
                "\t}");

        javaClass.addImport("com.alibaba.fastjson.JSONObject");

        FileWriter fileWriter = new FileWriter(GALLERY_TAG_PATH);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
    }

    private static void addBlackList(Schema schema) {
        Entity entity = schema.addEntity("BlackList");
        entity.setTableName("Black_List");
        entity.setClassNameDao("BlackListDao");
        entity.addIdProperty().primaryKey().autoincrement();
        entity.addStringProperty("badgayname").index();
        entity.addStringProperty("reason");
        entity.addStringProperty("angrywith");
        entity.addStringProperty("add_time");
        entity.addIntProperty("mode");
    }


    private static void adjustBlackList() throws Exception {
        JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, new File(BLACKLIST_PATH));

        // Set all field public
        javaClass.getField("id").setPublic();
        javaClass.getField("badgayname").setPublic();
        javaClass.getField("reason").setPublic();
        javaClass.getField("angrywith").setPublic();
        javaClass.getField("add_time").setPublic();
        javaClass.getField("mode").setPublic();
        javaClass.addMethod("\t@Override\n" +
                "\tpublic String toString() {\n" +
                "\t\treturn badgayname;\n" +
                "\t}");

        FileWriter fileWriter = new FileWriter(BLACKLIST_PATH);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
    }


    private static void addDownloads(Schema schema) {
        Entity entity = schema.addEntity("DownloadInfo");
        entity.setTableName("DOWNLOADS");
        entity.setClassNameDao("DownloadsDao");
        entity.setSuperclass("GalleryInfo");
        // GalleryInfo data
        entity.addLongProperty("gid").primaryKey().notNull();
        entity.addStringProperty("token");
        entity.addStringProperty("title");
        entity.addStringProperty("titleJpn");
        entity.addStringProperty("thumb");
        entity.addIntProperty("category").notNull();
        entity.addStringProperty("posted");
        entity.addStringProperty("uploader");
        entity.addFloatProperty("rating").notNull();
        entity.addStringProperty("simpleLanguage");
        // DownloadInfo data
        entity.addIntProperty("state").notNull();
        entity.addIntProperty("legacy").notNull();
        entity.addLongProperty("time").notNull();
        entity.addStringProperty("label");
    }

    private static void addDownloadLabel(Schema schema) {
        Entity entity = schema.addEntity("DownloadLabel");
        entity.setTableName("DOWNLOAD_LABELS");
        entity.setClassNameDao("DownloadLabelDao");
        entity.addIdProperty();
        entity.addStringProperty("label");
        entity.addLongProperty("time").notNull();
    }

    private static void addDownloadDirname(Schema schema) {
        Entity entity = schema.addEntity("DownloadDirname");
        entity.setTableName("DOWNLOAD_DIRNAME");
        entity.setClassNameDao("DownloadDirnameDao");
        entity.addLongProperty("gid").primaryKey().notNull();
        entity.addStringProperty("dirname");
    }

    private static void addHistoryInfo(Schema schema) {
        Entity entity = schema.addEntity("HistoryInfo");
        entity.setTableName("HISTORY");
        entity.setClassNameDao("HistoryDao");
        entity.setSuperclass("GalleryInfo");
        // GalleryInfo data
        entity.addLongProperty("gid").primaryKey().notNull();
        entity.addStringProperty("token");
        entity.addStringProperty("title");
        entity.addStringProperty("titleJpn");
        entity.addStringProperty("thumb");
        entity.addIntProperty("category").notNull();
        entity.addStringProperty("posted");
        entity.addStringProperty("uploader");
        entity.addFloatProperty("rating").notNull();
        entity.addStringProperty("simpleLanguage");
        // HistoryInfo data
        entity.addIntProperty("mode").notNull();
        entity.addLongProperty("time").notNull();
    }

    private static void addQuickSearch(Schema schema) {
        Entity entity = schema.addEntity("QuickSearch");
        entity.setTableName("QUICK_SEARCH");
        entity.setClassNameDao("QuickSearchDao");
        entity.addIdProperty();
        entity.addStringProperty("name");
        entity.addIntProperty("mode").notNull();
        entity.addIntProperty("category").notNull();
        entity.addStringProperty("keyword");
        entity.addIntProperty("advanceSearch").notNull();
        entity.addIntProperty("minRating").notNull();
        // Since 4
        entity.addIntProperty("pageFrom").notNull();
        // Since 4
        entity.addIntProperty("pageTo").notNull();
        entity.addLongProperty("time").notNull();
    }

    private static void addLocalFavorites(Schema schema) {
        Entity entity = schema.addEntity("LocalFavoriteInfo");
        entity.setTableName("LOCAL_FAVORITES");
        entity.setClassNameDao("LocalFavoritesDao");
        entity.setSuperclass("GalleryInfo");
        // GalleryInfo data
        entity.addLongProperty("gid").primaryKey().notNull();
        entity.addStringProperty("token");
        entity.addStringProperty("title");
        entity.addStringProperty("titleJpn");
        entity.addStringProperty("thumb");
        entity.addIntProperty("category").notNull();
        entity.addStringProperty("posted");
        entity.addStringProperty("uploader");
        entity.addFloatProperty("rating").notNull();
        entity.addStringProperty("simpleLanguage");
        // LocalFavoriteInfo data
        entity.addLongProperty("time").notNull();
    }

    private static void addBookmarks(Schema schema) {
        Entity entity = schema.addEntity("BookmarkInfo");
        entity.setTableName("BOOKMARKS");
        entity.setClassNameDao("BookmarksBao");
        entity.setSuperclass("GalleryInfo");
        // GalleryInfo data
        entity.addLongProperty("gid").primaryKey().notNull();
        entity.addStringProperty("token");
        entity.addStringProperty("title");
        entity.addStringProperty("titleJpn");
        entity.addStringProperty("thumb");
        entity.addIntProperty("category").notNull();
        entity.addStringProperty("posted");
        entity.addStringProperty("uploader");
        entity.addFloatProperty("rating").notNull();
        entity.addStringProperty("simpleLanguage");
        // Bookmark data
        entity.addIntProperty("page").notNull();
        entity.addLongProperty("time").notNull();
    }

    // Since 2
    private static void addFilter(Schema schema) {
        Entity entity = schema.addEntity("Filter");
        entity.setTableName("FILTER");
        entity.setClassNameDao("FilterDao");
        entity.addIdProperty();
        entity.addIntProperty("mode").notNull();
        entity.addStringProperty("text");
        // Since 3
        entity.addBooleanProperty("enable");
    }

    private static void adjustDownloadInfo() throws Exception {
        JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, new File(DOWNLOAD_INFO_PATH));
        // Remove field from GalleryInfo
        javaClass.removeField(javaClass.getField("gid"));
        javaClass.removeField(javaClass.getField("token"));
        javaClass.removeField(javaClass.getField("title"));
        javaClass.removeField(javaClass.getField("titleJpn"));
        javaClass.removeField(javaClass.getField("thumb"));
        javaClass.removeField(javaClass.getField("category"));
        javaClass.removeField(javaClass.getField("posted"));
        javaClass.removeField(javaClass.getField("uploader"));
        javaClass.removeField(javaClass.getField("rating"));
        javaClass.removeField(javaClass.getField("simpleLanguage"));
        // Set all field public
        javaClass.getField("state").setPublic();
        javaClass.getField("legacy").setPublic();
        javaClass.getField("time").setPublic();
        javaClass.getField("label").setPublic();
        // Add Parcelable stuff
        javaClass.addMethod("\t@Override\n" +
                "\tpublic int describeContents() {\n" +
                "\t\treturn 0;\n" +
                "\t}");
        javaClass.addMethod("\t@Override\n" +
                "\tpublic void writeToParcel(Parcel dest, int flags) {\n" +
                "\t\tsuper.writeToParcel(dest, flags);\n" +
                "\t\tdest.writeInt(this.state);\n" +
                "\t\tdest.writeInt(this.legacy);\n" +
                "\t\tdest.writeLong(this.time);\n" +
                "\t\tdest.writeString(this.label);\n" +
                "\t}");
        javaClass.addMethod("\tprotected DownloadInfo(Parcel in) {\n" +
                "\t\tsuper(in);\n" +
                "\t\tthis.state = in.readInt();\n" +
                "\t\tthis.legacy = in.readInt();\n" +
                "\t\tthis.time = in.readLong();\n" +
                "\t\tthis.label = in.readString();\n" +
                "\t}").setConstructor(true);
        javaClass.addField("\tpublic static final Creator<DownloadInfo> CREATOR = new Creator<DownloadInfo>() {\n" +
                "\t\t@Override\n" +
                "\t\tpublic DownloadInfo createFromParcel(Parcel source) {\n" +
                "\t\t\treturn new DownloadInfo(source);\n" +
                "\t\t}\n" +
                "\n" +
                "\t\t@Override\n" +
                "\t\tpublic DownloadInfo[] newArray(int size) {\n" +
                "\t\t\treturn new DownloadInfo[size];\n" +
                "\t\t}\n" +
                "\t};");
        javaClass.addImport("android.os.Parcel");
        // Add download info stuff
        javaClass.addField("public static final int STATE_INVALID = -1");
        javaClass.addField("public static final int STATE_NONE = 0");
        javaClass.addField("public static final int STATE_WAIT = 1");
        javaClass.addField("public static final int STATE_DOWNLOAD = 2");
        javaClass.addField("public static final int STATE_FINISH = 3");
        javaClass.addField("public static final int STATE_FAILED = 4");
        javaClass.addField("public static final int STATE_UPDATE = 5");
        javaClass.addField("public static final int GOTO_NEW = 6");
        javaClass.addField("public long speed");
        javaClass.addField("public long remaining");
        javaClass.addField("public int finished");
        javaClass.addField("public int downloaded");
        javaClass.addField("public int total");
        // Add from GalleryInfo constructor
        javaClass.addMethod("\tpublic DownloadInfo(GalleryInfo galleryInfo) {\n" +
                "\t\tthis.gid = galleryInfo.gid;\n" +
                "\t\tthis.token = galleryInfo.token;\n" +
                "\t\tthis.title = galleryInfo.title;\n" +
                "\t\tthis.titleJpn = galleryInfo.titleJpn;\n" +
                "\t\tthis.thumb = galleryInfo.thumb;\n" +
                "\t\tthis.category = galleryInfo.category;\n" +
                "\t\tthis.posted = galleryInfo.posted;\n" +
                "\t\tthis.uploader = galleryInfo.uploader;\n" +
                "\t\tthis.rating = galleryInfo.rating;\n" +
                "\t\tthis.simpleTags = galleryInfo.simpleTags;\n" +
                "\t\tthis.simpleLanguage = galleryInfo.simpleLanguage;\n" +
                "\t}").setConstructor(true);
        javaClass.addMethod("public void updateInfo(GalleryInfo galleryInfo) {\n" +
                "\t\tthis.token = galleryInfo.token;\n" +
                "\t\tthis.title = galleryInfo.title;\n" +
                "\t\tthis.titleJpn = galleryInfo.titleJpn;\n" +
                "\t\tthis.thumb = galleryInfo.thumb;\n" +
                "\t\tthis.category = galleryInfo.category;\n" +
                "\t\tthis.posted = galleryInfo.posted;\n" +
                "\t\tthis.uploader = galleryInfo.uploader;\n" +
                "\t\tthis.rating = galleryInfo.rating;\n" +
                "\t\tthis.simpleTags = galleryInfo.simpleTags;\n" +
                "\t\tthis.simpleLanguage = galleryInfo.simpleLanguage;\n" +
                "\t}");
        javaClass.addImport("com.hippo.ehviewer.client.data.GalleryInfo");

        javaClass.addMethod("\tpublic JSONObject toJson(){\n" +
                "\t\tJSONObject jsonObject = super.toJson();\n" +
                "\t\tjsonObject.put(\"finished\",finished);\n" +
                "\t\tjsonObject.put(\"legacy\",legacy);\n" +
                "\t\tjsonObject.put(\"label\",label);\n" +
                "\t\tjsonObject.put(\"downloaded\",downloaded);\n" +
                "\t\tjsonObject.put(\"remaining\",remaining);\n" +
                "\t\tjsonObject.put(\"speed\",speed);\n" +
                "\t\tjsonObject.put(\"state\",state);\n" +
                "\t\tjsonObject.put(\"time\",time);\n" +
                "\t\tjsonObject.put(\"total\",total);\n" +
                "\t\treturn  jsonObject;\n" +
                "\t}");
        javaClass.addImport("com.alibaba.fastjson.JSONObject");

        javaClass.addMethod("\tpublic static DownloadInfo downloadInfoFromJson(JSONObject object){\n" +
                "\t\tDownloadInfo downloadInfo = (DownloadInfo) GalleryInfo.galleryInfoFromJson(object);\n" +
                "\t\tdownloadInfo.finished = object.getIntValue(\"finished\");\n" +
                "\t\tdownloadInfo.legacy = object.getIntValue(\"legacy\");\n" +
                "\t\tdownloadInfo.label = object.getString(\"label\");\n" +
                "\t\tdownloadInfo.downloaded = object.getIntValue(\"downloaded\");\n" +
                "\t\tdownloadInfo.remaining = object.getLongValue(\"remaining\");\n" +
                "\t\tdownloadInfo.speed = object.getLongValue(\"speed\");\n" +
                "\t\tdownloadInfo.state = object.getIntValue(\"state\");\n" +
                "\t\tdownloadInfo.time = object.getLongValue(\"time\");\n" +
                "\t\tdownloadInfo.total = object.getIntValue(\"total\");\n" +
                "\t\treturn downloadInfo;\n" +
                "\t}");
        javaClass.addImport("com.alibaba.fastjson.JSONArray");
        javaClass.addImport("java.util.ArrayList");

        FileWriter fileWriter = new FileWriter(DOWNLOAD_INFO_PATH);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
    }

    private static void adjustHistoryInfo() throws Exception {
        JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, new File(HISTORY_INFO_PATH));
        // Remove field from GalleryInfo
        javaClass.removeField(javaClass.getField("gid"));
        javaClass.removeField(javaClass.getField("token"));
        javaClass.removeField(javaClass.getField("title"));
        javaClass.removeField(javaClass.getField("titleJpn"));
        javaClass.removeField(javaClass.getField("thumb"));
        javaClass.removeField(javaClass.getField("category"));
        javaClass.removeField(javaClass.getField("posted"));
        javaClass.removeField(javaClass.getField("uploader"));
        javaClass.removeField(javaClass.getField("rating"));
        javaClass.removeField(javaClass.getField("simpleLanguage"));
        // Set all field public
        javaClass.getField("mode").setPublic();
        javaClass.getField("time").setPublic();
        // Add Parcelable stuff
        javaClass.addMethod("\t@Override\n" +
                "\tpublic int describeContents() {\n" +
                "\t\treturn 0;\n" +
                "\t}");
        javaClass.addMethod("\t@Override\n" +
                "\tpublic void writeToParcel(Parcel dest, int flags) {\n" +
                "\t\tsuper.writeToParcel(dest, flags);\n" +
                "\t\tdest.writeInt(this.mode);\n" +
                "\t\tdest.writeLong(this.time);\n" +
                "\t}");
        javaClass.addMethod("\tprotected HistoryInfo(Parcel in) {\n" +
                "\t\tsuper(in);\n" +
                "\t\tthis.mode = in.readInt();\n" +
                "\t\tthis.time = in.readLong();\n" +
                "\t}").setConstructor(true);
        javaClass.addField("\tpublic static final Creator<HistoryInfo> CREATOR = new Creator<HistoryInfo>() {\n" +
                "\t\t@Override\n" +
                "\t\tpublic HistoryInfo createFromParcel(Parcel source) {\n" +
                "\t\t\treturn new HistoryInfo(source);\n" +
                "\t\t}\n" +
                "\n" +
                "\t\t@Override\n" +
                "\t\tpublic HistoryInfo[] newArray(int size) {\n" +
                "\t\t\treturn new HistoryInfo[size];\n" +
                "\t\t}\n" +
                "\t};");
        javaClass.addImport("android.os.Parcel");
        // Add from GalleryInfo constructor
        javaClass.addMethod("\tpublic HistoryInfo(GalleryInfo galleryInfo) {\n" +
                "\t\tthis.gid = galleryInfo.gid;\n" +
                "\t\tthis.token = galleryInfo.token;\n" +
                "\t\tthis.title = galleryInfo.title;\n" +
                "\t\tthis.titleJpn = galleryInfo.titleJpn;\n" +
                "\t\tthis.thumb = galleryInfo.thumb;\n" +
                "\t\tthis.category = galleryInfo.category;\n" +
                "\t\tthis.posted = galleryInfo.posted;\n" +
                "\t\tthis.uploader = galleryInfo.uploader;\n" +
                "\t\tthis.rating = galleryInfo.rating;\n" +
                "\t\tthis.simpleTags = galleryInfo.simpleTags;\n" +
                "\t\tthis.simpleLanguage = galleryInfo.simpleLanguage;\n" +
                "\t}").setConstructor(true);
        javaClass.addImport("com.hippo.ehviewer.client.data.GalleryInfo");

        FileWriter fileWriter = new FileWriter(HISTORY_INFO_PATH);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
    }

    private static void adjustQuickSearch() throws Exception {
        JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, new File(QUICK_SEARCH_PATH));

        // Set all field public
        javaClass.getField("id").setPublic();
        javaClass.getField("name").setPublic();
        javaClass.getField("mode").setPublic();
        javaClass.getField("category").setPublic();
        javaClass.getField("keyword").setPublic();
        javaClass.getField("advanceSearch").setPublic();
        javaClass.getField("minRating").setPublic();
        javaClass.getField("pageFrom").setPublic();
        javaClass.getField("pageTo").setPublic();
        javaClass.getField("time").setPublic();

        javaClass.addMethod("\t@Override\n" +
                "\tpublic String toString() {\n" +
                "\t\treturn name;\n" +
                "\t}");
        javaClass.addImport("com.alibaba.fastjson.JSONObject");

        javaClass.addMethod("public JSONObject toJson(){\n" +
                "\t\tJSONObject object = new JSONObject();\n" +
                "\t\tobject.put(\"name\",name);\n" +
                "\t\tobject.put(\"mode\",mode);\n" +
                "\t\tobject.put(\"category\",category);\n" +
                "\t\tobject.put(\"keyword\",keyword);\n" +
                "\t\tobject.put(\"advanceSearch\",advanceSearch);\n" +
                "\t\tobject.put(\"minRating\",minRating);\n" +
                "\t\tobject.put(\"pageFrom\",pageFrom);\n" +
                "\t\tobject.put(\"pageTo\",pageTo);\n" +
                "\t\tobject.put(\"time\",time);\n" +
                "\t\treturn object;\n" +
                "\t}");
        javaClass.addMethod("public static QuickSearch quickSearchFromJson(JSONObject object){\n" +
                "\t\tQuickSearch search = new QuickSearch();\n" +
                "\t\tsearch.name = object.getString(\"name\");\n" +
                "\t\tsearch.mode = object.getIntValue(\"mode\");\n" +
                "\t\tsearch.category = object.getIntValue(\"category\");\n" +
                "\t\tsearch.keyword = object.getString(\"keyword\");\n" +
                "\t\tsearch.advanceSearch = object.getIntValue(\"advanceSearch\");\n" +
                "\t\tsearch.minRating = object.getIntValue(\"minRating\");\n" +
                "\t\tsearch.pageFrom = object.getIntValue(\"pageFrom\");\n" +
                "\t\tsearch.pageTo = object.getIntValue(\"pageTo\");\n" +
                "\t\tsearch.time = object.getLongValue(\"time\");\n" +
                "\t\treturn search;\n" +
                "\t}");

        FileWriter fileWriter = new FileWriter(QUICK_SEARCH_PATH);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
    }

    private static void adjustLocalFavoriteInfo() throws Exception {
        JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, new File(LOCAL_FAVORITE_INFO_PATH));
        // Remove field from GalleryInfo
        javaClass.removeField(javaClass.getField("gid"));
        javaClass.removeField(javaClass.getField("token"));
        javaClass.removeField(javaClass.getField("title"));
        javaClass.removeField(javaClass.getField("titleJpn"));
        javaClass.removeField(javaClass.getField("thumb"));
        javaClass.removeField(javaClass.getField("category"));
        javaClass.removeField(javaClass.getField("posted"));
        javaClass.removeField(javaClass.getField("uploader"));
        javaClass.removeField(javaClass.getField("rating"));
        javaClass.removeField(javaClass.getField("simpleLanguage"));
        // Set all field public
        javaClass.getField("time").setPublic();
        // Add Parcelable stuff
        javaClass.addMethod("\t@Override\n" +
                "\tpublic int describeContents() {\n" +
                "\t\treturn 0;\n" +
                "\t}");
        javaClass.addMethod("\t@Override\n" +
                "\tpublic void writeToParcel(Parcel dest, int flags) {\n" +
                "\t\tsuper.writeToParcel(dest, flags);\n" +
                "\t\tdest.writeLong(this.time);\n" +
                "\t}");
        javaClass.addMethod("\tprotected LocalFavoriteInfo(Parcel in) {\n" +
                "\t\tsuper(in);\n" +
                "\t\tthis.time = in.readLong();\n" +
                "\t}").setConstructor(true);
        javaClass.addField("\tpublic static final Creator<LocalFavoriteInfo> CREATOR = new Creator<LocalFavoriteInfo>() {\n" +
                "\t\t@Override\n" +
                "\t\tpublic LocalFavoriteInfo createFromParcel(Parcel source) {\n" +
                "\t\t\treturn new LocalFavoriteInfo(source);\n" +
                "\t\t}\n" +
                "\n" +
                "\t\t@Override\n" +
                "\t\tpublic LocalFavoriteInfo[] newArray(int size) {\n" +
                "\t\t\treturn new LocalFavoriteInfo[size];\n" +
                "\t\t}\n" +
                "\t};");
        javaClass.addImport("android.os.Parcel");
        // Add from GalleryInfo constructor
        javaClass.addMethod("\tpublic LocalFavoriteInfo(GalleryInfo galleryInfo) {\n" +
                "\t\tthis.gid = galleryInfo.gid;\n" +
                "\t\tthis.token = galleryInfo.token;\n" +
                "\t\tthis.title = galleryInfo.title;\n" +
                "\t\tthis.titleJpn = galleryInfo.titleJpn;\n" +
                "\t\tthis.thumb = galleryInfo.thumb;\n" +
                "\t\tthis.category = galleryInfo.category;\n" +
                "\t\tthis.posted = galleryInfo.posted;\n" +
                "\t\tthis.uploader = galleryInfo.uploader;\n" +
                "\t\tthis.rating = galleryInfo.rating;\n" +
                "\t\tthis.simpleTags = galleryInfo.simpleTags;\n" +
                "\t\tthis.simpleLanguage = galleryInfo.simpleLanguage;\n" +
                "\t}").setConstructor(true);
        javaClass.addImport("com.hippo.ehviewer.client.data.GalleryInfo");

        FileWriter fileWriter = new FileWriter(LOCAL_FAVORITE_INFO_PATH);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
    }

    private static void adjustBookmarkInfo() throws Exception {
        JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, new File(BOOKMARK_INFO_PATH));
        // Remove field from GalleryInfo
        javaClass.removeField(javaClass.getField("gid"));
        javaClass.removeField(javaClass.getField("token"));
        javaClass.removeField(javaClass.getField("title"));
        javaClass.removeField(javaClass.getField("titleJpn"));
        javaClass.removeField(javaClass.getField("thumb"));
        javaClass.removeField(javaClass.getField("category"));
        javaClass.removeField(javaClass.getField("posted"));
        javaClass.removeField(javaClass.getField("uploader"));
        javaClass.removeField(javaClass.getField("rating"));
        javaClass.removeField(javaClass.getField("simpleLanguage"));
        // Set all field public
        javaClass.getField("page").setPublic();
        javaClass.getField("time").setPublic();
        // Add Parcelable stuff
        javaClass.addMethod("\t@Override\n" +
                "\tpublic int describeContents() {\n" +
                "\t\treturn 0;\n" +
                "\t}");
        javaClass.addMethod("\t@Override\n" +
                "\tpublic void writeToParcel(Parcel dest, int flags) {\n" +
                "\t\tsuper.writeToParcel(dest, flags);\n" +
                "\t\tdest.writeInt(this.page);\n" +
                "\t\tdest.writeLong(this.time);\n" +
                "\t}");
        javaClass.addMethod("\tprotected BookmarkInfo(Parcel in) {\n" +
                "\t\tsuper(in);\n" +
                "\t\tthis.page = in.readInt();\n" +
                "\t\tthis.time = in.readLong();\n" +
                "\t}").setConstructor(true);
        javaClass.addField("\tpublic static final Creator<BookmarkInfo> CREATOR = new Creator<BookmarkInfo>() {\n" +
                "\t\t@Override\n" +
                "\t\tpublic BookmarkInfo createFromParcel(Parcel source) {\n" +
                "\t\t\treturn new BookmarkInfo(source);\n" +
                "\t\t}\n" +
                "\n" +
                "\t\t@Override\n" +
                "\t\tpublic BookmarkInfo[] newArray(int size) {\n" +
                "\t\t\treturn new BookmarkInfo[size];\n" +
                "\t\t}\n" +
                "\t};");
        javaClass.addImport("android.os.Parcel");
        // Add from GalleryInfo constructor
        javaClass.addMethod("\tpublic BookmarkInfo(GalleryInfo galleryInfo) {\n" +
                "\t\tthis.gid = galleryInfo.gid;\n" +
                "\t\tthis.token = galleryInfo.token;\n" +
                "\t\tthis.title = galleryInfo.title;\n" +
                "\t\tthis.titleJpn = galleryInfo.titleJpn;\n" +
                "\t\tthis.thumb = galleryInfo.thumb;\n" +
                "\t\tthis.category = galleryInfo.category;\n" +
                "\t\tthis.posted = galleryInfo.posted;\n" +
                "\t\tthis.uploader = galleryInfo.uploader;\n" +
                "\t\tthis.rating = galleryInfo.rating;\n" +
                "\t\tthis.simpleTags = galleryInfo.simpleTags;\n" +
                "\t\tthis.simpleLanguage = galleryInfo.simpleLanguage;\n" +
                "\t}").setConstructor(true);
        javaClass.addImport("com.hippo.ehviewer.client.data.GalleryInfo");

        FileWriter fileWriter = new FileWriter(BOOKMARK_INFO_PATH);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
    }

    // Since 2
    private static void adjustFilter() throws Exception {
        JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, new File(FILTER_PATH));
        // Set field public
        javaClass.getField("mode").setPublic();
        javaClass.getField("text").setPublic();
        javaClass.getField("enable").setPublic();
        // Add hashCode method and equals method
        javaClass.addImport("com.hippo.util.HashCodeUtils");
        javaClass.addMethod("\t@Override\n" +
                "\tpublic int hashCode() {\n" +
                "\t\treturn HashCodeUtils.hashCode(mode, text);\n" +
                "\t}");
        javaClass.addImport("com.hippo.yorozuya.ObjectUtils");
        javaClass.addMethod("\t@Override\n" +
                "\tpublic boolean equals(Object o) {\n" +
                "\t\tif (!(o instanceof Filter)) {\n" +
                "\t\t\treturn false;\n" +
                "\t\t}\n" +
                "\n" +
                "\t\tFilter filter = (Filter) o;\n" +
                "\t\treturn filter.mode == mode && ObjectUtils.equal(filter.text, text);\n" +
                "\t}");

        FileWriter fileWriter = new FileWriter(FILTER_PATH);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
    }
}
