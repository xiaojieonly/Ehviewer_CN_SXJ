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

package com.hippo.unifile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Test;

public class SmbUriTest {

    @Test
    public void parseDefaultPortAndPath() {
        SmbUri uri = SmbUri.parse(Uri.parse("smb://nas.local/photos/archive"));
        assertEquals("nas.local", uri.getHost());
        assertEquals(SmbUri.DEFAULT_PORT, uri.getPort());
        assertEquals("photos", uri.getShare());
        assertEquals("archive", uri.getPath());
        assertEquals("nas.local", uri.toUri().getEncodedAuthority());
    }

    @Test
    public void parseCustomPort() {
        SmbUri uri = SmbUri.parse(Uri.parse("smb://192.168.1.10:4450/share/path/to/folder"));
        assertEquals("192.168.1.10", uri.getHost());
        assertEquals(4450, uri.getPort());
        assertEquals("share", uri.getShare());
        assertEquals("path/to/folder", uri.getPath());
        assertEquals("192.168.1.10:4450", uri.toUri().getEncodedAuthority());
    }

    @Test
    public void rejectCredentialsInUri() {
        assertNull(SmbUri.parse(Uri.parse("smb://user:pass@nas.local/share")));
    }

    @Test
    public void rejectParentSegments() {
        assertNull(SmbUri.parse(Uri.parse("smb://nas.local/share/folder/../other")));
    }

    @Test
    public void normalizeBackslashesAndTrailingSlashes() {
        SmbUri uri = SmbUri.create("nas.local", SmbUri.DEFAULT_PORT, "share", "\\folder\\sub/");
        assertEquals("folder/sub", uri.getPath());
        assertTrue(uri.toUri().toString().endsWith("/folder/sub"));
    }
}
