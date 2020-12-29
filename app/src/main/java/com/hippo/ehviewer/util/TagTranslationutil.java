package com.hippo.ehviewer.util;

import android.content.res.AssetManager;

import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.client.EhTagDatabase;
import com.hippo.ehviewer.dao.TagTranslation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
