package com.hippo.ehviewer.util;

import android.content.res.AssetManager;


import com.hippo.ehviewer.client.EhTagDatabase;


public class TagTranslationUtil {

    public static String getTagCN(String[] tags, EhTagDatabase ehTags) {
        if (ehTags != null && tags.length == 2) {
            String namespace = EhTagDatabase.prefixToNamespace(tags[0]+":");
            if (namespace==null){
                namespace = tags[0];
            }
            String group = ehTags.getTranslation("n:" + namespace);
            //翻译标签名
            String prefix = translationPrefix(tags[0]);
            String tagStr = ehTags.getTranslation(prefix + tags[1]);


            if (group != null && tagStr != null) {
                return group + ":" + tagStr;
            } else if (group != null && tagStr == null) {
                return group + ":" + tags[1];
            } else if (group == null && tagStr != null) {
                return tags[0] + ":" + tagStr;
            } else {
                if(tagStr != null)
                    return tags[0] + ":" + tagStr;
                else
                    return tags[0] + ":" + tags[1];
            }
        } else {
            StringBuffer s = new StringBuffer();
            for (int i = 0; i < tags.length; i++) {
                if (i == 0) {
                    s = s.append(tags[i]);
//                    s +=  tags[i];
                } else {
                    s = s.append(":").append(tags[i]);
//                    s +=  ":" + tags[i];
                }
            }

            return s.toString();
        }
    }

    public static String getTagCNBody(String[] tags, EhTagDatabase ehTags) {
        if (ehTags != null && tags.length == 2) {
            String group = ehTags.getTranslation("n:" + tags[0]);
            //翻译标签名
            String prefix = translationPrefix(tags[0]);
            String tagstr = ehTags.getTranslation(prefix + tags[1]);


            if (group != null && tagstr != null) {
                return tagstr;
            } else if (group != null && tagstr == null) {
                return tags[1];
            } else if (group == null && tagstr != null) {
                return tagstr;
            } else {
                return tags[1];
            }
        } else {
            StringBuffer s = new StringBuffer();
            for (String i : tags) {
                s = (new StringBuffer()).append(i);
            }
            return s.toString();
        }
    }

    public static String getTagCN(String tag, EhTagDatabase ehTags) {
        return getTagCN(tag.split(":"),ehTags);
    }

    /**
     * Resolve lookup prefix for a full namespace ({@code location}) or short prefix ({@code loc}).
     */
    private static String translationPrefix(String namespaceOrPrefix) {
        if (EhTagDatabase.prefixToNamespace(namespaceOrPrefix + ":") != null) {
            return namespaceOrPrefix + ":";
        }
        String prefix = EhTagDatabase.namespaceToPrefix(namespaceOrPrefix);
        return prefix != null ? prefix : "";
    }
}
