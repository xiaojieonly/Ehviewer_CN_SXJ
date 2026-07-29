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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.hippo.ehviewer.client.data.GalleryTagGroup;

import org.junit.Test;

public class GalleryTagSearchKeywordResolverTest {

    @Test
    public void prefersArtistEvenWhenCosplayerAppearsFirst() {
        GalleryTagGroup cosplayer = group("cosplayer", "cosplayer-name");
        GalleryTagGroup artist = group("artist", "artist-name");

        assertEquals("artist:artist-name",
                GalleryTagSearchKeywordResolver.resolveArtistActionTag(
                        new GalleryTagGroup[]{cosplayer, artist}));
    }

    @Test
    public void fallsBackToCosplayerWhenArtistIsAbsent() {
        GalleryTagGroup cosplayer = group("cosplayer", "cosplayer-name");

        assertEquals("cosplayer:cosplayer-name",
                GalleryTagSearchKeywordResolver.resolveArtistActionTag(
                        new GalleryTagGroup[]{group("female", "tag"), cosplayer}));
    }

    @Test
    public void fallsBackToCosplayerWhenArtistGroupIsEmpty() {
        GalleryTagGroup artist = group("artist");
        GalleryTagGroup cosplayer = group("cosplayer", "cosplayer-name");

        assertEquals("cosplayer:cosplayer-name",
                GalleryTagSearchKeywordResolver.resolveArtistActionTag(
                        new GalleryTagGroup[]{artist, cosplayer}));
    }

    @Test
    public void returnsNullWhenNeitherPreferredTagExists() {
        assertNull(GalleryTagSearchKeywordResolver.resolveArtistActionTag(
                new GalleryTagGroup[]{group("female", "tag")}));
        assertNull(GalleryTagSearchKeywordResolver.resolveArtistActionTag(null));
    }

    @Test
    public void usesFirstTagInPreferredNamespace() {
        GalleryTagGroup artist = group("artist", "first", "second");

        assertEquals("artist:first",
                GalleryTagSearchKeywordResolver.resolveArtistActionTag(
                        new GalleryTagGroup[]{artist}));
    }

    private static GalleryTagGroup group(String namespace, String... tags) {
        GalleryTagGroup group = new GalleryTagGroup();
        group.groupName = namespace;
        for (String tag : tags) {
            group.addTag(tag);
        }
        return group;
    }
}
