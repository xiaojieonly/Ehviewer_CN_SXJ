package com.hippo.util;

import com.hippo.ehviewer.client.data.GalleryComment;
import com.hippo.ehviewer.dao.BlackList;

public class BlackListUtils {

    public static BlackList parseBlacklist(GalleryComment comment) {
        BlackList blackList = new BlackList();
        blackList.badgayname = comment.user;
        blackList.angrywith = comment.comment;
        blackList.mode = 1;
        blackList.add_time = TimeUtils.getTimeNow();
        return blackList;
    }
}
