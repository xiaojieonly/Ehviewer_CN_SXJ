/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.text;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.ParagraphStyle;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TextAppearanceSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Locale;
import org.ccil.cowan.tagsoup.HTMLSchema;
import org.ccil.cowan.tagsoup.Parser;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * This class processes HTML strings into displayable styled text.
 * Not all HTML tags are supported.
 */
public class Html {

    static Resources sResources;

    public static void initialize(Context context) {
        sResources = context.getResources();
    }

    /**
     * Retrieves images for HTML &lt;img&gt; tags.
     */
    public interface ImageGetter {
        /**
         * This method is called when the HTML parser encounters an
         * &lt;img&gt; tag.  The <code>source</code> argument is the
         * string from the "src" attribute; the return value should be
         * a Drawable representation of the image or <code>null</code>
         * for a generic replacement image.  Make sure you call
         * setBounds() on your Drawable if it doesn't already have
         * its bounds set.
         */
        Drawable getDrawable(String source);
    }

    /**
     * Is notified when HTML tags are encountered that the parser does
     * not know how to interpret.
     */
    public interface TagHandler {
        /**
         * This method will be called whenn the HTML parser encounters
         * a tag that it does not know how to interpret.
         */
        void handleTag(boolean opening, String tag,
                Editable output, XMLReader xmlReader);
    }

    private Html() { }

    /**
     * Returns displayable styled text from the provided HTML string.
     * Any &lt;img&gt; tags in the HTML will display as a generic
     * replacement image which your program can then go through and
     * replace with real images.
     *
     * <p>This uses TagSoup to handle real HTML, including all of the brokenness found in the wild.
     */
    public static Spanned fromHtml(String source) {
        return fromHtml(source, null, null);
    }

    /**
     * Lazy initialization holder for HTML parser. This class will
     * a) be preloaded by the zygote, or b) not loaded until absolutely
     * necessary.
     */
    private static class HtmlParser {
        private static final HTMLSchema schema = new HTMLSchema();
    }

    /**
     * Returns displayable styled text from the provided HTML string.
     * Any &lt;img&gt; tags in the HTML will use the specified ImageGetter
     * to request a representation of the image (use null if you don't
     * want this) and the specified TagHandler to handle unknown tags
     * (specify null if you don't want this).
     *
     * <p>This uses TagSoup to handle real HTML, including all of the brokenness found in the wild.
     */
    public static SpannableStringBuilder fromHtml(String source, ImageGetter imageGetter,
            TagHandler tagHandler) {
        Parser parser = new Parser();
        try {
            parser.setProperty(Parser.schemaProperty, HtmlParser.schema);
        } catch (org.xml.sax.SAXNotRecognizedException e) {
            // Should not happen.
            throw new RuntimeException(e);
        } catch (org.xml.sax.SAXNotSupportedException e) {
            // Should not happen.
            throw new RuntimeException(e);
        }

        HtmlToSpannedConverter converter =
                new HtmlToSpannedConverter(source, imageGetter, tagHandler,
                        parser);
        return converter.convert();
    }

    /**
     * Returns an HTML representation of the provided Spanned text. A best effort is
     * made to add HTML tags corresponding to spans. Also note that HTML metacharacters
     * (such as "&lt;" and "&amp;") within the input text are escaped.
     *
     * @param text input text to convert
     * @return string containing input converted to HTML
     */
    public static String toHtml(Spanned text) {
        StringBuilder out = new StringBuilder();
        withinHtml(out, text);
        return out.toString();
    }

    /**
     * Returns an HTML escaped representation of the given plain text.
     */
    public static String escapeHtml(CharSequence text) {
        StringBuilder out = new StringBuilder();
        withinStyle(out, text, 0, text.length());
        return out.toString();
    }

    private static void withinHtml(StringBuilder out, Spanned text) {
        int len = text.length();

        int next;
        for (int i = 0; i < text.length(); i = next) {
            next = text.nextSpanTransition(i, len, ParagraphStyle.class);
            ParagraphStyle[] style = text.getSpans(i, next, ParagraphStyle.class);
            String elements = " ";
            boolean needDiv = false;

            for(int j = 0; j < style.length; j++) {
                if (style[j] instanceof AlignmentSpan) {
                    Layout.Alignment align =
                            ((AlignmentSpan) style[j]).getAlignment();
                    needDiv = true;
                    if (align == Layout.Alignment.ALIGN_CENTER) {
                        elements = "align=\"center\" " + elements;
                    } else if (align == Layout.Alignment.ALIGN_OPPOSITE) {
                        elements = "align=\"right\" " + elements;
                    } else {
                        elements = "align=\"left\" " + elements;
                    }
                }
            }
            if (needDiv) {
                out.append("<div ").append(elements).append(">");
            }

            withinDiv(out, text, i, next);

            if (needDiv) {
                out.append("</div>");
            }
        }
    }

