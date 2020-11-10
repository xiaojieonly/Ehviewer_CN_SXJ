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

package com.hippo.ehviewer.preference;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.textfield.TextInputLayout;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhProxySelector;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.network.InetValidator;
import com.hippo.preference.DialogPreference;
import com.hippo.yorozuya.MathUtils;
import com.hippo.yorozuya.ViewUtils;

public class ProxyPreference extends DialogPreference implements View.OnClickListener {

    private Spinner mType;
    private TextInputLayout mIpInputLayout;
    private EditText mIp;
    private TextInputLayout mPortInputLayout;
    private EditText mPort;

    public ProxyPreference(Context context) {
        super(context);
        init();
    }

    public ProxyPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ProxyPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setDialogLayoutResource(R.layout.preference_dialog_proxy);
        updateSummary(Settings.getProxyType(), Settings.getProxyIp(), Settings.getProxyPort());
    }

    private String getProxyTypeText(Context context, int type) {
        String[] array = context.getResources().getStringArray(R.array.proxy_types);
        return array[MathUtils.clamp(type, 0, array.length - 1)];
    }

    private void updateSummary(int type, String ip, int port) {
        if ((type == EhProxySelector.TYPE_HTTP || type == EhProxySelector.TYPE_SOCKS)
                && (TextUtils.isEmpty(ip) || !InetValidator.isValidInetPort(port)) ) {
            type = EhProxySelector.TYPE_SYSTEM;
        }

        if (type == EhProxySelector.TYPE_HTTP || type == EhProxySelector.TYPE_SOCKS) {
            Context context = getContext();
            setSummary(context.getString(R.string.settings_advanced_proxy_summary_1,
                getProxyTypeText(context, type),
                ip,
                port));
        } else {
            Context context = getContext();
            setSummary(context.getString(R.string.settings_advanced_proxy_summary_2,
                getProxyTypeText(context, type)));
        }
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        builder.setPositiveButton(android.R.string.ok, null);
    }

    @Override
    @SuppressLint("SetTextI18n")
    protected void onDialogCreated(AlertDialog dialog) {
        super.onDialogCreated(dialog);

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(this);

        mType = (Spinner) ViewUtils.$$(dialog, R.id.type);
        mIpInputLayout = (TextInputLayout) ViewUtils.$$(dialog, R.id.ip_input_layout);
        mIp = (EditText) ViewUtils.$$(dialog, R.id.ip);
        mPortInputLayout = (TextInputLayout) ViewUtils.$$(dialog, R.id.port_input_layout);
        mPort = (EditText) ViewUtils.$$(dialog, R.id.port);

        int type = Settings.getProxyType();
        String[] array = getContext().getResources().getStringArray(R.array.proxy_types);
        mType.setSelection(MathUtils.clamp(type, 0, array.length));

        mIp.setText(Settings.getProxyIp());

        String portString;
        int port = Settings.getProxyPort();
        if (!InetValidator.isValidInetPort(port)) {
            portString = null;
        } else {
            portString = Integer.toString(port);
        }
        mPort.setText(portString);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        mType = null;
        mIpInputLayout = null;
        mIp = null;
        mPortInputLayout = null;
        mPort = null;
    }

    @Override
    public void onClick(View v) {
        Dialog dialog = getDialog();
        Context context = getContext();
        if (null == dialog || null == context || null == mType ||
                null == mIpInputLayout || null == mIp ||
                null == mPortInputLayout || null == mPort) {
            return;
        }

        int type = mType.getSelectedItemPosition();

        String ip = mIp.getText().toString().trim();
        if (ip.isEmpty()) {
            if (type == EhProxySelector.TYPE_HTTP || type == EhProxySelector.TYPE_SOCKS) {
                mIpInputLayout.setError(context.getString(R.string.text_is_empty));
                return;
            }
        }
        mIpInputLayout.setError(null);

        int port;
        String portString = mPort.getText().toString().trim();
        if (portString.isEmpty()) {
            if (type == EhProxySelector.TYPE_HTTP || type == EhProxySelector.TYPE_SOCKS) {
                mPortInputLayout.setError(context.getString(R.string.text_is_empty));
                return;
            } else {
                port = -1;
            }
        } else {
            try {
                port = Integer.parseInt(portString);
            } catch (NumberFormatException e) {
                port = -1;
            }
            if (!InetValidator.isValidInetPort(port)) {
                mPortInputLayout.setError(context.getString(R.string.proxy_invalid_port));
                return;
            }
        }
        mPortInputLayout.setError(null);

        Settings.putProxyType(type);
        Settings.putProxyIp(ip);
        Settings.putProxyPort(port);

        updateSummary(type, ip, port);

        EhApplication.getEhProxySelector(getContext()).updateProxy();

        dialog.dismiss();
    }
}
