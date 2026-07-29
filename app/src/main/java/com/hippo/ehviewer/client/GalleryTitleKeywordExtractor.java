/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Extracts a likely artist keyword from a gallery title.
 */
public final class GalleryTitleKeywordExtractor {

    private static final int BRACKET_SEARCH_WORD_LIMIT = 8;

    // The order is significant: higher-priority bracket types come first.
    private static final Bracket[] BRACKETS = {
            new Bracket('[', ']'),
            new Bracket('【', '】'),
            new Bracket('（', '）'),
            new Bracket('(', ')')
    };

    private GalleryTitleKeywordExtractor() {
    }

    public static String extractArtistKeyword(String title) {
        if (title == null || title.isEmpty()) {
            return null;
        }

        int cursor = 0;
        List<Range> rejectedRanges = new ArrayList<>();
        while (cursor < title.length()) {
            Range selected = null;
            for (Bracket bracket : BRACKETS) {
                Range range = findFirstPair(title, bracket, cursor);
                if (range != null && isWithinBracketSearchLimit(title, range.start)) {
                    selected = range;
                    break;
                }
            }

            if (selected == null) {
                break;
            }

            String candidate = normalizeWhitespace(removeNestedBrackets(
                    title.substring(selected.start + 1, selected.end)));
            if (!candidate.isEmpty() && !isIgnored(candidate)) {
                return candidate;
            }

            // Continue after the rejected outer pair so its nested brackets are not reconsidered.
            rejectedRanges.add(selected);
            cursor = selected.end + 1;
        }

        return findFirstFeasibleWord(removeRanges(title, rejectedRanges));
    }

    private static Range findFirstPair(String text, Bracket bracket, int fromIndex) {
        int depth = 0;
        int start = -1;
        for (int i = fromIndex; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == bracket.open) {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (ch == bracket.close && depth > 0) {
                depth--;
                if (depth == 0) {
                    return new Range(start, i);
                }
            }
        }
        return null;
    }

    private static List<Range> findAllPairs(String text, Bracket bracket) {
        List<Range> result = new ArrayList<>();
        int cursor = 0;
        while (cursor < text.length()) {
            Range range = findFirstPair(text, bracket, cursor);
            if (range == null) {
                break;
            }
            result.add(range);
            cursor = range.end + 1;
        }
        return result;
    }

    private static String removeNestedBrackets(String text) {
        List<Range> ranges = new ArrayList<>();
        for (Bracket bracket : BRACKETS) {
            ranges.addAll(findAllPairs(text, bracket));
        }
        return removeRanges(text, ranges);
    }

    private static String removeRanges(String text, List<Range> ranges) {
        if (ranges.isEmpty()) {
            return text;
        }

        ranges.sort(Comparator.comparingInt(range -> range.start));
        StringBuilder result = new StringBuilder(text.length());
        int cursor = 0;
        for (Range range : ranges) {
            if (range.end < cursor) {
                continue;
            }
            if (range.start > cursor) {
                result.append(text, cursor, range.start);
            }
            cursor = Math.max(cursor, range.end + 1);
        }
        if (cursor < text.length()) {
            result.append(text, cursor, text.length());
        }
        return result.toString();
    }

    private static String normalizeWhitespace(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean pendingSpace = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isWhitespace(ch)) {
                pendingSpace = result.length() > 0;
            } else {
                if (pendingSpace) {
                    result.append(' ');
                    pendingSpace = false;
                }
                result.append(ch);
            }
        }
        return result.toString();
    }

    private static boolean isWithinBracketSearchLimit(String title, int openingIndex) {
        List<String> words = splitWords(title.substring(0, openingIndex));
        int wordCount = 0;
        for (int i = 0; i < words.size(); i++) {
            if (isAiGeneratedGroup(words, i)) {
                i++;
            }
            wordCount++;
        }
        return wordCount < BRACKET_SEARCH_WORD_LIMIT;
    }

    private static String findFirstFeasibleWord(String title) {
        List<String> words = splitWords(title);
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            if (isAiGeneratedGroup(words, i)) {
                i++;
                continue;
            }
            if (!isIgnored(word)) {
                return word;
            }
        }
        return null;
    }

    private static List<String> splitWords(String title) {
        List<String> result = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= title.length(); i++) {
            boolean separator = i == title.length() || isWordSeparator(title.charAt(i));
            if (separator) {
                if (start >= 0) {
                    result.add(title.substring(start, i));
                    start = -1;
                }
            } else if (start < 0) {
                start = i;
            }
        }
        return result;
    }

    private static boolean isAiGeneratedGroup(List<String> words, int index) {
        return "AI".equalsIgnoreCase(words.get(index))
                && index + 1 < words.size()
                && "Generated".equalsIgnoreCase(words.get(index + 1));
    }

    private static boolean isWordSeparator(char ch) {
        if (Character.isWhitespace(ch)) {
            return true;
        }
        for (Bracket bracket : BRACKETS) {
            if (ch == bracket.open || ch == bracket.close) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIgnored(String value) {
        return "AI Generated".equalsIgnoreCase(value)
                || "Decensored".equalsIgnoreCase(value)
                || "Patreon".equalsIgnoreCase(value)
                || "Pixiv".equalsIgnoreCase(value)
                || "Fanbox".equalsIgnoreCase(value)
                || "Animated".equalsIgnoreCase(value)
                || "Artist".equalsIgnoreCase(value)
                || "Part".equalsIgnoreCase(value);
    }

    private static final class Bracket {
        final char open;
        final char close;

        Bracket(char open, char close) {
            this.open = open;
            this.close = close;
        }
    }

    private static final class Range {
        final int start;
        final int end;

        Range(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
}
