package com.hippo.ehviewer.server.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.hippo.ehviewer.dao.GalleryTags;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BrowseFilterUtilsTest {

    @Test
    public void normalizeFilterValue_emptyBecomesNull() {
        assertNull(BrowseFilterUtils.normalizeFilterValue("   "));
        assertEquals("Work", BrowseFilterUtils.normalizeFilterValue(" Work "));
    }

    @Test
    public void parseTagFilters_trimsAndSkipsEmpty() {
        List<String> tags = BrowseFilterUtils.parseTagFilters(" manga, doujin , ,female:big ");
        assertEquals(Arrays.asList("manga", "doujin", "female:big"), tags);
    }

    @Test
    public void matchesLabel_caseInsensitive() {
        assertTrue(BrowseFilterUtils.matchesLabel(Arrays.asList("Work"), "work"));
        assertFalse(BrowseFilterUtils.matchesLabel(Arrays.asList("Work"), "home"));
    }

    @Test
    public void matchesLabel_defaultMatchesUnlabeled() {
        // "Default" should match items with empty labels (unlabeled)
        assertTrue(BrowseFilterUtils.matchesLabel(Collections.emptyList(), "Default"));
        assertTrue(BrowseFilterUtils.matchesLabel(Collections.emptyList(), "default"));
        // "Default" should NOT match items that have labels
        assertFalse(BrowseFilterUtils.matchesLabel(Arrays.asList("Work"), "Default"));
        assertFalse(BrowseFilterUtils.matchesLabel(Arrays.asList("Work"), "default"));
    }

    @Test
    public void matchesTags_orLogicAndNamespaceSupport() {
        List<String> tags = Arrays.asList("female:big_breasts", "language:english");
        assertTrue(BrowseFilterUtils.matchesTags(tags, Arrays.asList("manga", "big_breasts")));
        assertTrue(BrowseFilterUtils.matchesTags(tags, Arrays.asList("female:big")));
        assertFalse(BrowseFilterUtils.matchesTags(tags, Arrays.asList("male:big")));
    }

    @Test
    public void matchesSearch_checksTitleLabelsAndTags() {
        assertTrue(BrowseFilterUtils.matchesSearch(
                "Demon Hunter",
                Arrays.asList("Work"),
                Arrays.asList("genre:action"),
                "demon"));

        assertTrue(BrowseFilterUtils.matchesSearch(
                "Other title",
                Arrays.asList("Favorites"),
                Arrays.asList("genre:action"),
                "acti"));

        assertFalse(BrowseFilterUtils.matchesSearch(
                "Other title",
                Arrays.asList("Favorites"),
                Arrays.asList("genre:action"),
                "romance"));
    }

    @Test
    public void extractTags_collectsAllNamespaces() {
        GalleryTags tags = new GalleryTags(1L);
        tags.artist = "foo, bar";
        tags.language = "english";
        tags.female = "big_breasts";

        List<String> result = BrowseFilterUtils.extractTags(tags);
        assertTrue(result.contains("artist:foo"));
        assertTrue(result.contains("artist:bar"));
        assertTrue(result.contains("language:english"));
        assertTrue(result.contains("female:big_breasts"));
    }
}
