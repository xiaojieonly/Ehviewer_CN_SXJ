/*
 * Copyright 2019 Hippo Seven
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

package com.hippo.ehviewer.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.hippo.ehviewer.R;

public class DialogWebChromeClient extends WebChromeClient {

  private Context context;

  public DialogWebChromeClient(Context context) {
    this.context = context;
  }

  @Override
  public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
    new AlertDialog.Builder(view.getContext())
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
        .setOnCancelListener(dialog -> result.cancel())
        .show();
    return true;
  }

  @Override
  public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
    new AlertDialog.Builder(view.getContext())
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
        .setNegativeButton(android.R.string.cancel, (dialog, which) -> result.cancel())
        .setOnCancelListener(dialog -> result.cancel())
        .show();
    return true;
  }

  @Override
  public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
    LayoutInflater inflater = LayoutInflater.from(context);
    View promptView = inflater.inflate(R.layout.dialog_js_prompt, null, false);
    TextView messageView = promptView.findViewById(R.id.message);
    messageView.setText(message);
    final EditText valueView = promptView.findViewById(R.id.value);
    valueView.setText(defaultValue);

    new AlertDialog.Builder(context)
        .setView(promptView)
        .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm(valueView.getText().toString()))
        .setOnCancelListener(dialog -> result.cancel())
        .show();

    return true;
  }
}
