package com.hippo.ehviewer.sync;

import android.util.Log;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.client.data.GalleryDetail;
import com.hippo.ehviewer.client.data.GalleryTagGroup;
import com.hippo.ehviewer.dao.GalleryTags;
import java.util.ArrayList;

public class GalleryDetailTagsSyncTask extends Thread {

    private final String TAG = "TagsSyncTask";

    final GalleryDetail detail;

    public GalleryDetailTagsSyncTask(GalleryDetail detail) {
        this.detail = detail;
    }

    @Override
    public synchronized void start() {
        super.start();
    }

    @Override
    public void run() {
        super.run();
        GalleryTags galleryTags = getTags();
        try {
            if (EhDB.inGalleryTags(detail.gid)) {
                EhDB.updateGalleryTags(galleryTags);
            } else {
                EhDB.insertGalleryTags(galleryTags);
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private GalleryTags getTags() {
        GalleryTags tags = new GalleryTags(detail.gid);
        GalleryTagGroup[] groups = detail.tags;
        for (GalleryTagGroup group : groups) {
            parserData(tags, group);
        }
        return tags;
    }

    private void parserData(GalleryTags tags, GalleryTagGroup group) {
        switch(group.groupName) {
            case "rows":
                tags.rows = getTagString(group.size(), group);
                break;
            case "artist":
                tags.artist = getTagString(group.size(), group);
                break;
            case "cosplayer":
                tags.cosplayer = getTagString(group.size(), group);
                break;
            case "character":
                tags.character = getTagString(group.size(), group);
                break;
            case "female":
                tags.female = getTagString(group.size(), group);
                break;
            case "group":
                tags.group = getTagString(group.size(), group);
                break;
            case "language":
                tags.language = getTagString(group.size(), group);
                break;
            case "male":
                tags.male = getTagString(group.size(), group);
                break;
            case "misc":
                tags.misc = getTagString(group.size(), group);
                break;
            case "mixed":
                tags.mixed = getTagString(group.size(), group);
                break;
            case "other":
                tags.other = getTagString(group.size(), group);
                break;
            case "parody":
                tags.parody = getTagString(group.size(), group);
                break;
            case "reclass":
                tags.reclass = getTagString(group.size(), group);
                break;
            default:
        }
    }

    private String getTagString(int size, GalleryTagGroup group) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (i == size - 1) {
                builder.append(group.getTagAt(i));
            } else {
                builder.append(group.getTagAt(i)).append(",");
            }
        }
        return builder.toString();
    }
}
