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

package com.hippo.anotherviewer.ui.scene;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.hippo.anotherviewer.R;
import com.hippo.anotherviewer.Settings;
import com.hippo.anotherviewer.ui.MainActivity;
import com.hippo.anotherviewer.webui.WebUiApiClient;
import com.hippo.anotherviewer.webui.WebUiConfig;
import com.hippo.anotherviewer.webui.WebUiSettings;
import com.hippo.anotherviewer.webui.WebUiSyncModels;
import com.hippo.lib.yorozuya.ViewUtils;

/**
 * First-launch step that asks for the WebUI companion server address. Keeps
 * the form deliberately minimal (host + port only); completing the pairing
 * happens in the settings screen ({@code Settings → Server Sync}), so this
 * scene only persists the connection and points the user there. Skippable:
 * once skipped it never appears again.
 */
public final class WebUiSetupScene extends SolidScene implements View.OnClickListener {

    /** Preference key marking the setup step as dismissed. */
    public static final String KEY_SKIP = "webui_setup_skipped";

    @Nullable
    private EditText mHost;
    @Nullable
    private EditText mPort;

    @Override
    public boolean needShowLeftDrawer() {
        return false;
    }

    @Nullable
    @Override
    public View onCreateView2(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.scene_webui_setup, container, false);

        mHost = (EditText) ViewUtils.$$(view, R.id.webui_host);
        mPort = (EditText) ViewUtils.$$(view, R.id.webui_port);
        View save = ViewUtils.$$(view, R.id.webui_save);
        View skip = ViewUtils.$$(view, R.id.webui_skip);
        save.setOnClickListener(this);
        skip.setOnClickListener(this);

        Context context = getEHContext();
        if (context != null) {
            WebUiConfig config = new WebUiSettings(context).loadConfig();
            if (config != null) {
                mHost.setText(config.getHost());
                mPort.setText(String.valueOf(config.getPort()));
            } else {
                mPort.setText(String.valueOf(WebUiConfig.DEFAULT_PORT));
            }
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        mHost = null;
        mPort = null;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.webui_save) {
            onSave();
        } else if (id == R.id.webui_skip) {
            Settings.putBoolean(KEY_SKIP, true);
            redirectTo();
        }
    }

    private void onSave() {
        Context context = getEHContext();
        if (context == null || mHost == null || mPort == null) {
            return;
        }

        String hostValue = mHost.getText().toString().trim();
        String portValue = mPort.getText().toString().trim();
        int portNumber;
        try {
            portNumber = portValue.isEmpty() ? WebUiConfig.DEFAULT_PORT : Integer.parseInt(portValue);
        } catch (NumberFormatException e) {
            Toast.makeText(context, R.string.settings_webui_invalid_config, Toast.LENGTH_SHORT).show();
            return;
        }
        String validation = WebUiConfig.validate(hostValue, portNumber);
        if (validation != null) {
            Toast.makeText(context, validation, Toast.LENGTH_SHORT).show();
            return;
        }

        final Context ctx = context;
        final WebUiConfig config = new WebUiConfig(WebUiConfig.PROTOCOL_HTTP, hostValue, portNumber, "", "");
        new WebUiSettings(context).saveConfig(config);
        Toast.makeText(context, R.string.settings_webui_setup_saved, Toast.LENGTH_LONG).show();
        redirectTo();
        // Background auto-pair: on servers with auto-pairing enabled this
        // finishes the connection so no code entry is needed. setupKey 恒为
        // null：App 没有 setup-key 输入入口，配置了 security.setup_key 的
        // 服务器会以 409 拒绝。失败不再静默（老服务器 404、自动配对关闭 400、
        // 需要 setup-key 409、或网络不可达）——保留上面已保存的连接配置
        // （地址信息有用），提示用户到 设置 → 服务器同步 用配对码完成配对。
        Thread thread = new Thread(() -> {
            try {
                WebUiSettings settings = new WebUiSettings(ctx);
                WebUiSyncModels.PairCompleteResponse paired = WebUiApiClient.registerDevice(
                        config, settings.deviceId(), android.os.Build.MODEL, "android", null);
                if (paired.success && !TextUtils.isEmpty(paired.token)) {
                    settings.saveConfig(new WebUiConfig(WebUiConfig.PROTOCOL_HTTP, hostValue, portNumber,
                            paired.username, paired.token));
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(ctx, R.string.settings_webui_pair_done, Toast.LENGTH_LONG).show());
                } else {
                    // 服务器拒绝自动配对：不静默，提示用户完成配对。
                    String detail = TextUtils.isEmpty(paired.message) ? "自动配对被拒绝" : paired.message;
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(ctx, ctx.getString(R.string.settings_webui_pair_failed, detail),
                                    Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                // 网络不可达同样不静默：保留已保存配置，提示用户完成配对。
                String detail = TextUtils.isEmpty(e.getMessage())
                        ? e.getClass().getSimpleName() : e.getMessage();
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(ctx, ctx.getString(R.string.settings_webui_pair_failed, detail),
                                Toast.LENGTH_LONG).show());
            }
        }, "webui-auto-pair");
        thread.setDaemon(true);
        thread.start();
    }

    private void redirectTo() {
        MainActivity activity = getActivity2();
        if (activity != null) {
            // Resume the gate chain at the step right after this one: the
            // sign-in check lives in the ANALYTICS case of startSceneForCheckStep.
            startSceneForCheckStep(CHECK_STEP_ANALYTICS, getArguments());
        }
        finish();
    }
}
