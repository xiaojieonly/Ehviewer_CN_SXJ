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

package com.hippo.ehviewer.ui.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Base64;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.ui.CommonOperations;
import com.hippo.util.AppHelper;

import java.io.UnsupportedEncodingException;

public class AboutFragment extends PreferenceFragment
        implements Preference.OnPreferenceClickListener {

    private static final String KEY_AUTHOR = "author";
    private static final String KEY_DONATE = "donate";
    private static final String KEY_CHECK_FOR_UPDATES = "check_for_updates";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.about_settings);

        Preference author = findPreference(KEY_AUTHOR);
        Preference donate = findPreference(KEY_DONATE);
        Preference checkForUpdate = findPreference(KEY_CHECK_FOR_UPDATES);

        author.setSummary(getString(R.string.settings_about_author_summary).replace('$', '@'));

        author.setOnPreferenceClickListener(this);
        donate.setOnPreferenceClickListener(this);
        checkForUpdate.setOnPreferenceClickListener(this);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        if (KEY_AUTHOR.equals(key)) {
            AppHelper.sendEmail(getActivity(), EhApplication.getDeveloperEmail(),
                    "About EhViewer", null);
        } else if (KEY_DONATE.equals(key)) {
            showDonationDialog();
        } else if (KEY_CHECK_FOR_UPDATES.equals(key)) {
            CommonOperations.checkUpdate(getActivity(), true);
        }
        return true;
    }

    //讨饭页面设置
    private void showDonationDialog() {
        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setView(R.layout.dialog_donate)
                .show();

//        String alipayStr = base64Decode("c2V2ZW4zMzJAMTYzLmNvbQ==");
        String alipayStr = base64Decode("aHR0cHM6Ly9naXRodWIuY29tL3hpYW9qaWVvbmx5L0Vodmlld2VyX0NOX1NYSi9ibG9iL0JpTGlfUENfR2FtZXIvZmVlZGF1dGhvci9zdXBwb3J0Lm1k");
        TextView alipayText = dialog.findViewById(R.id.alipay_text);
        alipayText.setText(alipayStr);
        dialog.findViewById(R.id.alipay_copy).setOnClickListener(v -> copyToClipboard(alipayStr));

        String guideStr = base64Decode("5oKo55qE5pSv5oyB5piv5oiR5pu05paw55qE5pyA5aSn5Yqo5Yqb77yM5oKo5Y+v5Lul6YCa6L+H6YKu5Lu" +
                "26I635Y+W5L2c6ICF55qE5pSv5LuY5a6d5oiW5b6u5L+h6LSm5Y+377yM5Lmf5Y+v5Lul6YCa6L+H6YKu5Lu25YWI5L2c6ICF5o+Q5Ye65oKo5oOz6Ka" +
                "B55qE5paw5Yqf6IO95oiW55uu5YmN5piv5LiN5aW955So55qE5Yqf6IO977yM5oiR5Lya5LiA5LiA5Zue5aSN5bm25YGa5Ye65oSf6LCi44CCKCDigKL" +
                "MgCDPiSDigKLMgSAp4pyn");
        TextView guideText = dialog.findViewById(R.id.guide_text);
        guideText.setText(guideStr);


//        String paypalStr = base64Decode("aHR0cHM6Ly9wYXlwYWwubWUvc2V2ZW4zMzI=");
//        TextView paypalText = dialog.findViewById(R.id.paypal_text);
//        paypalText.setText(paypalStr);
//        dialog.findViewById(R.id.paypal_open).setOnClickListener(v -> openUrl(paypalStr));
//        dialog.findViewById(R.id.paypal_copy).setOnClickListener(v -> copyToClipboard(paypalStr));
    }

    private static String base64Decode(String encoded) {
        byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
        try {
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void copyToClipboard(String text) {
        ClipboardManager cmb = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cmb != null) {
            cmb.setPrimaryClip(ClipData.newPlainText(null, text));
            Toast.makeText(getActivity(), R.string.settings_about_donate_copied, Toast.LENGTH_SHORT).show();
        }
    }

//    private void openUrl(String url) {
//        Intent intent = new Intent(Intent.ACTION_VIEW);
//        intent.setData(Uri.parse(url));
//        Intent chooser = Intent.createChooser(intent, "");
//        startActivity(chooser);
//    }
}
