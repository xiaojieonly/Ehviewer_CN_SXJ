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

package com.hippo.ehviewer.spider;

import static com.hippo.ehviewer.spider.SpiderDen.getGalleryDownloadDir;
import static com.hippo.ehviewer.spider.SpiderQueen.SPIDER_INFO_BACKUP_FILENAME;
import static com.hippo.ehviewer.spider.SpiderQueen.SPIDER_INFO_FILENAME;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.client.data.GalleryDetail;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.data.PreviewSet;
import com.hippo.ehviewer.client.exception.ParseException;
import com.hippo.ehviewer.client.parser.GalleryDetailParser;
import com.hippo.ehviewer.client.parser.GalleryPageUrlParser;
import com.hippo.streampipe.OutputStreamPipe;
import com.hippo.unifile.UniFile;
import com.hippo.util.ExceptionUtils;
import com.hippo.yorozuya.IOUtils;
import com.hippo.yorozuya.NumberUtils;
import com.microsoft.appcenter.crashes.Crashes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

public class SpiderInfo {

    private static final String TAG = SpiderInfo.class.getSimpleName();

    private static final String VERSION_STR = "VERSION";
    private static final int VERSION = 2;

    static final String TOKEN_FAILED = "failed";

    public int startPage = 0;
    public long gid = -1;
    public String token = null;
    public int pages = -1;
    public int previewPages = -1;
    public int previewPerPage = -1;
    public SparseArray<String> pTokenMap = null;

