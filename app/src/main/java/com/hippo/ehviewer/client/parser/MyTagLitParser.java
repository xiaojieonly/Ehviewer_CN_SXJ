package com.hippo.ehviewer.client.parser;

import com.hippo.ehviewer.client.data.userTag.UserTag;
import com.hippo.ehviewer.client.data.userTag.UserTagList;
import com.hippo.ehviewer.client.exception.EhException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyTagLitParser {

    private static final Pattern PATTERN_ERROR = Pattern.compile("<div class=\"d\">\n<p>([^<]+)</p>");

    public static UserTagList parse(String body) throws EhException {

        UserTagList list = new UserTagList();
        list.userTags = new ArrayList<>();
        Matcher m = PATTERN_ERROR.matcher(body);
        if (m.find()) {
            throw new EhException(m.group(1));
        }

        Document document = Jsoup.parse(body);

        Element element = document.getElementById("usertags_outer");

        if (element == null){
            return list;
        }

        Elements tags = element.children();

        for (int i = 0;i<tags.size();i++){
            if (i !=0){
                Element tag = tags.get(i);

                list.userTags.add(parserUserTag(tag));
            }
        }


        return list;
    }

    private static UserTag parserUserTag(Element tag){
        UserTag userTag = new UserTag();
        userTag.userTagId = tag.id();
        String id = userTag.userTagId.substring(7);
        String nameId = "tagpreview"+id;
        String tagName = tag.getElementById(nameId).attr("title");
        if (tagName!=null){
            userTag.tagName = tagName;
        }
        String watchId = "tagwatch" + id;
        String hideId = "taghide" + id;
        Element watchInput = tag.getElementById(watchId);
        Element hideInput = tag.getElementById(hideId);

        if (watchInput != null ){
            userTag.watched = Objects.equals(watchInput.attr("checked"), "checked");
        }else{
            userTag.watched = false;
        }

        if (hideInput != null ){
            userTag.hidden = Objects.equals(hideInput.attr("checked"), "checked");
        }else{
            userTag.hidden = false;
        }
        String colorId = "tagcolor"+id;
        userTag.color = tag.getElementById(colorId).attr("placeholder");

        String weightId = "tagweight" + id;
        String weightString = tag.getElementById(weightId).attr("value");
        if (weightString.isEmpty()){
            userTag.tagWeight = 0;
        }
        userTag.tagWeight = Integer.parseInt(weightString);

        return  userTag;
    }
}
