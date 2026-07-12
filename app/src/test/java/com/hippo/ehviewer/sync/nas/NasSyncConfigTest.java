package com.hippo.ehviewer.sync.nas;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NasSyncConfigTest {
    @Test
    public void normalizesUncStyleDirectory() {
        NasSyncConfig config = new NasSyncConfig(" 192.0.2.10 ", " Share ",
                "\\Ehviewer\\", "", " user ", "secret".toCharArray());

        assertEquals("192.0.2.10", config.host);
        assertEquals("Share", config.share);
        assertEquals("Ehviewer", config.remoteDirectory);
        assertEquals("user", config.username);
    }

    @Test
    public void clearsPasswordAfterUse() {
        NasSyncConfig config = new NasSyncConfig("host", "share", "dir", "", "",
                "secret".toCharArray());

        config.clearPassword();

        assertArrayEquals(new char[]{'\0', '\0', '\0', '\0', '\0', '\0'}, config.password);
    }
}