    private static void withinDiv(StringBuilder out, Spanned text,
            int start, int end) {
        int next;
        for (int i = start; i < end; i = next) {
            next = text.nextSpanTransition(i, end, QuoteSpan.class);
            QuoteSpan[] quotes = text.getSpans(i, next, QuoteSpan.class);

            for (QuoteSpan quote : quotes) {
                out.append("<blockquote>");
            }

            withinBlockquote(out, text, i, next);

            for (QuoteSpan quote : quotes) {
                out.append("</blockquote>\n");
            }
        }
    }

    private static String getOpenParaTagWithDirection(Spanned text, int start, int end) {
        //final int len = end - start;
        //final byte[] levels = new byte[len];
        //final char[] buffer = new char[len];
        //TextUtils.getChars(text, start, end, buffer, 0);

        //int paraDir = AndroidBidi.bidi(Layout.DIR_REQUEST_DEFAULT_LTR, buffer, levels, len,
        //        false /* no info */);
        //switch(paraDir) {
        //    case Layout.DIR_RIGHT_TO_LEFT:
        //        return "<p dir=\"rtl\">";
        //    case Layout.DIR_LEFT_TO_RIGHT:
        //    default:
        //        return "<p dir=\"ltr\">";
        //}
        return "<p dir=\"ltr\">";
    }

    private static void withinBlockquote(StringBuilder out, Spanned text,
            int start, int end) {
        out.append(getOpenParaTagWithDirection(text, start, end));

        int next;
        for (int i = start; i < end; i = next) {
            next = TextUtils.indexOf(text, '\n', i, end);
            if (next < 0) {
                next = end;
            }

            int nl = 0;

            while (next < end && text.charAt(next) == '\n') {
                nl++;
                next++;
            }

            if (withinParagraph(out, text, i, next - nl, nl, next == end)) {
                /* Paragraph should be closed */
                out.append("</p>\n");
                out.append(getOpenParaTagWithDirection(text, next, end));
            }
        }

        out.append("</p>\n");
    }

    /* Returns true if the caller should close and reopen the paragraph. */
    private static boolean withinParagraph(StringBuilder out, Spanned text,
            int start, int end, int nl,
            boolean last) {
        int next;
        for (int i = start; i < end; i = next) {
            next = text.nextSpanTransition(i, end, CharacterStyle.class);
            CharacterStyle[] style = text.getSpans(i, next,
                    CharacterStyle.class);

            for (int j = 0; j < style.length; j++) {
                if (style[j] instanceof StyleSpan) {
                    int s = ((StyleSpan) style[j]).getStyle();

                    if ((s & Typeface.BOLD) != 0) {
                        out.append("<b>");
                    }
                    if ((s & Typeface.ITALIC) != 0) {
                        out.append("<i>");
                    }
                }
                if (style[j] instanceof TypefaceSpan) {
                    String s = ((TypefaceSpan) style[j]).getFamily();

                    if ("monospace".equals(s)) {
                        out.append("<tt>");
                    }
                }
                if (style[j] instanceof SuperscriptSpan) {
                    out.append("<sup>");
                }
                if (style[j] instanceof SubscriptSpan) {
                    out.append("<sub>");
                }
                if (style[j] instanceof UnderlineSpan) {
                    out.append("<u>");
                }
                if (style[j] instanceof StrikethroughSpan) {
                    out.append("<strike>");
                }
                if (style[j] instanceof URLSpan) {
                    out.append("<a href=\"");
                    out.append(((URLSpan) style[j]).getURL());
                    out.append("\">");
                }
                if (style[j] instanceof ImageSpan) {
                    out.append("<img src=\"");
                    out.append(((ImageSpan) style[j]).getSource());
                    out.append("\">");

                    // Don't output the dummy character underlying the image.
                    i = next;
                }
                if (style[j] instanceof AbsoluteSizeSpan) {
                    out.append("<font size =\"");
                    out.append(((AbsoluteSizeSpan) style[j]).getSize() / 6);
                    out.append("\">");
                }
                if (style[j] instanceof ForegroundColorSpan) {
                    out.append("<font color =\"#");
                    String color = Integer.toHexString(((ForegroundColorSpan)
                            style[j]).getForegroundColor() + 0x01000000);
                    while (color.length() < 6) {
                        color = "0" + color;
                    }
                    out.append(color);
                    out.append("\">");
                }
            }

            withinStyle(out, text, i, next);

            for (int j = style.length - 1; j >= 0; j--) {
                if (style[j] instanceof ForegroundColorSpan) {
                    out.append("</font>");
                }
                if (style[j] instanceof AbsoluteSizeSpan) {
                    out.append("</font>");
                }
                if (style[j] instanceof URLSpan) {
                    out.append("</a>");
                }
                if (style[j] instanceof StrikethroughSpan) {
                    out.append("</strike>");
                }
                if (style[j] instanceof UnderlineSpan) {
                    out.append("</u>");
                }
                if (style[j] instanceof SubscriptSpan) {
                    out.append("</sub>");
                }
                if (style[j] instanceof SuperscriptSpan) {
                    out.append("</sup>");
                }
                if (style[j] instanceof TypefaceSpan) {
                    String s = ((TypefaceSpan) style[j]).getFamily();

                    if (s.equals("monospace")) {
                        out.append("</tt>");
                    }
                }
                if (style[j] instanceof StyleSpan) {
                    int s = ((StyleSpan) style[j]).getStyle();

                    if ((s & Typeface.BOLD) != 0) {
                        out.append("</b>");
                    }
                    if ((s & Typeface.ITALIC) != 0) {
                        out.append("</i>");
                    }
                }
            }
        }

        if (nl == 1) {
            out.append("<br>\n");
            return false;
        } else {
            for (int i = 2; i < nl; i++) {
                out.append("<br>");
            }
            return !last;
        }
    }

