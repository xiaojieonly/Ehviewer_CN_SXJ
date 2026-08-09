/*
 * Copyright 2016 Hippo Seven
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

package com.hippo.anotherviewer.gallery;

import static org.junit.Assert.assertEquals;

import com.hippo.anotherviewer.GetText;
import com.hippo.anotherviewer.dao.DownloadInfo;
import com.hippo.anotherviewer.download.DownloadManager;
import com.hippo.unifile.UniFile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class DownloadGalleryProviderTest {

    private File tempDir;
    private DownloadInfo galleryInfo;

    @Before
    public void setUp() throws IOException {
        GetText.initialize(RuntimeEnvironment.application);
        tempDir = File.createTempFile("download_gallery_provider_test", "");
        tempDir.delete();
        if (!tempDir.mkdirs()) {
            throw new IOException("Failed to create temp dir " + tempDir);
        }
        galleryInfo = new DownloadInfo();
        galleryInfo.gid = 12345L;
        galleryInfo.token = "tok123";
        galleryInfo.pages = 2;
    }

    @After
    public void tearDown() {
        deleteRecursively(tempDir);
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private void writeSpiderInfoFile(long gid, String token, int startPage, int pages) throws IOException {
        File file = new File(tempDir, DownloadManager.DOWNLOAD_INFO_FILENAME);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("VERSION2");
            writer.write("\n");
            writer.write(String.format(Locale.US, "%08x", startPage));
            writer.write("\n");
            writer.write(Long.toString(gid));
            writer.write("\n");
            writer.write(token);
            writer.write("\n");
            writer.write("1");
            writer.write("\n");
            writer.write("0");
            writer.write("\n");
            writer.write("0");
            writer.write("\n");
            writer.write(Integer.toString(pages));
            writer.write("\n");
            for (int i = 0; i < pages; i++) {
                writer.write(i + " ptok" + i);
                writer.write("\n");
            }
        }
    }

    private void writeImageFiles(int count) throws IOException {
        for (int i = 0; i < count; i++) {
            File file = new File(tempDir, String.format(Locale.US, "%08d.jpg", i + 1));
            if (!file.createNewFile()) {
                throw new IOException("Failed to create " + file);
            }
        }
    }

    private DownloadGalleryProvider newProvider() {
        return new DownloadGalleryProvider(RuntimeEnvironment.application, galleryInfo,
                UniFile.fromFile(tempDir));
    }

    @Test
    public void testSizeFromSpiderInfo() throws IOException {
        writeSpiderInfoFile(galleryInfo.gid, galleryInfo.token, 0, 2);
        writeImageFiles(2);

        DownloadGalleryProvider provider = newProvider();
        provider.start();
        assertEquals(2, provider.size());
        provider.stop();
    }

    @Test
    public void testGetStartPage() throws IOException {
        writeSpiderInfoFile(galleryInfo.gid, galleryInfo.token, 3, 2);

        DownloadGalleryProvider provider = newProvider();
        provider.start();
        assertEquals(3, provider.getStartPage());
        provider.stop();
    }

    @Test
    public void testPutStartPage() throws IOException {
        writeSpiderInfoFile(galleryInfo.gid, galleryInfo.token, 0, 2);
        writeImageFiles(2);

        DownloadGalleryProvider provider = newProvider();
        provider.start();
        provider.putStartPage(5);

        DownloadGalleryProvider fresh = newProvider();
        fresh.start();
        assertEquals(5, fresh.getStartPage());
        fresh.stop();
        provider.stop();
    }

    @Test
    public void testMissingSpiderInfoIsError() {
        DownloadGalleryProvider provider = newProvider();
        provider.start();
        assertEquals(DownloadGalleryProvider.STATE_ERROR, provider.size());
        provider.stop();
    }

    @Test
    public void testGetImageFilename() {
        DownloadGalleryProvider provider = newProvider();
        assertEquals("12345-tok123-00000001", provider.getImageFilename(0));
        assertEquals("12345-tok123-00000008", provider.getImageFilename(7));
    }
}