    public static SpiderInfo read(@Nullable UniFile file) {
        if (file == null) {
            return null;
        }

        InputStream is = null;
        try {
            is = file.openInputStream();
            return read(is);
        } catch (IOException e) {
            return null;
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    private static int getStartPage(String str) {
        if (null == str) {
            return 0;
        }

        int startPage = 0;
        for (int i = 0, n = str.length(); i < n; i++) {
            startPage *= 16;
            char ch = str.charAt(i);
            if (ch >= '0' && ch <= '9') {
                startPage += ch - '0';
            } else if (ch >= 'a' && ch <= 'f') {
                startPage += ch - 'a' + 10;
            }
        }

        return Math.max(startPage, 0);
    }

    private static int getVersion(String str) {
        if (null == str) {
            return -1;
        }
        if (str.startsWith(VERSION_STR)) {
            return NumberUtils.parseIntSafely(str.substring(VERSION_STR.length()), -1);
        } else {
            return 1;
        }
    }

    @Nullable
    @SuppressWarnings("InfiniteLoopStatement")
    public static SpiderInfo read(@Nullable InputStream is) {
        if (null == is) {
            return null;
        }

        SpiderInfo spiderInfo = null;
        try {
            spiderInfo = new SpiderInfo();
            // Get version
            String line = IOUtils.readAsciiLine(is);
            int version = getVersion(line);
            if (version == VERSION) {
                // Read next line
                line = IOUtils.readAsciiLine(is);
            } else if (version == 1) {
                // pass
            } else {
                // Invalid version
                return null;
            }
            // Start page
            spiderInfo.startPage = getStartPage(line);
            // Gid
            spiderInfo.gid = Long.parseLong(IOUtils.readAsciiLine(is));
            // Token
            spiderInfo.token = IOUtils.readAsciiLine(is);
            // Deprecated, mode, skip it
            IOUtils.readAsciiLine(is);
            // Preview pages
            spiderInfo.previewPages = Integer.parseInt(IOUtils.readAsciiLine(is));
            // Preview pre page
            line = IOUtils.readAsciiLine(is);
            if (version == 1) {
                // Skip it
            } else {
                spiderInfo.previewPerPage = Integer.parseInt(line);
            }
            // Pages
            spiderInfo.pages = Integer.parseInt(IOUtils.readAsciiLine(is));
            // Check pages
            if (spiderInfo.pages <= 0) {
                return null;
            }
            // PToken
            spiderInfo.pTokenMap = new SparseArray<>(spiderInfo.pages);
            while (true) { // EOFException will raise
                line = IOUtils.readAsciiLine(is);
                int pos = line.indexOf(" ");
                if (pos > 0) {
                    int index = Integer.parseInt(line.substring(0, pos));
                    String pToken = line.substring(pos + 1);
                    if (!TextUtils.isEmpty(pToken)) {
                        spiderInfo.pTokenMap.put(index, pToken);
                    }
                } else {
                    Log.e(TAG, "Can't parse index and pToken, index = " + pos);
                }
            }
        } catch (IOException | NumberFormatException e) {
            // Ignore
        }

        if (spiderInfo == null || spiderInfo.gid == -1 || spiderInfo.token == null ||
                spiderInfo.pages == -1 || spiderInfo.pTokenMap == null) {
            return null;
        } else {
            return spiderInfo;
        }
    }

    public void write(@NonNull OutputStream os) {
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(os);
            writer.write(VERSION_STR);
            writer.write(Integer.toString(VERSION));
            writer.write("\n");
            writer.write(String.format("%08x", Math.max(startPage, 0))); // Avoid negative
            writer.write("\n");
            writer.write(Long.toString(gid));
            writer.write("\n");
            writer.write(token);
            writer.write("\n");
            writer.write("1");
            writer.write("\n");
            writer.write(Integer.toString(previewPages));
            writer.write("\n");
            writer.write(Integer.toString(previewPerPage));
            writer.write("\n");
            writer.write(Integer.toString(pages));
            writer.write("\n");
            for (int i = 0; i < pTokenMap.size(); i++) {
                int key = pTokenMap.keyAt(i);
                String value = pTokenMap.valueAt(i);
                if (TOKEN_FAILED.equals(value) || TextUtils.isEmpty(value)) {
                    continue;
                }
                writer.write(Integer.toString(key));
                writer.write(" ");
                writer.write(value);
                writer.write("\n");
            }
            writer.flush();
        } catch (IOException e) {
            // Ignore
        } finally {
            IOUtils.closeQuietly(writer);
            IOUtils.closeQuietly(os);
        }
    }

    public void updateSpiderInfo(SpiderInfo newInfo){
        this.pages = newInfo.pages;
        this.gid = newInfo.gid;
        this.token = newInfo.token;
        this.pTokenMap = newInfo.pTokenMap;
        this.previewPerPage = newInfo.previewPerPage;this.previewPages = newInfo.previewPages;
    }

    public synchronized void writeNewSpiderInfoToLocal(@NonNull SpiderDen spiderDen, Context context) {
        UniFile downloadDir = spiderDen.getDownloadDir();
        if (downloadDir != null) {
            UniFile file = downloadDir.createFile(SPIDER_INFO_FILENAME);
            try {
                write(file.openOutputStream());
            } catch (Throwable e) {
                ExceptionUtils.throwIfFatal(e);
                // Ignore
            }
            // Read from cache
            OutputStreamPipe pipe = EhApplication.getSpiderInfoCache(context).getOutputStreamPipe(Long.toString(gid));
            try {
                pipe.obtain();
                write(pipe.open());
            } catch (IOException e) {
                // Ignore
            } finally {
                pipe.close();
                pipe.release();
            }
        }
    }

    public static SpiderInfo getSpiderInfo(GalleryInfo info) {
        SpiderInfo spiderInfo;
        UniFile mDownloadDir = getGalleryDownloadDir(info);
        if (mDownloadDir != null && mDownloadDir.isDirectory()) {
            UniFile file = mDownloadDir.findFile(SPIDER_INFO_FILENAME);
            spiderInfo = SpiderInfo.read(file);
            if (spiderInfo != null && spiderInfo.gid == info.gid &&
                    spiderInfo.token.equals(info.token)) {
                return spiderInfo;
            }
        }
        return null;
    }

    public static SpiderInfo createBackupSpiderInfo(GalleryInfo info) {
        UniFile mDownloadDir = getGalleryDownloadDir(info);
        if (mDownloadDir != null && mDownloadDir.isDirectory()) {
            UniFile file = mDownloadDir.findFile(SPIDER_INFO_FILENAME);
            if (file==null){
                return null;
            }
            UniFile backupFile = mDownloadDir.findFile(SPIDER_INFO_BACKUP_FILENAME);
            if (backupFile!=null){
                backupFile.delete();
            }
            backupFile = mDownloadDir.createFile(SPIDER_INFO_BACKUP_FILENAME);
            InputStream is;
            OutputStream os = null;

            try {
                is = file.openInputStream();
                os = backupFile.openOutputStream();

                byte[] bytes = new byte[1024];
                int l;
                while((l=is.read(bytes))>0){
                    os.write(bytes,0,l);
                }
                os.flush();
                IOUtils.closeQuietly(is);
                SpiderInfo spiderInfo;
                spiderInfo = SpiderInfo.read(file);
                if (spiderInfo != null && spiderInfo.gid == info.gid &&
                        spiderInfo.token.equals(info.token)) {
                    return spiderInfo;
                }
                return null;
            } catch (IOException e) {
                return null;
            } finally {
                IOUtils.closeQuietly(os);
            }

        }
        return null;
    }

    public static SpiderInfo getSpiderInfo(GalleryDetail info) {
        try {
            SpiderInfo spiderInfo = new SpiderInfo();
            spiderInfo.gid = info.gid;
            spiderInfo.token = info.token;
//            spiderInfo.pages = GalleryDetailParser.parsePages(info.body);
            spiderInfo.pages = info.SpiderInfoPages;
            spiderInfo.pTokenMap = new SparseArray<>(spiderInfo.pages);
            readPreviews(info, 0, spiderInfo);
            return spiderInfo;
        } catch (ParseException e) {
            Crashes.trackError(e);
        }
        return null;
    }

    private static void readPreviews(GalleryDetail info, int index, SpiderInfo spiderInfo) throws ParseException {
        spiderInfo.pages = info.SpiderInfoPages;
        spiderInfo.previewPages = info.SpiderInfoPreviewPages;
        PreviewSet previewSet = info.SpiderInfoPreviewSet;

        if (previewSet.size() > 0) {
            if (index == 0) {
                spiderInfo.previewPerPage = previewSet.size();
            } else {
                spiderInfo.previewPerPage = previewSet.getPosition(0) / index;
            }
        }

        for (int i = 0, n = previewSet.size(); i < n; i++) {
            GalleryPageUrlParser.Result result = GalleryPageUrlParser.parse(previewSet.getPageUrlAt(i));
            if (result != null) {
                spiderInfo.pTokenMap.put(result.page, result.pToken);
            }
        }
    }

}