    private static void withinStyle(StringBuilder out, CharSequence text,
            int start, int end) {
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);

            if (c == '<') {
                out.append("&lt;");
            } else if (c == '>') {
                out.append("&gt;");
            } else if (c == '&') {
                out.append("&amp;");
            } else if (c >= 0xD800 && c <= 0xDFFF) {
                if (c < 0xDC00 && i + 1 < end) {
                    char d = text.charAt(i + 1);
                    if (d >= 0xDC00 && d <= 0xDFFF) {
                        i++;
                        int codepoint = 0x010000 | (int) c - 0xD800 << 10 | (int) d - 0xDC00;
                        out.append("&#").append(codepoint).append(";");
                    }
                }
            } else if (c > 0x7E || c < ' ') {
                out.append("&#").append((int) c).append(";");
            } else if (c == ' ') {
                while (i + 1 < end && text.charAt(i + 1) == ' ') {
                    out.append("&nbsp;");
                    i++;
                }

                out.append(' ');
            } else {
                out.append(c);
            }
        }
    }
}

class HtmlToSpannedConverter implements ContentHandler {

    private static final float[] HEADER_SIZES = {
            1.5f, 1.4f, 1.3f, 1.2f, 1.1f, 1f,
    };

    private String mSource;
    private XMLReader mReader;
    private SpannableStringBuilder mSpannableStringBuilder;
    private Html.ImageGetter mImageGetter;
    private Html.TagHandler mTagHandler;

    public HtmlToSpannedConverter(
            String source, Html.ImageGetter imageGetter, Html.TagHandler tagHandler,
            Parser parser) {
        mSource = source;
        mSpannableStringBuilder = new SpannableStringBuilder();
        mImageGetter = imageGetter;
        mTagHandler = tagHandler;
        mReader = parser;
    }

    public SpannableStringBuilder convert() {

        mReader.setContentHandler(this);
        try {
            mReader.parse(new InputSource(new StringReader(mSource)));
        } catch (IOException e) {
            // We are reading from a string. There should not be IO problems.
            throw new RuntimeException(e);
        } catch (SAXException e) {
            // TagSoup doesn't throw parse exceptions.
            throw new RuntimeException(e);
        }

        // Fix flags and range for paragraph-type markup.
        Object[] obj = mSpannableStringBuilder.getSpans(0, mSpannableStringBuilder.length(), ParagraphStyle.class);
        for (int i = 0; i < obj.length; i++) {
            int start = mSpannableStringBuilder.getSpanStart(obj[i]);
            int end = mSpannableStringBuilder.getSpanEnd(obj[i]);

            // If the last line of the range is blank, back off by one.
            if (end - 2 >= 0) {
                if (mSpannableStringBuilder.charAt(end - 1) == '\n' &&
                        mSpannableStringBuilder.charAt(end - 2) == '\n') {
                    end--;
                }
            }

            if (end == start) {
                mSpannableStringBuilder.removeSpan(obj[i]);
            } else {
                mSpannableStringBuilder.setSpan(obj[i], start, end, Spannable.SPAN_PARAGRAPH);
            }
        }

        return mSpannableStringBuilder;
    }

