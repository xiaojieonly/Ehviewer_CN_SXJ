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

package com.hippo.anotherviewer.webui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Exercises {@link WebUiConfig#validate}: the host empty/illegal-character
 * checks and the port range check that the configure and pair dialogs surface
 * before any connection attempt is made (L-2).
 */
public class WebUiConfigTest {

    @Test
    public void testValidHostAndPort() {
        assertNull(WebUiConfig.validate("192.168.1.10", 8080));
        assertNull(WebUiConfig.validate("my-server.local", WebUiConfig.MAX_PORT));
        assertNull(WebUiConfig.validate("http://192.168.1.10", 8080));
        assertNull(WebUiConfig.validate("192.168.1.10:8080", 8080));
    }

    @Test
    public void testEmptyHostRejected() {
        assertNotNull(WebUiConfig.validate("", 8080));
        assertNotNull(WebUiConfig.validate("   ", 8080));
        assertNotNull(WebUiConfig.validate("http://", 8080));
    }

    @Test
    public void testIllegalCharactersRejected() {
        assertNotNull(WebUiConfig.validate("192.168.1.10 !", 8080));
        assertNotNull(WebUiConfig.validate("ho st", 8080));
        assertNotNull(WebUiConfig.validate("host;drop", 8080));
    }

    @Test
    public void testPortRange() {
        assertNull(WebUiConfig.validate("192.168.1.10", 1));
        assertNull(WebUiConfig.validate("192.168.1.10", WebUiConfig.MAX_PORT));
        assertNotNull(WebUiConfig.validate("192.168.1.10", 0));
        assertNotNull(WebUiConfig.validate("192.168.1.10", -1));
        assertNotNull(WebUiConfig.validate("192.168.1.10", WebUiConfig.MAX_PORT + 1));
    }

    @Test
    public void testValidatedValuesProduceUsableBaseUrl() {
        assertNull(WebUiConfig.validate("192.168.1.10", 8080));
        WebUiConfig config = new WebUiConfig("http", "192.168.1.10", 8080, "", "");
        assertEquals("http://192.168.1.10:8080", config.baseUrl());
    }
}
