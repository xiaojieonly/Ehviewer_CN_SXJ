package com.hippo.ehviewer.client.data.userTag;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class TagPushParam {

    public String userTagAction;
    public String tagNameNew;
    public String tagWatchNew;
    public String tagHiddenNew;
    public String tagColorNew;
    public int tagWeightNew;
    public int userTagTarget;

    public TagPushParam() {

    }


    public String addTagParam() {

        String state = "";

        if (tagHiddenNew != null && tagHiddenNew.equals("on")) {
            state = "&taghide_new=on";
        }
        if (tagWatchNew != null && tagWatchNew.equals("on")) {
            state = "&tagwatch_new=on";
        }

        return "usertag_action=add&tagname_new=" + getEncodeTagName()
                + state + "&tagcolor_new=" + getEncodeColorName()
                + "&tagweight_new=10&usertag_target=0";
    }

    private String getEncodeTagName() {
        return encode(tagNameNew);
    }

    private String getEncodeColorName() {
        return encode(tagColorNew);
    }

    private static String encode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}
