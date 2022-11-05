package com.hippo.ehviewer.sync;

import android.util.Log;

import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.client.data.GalleryDetail;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.data.GalleryTagGroup;
import com.hippo.ehviewer.dao.GalleryTags;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GalleryListTagsSyncTask {
    ExecutorService service = Executors.newSingleThreadExecutor();

    private final String TAG = "GalleryListTagsSyncTask";
    final List<GalleryInfo> galleryInfoList;

    public GalleryListTagsSyncTask(List<GalleryInfo> galleryInfoList) {
        this.galleryInfoList = galleryInfoList;
    }

    public void execute() {
        service.execute(this::executeFunction);

    }

    private void executeFunction() {
//        for (GalleryInfo info : galleryInfoList) {
//            GalleryTags galleryTags = getTags(info);
//            if (galleryTags != null) {
//                try {
//                    if (!EhDB.inGalleryTags(info.gid)) {
//                        EhDB.insertGalleryTags(galleryTags);
//                    }
//                } catch (Exception e) {
//                    Log.e(TAG, e.toString());
//                }
//            }
//
//        }
    }

    private GalleryTags getTags(GalleryInfo info) {

        GalleryTags tags = new GalleryTags(info.gid);

        ArrayList<String> groups = info.tgList;
        if (groups == null || groups.isEmpty()) {
            return null;
        }
        for (String tag : groups) {
            parserData(tags, tag);
        }

        return tags;
    }

    private void parserData(GalleryTags tags, String tag) {
        String[] tagArr = tag.split(":");
        if (tagArr.length < 2) {
            return;
        }
        switch (tagArr[0]) {
            case "rows":
                tags.rows = getTagString(tags.rows, tagArr[1]);
                break;
            case "artist":
                tags.artist = getTagString(tags.artist, tagArr[1]);
                break;
            case "cosplayer":
                tags.cosplayer = getTagString(tags.cosplayer, tagArr[1]);
                break;
            case "character":
                tags.character = getTagString(tags.character, tagArr[1]);
                break;
            case "female":
                tags.female = getTagString(tags.female, tagArr[1]);
                break;
            case "group":
                tags.group = getTagString(tags.group, tagArr[1]);
                break;
            case "language":
                tags.language = getTagString(tags.language, tagArr[1]);
                break;
            case "male":
                tags.male = getTagString(tags.male, tagArr[1]);
                break;
            case "misc":
                tags.misc = getTagString(tags.misc, tagArr[1]);
                break;
            case "mixed":
                tags.mixed = getTagString(tags.mixed, tagArr[1]);
                break;
            case "other":
                tags.other = getTagString(tags.other, tagArr[1]);
                break;
            case "parody":
                tags.parody = getTagString(tags.parody, tagArr[1]);
                break;
            case "reclass":
                tags.reclass = getTagString(tags.reclass, tagArr[1]);
                break;
            default:
        }
    }

    private String getTagString(String tagContent, String newTag) {
        if (tagContent == null || tagContent.isEmpty()) {
            return newTag;
        }

        return tagContent + "," + newTag;
    }

}
