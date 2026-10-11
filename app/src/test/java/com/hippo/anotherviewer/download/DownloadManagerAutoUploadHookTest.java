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

package com.hippo.anotherviewer.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

import com.hippo.anotherviewer.AppConfig;
import com.hippo.anotherviewer.Settings;
import com.hippo.anotherviewer.SiteDB;
import com.hippo.anotherviewer.client.data.GalleryInfo;
import com.hippo.anotherviewer.dao.DownloadInfo;
import com.hippo.anotherviewer.upload.AutoUploadPrefs;
import com.hippo.anotherviewer.upload.DownloadUploadService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.Collections;

/**
 * WP7：DownloadManager 自动上传钩子（{@code maybeAutoUploadToWebUi}）的门控测试。
 *
 * <p>Robolectric {@code @Config(manifest = Config.NONE)} 下 application 是普通
 * Application，{@code SiteApplication.getDownloadManager} 的 cast 会 CCE，所以
 * 直接 {@code new DownloadManager(context)}（构造只依赖 SiteDB）。钩子经公共
 * 入口 {@code addDownload(gi, label, state)} 触发，用 ShadowApplication 的
 * started-service 队列断言「发/不发 + 携带的 gid」。TYPE_ON_FINISH 钩子与
 * addDownload 钩子共用同一个私有方法，addDownload 侧已足够钉死门控矩阵；
 * SpiderQueen 全流程不在本测试射程内。
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class DownloadManagerAutoUploadHookTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.application;
        AppConfig.initialize(context);
        Settings.initialize(context);
        SiteDB.initialize(context);
        // webui_settings 整文件清空（host / 自动开关 / pushed 不残留）；
        // 需要「已配置」的用例各自 seed。
        context.getSharedPreferences("webui_settings", Context.MODE_PRIVATE)
                .edit().clear().commit();
        while (Shadows.shadowOf((Application) context).getNextStartedService() != null) {
            // 排空 started-service 残留，隔离用例
        }
    }

    private static GalleryInfo gallery(long gid) {
        GalleryInfo info = new GalleryInfo();
        info.gid = gid;
        info.token = "tok" + gid;
        info.title = "Title " + gid;
        info.thumb = "";
        info.uploader = "uploader";
        return info;
    }

    /** isConfigured() = host 非空；token 可空。 */
    private void seedWebUiHost() {
        context.getSharedPreferences("webui_settings", Context.MODE_PRIVATE)
                .edit().putString("host", "127.0.0.1").commit();
    }

    @Test
    public void addDownloadFinishWithConfiguredServerStartsPushServiceWithGid() {
        seedWebUiHost();
        DownloadManager dm = new DownloadManager(context);
        // label=null → 默认列表，addDownload 无需预登记 label。
        dm.addDownload(gallery(601L), null, DownloadInfo.STATE_FINISH);

        Intent intent = Shadows.shadowOf((Application) context).getNextStartedService();
        assertNotNull("已配置 + 默认开：FINISH 落库即自动精推", intent);
        assertEquals(DownloadUploadService.ACTION_PUSH_GIDS, intent.getAction());
        assertEquals(DownloadUploadService.class.getName(),
                intent.getComponent().getClassName());
        assertEquals(Collections.singletonList(601L),
                intent.getSerializableExtra(DownloadUploadService.EXTRA_GIDS));
    }

    @Test
    public void addDownloadFinishWithSwitchOffDoesNotStartService() {
        seedWebUiHost();
        AutoUploadPrefs.setEnabled(context, false);
        DownloadManager dm = new DownloadManager(context);
        dm.addDownload(gallery(602L), null, DownloadInfo.STATE_FINISH);
        assertNull("开关关：不发", Shadows.shadowOf((Application) context).getNextStartedService());
    }

    @Test
    public void addDownloadFinishWithoutConfiguredServerDoesNotStartService() {
        // 不 seed host：钩子第一道门（isConfigured）短路。
        DownloadManager dm = new DownloadManager(context);
        dm.addDownload(gallery(603L), null, DownloadInfo.STATE_FINISH);
        assertNull("未配置：不发", Shadows.shadowOf((Application) context).getNextStartedService());
    }

    @Test
    public void addDownloadNoneNeverStartsService() {
        seedWebUiHost();
        DownloadManager dm = new DownloadManager(context);
        dm.addDownload(gallery(604L), null, DownloadInfo.STATE_NONE);
        assertNull("非 FINISH 不触发", Shadows.shadowOf((Application) context).getNextStartedService());
    }
}
