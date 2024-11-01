/*
 * Copyright 2015 Hippo Seven
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

package com.hippo.text;

import android.graphics.drawable.Drawable;

import com.hippo.conaco.Conaco;
import com.hippo.drawable.UnikeryDrawable;
import com.hippo.lib.image.ImageBitmap;
import com.hippo.widget.ObservedTextView;

public class URLImageGetter implements Html.ImageGetter {

    private final ObservedTextView mTextView;
    private final Conaco<ImageBitmap> mConaco;

    public URLImageGetter(ObservedTextView textView, Conaco<ImageBitmap> conaco) {
        mTextView = textView;
        mConaco = conaco;
    }

    @Override
    public Drawable getDrawable(String source) {
        UnikeryDrawable drawable = new UnikeryDrawable(mTextView, mConaco);
        drawable.load(source);
        return drawable;
    }
}
