package com.hippo.util;

import com.hippo.ehviewer.client.EhTagDatabase;

public class TagTranslation {


    public static String translationIt(String tag,EhTagDatabase ehTags){
        //根据‘：’分割字符串为组名和标签名
        String[] tags = tag.split(":");
        //翻译组名
        String group = ehTags.getTranslation("n:" + tags[0]);
        //翻译标签名
        String prefix = EhTagDatabase.namespaceToPrefix(tags[0]);
        String tagstr = ehTags.getTranslation( prefix != null ? prefix+tags[1] : ""+tags[1] );
        //重设标签名称
        return group+"："+tagstr;
    }






}
