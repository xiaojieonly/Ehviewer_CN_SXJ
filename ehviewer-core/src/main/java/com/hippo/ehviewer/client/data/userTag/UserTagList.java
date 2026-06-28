package com.hippo.ehviewer.client.data.userTag;

import java.util.ArrayList;
import java.util.List;

public class UserTagList {

    public List<UserTag> userTags;
    public int stageId;
    public UserTagList() {
        userTags = new ArrayList<>();
    }

    public UserTag get(int index){
        return userTags.get(index);
    }

    public int size(){
        return userTags.size();
    }
}
