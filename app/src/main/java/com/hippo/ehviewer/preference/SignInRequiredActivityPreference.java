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

package com.hippo.ehviewer.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import com.google.android.material.snackbar.Snackbar;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhCookieStore;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.preference.ActivityPreference;
import okhttp3.HttpUrl;

public class SignInRequiredActivityPreference extends ActivityPreference {

  private View view;

  public SignInRequiredActivityPreference(Context context) {
    super(context);
  }

  public SignInRequiredActivityPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public SignInRequiredActivityPreference(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  protected View onCreateView(ViewGroup parent) {
    return view = super.onCreateView(parent);
  }

  @Override
  protected void onClick() {
    EhCookieStore store = EhApplication.getEhCookieStore(getContext());
    HttpUrl e = HttpUrl.get(EhUrl.HOST_E);
    HttpUrl ex = HttpUrl.get(EhUrl.HOST_EX);

    if (store.contains(e, EhCookieStore.KEY_IPD_MEMBER_ID) ||
        store.contains(e, EhCookieStore.KEY_IPD_PASS_HASH) ||
        store.contains(ex, EhCookieStore.KEY_IPD_MEMBER_ID) ||
        store.contains(ex, EhCookieStore.KEY_IPD_PASS_HASH)) {
      super.onClick();
    } else {
      if (view != null) {
        Snackbar.make(view, R.string.error_please_login_first, 3000).show();
      } else {
        Toast.makeText(getContext(), R.string.error_please_login_first, Toast.LENGTH_LONG).show();
      }
    }
  }
}
