package com.hippo.ehviewer.client.parser;

import com.hippo.ehviewer.client.data.EhNewsDetail;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class EhEventParse {

    public static String parse(String body) {
        //        String s = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\"\n" +
        //                "        \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n" +
        //                "<!-- saved from url=(0029)https://e-hentai.org/news.php -->\n" +
        //                "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\">\n" +
        //                "<body>\n" +
        //                "<div id=\"newsouter\">\n" +
        //                "    <div id=\"newsinner\">\n" +
        //                "        <div id=\"eventpane\"\n" +
        //                "             style=\"width:720px; height:80px; margin:5px auto 0; text-align:center !important; background:#F2EFDF; border:1px solid #5C0D12; padding:3px; font-size:9pt\">\n" +
        //                "            <p style=\"font-size:10pt; font-weight:bold; padding:0px; margin:6px auto 2px\">It is the\n" +
        //                "                dawn of a new day!</p>\n" +
        //                "            <p style=\"padding:0px; margin:6px auto 2px\">Reflecting on your journey so far, you find\n" +
        //                "                that you are a little wiser.</p>\n" +
        //                "            <p style=\"padding:0px; margin:6px auto 2px\">You gain <strong>35</strong> EXP,\n" +
        //                "                <strong>7</strong> Credits!</p>\n" +
        //                "        </div>\n" +
        //                "    </div>\n" +
        //                "    <div class=\"baredge\"></div>\n" +
        //                "</div>\n" +
        //                "<div class=\"dp\" style=\"margin:0 auto 5px\">\n" +
        //                "    <a href=\"https://e-hentai.org/\">Front Page</a>\n" +
        //                "    &nbsp; <a href=\"https://e-hentai.org/tos.php\">Terms of Service</a> &nbsp; <a\n" +
        //                "        href=\"mailto:luke@juicyads.com\">Advertise</a>\n" +
        //                "\n" +
        //                "</div>\n" +
        //                "\n" +
        //                "\n" +
        //                "</body>\n" +
        //                "</html>";
        Document document = Jsoup.parse(body);
        //        Document document = Jsoup.parse(s);
        Element eventPane = document.getElementById("eventpane");
        if (eventPane != null) {
            return eventPane.html();
        }
        return null;
    }
}
