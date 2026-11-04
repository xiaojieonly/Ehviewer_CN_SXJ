package com.hippo.ehviewer.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TagSearchQueryHelperTest {

    @Test
    public void shouldUseTagSearch_detectsSearchSyntax() {
        assertEquals(true, TagSearchQueryHelper.shouldUseTagSearch("m:shotacon"));
        assertEquals(true, TagSearchQueryHelper.shouldUseTagSearch("m:\"shotacon$\""));
        assertEquals(true, TagSearchQueryHelper.shouldUseTagSearch("female:big breasts|male:sole male"));
        assertEquals(false, TagSearchQueryHelper.shouldUseTagSearch("male:shotacon"));
        assertEquals(false, TagSearchQueryHelper.shouldUseTagSearch("female:big breasts"));
    }

    @Test
    public void normalizeSpecifiedTagQuery_trimsOnly() {
        assertEquals("m:\"shotacon$\"",
                TagSearchQueryHelper.normalizeSpecifiedTagQuery("  m:\"shotacon$\"  "));
    }
}
