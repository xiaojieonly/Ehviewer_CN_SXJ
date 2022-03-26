package com.hippo.ehviewer.client.parser;

import com.hippo.ehviewer.client.data.MyTagList;
import com.hippo.ehviewer.client.exception.EhException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyTagLitParser {

    private static final Pattern PATTERN_ERROR = Pattern.compile("<div class=\"d\">\n<p>([^<]+)</p>");

    public static MyTagList parse(String body) throws EhException {


        Matcher m = PATTERN_ERROR.matcher(body);
        if (m.find()) {
            throw new EhException(m.group(1));
        }

        Document document = Jsoup.parse(body);



        return null;
    }
}
