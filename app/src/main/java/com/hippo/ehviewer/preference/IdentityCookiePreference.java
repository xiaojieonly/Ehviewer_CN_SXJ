/*
 * Copyright 2018 Hippo Seven
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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhCookieStore;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.preference.MessagePreference;
import com.hippo.text.Html;
import java.util.LinkedList;
import java.util.List;
import okhttp3.Cookie;
import okhttp3.HttpUrl;

public class IdentityCookiePreference extends MessagePreference {

    private String message;

    public IdentityCookiePreference(Context context) {
        super(context);
        init();
    }

    public IdentityCookiePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public IdentityCookiePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        EhCookieStore store = EhApplication.getEhCookieStore(getContext());
        List<Cookie> eCookies = store.getCookies(HttpUrl.get(EhUrl.HOST_E));
        List<Cookie> exCookies = store.getCookies(HttpUrl.get(EhUrl.HOST_EX));
        List<Cookie> cookies = new LinkedList<>(eCookies);
        cookies.addAll(exCookies);

        String ipbMemberId = null;
        String ipbPassHash = null;
        String igneous = null;

        for (int i = 0, n = cookies.size(); i < n; i++) {
            Cookie cookie = cookies.get(i);
            switch (cookie.name()) {
                case EhCookieStore.KEY_IPD_MEMBER_ID:
                    ipbMemberId = cookie.value();
                    break;
                case EhCookieStore.KEY_IPD_PASS_HASH:
                    ipbPassHash = cookie.value();
                    break;
                case EhCookieStore.KEY_IGNEOUS:
                    igneous = cookie.value();
                    break;
            }
        }

        if (ipbMemberId != null || ipbPassHash != null || igneous != null) {
            message = EhCookieStore.KEY_IPD_MEMBER_ID + ": " + ipbMemberId + "<br>"
                    + EhCookieStore.KEY_IPD_PASS_HASH + ": " + ipbPassHash + "<br>"
                    + EhCookieStore.KEY_IGNEOUS + ": " + igneous;
            setDialogMessage(Html.fromHtml(getContext().getString(R.string.settings_eh_identity_cookies_signed, message)));
            message = message.replace("<br>", "\n");
        } else {
            setDialogMessage(getContext().getString(R.string.settings_eh_identity_cookies_tourist));
        }
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        if (message != null) {
            builder.setPositiveButton(R.string.settings_eh_identity_cookies_copy, (dialog, which) -> {
                ClipboardManager cmb = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                cmb.setPrimaryClip(ClipData.newPlainText(null, message));
                Toast.makeText(getContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();

                IdentityCookiePreference.this.onClick(dialog, which);
            });
        }
    }
}
