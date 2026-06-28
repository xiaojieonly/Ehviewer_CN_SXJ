package com.hippo.ehviewer.client.data.userTag;

import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhTagDatabase;
import com.hippo.ehviewer.util.TagTranslationUtil;

public class UserTag {

    public String userTagId;
    public String tagName;
    public boolean watched;
    public boolean hidden;
    public String color;
    public int tagWeight;

    public UserTag(){

    }

    public String getName(EhTagDatabase ehTags) {
        //汉化标签
        final boolean judge = Settings.getShowTagTranslations();
        if (judge) {
            String name = tagName;
            //重设标签名称,并跳过已翻译的标签
            if (name != null && 2 == name.split(":").length) {
                return  TagTranslationUtil.getTagCN(name.split(":"), ehTags);
            }
        }
        return tagName;
    }

    public long getId(){
        return Long.parseLong(userTagId.substring(8));
    }

    public String deleteParam(){
        return "usertag_action=mass" +
                "&tagname_new=" +
                "&tagcolor_new=" +
                "&tagweight_new="+tagWeight
                +"&modify_usertags%5B%5D=" + getId()+
                "&usertag_target=0";
    }

}
