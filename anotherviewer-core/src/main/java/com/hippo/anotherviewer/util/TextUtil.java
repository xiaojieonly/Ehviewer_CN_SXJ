package com.hippo.anotherviewer.util;

public class TextUtil {
    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }

    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.toString().equals(b.toString());
    }

    public static boolean isBlank(CharSequence str) {
        if (str == null) return true;
        return str.toString().trim().isEmpty();
    }

    public static String trim(CharSequence str) {
        return str == null ? "" : str.toString().trim();
    }

    public static String join(CharSequence delimiter, CharSequence... tokens) {
        if (tokens == null || tokens.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) sb.append(delimiter);
            if (tokens[i] != null) sb.append(tokens[i]);
        }
        return sb.toString();
    }

    public static int indexOf(CharSequence s, CharSequence target) {
        if (s == null || target == null) return -1;
        return s.toString().indexOf(target.toString());
    }

    private TextUtil() {}
}
