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

import androidx.annotation.Nullable;

import com.hippo.ehviewer.client.data.GalleryTagGroup;

/**
 * Resolves the preferred namespaced tag for the gallery Artist action.
 */
public final class GalleryTagSearchKeywordResolver {

    private static final String[] PREFERRED_NAMESPACES = {"artist", "cosplayer"};

    private GalleryTagSearchKeywordResolver() {
    }

    @Nullable
    public static String resolveArtistActionTag(GalleryTagGroup[] tagGroups) {
        if (tagGroups == null) {
            return null;
        }

        for (String namespace : PREFERRED_NAMESPACES) {
            for (GalleryTagGroup tagGroup : tagGroups) {
                if (tagGroup != null
                        && namespace.equals(tagGroup.groupName)
                        && tagGroup.size() > 0) {
                    return namespace + ":" + tagGroup.getTagAt(0);
                }
            }
        }
        return null;
    }
}
