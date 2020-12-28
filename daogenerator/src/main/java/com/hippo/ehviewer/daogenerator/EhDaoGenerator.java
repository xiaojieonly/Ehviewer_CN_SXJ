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
    private static final String OUT_DIR = "../app/src/main/java-gen";
    private static final String DELETE_DIR = "../app/src/main/java-gen/com/hippo/ehviewer/dao";

    private static final int VERSION = 5;

    private static final String DOWNLOAD_INFO_PATH = "../app/src/main/java-gen/com/hippo/ehviewer/dao/DownloadInfo.java";
    private static final String HISTORY_INFO_PATH = "../app/src/main/java-gen/com/hippo/ehviewer/dao/HistoryInfo.java";
    private static final String QUICK_SEARCH_PATH = "../app/src/main/java-gen/com/hippo/ehviewer/dao/QuickSearch.java";
    private static final String LOCAL_FAVORITE_INFO_PATH = "../app/src/main/java-gen/com/hippo/ehviewer/dao/LocalFavoriteInfo.java";
    private static final String BOOKMARK_INFO_PATH = "../app/src/main/java-gen/com/hippo/ehviewer/dao/BookmarkInfo.java";
    private static final String FILTER_PATH = "../app/src/main/java-gen/com/hippo/ehviewer/dao/Filter.java";
    private static final String BLACKLIST_PATH = "../app/src/main/java-gen/com/hippo/ehviewer/dao/BlackList.java";
    private static final String ARTIST_PATH = "../app/src/main/java-gen/com/hippo/ehviewer/dao/ArtistTag.java";
    private static final String CHARACTER_PATH = "../app/src/main/java-gen/com/hippo/ehviewer/dao/CharacterTag.java";
    private static final String FEMALE_PATH = "../app/src/main/java-gen/com/hippo/ehviewer/dao/FemaleTag.java";
    private static final String GROUP_PATH = "../app/src/main/java-gen/com/hippo/ehviewer/dao/GroupTag.java";
    private static final String LANGUAGE_PATH = "../app/src/main/java-gen/com/hippo/ehviewer/dao/LanguageTag.java";
    private static final String MALE_PATH = "../app/src/main/java-gen/com/hippo/ehviewer/dao/MaleTag.java";
    private static final String MISC_PATH = "../app/src/main/java-gen/com/hippo/ehviewer/dao/MiscTag.java";
    private static final String PARODY_PATH = "../app/src/main/java-gen/com/hippo/ehviewer/dao/ParodyTag.java";
    private static final String RECLASS_PATH = "../app/src/main/java-gen/com/hippo/ehviewer/dao/ReclassTag.java";
    private static final String ROWS_PATH = "../app/src/main/java-gen/com/hippo/ehviewer/dao/RowsTag.java";

    public static void generate() throws Exception {
        Utilities.deleteContents(new File(DELETE_DIR));
        File outDir = new File(OUT_DIR);
        outDir.delete();
        outDir.mkdirs();

        Schema schema = new Schema(VERSION, PACKAGE);
        addDownloads(schema);
        addDownloadLabel(schema);
        addDownloadDirname(schema);
        addHistoryInfo(schema);
        addQuickSearch(schema);
        addBlackList(schema);
        addLocalFavorites(schema);
        addBookmarks(schema);
        addFilter(schema);
        addArtistTag(schema);
        addCharacterTag(schema);
        addFemaleTag(schema);
        addGroupTag(schema);
        addLanguageTag(schema);
        addMaleTag(schema);
        addMiscTag(schema);
        addParodyTag(schema);
        addReclassTag(schema);
        addRowsTag(schema);
        new DaoGenerator().generateAll(schema, OUT_DIR);

        adjustDownloadInfo();
        adjustHistoryInfo();
        adjustQuickSearch();
        adjustBlackList();
        adjustLocalFavoriteInfo();
        adjustBookmarkInfo();
        adjustFilter();
        adjustArtistTag();
        adjustCharacterTag();
        adjustFemaleTag();
        adjustGroupTag();
        adjustLanguageTag();
        adjustMaleTag();
        adjustMiscTag();
        adjustParodyTag();
        adjustReclassTag();
        adjustRowsTag();
    }

    private static void addRowsTag(Schema schema) {
        Entity entity = schema.addEntity("RowsTag");
        entity.setTableName("ROWS");
        entity.setClassNameDao("RowsTagDao");
        entity.addIdProperty().primaryKey().autoincrement();
        entity.addStringProperty("originalLabel").index();
        entity.addStringProperty("name");
        entity.addStringProperty("description");
        entity.addStringProperty("externalLink");
    }


    private static void adjustRowsTag() throws Exception {
        JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, new File(ROWS_PATH));

        // Set all field public
        javaClass.getField("id").setPublic();
        javaClass.getField("originalLabel").setPublic();
        javaClass.getField("name").setPublic();
        javaClass.getField("description").setPublic();
        javaClass.getField("externalLink").setPublic();
        javaClass.addMethod("\t@Override\n" +
                "\tpublic String toString() {\n" +
                "\t\treturn name;\n" +
                "\t}");
        FileWriter fileWriter = new FileWriter(ROWS_PATH);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
    }

    private static void addReclassTag(Schema schema) {
        Entity entity = schema.addEntity("ReclassTag");
        entity.setTableName("RECLASS");
        entity.setClassNameDao("ReclassTagDao");
        entity.addIdProperty().primaryKey().autoincrement();
        entity.addStringProperty("originalLabel").index();
        entity.addStringProperty("name");
        entity.addStringProperty("description");
        entity.addStringProperty("externalLink");
    }


    private static void adjustReclassTag() throws Exception {
        JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, new File(RECLASS_PATH));

        // Set all field public
        javaClass.getField("id").setPublic();
        javaClass.getField("originalLabel").setPublic();
        javaClass.getField("name").setPublic();
        javaClass.getField("description").setPublic();
        javaClass.getField("externalLink").setPublic();
        javaClass.addMethod("\t@Override\n" +
                "\tpublic String toString() {\n" +
                "\t\treturn name;\n" +
                "\t}");
        FileWriter fileWriter = new FileWriter(RECLASS_PATH);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
    }

    private static void addParodyTag(Schema schema) {
        Entity entity = schema.addEntity("ParodyTag");
        entity.setTableName("PARODY");
        entity.setClassNameDao("ParodyTagDao");
        entity.addIdProperty().primaryKey().autoincrement();
        entity.addStringProperty("originalLabel").index();
        entity.addStringProperty("name");
        entity.addStringProperty("description");
        entity.addStringProperty("externalLink");
    }


    private static void adjustParodyTag() throws Exception {
        JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, new File(PARODY_PATH));

        // Set all field public
        javaClass.getField("id").setPublic();
        javaClass.getField("originalLabel").setPublic();
        javaClass.getField("name").setPublic();
        javaClass.getField("description").setPublic();
        javaClass.getField("externalLink").setPublic();
        javaClass.addMethod("\t@Override\n" +
                "\tpublic String toString() {\n" +
                "\t\treturn name;\n" +
                "\t}");
        FileWriter fileWriter = new FileWriter(PARODY_PATH);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
    }

    private static void addMiscTag(Schema schema) {
        Entity entity = schema.addEntity("MiscTag");
        entity.setTableName("MISC");
        entity.setClassNameDao("MiscTagDao");
        entity.addIdProperty().primaryKey().autoincrement();
        entity.addStringProperty("originalLabel").index();
        entity.addStringProperty("name");
        entity.addStringProperty("description");
        entity.addStringProperty("externalLink");
    }


    private static void adjustMiscTag() throws Exception {
        JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, new File(MISC_PATH));

        // Set all field public
        javaClass.getField("id").setPublic();
        javaClass.getField("originalLabel").setPublic();
        javaClass.getField("name").setPublic();
        javaClass.getField("description").setPublic();
        javaClass.getField("externalLink").setPublic();
        javaClass.addMethod("\t@Override\n" +
                "\tpublic String toString() {\n" +
                "\t\treturn name;\n" +
                "\t}");
        FileWriter fileWriter = new FileWriter(MISC_PATH);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
    }

    private static void addMaleTag(Schema schema) {
        Entity entity = schema.addEntity("MaleTag");
        entity.setTableName("MALE");
        entity.setClassNameDao("MaleTagDao");
        entity.addIdProperty().primaryKey().autoincrement();
        entity.addStringProperty("originalLabel").index();
        entity.addStringProperty("name");
        entity.addStringProperty("description");
        entity.addStringProperty("externalLink");
    }


    private static void adjustMaleTag() throws Exception {
        JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, new File(MALE_PATH));

        // Set all field public
        javaClass.getField("id").setPublic();
        javaClass.getField("originalLabel").setPublic();
        javaClass.getField("name").setPublic();
        javaClass.getField("description").setPublic();
        javaClass.getField("externalLink").setPublic();
        javaClass.addMethod("\t@Override\n" +
                "\tpublic String toString() {\n" +
                "\t\treturn name;\n" +
                "\t}");
        FileWriter fileWriter = new FileWriter(MALE_PATH);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
    }

    private static void addLanguageTag(Schema schema) {
        Entity entity = schema.addEntity("LanguageTag");
        entity.setTableName("LANGUAGE");
        entity.setClassNameDao("LanguageTagDao");
        entity.addIdProperty().primaryKey().autoincrement();
        entity.addStringProperty("originalLabel").index();
        entity.addStringProperty("name");
        entity.addStringProperty("description");
        entity.addStringProperty("externalLink");
    }


    private static void adjustLanguageTag() throws Exception {
        JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, new File(LANGUAGE_PATH));

        // Set all field public
        javaClass.getField("id").setPublic();
        javaClass.getField("originalLabel").setPublic();
        javaClass.getField("name").setPublic();
        javaClass.getField("description").setPublic();
        javaClass.getField("externalLink").setPublic();
        javaClass.addMethod("\t@Override\n" +
                "\tpublic String toString() {\n" +
                "\t\treturn name;\n" +
                "\t}");
        FileWriter fileWriter = new FileWriter(LANGUAGE_PATH);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
    }

    private static void addGroupTag(Schema schema) {
        Entity entity = schema.addEntity("GroupTag");
        entity.setTableName("TAGGROUP");
        entity.setClassNameDao("GroupTagDao");
        entity.addIdProperty().primaryKey().autoincrement();
        entity.addStringProperty("originalLabel").index();
        entity.addStringProperty("name");
        entity.addStringProperty("description");
        entity.addStringProperty("externalLink");
    }


    private static void adjustGroupTag() throws Exception {
        JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, new File(GROUP_PATH));

        // Set all field public
        javaClass.getField("id").setPublic();
        javaClass.getField("originalLabel").setPublic();
        javaClass.getField("name").setPublic();
        javaClass.getField("description").setPublic();
        javaClass.getField("externalLink").setPublic();
        javaClass.addMethod("\t@Override\n" +
                "\tpublic String toString() {\n" +
                "\t\treturn name;\n" +
                "\t}");
        FileWriter fileWriter = new FileWriter(GROUP_PATH);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
    }

    private static void addFemaleTag(Schema schema) {
        Entity entity = schema.addEntity("FemaleTag");
        entity.setTableName("FEMALE");
        entity.setClassNameDao("FemaleTagDao");
        entity.addIdProperty().primaryKey().autoincrement();
        entity.addStringProperty("originalLabel").index();
        entity.addStringProperty("name");
        entity.addStringProperty("description");
        entity.addStringProperty("externalLink");
    }


    private static void adjustFemaleTag() throws Exception {
        JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, new File(FEMALE_PATH));

        // Set all field public
        javaClass.getField("id").setPublic();
        javaClass.getField("originalLabel").setPublic();
        javaClass.getField("name").setPublic();
        javaClass.getField("description").setPublic();
        javaClass.getField("externalLink").setPublic();
        javaClass.addMethod("\t@Override\n" +
                "\tpublic String toString() {\n" +
                "\t\treturn name;\n" +
                "\t}");
        FileWriter fileWriter = new FileWriter(FEMALE_PATH);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
    }

    private static void addCharacterTag(Schema schema) {
        Entity entity = schema.addEntity("CharacterTag");
        entity.setTableName("CHARACTER");
        entity.setClassNameDao("CharacterTagDao");
        entity.addIdProperty().primaryKey().autoincrement();
        entity.addStringProperty("originalLabel").index();
        entity.addStringProperty("name");
        entity.addStringProperty("description");
        entity.addStringProperty("externalLink");
    }


    private static void adjustCharacterTag() throws Exception {
        JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, new File(CHARACTER_PATH));

        // Set all field public
        javaClass.getField("id").setPublic();
        javaClass.getField("originalLabel").setPublic();
        javaClass.getField("name").setPublic();
        javaClass.getField("description").setPublic();
        javaClass.getField("externalLink").setPublic();
        javaClass.addMethod("\t@Override\n" +
                "\tpublic String toString() {\n" +
                "\t\treturn name;\n" +
                "\t}");
        FileWriter fileWriter = new FileWriter(CHARACTER_PATH);
        fileWriter.write(javaClass.toString());
        fileWriter.close();
    }

    private static void addArtistTag(Schema schema) {
        Entity entity = schema.addEntity("ArtistTag");
        entity.setTableName("ARTIST");
        entity.setClassNameDao("ArtistTagDao");
        entity.addIdProperty().primaryKey().autoincrement();
        entity.addStringProperty("originalLabel").index();
        entity.addStringProperty("name");
        entity.addStringProperty("description");
        entity.addStringProperty("externalLink");
    }


    private static void adjustArtistTag() throws Exception {
        JavaClassSource javaClass = Roaster.parse(JavaClassSource.class, new File(ARTIST_PATH));

        // Set all field public
        javaClass.getField("id").setPublic();
        javaClass.getField("originalLabel").setPublic();
        javaClass.getField("name").setPublic();
        javaClass.getField("description").setPublic();
        javaClass.getField("externalLink").setPublic();
        javaClass.addMethod("\t@Override\n" +
                "\tpublic String toString() {\n" +
                "\t\treturn name;\n" +
                "\t}");
        FileWriter fileWriter = new FileWriter(ARTIST_PATH);
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
        javaClass.addImport("com.hippo.ehviewer.client.data.GalleryInfo");

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
