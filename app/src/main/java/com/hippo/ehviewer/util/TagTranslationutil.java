package com.hippo.ehviewer.util;

import android.content.res.AssetManager;


import com.hippo.ehviewer.client.EhTagDatabase;


public class TagTranslationutil {

    public static String getTagCN(String[] tags , EhTagDatabase ehTags ){
        if (ehTags != null && tags.length == 2) {
            String group = ehTags.getTranslation("n:" + tags[0]);
            //翻译标签名
            String prefix = EhTagDatabase.namespaceToPrefix(tags[0]);
            String tagstr = ehTags.getTranslation(prefix != null ? prefix + tags[1] : "" + tags[1]);


            if (group != null && tagstr != null){
                return group + "：" + tagstr;
            }else if (group != null && tagstr == null) {
                return group + "：" + tags[1];
            }else if (group == null && tagstr != null) {
                return tags[0] + "：" + tagstr;
            }else {
                return tags[0]+":"+tags[1];
            }
        }else {
            String s="";
            for (int i = 0; i < tags.length; i++) {
                if (i==0){
                    s=s+tags[i];
                }else {
                    s=s+":"+tags[i];
                }
            }

            return s;
        }
    }



}
