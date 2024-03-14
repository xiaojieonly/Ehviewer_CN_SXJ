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

package com.hippo.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.hippo.ehviewer.R;
import com.hippo.text.Html;

public class MessagePreference extends DialogPreference {

    private CharSequence mDialogMessage;
    private boolean mDialogMessageLinkify;

    public MessagePreference(Context context) {
        super(context);
        init(context, null, 0, 0);
    }

    public MessagePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    public MessagePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    public void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MessagePreference, defStyleAttr, defStyleRes);
        String message = a.getString(R.styleable.MessagePreference_dialogMessage);
        if (a.getBoolean(R.styleable.MessagePreference_dialogMessageHtml, false)) {
            mDialogMessage = Html.fromHtml(message);
        } else {
            mDialogMessage = message;
        }
        mDialogMessageLinkify = a.getBoolean(R.styleable.MessagePreference_dialogMessageLinkify, false);
        a.recycle();
    }

    public void setDialogMessage(CharSequence message) {
        mDialogMessage = message;
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        builder.setMessage(mDialogMessage);
        builder.setPositiveButton(android.R.string.ok, this);
        builder.setNegativeButton(null, null);
    }

    @Override
    protected void onDialogCreated(AlertDialog dialog) {
        super.onDialogCreated(dialog);

        if (mDialogMessageLinkify) {
            final View messageView = dialog.findViewById(android.R.id.message);
            if (null != messageView && messageView instanceof TextView) {
                ((TextView) messageView).setMovementMethod(LinkMovementMethod.getInstance());
            }
        }
    }
}
