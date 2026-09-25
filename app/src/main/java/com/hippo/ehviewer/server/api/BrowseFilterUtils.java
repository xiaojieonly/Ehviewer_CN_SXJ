package com.hippo.ehviewer.server.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.dao.GalleryTags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class BrowseFilterUtils {

    /** The label name used for unlabeled items, matching the in-app default label string. */
    static final String DEFAULT_LABEL_NAME = "Default";

    private BrowseFilterUtils() {
    }

    @Nullable
    static String normalizeFilterValue(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @NonNull
    static List<String> parseTagFilters(@Nullable String rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return Collections.emptyList();
        }
        String[] split = rawTags.split(",");
        ArrayList<String> tags = new ArrayList<>(split.length);
        for (String tag : split) {
            if (tag == null) {
                continue;
            }
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                tags.add(trimmed);
            }
        }
        return tags;
    }

    static boolean matchesLabel(@NonNull List<String> labels, @NonNull String labelFilter) {
        // "Default" matches items that have no label (empty labels list),
        // matching the in-app download page behavior for unlabeled items.
        if (DEFAULT_LABEL_NAME.equalsIgnoreCase(labelFilter.trim())) {
            return labels.isEmpty();
        }
        for (String label : labels) {
            if (equalsIgnoreCase(label, labelFilter)) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesTags(@NonNull List<String> tags, @NonNull List<String> filters) {
        for (String filter : filters) {
            for (String tag : tags) {
                if (matchSingleTag(tag, filter)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean matchesSearch(@Nullable String title,
                                 @NonNull List<String> labels,
                                 @NonNull List<String> tags,
                                 @NonNull String searchFilter) {
        if (containsIgnoreCase(title, searchFilter)) {
            return true;
        }
        for (String label : labels) {
            if (containsIgnoreCase(label, searchFilter)) {
                return true;
            }
        }
        for (String tag : tags) {
            if (containsIgnoreCase(tag, searchFilter)) {
                return true;
            }
        }
        return false;
    }

    static boolean matchSingleTag(@Nullable String tag, @Nullable String searchTag) {
        if (tag == null || searchTag == null) {
            return false;
        }
        String normalizedTag = tag.trim().toLowerCase(Locale.ROOT);
        String normalizedSearchTag = searchTag.trim().toLowerCase(Locale.ROOT);
        if (normalizedTag.isEmpty() || normalizedSearchTag.isEmpty()) {
            return false;
        }

        int tagIndex = normalizedTag.indexOf(':');
        String tagNamespace = tagIndex >= 0 ? normalizedTag.substring(0, tagIndex) : null;
        String tagName = tagIndex >= 0 ? normalizedTag.substring(tagIndex + 1) : normalizedTag;

        int searchIndex = normalizedSearchTag.indexOf(':');
        String searchNamespace = searchIndex >= 0 ? normalizedSearchTag.substring(0, searchIndex) : null;
        String searchName = searchIndex >= 0 ? normalizedSearchTag.substring(searchIndex + 1) : normalizedSearchTag;

        if (searchNamespace != null && (tagNamespace == null || !tagNamespace.equals(searchNamespace))) {
            return false;
        }

        return tagName.equals(searchName) || tagName.contains(searchName);
    }

    @NonNull
    static List<String> extractTags(@Nullable GalleryTags galleryTags) {
        if (galleryTags == null) {
            return Collections.emptyList();
        }

        List<String> output = new ArrayList<>();
        addNamespacedTags(output, "artist", galleryTags.artist);
        addNamespacedTags(output, "rows", galleryTags.rows);
        addNamespacedTags(output, "cosplayer", galleryTags.cosplayer);
        addNamespacedTags(output, "character", galleryTags.character);
        addNamespacedTags(output, "female", galleryTags.female);
        addNamespacedTags(output, "group", galleryTags.group);
        addNamespacedTags(output, "language", galleryTags.language);
        addNamespacedTags(output, "male", galleryTags.male);
        addNamespacedTags(output, "misc", galleryTags.misc);
        addNamespacedTags(output, "mixed", galleryTags.mixed);
        addNamespacedTags(output, "other", galleryTags.other);
        addNamespacedTags(output, "parody", galleryTags.parody);
        addNamespacedTags(output, "reclass", galleryTags.reclass);
        return output;
    }

    private static void addNamespacedTags(@NonNull List<String> output,
                                          @NonNull String namespace,
                                          @Nullable String csvTags) {
        if (csvTags == null || csvTags.isEmpty()) {
            return;
        }
        String[] split = csvTags.split(",");
        for (String value : split) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                output.add(namespace + ":" + trimmed);
            }
        }
    }

    private static boolean equalsIgnoreCase(@Nullable String value, @Nullable String target) {
        if (value == null || target == null) {
            return false;
        }
        return value.trim().equalsIgnoreCase(target.trim());
    }

    private static boolean containsIgnoreCase(@Nullable String value, @Nullable String query) {
        if (value == null || query == null) {
            return false;
        }
        String normalizedValue = value.toLowerCase(Locale.ROOT);
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        return normalizedValue.contains(normalizedQuery);
    }
}