    private void handleStartTag(String tag, Attributes attributes) {
        if (tag.equalsIgnoreCase("br")) {
            // We don't need to handle this. TagSoup will ensure that there's a </br> for each <br>
            // so we can safely emite the linebreaks when we handle the close tag.
        } else if (tag.equalsIgnoreCase("p")) {
            handleP(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("div")) {
            handleP(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("strong")) {
            start(mSpannableStringBuilder, new Bold());
        } else if (tag.equalsIgnoreCase("b")) {
            start(mSpannableStringBuilder, new Bold());
        } else if (tag.equalsIgnoreCase("em")) {
            start(mSpannableStringBuilder, new Italic());
        } else if (tag.equalsIgnoreCase("cite")) {
            start(mSpannableStringBuilder, new Italic());
        } else if (tag.equalsIgnoreCase("dfn")) {
            start(mSpannableStringBuilder, new Italic());
        } else if (tag.equalsIgnoreCase("i")) {
            start(mSpannableStringBuilder, new Italic());
        } else if (tag.equalsIgnoreCase("big")) {
            start(mSpannableStringBuilder, new Big());
        } else if (tag.equalsIgnoreCase("small")) {
            start(mSpannableStringBuilder, new Small());
        } else if (tag.equalsIgnoreCase("font")) {
            startFont(mSpannableStringBuilder, attributes);
        } else if (tag.equalsIgnoreCase("blockquote")) {
            handleP(mSpannableStringBuilder);
            start(mSpannableStringBuilder, new Blockquote());
        } else if (tag.equalsIgnoreCase("tt")) {
            start(mSpannableStringBuilder, new Monospace());
        } else if (tag.equalsIgnoreCase("a")) {
            startA(mSpannableStringBuilder, attributes);
        } else if (tag.equalsIgnoreCase("u")) {
            start(mSpannableStringBuilder, new Underline());
        } else if (tag.equalsIgnoreCase("ins")) {
            start(mSpannableStringBuilder, new Underline());
        } else if (tag.equalsIgnoreCase("strike")) {
            start(mSpannableStringBuilder, new Strike());
        } else if (tag.equalsIgnoreCase("s")) {
            start(mSpannableStringBuilder, new Strike());
        } else if (tag.equalsIgnoreCase("del")) {
            start(mSpannableStringBuilder, new Strike());
        } else if (tag.equalsIgnoreCase("sup")) {
            start(mSpannableStringBuilder, new Super());
        } else if (tag.equalsIgnoreCase("sub")) {
            start(mSpannableStringBuilder, new Sub());
        } else if (tag.length() == 2 &&
                Character.toLowerCase(tag.charAt(0)) == 'h' &&
                tag.charAt(1) >= '1' && tag.charAt(1) <= '6') {
            handleP(mSpannableStringBuilder);
            start(mSpannableStringBuilder, new Header(tag.charAt(1) - '1'));
        } else if (tag.equalsIgnoreCase("img")) {
            startImg(mSpannableStringBuilder, attributes, mImageGetter);
        } else if (mTagHandler != null) {
            mTagHandler.handleTag(true, tag, mSpannableStringBuilder, mReader);
        }
    }

    private void handleEndTag(String tag) {
        if (tag.equalsIgnoreCase("br")) {
            handleBr(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("p")) {
            handleP(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("div")) {
            handleP(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("strong")) {
            end(mSpannableStringBuilder, Bold.class, new StyleSpan(Typeface.BOLD));
        } else if (tag.equalsIgnoreCase("b")) {
            end(mSpannableStringBuilder, Bold.class, new StyleSpan(Typeface.BOLD));
        } else if (tag.equalsIgnoreCase("em")) {
            end(mSpannableStringBuilder, Italic.class, new StyleSpan(Typeface.ITALIC));
        } else if (tag.equalsIgnoreCase("cite")) {
            end(mSpannableStringBuilder, Italic.class, new StyleSpan(Typeface.ITALIC));
        } else if (tag.equalsIgnoreCase("dfn")) {
            end(mSpannableStringBuilder, Italic.class, new StyleSpan(Typeface.ITALIC));
        } else if (tag.equalsIgnoreCase("i")) {
            end(mSpannableStringBuilder, Italic.class, new StyleSpan(Typeface.ITALIC));
        } else if (tag.equalsIgnoreCase("big")) {
            end(mSpannableStringBuilder, Big.class, new RelativeSizeSpan(1.25f));
        } else if (tag.equalsIgnoreCase("small")) {
            end(mSpannableStringBuilder, Small.class, new RelativeSizeSpan(0.8f));
        } else if (tag.equalsIgnoreCase("font")) {
            endFont(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("blockquote")) {
            handleP(mSpannableStringBuilder);
            end(mSpannableStringBuilder, Blockquote.class, new QuoteSpan());
        } else if (tag.equalsIgnoreCase("tt")) {
            end(mSpannableStringBuilder, Monospace.class,
                    new TypefaceSpan("monospace"));
        } else if (tag.equalsIgnoreCase("a")) {
            endA(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("u")) {
            end(mSpannableStringBuilder, Underline.class, new UnderlineSpan());
        } else if (tag.equalsIgnoreCase("ins")) {
            end(mSpannableStringBuilder, Underline.class, new UnderlineSpan());
        } else if (tag.equalsIgnoreCase("strike")) {
            end(mSpannableStringBuilder, Strike.class, new StrikethroughSpan());
        } else if (tag.equalsIgnoreCase("s")) {
            end(mSpannableStringBuilder, Strike.class, new StrikethroughSpan());
        } else if (tag.equalsIgnoreCase("del")) {
            end(mSpannableStringBuilder, Strike.class, new StrikethroughSpan());
        } else if (tag.equalsIgnoreCase("sup")) {
            end(mSpannableStringBuilder, Super.class, new SuperscriptSpan());
        } else if (tag.equalsIgnoreCase("sub")) {
            end(mSpannableStringBuilder, Sub.class, new SubscriptSpan());
        } else if (tag.length() == 2 &&
                Character.toLowerCase(tag.charAt(0)) == 'h' &&
                tag.charAt(1) >= '1' && tag.charAt(1) <= '6') {
            handleP(mSpannableStringBuilder);
            endHeader(mSpannableStringBuilder);
        } else if (mTagHandler != null) {
            mTagHandler.handleTag(false, tag, mSpannableStringBuilder, mReader);
        }
    }

    private static void handleP(SpannableStringBuilder text) {
        int len = text.length();

        if (len >= 1 && text.charAt(len - 1) == '\n') {
            if (len >= 2 && text.charAt(len - 2) == '\n') {
                return;
            }

            text.append("\n");
            return;
        }

        if (len != 0) {
            text.append("\n\n");
        }
    }

    private static void handleBr(SpannableStringBuilder text) {
        text.append("\n");
    }

    private static Object getLast(Spanned text, Class kind) {
        /*
         * This knows that the last returned object from getSpans()
         * will be the most recently added.
         */
        Object[] objs = text.getSpans(0, text.length(), kind);

        if (objs.length == 0) {
            return null;
        } else {
            return objs[objs.length - 1];
        }
    }

    private static void start(SpannableStringBuilder text, Object mark) {
        int len = text.length();
        text.setSpan(mark, len, len, Spannable.SPAN_MARK_MARK);
    }

    private static void end(SpannableStringBuilder text, Class kind,
            Object repl) {
        int len = text.length();
        Object obj = getLast(text, kind);
        int where = text.getSpanStart(obj);

        text.removeSpan(obj);

        if (where != len) {
            text.setSpan(repl, where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void startImg(SpannableStringBuilder text,
            Attributes attributes, Html.ImageGetter img) {
        String src = attributes.getValue("", "src");
        Drawable d = null;

        if (img != null) {
            d = img.getDrawable(src);
        }

        if (d == null) {
            d = Resources.getSystem().getDrawable(android.R.drawable.ic_menu_report_image);
            d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        }

        int len = text.length();
        text.append("\uFFFC");

        text.setSpan(new ImageSpan(d, src), len, text.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void startFont(SpannableStringBuilder text,
            Attributes attributes) {
        String color = attributes.getValue("", "color");
        String face = attributes.getValue("", "face");

        int len = text.length();
        text.setSpan(new Font(color, face), len, len, Spannable.SPAN_MARK_MARK);
    }

    private static void endFont(SpannableStringBuilder text) {
        int len = text.length();
        Object obj = getLast(text, Font.class);
        int where = text.getSpanStart(obj);

        text.removeSpan(obj);

        if (where != len) {
            Font f = (Font) obj;

            if (!TextUtils.isEmpty(f.mColor)) {
                if (f.mColor.startsWith("@")) {
                    Resources res = Resources.getSystem();
                    String name = f.mColor.substring(1);
                    int colorRes = res.getIdentifier(name, "color", "android");
                    text.setSpan(new TextAppearanceSpan(null, 0, 0, ColorStateList.valueOf(res.getColor(colorRes)), null),
                            where, len,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    try {
                        int c = getHtmlColor(f.mColor);
                        text.setSpan(new ForegroundColorSpan(c | 0xFF000000),
                                where, len,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } catch (IllegalArgumentException e) {
                        // Ignore
                    }
                }
            }

            if (f.mFace != null) {
                text.setSpan(new TypefaceSpan(f.mFace), where, len,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static void startA(SpannableStringBuilder text, Attributes attributes) {
        String href = attributes.getValue("", "href");

        int len = text.length();
        text.setSpan(new Href(href), len, len, Spannable.SPAN_MARK_MARK);
    }

    private static void endA(SpannableStringBuilder text) {
        int len = text.length();
        Object obj = getLast(text, Href.class);
        int where = text.getSpanStart(obj);

        text.removeSpan(obj);

        if (where != len) {
            Href h = (Href) obj;

            if (h.mHref != null) {
                text.setSpan(new URLSpan(h.mHref), where, len,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static void endHeader(SpannableStringBuilder text) {
        int len = text.length();
        Object obj = getLast(text, Header.class);

        int where = text.getSpanStart(obj);

        text.removeSpan(obj);

        // Back off not to change only the text, not the blank line.
        while (len > where && text.charAt(len - 1) == '\n') {
            len--;
        }

        if (where != len) {
            Header h = (Header) obj;

            text.setSpan(new RelativeSizeSpan(HEADER_SIZES[h.mLevel]),
                    where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new StyleSpan(Typeface.BOLD),
                    where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    @Override
    public void setDocumentLocator(Locator locator) {
    }

    @Override
    public void startDocument() throws SAXException {
    }

    @Override
    public void endDocument() throws SAXException {
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        handleStartTag(localName, attributes);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        handleEndTag(localName);
    }

    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        StringBuilder sb = new StringBuilder();

        /*
         * Ignore whitespace that immediately follows other whitespace;
         * newlines count as spaces.
         */

        for (int i = 0; i < length; i++) {
            char c = ch[i + start];

            if (c == ' ' || c == '\n') {
                char pred;
                int len = sb.length();

                if (len == 0) {
                    len = mSpannableStringBuilder.length();

                    if (len == 0) {
                        pred = '\n';
                    } else {
                        pred = mSpannableStringBuilder.charAt(len - 1);
                    }
                } else {
                    pred = sb.charAt(len - 1);
                }

                if (pred != ' ' && pred != '\n') {
                    sb.append(' ');
                }
            } else {
                sb.append(c);
            }
        }

        mSpannableStringBuilder.append(sb);
    }

    @Override
    public void ignorableWhitespace(char ch[], int start, int length) throws SAXException {
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
    }

    @Override
    public void skippedEntity(String name) throws SAXException {
    }

    private static class Bold { }
    private static class Italic { }
    private static class Underline { }
    private static class Strike { }
    private static class Big { }
    private static class Small { }
    private static class Monospace { }
    private static class Blockquote { }
    private static class Super { }
    private static class Sub { }

    private static class Font {
        public String mColor;
        public String mFace;

        public Font(String color, String face) {
            mColor = color;
            mFace = face;
        }
    }

    private static class Href {
        public String mHref;

        public Href(String href) {
            mHref = href;
        }
    }

    private static class Header {
        private int mLevel;

        public Header(int level) {
            mLevel = level;
        }
    }

    /**
     * Parse the color string, and return the corresponding color-int.
     * If the string cannot be parsed, throws an IllegalArgumentException
     * exception. Supported formats are:
     * #RRGGBB
     * #AARRGGBB
     * rgb(255, 255, 255)
     * or color name
     */
    @ColorInt
    public static int getHtmlColor(@NonNull String colorString) {
        if ((colorString.length() == 7 || colorString.length() == 9) && colorString.charAt(0) == '#') {
            // Use a long to avoid rollovers on #ffXXXXXX
            long color;
            try {
                color = Long.parseLong(colorString.substring(1), 16);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Unknown color: " + colorString);
            }
            if (colorString.length() == 7) {
                // Set the alpha value
                color |= 0x00000000ff000000;
            }
            return (int) color;
        } else if (colorString.startsWith("rgb(") && colorString.endsWith(")")) {
            String str = colorString.substring(4, colorString.length() - 1);
            String[] colors = str.split("[\\s]*,[\\s]*");
            if (colors.length == 3) {
                try {
                    return Color.argb(0xff, Integer.valueOf(colors[0]), Integer.valueOf(colors[1]), Integer.valueOf(colors[2]));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Unknown color: " + colorString);
                }
            }
        } else {
            Integer color = sColorNameMap.get(colorString.toLowerCase(Locale.ROOT));
            if (color != null) {
                return color;
            }
        }
        throw new IllegalArgumentException("Unknown color: " + colorString);
    }

    private static final HashMap<String, Integer> sColorNameMap;

    static {
        sColorNameMap = new HashMap<>();
        sColorNameMap.put("aliceblue", 0xFFF0F8FF);
        sColorNameMap.put("antiquewhite", 0xFFFAEBD7);
        sColorNameMap.put("aqua", 0xFFFFFF);
        sColorNameMap.put("aquamarine", 0xFF7FFFD4);
        sColorNameMap.put("azure", 0xFFF0FFFF);
        sColorNameMap.put("beige", 0xFFF5F5DC);
        sColorNameMap.put("bisque", 0xFFFFE4C4);
        sColorNameMap.put("black", 0xFF0);
        sColorNameMap.put("blanchedalmond", 0xFFFFEBCD);
        sColorNameMap.put("blue", 0xFFFF);
        sColorNameMap.put("blueviolet", 0xFF8A2BE2);
        sColorNameMap.put("brown", 0xFFA52A2A);
        sColorNameMap.put("burlywood", 0xFFDEB887);
        sColorNameMap.put("cadetblue", 0xFF5F9EA0);
        sColorNameMap.put("chartreuse", 0xFF7FFF00);
        sColorNameMap.put("chocolate", 0xFFD2691E);
        sColorNameMap.put("coral", 0xFFFF7F50);
        sColorNameMap.put("cornflowerblue", 0xFF6495ED);
        sColorNameMap.put("cornsilk", 0xFFFFF8DC);
        sColorNameMap.put("crimson", 0xFFDC143C);
        sColorNameMap.put("cyan", 0xFFFFFF);
        sColorNameMap.put("darkblue", 0xFF8B);
        sColorNameMap.put("darkcyan", 0xFF8B8B);
        sColorNameMap.put("darkgoldenrod", 0xFFB8860B);
        sColorNameMap.put("darkgray", 0xFFA9A9A9);
        sColorNameMap.put("darkgreen", 0xFF6400);
        sColorNameMap.put("darkkhaki", 0xFFBDB76B);
        sColorNameMap.put("darkmagenta", 0xFF8B008B);
        sColorNameMap.put("darkolivegreen", 0xFF556B2F);
        sColorNameMap.put("darkorange", 0xFFFF8C00);
        sColorNameMap.put("darkorchid", 0xFF9932CC);
        sColorNameMap.put("darkred", 0xFF8B0000);
        sColorNameMap.put("darksalmon", 0xFFE9967A);
        sColorNameMap.put("darkseagreen", 0xFF8FBC8F);
        sColorNameMap.put("darkslateblue", 0xFF483D8B);
        sColorNameMap.put("darkslategray", 0xFF2F4F4F);
        sColorNameMap.put("darkturquoise", 0xFFCED1);
        sColorNameMap.put("darkviolet", 0xFF9400D3);
        sColorNameMap.put("deeppink", 0xFFFF1493);
        sColorNameMap.put("deepskyblue", 0xFFBFFF);
        sColorNameMap.put("dimgray", 0xFF696969);
        sColorNameMap.put("dodgerblue", 0xFF1E90FF);
        sColorNameMap.put("firebrick", 0xFFB22222);
        sColorNameMap.put("floralwhite", 0xFFFFFAF0);
        sColorNameMap.put("forestgreen", 0xFF228B22);
        sColorNameMap.put("fuchsia", 0xFFFF00FF);
        sColorNameMap.put("gainsboro", 0xFFDCDCDC);
        sColorNameMap.put("ghostwhite", 0xFFF8F8FF);
        sColorNameMap.put("gold", 0xFFFFD700);
        sColorNameMap.put("goldenrod", 0xFFDAA520);
        sColorNameMap.put("gray", 0xFF808080);
        sColorNameMap.put("green", 0xFF8000);
        sColorNameMap.put("greenyellow", 0xFFADFF2F);
        sColorNameMap.put("honeydew", 0xFFF0FFF0);
        sColorNameMap.put("hotpink", 0xFFFF69B4);
        sColorNameMap.put("indianred", 0xFFCD5C5C);
        sColorNameMap.put("indigo", 0xFF4B0082);
        sColorNameMap.put("ivory", 0xFFFFFFF0);
        sColorNameMap.put("khaki", 0xFFF0E68C);
        sColorNameMap.put("lavender", 0xFFE6E6FA);
        sColorNameMap.put("lavenderblush", 0xFFFFF0F5);
        sColorNameMap.put("lawngreen", 0xFF7CFC00);
        sColorNameMap.put("lemonchiffon", 0xFFFFFACD);
        sColorNameMap.put("lightblue", 0xFFADD8E6);
        sColorNameMap.put("lightcoral", 0xFFF08080);
        sColorNameMap.put("lightcyan", 0xFFE0FFFF);
        sColorNameMap.put("lightgoldenrodyellow", 0xFFFAFAD2);
        sColorNameMap.put("lightgreen", 0xFF90EE90);
        sColorNameMap.put("lightgrey", 0xFFD3D3D3);
        sColorNameMap.put("lightpink", 0xFFFFB6C1);
        sColorNameMap.put("lightsalmon", 0xFFFFA07A);
        sColorNameMap.put("lightseagreen", 0xFF20B2AA);
        sColorNameMap.put("lightskyblue", 0xFF87CEFA);
        sColorNameMap.put("lightslategray", 0xFF778899);
        sColorNameMap.put("lightsteelblue", 0xFFB0C4DE);
        sColorNameMap.put("lightyellow", 0xFFFFFFE0);
        sColorNameMap.put("lime", 0xFFFF00);
        sColorNameMap.put("limegreen", 0xFF32CD32);
        sColorNameMap.put("linen", 0xFFFAF0E6);
        sColorNameMap.put("magenta", 0xFFFF00FF);
        sColorNameMap.put("maroon", 0xFF800000);
        sColorNameMap.put("mediumaquamarine", 0xFF66CDAA);
        sColorNameMap.put("mediumblue", 0xFFCD);
        sColorNameMap.put("mediumorchid", 0xFFBA55D3);
        sColorNameMap.put("mediumpurple", 0xFF9370DB);
        sColorNameMap.put("mediumseagreen", 0xFF3CB371);
        sColorNameMap.put("mediumslateblue", 0xFF7B68EE);
        sColorNameMap.put("mediumspringgreen", 0xFFFA9A);
        sColorNameMap.put("mediumturquoise", 0xFF48D1CC);
        sColorNameMap.put("mediumvioletred", 0xFFC71585);
        sColorNameMap.put("midnightblue", 0xFF191970);
        sColorNameMap.put("mintcream", 0xFFF5FFFA);
        sColorNameMap.put("mistyrose", 0xFFFFE4E1);
        sColorNameMap.put("moccasin", 0xFFFFE4B5);
        sColorNameMap.put("navajowhite", 0xFFFFDEAD);
        sColorNameMap.put("navy", 0xFF80);
        sColorNameMap.put("oldlace", 0xFFFDF5E6);
        sColorNameMap.put("olive", 0xFF808000);
        sColorNameMap.put("olivedrab", 0xFF6B8E23);
        sColorNameMap.put("orange", 0xFFFFA500);
        sColorNameMap.put("orangered", 0xFFFF4500);
        sColorNameMap.put("orchid", 0xFFDA70D6);
        sColorNameMap.put("palegoldenrod", 0xFFEEE8AA);
        sColorNameMap.put("palegreen", 0xFF98FB98);
        sColorNameMap.put("paleturquoise", 0xFFAFEEEE);
        sColorNameMap.put("palevioletred", 0xFFDB7093);
        sColorNameMap.put("papayawhip", 0xFFFFEFD5);
        sColorNameMap.put("peachpuff", 0xFFFFDAB9);
        sColorNameMap.put("peru", 0xFFCD853F);
        sColorNameMap.put("pink", 0xFFFFC0CB);
        sColorNameMap.put("plum", 0xFFDDA0DD);
        sColorNameMap.put("powderblue", 0xFFB0E0E6);
        sColorNameMap.put("purple", 0xFF800080);
        sColorNameMap.put("red", 0xFFFF0000);
        sColorNameMap.put("rosybrown", 0xFFBC8F8F);
        sColorNameMap.put("royalblue", 0xFF4169E1);
        sColorNameMap.put("saddlebrown", 0xFF8B4513);
        sColorNameMap.put("salmon", 0xFFFA8072);
        sColorNameMap.put("sandybrown", 0xFFF4A460);
        sColorNameMap.put("seagreen", 0xFF2E8B57);
        sColorNameMap.put("seashell", 0xFFFFF5EE);
        sColorNameMap.put("sienna", 0xFFA0522D);
        sColorNameMap.put("silver", 0xFFC0C0C0);
        sColorNameMap.put("skyblue", 0xFF87CEEB);
        sColorNameMap.put("slateblue", 0xFF6A5ACD);
        sColorNameMap.put("slategray", 0xFF708090);
        sColorNameMap.put("snow", 0xFFFFFAFA);
        sColorNameMap.put("springgreen", 0xFFFF7F);
        sColorNameMap.put("steelblue", 0xFF4682B4);
        sColorNameMap.put("tan", 0xFFD2B48C);
        sColorNameMap.put("teal", 0xFF8080);
        sColorNameMap.put("thistle", 0xFFD8BFD8);
        sColorNameMap.put("tomato", 0xFFFF6347);
        sColorNameMap.put("turquoise", 0xFF40E0D0);
        sColorNameMap.put("violet", 0xFFEE82EE);
        sColorNameMap.put("wheat", 0xFFF5DEB3);
        sColorNameMap.put("white", 0xFFFFFFFF);
        sColorNameMap.put("whitesmoke", 0xFFF5F5F5);
        sColorNameMap.put("yellow", 0xFFFFFF00);
        sColorNameMap.put("yellowgreen", 0xFF9ACD32);
    }
}
