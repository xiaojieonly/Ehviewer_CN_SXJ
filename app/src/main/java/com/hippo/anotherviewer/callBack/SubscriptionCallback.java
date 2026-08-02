package com.hippo.anotherviewer.callBack;

import com.hippo.anotherviewer.client.data.userTag.UserTagList;

public interface SubscriptionCallback {
    void onSubscriptionItemClick(String name);

    String getAddTagName(UserTagList userTagList);
}
