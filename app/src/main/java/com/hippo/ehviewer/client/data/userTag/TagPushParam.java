package com.hippo.ehviewer.client.data.userTag;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
        if (tagNameNew == null) {
            return "";
        }
        return URLEncoder.encode(tagNameNew, StandardCharsets.UTF_8);
    }

    private String getEncodeColorName() {
        if (tagColorNew == null) {
            return "";
        }
        return URLEncoder.encode(tagColorNew, StandardCharsets.UTF_8);
    }

}
