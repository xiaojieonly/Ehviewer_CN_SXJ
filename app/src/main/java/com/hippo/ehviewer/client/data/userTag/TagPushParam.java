package com.hippo.ehviewer.client.data.userTag;

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
        String tagName = tagNameNew;

        tagName.replace(":", "%3A");
        tagName.replace(" ", "+");

        return tagName;
    }

    private String getEncodeColorName() {
        if (tagColorNew == null) {
            return "";
        }
        String tagColor = tagColorNew;
        tagColor.replace("#", "%23");

        return tagColor;
    }

}
