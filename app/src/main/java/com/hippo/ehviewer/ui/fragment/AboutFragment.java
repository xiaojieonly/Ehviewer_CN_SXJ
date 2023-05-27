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
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.util.AppHelper;
import com.hippo.util.ExceptionUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import com.microsoft.appcenter.distribute.Distribute;

public class AboutFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener {

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
            AppHelper.sendEmail(getActivity(), EhApplication.getDeveloperEmail(), "About EhViewer", null);
        } else if (KEY_DONATE.equals(key)) {
            showDonationDialog();
        } else if (KEY_CHECK_FOR_UPDATES.equals(key)) {
            //            Settings.setCheckUpdate(false);
            Distribute.checkForUpdate();
        }
        return true;
    }

    //讨饭页面设置
    private void showDonationDialog() {
        AlertDialog dialog = new AlertDialog.Builder(getActivity()).setView(R.layout.dialog_donate).show();
        String alipayStr = base64Decode("MTAzMTA4MjA5MUBxcS5jb20=");
        TextView alipayText = dialog.findViewById(R.id.alipay_text);
        assert alipayText != null;
        alipayText.setText(alipayStr);
        Objects.<View>requireNonNull(dialog.findViewById(R.id.alipay_copy)).setOnClickListener(v -> copyToClipboard(alipayStr));
        ImageView aliPayImage = dialog.findViewById(R.id.image_aliPay);
        ImageView weChatImage = dialog.findViewById(R.id.image_weChat);
        Objects.<View>requireNonNull(dialog.findViewById(R.id.save_image_aliPay)).setOnClickListener(v -> saveImage(1, aliPayImage));
        Objects.<View>requireNonNull(dialog.findViewById(R.id.save_image_weChat)).setOnClickListener(v -> saveImage(2, weChatImage));
        String guideStr = base64Decode("5oKo55qE5pSv5oyB5piv5oiR5pu05paw55qE5pyA5aSn5Yqo5Yqb77yM5oKo5Y+v5Lul5oiq5Zu+5ZCO5Zyo5b6u5L+h5oiW5p" + "Sv5LuY5a6d5Lit5omr5o+P5LqM57u056CB5o+Q5L6b546w6YeR5pSv5oyB77yM5Lmf5Y+v5Lul6YCa6L+H6YKu5Lu25YWI5L2c6ICF5o+Q5Ye65oKo5oOz6KaB55qE5paw5Y" + "qf6IO95oiW55uu5YmN5piv5LiN5aW955So55qE5Yqf6IO977yM5oiR5Lya5LiA5LiA5Zue5aSN5bm25YGa5Ye65oSf6LCi44CCKCDigKLMgCDPiSDigKLMgSAp4pyn");
        TextView guideText = dialog.findViewById(R.id.guide_text);
        assert guideText != null;
        guideText.setText(guideStr);
    }

    private void saveImage(int drawable, ImageView imageView) {
        Context context = getContext();
        File dir = AppConfig.getExternalImageDir();
        File mFile;
        FileOutputStream fileOutputStream;
        Bitmap needSaveData;
        Toast errorToast = Toast.makeText(context, R.string.error_save_image_existed, Toast.LENGTH_SHORT);
        errorToast.setGravity(Gravity.CENTER, 0, 0);
        Toast successToast = Toast.makeText(context, R.string.image_save_success, Toast.LENGTH_SHORT);
        successToast.setGravity(Gravity.CENTER, 0, 0);
        try {
            if (drawable == 1) {
                needSaveData = BitmapFactory.decodeResource(getResources(), R.drawable.zhifubao);
                mFile = new File(dir + "zhifubao.jpg");
                if (mFile.exists()) {
                    errorToast.show();
                    return;
                }
                fileOutputStream = new FileOutputStream(mFile);
                needSaveData.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
            } else {
                needSaveData = BitmapFactory.decodeResource(getResources(), R.drawable.weixin);
                mFile = new File(dir + "weixin.png");
                if (mFile.exists()) {
                    errorToast.show();
                    return;
                }
                fileOutputStream = new FileOutputStream(mFile);
                needSaveData.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
            }
            Uri uri = Uri.fromFile(mFile);
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
            successToast.show();
        } catch (FileNotFoundException e) {
            ExceptionUtils.throwIfFatal(e);
        }
    }

    private static String base64Decode(String encoded) {
        byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
        return new String(bytes, StandardCharsets.UTF_8);
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
