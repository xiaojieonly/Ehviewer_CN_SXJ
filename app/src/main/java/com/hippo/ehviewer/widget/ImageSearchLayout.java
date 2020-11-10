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

package com.hippo.ehviewer.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.AbsSavedState;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;
import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.data.ListUrlBuilder;
import com.hippo.ehviewer.client.exception.EhException;
import com.hippo.io.UniFileInputStreamPipe;
import com.hippo.unifile.UniFile;
import com.hippo.util.BitmapUtils;
import com.hippo.yorozuya.IOUtils;
import com.hippo.yorozuya.ViewUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public final class ImageSearchLayout extends LinearLayout implements View.OnClickListener {

    private static final String TAG = ImageSearchLayout.class.getSimpleName();

    private ImageView mPreview;
    private View mSelectImage;
    private CheckBox mSearchUSS;
    private CheckBox mSearchOSC;
    private CheckBox mSearchSE;

    private Helper mHelper;

    @Nullable
    private String mImagePath;

    public ImageSearchLayout(Context context) {
        super(context);
        init(context);
    }

    public ImageSearchLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ImageSearchLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @SuppressWarnings("deprecation")
    public void init(Context context) {
        setOrientation(VERTICAL);
        setDividerDrawable(context.getResources().getDrawable(R.drawable.spacer_keyline));
        setShowDividers(SHOW_DIVIDER_MIDDLE);
        LayoutInflater.from(context).inflate(R.layout.widget_image_search, this);

        mPreview = (ImageView) ViewUtils.$$(this, R.id.preview);
        mSelectImage = ViewUtils.$$(this, R.id.select_image);
        mSearchUSS = (CheckBox) ViewUtils.$$(this, R.id.search_uss);
        mSearchOSC = (CheckBox) ViewUtils.$$(this, R.id.search_osc);
        mSearchSE = (CheckBox) ViewUtils.$$(this, R.id.search_se);

        mSelectImage.setOnClickListener(this);
    }

    public void setHelper(Helper helper) {
        mHelper = helper;
    }

    @Override
    public void onClick(View v) {
        if (v == mSelectImage) {
            if (null != mHelper) {
                mHelper.onSelectImage();
            }
        }
    }

    public void setImageUri(@Nullable Uri imageUri) {
        if (null == imageUri) {
            return;
        }

        Context context = getContext();
        UniFile file = UniFile.fromUri(context, imageUri);
        if (null == file) {
            return;
        }

        try {
            int maxSize = context.getResources().getDimensionPixelOffset(R.dimen.image_search_max_size);
            Bitmap bitmap = BitmapUtils.decodeStream(new UniFileInputStreamPipe(file), maxSize, maxSize);
            if (null == bitmap) {
                return;
            }
            File temp = AppConfig.createTempFile();
            if (null == temp) {
                return;
            }

            // TODO ehentai image search is bad when I'm writing this line.
            // Re-compress image will make image search failed.
            OutputStream os = null;
            try {
                os = new FileOutputStream(temp);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, os);
                mImagePath = temp.getPath();
                mPreview.setImageBitmap(bitmap);
                mPreview.setVisibility(VISIBLE);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } finally {
                IOUtils.closeQuietly(os);
            }
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Out of memory");
        }
    }

    private void setImagePath(@Nullable String imagePath) {
        if (null == imagePath) {
            return;
        }

        InputStream is = null;
        try {
            is = new FileInputStream(imagePath);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (null == bitmap) {
                return;
            }
            mImagePath = imagePath;
            mPreview.setImageBitmap(bitmap);
            mPreview.setVisibility(VISIBLE);
        } catch (FileNotFoundException e) {
            // Ignore
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    public void formatListUrlBuilder(ListUrlBuilder builder) throws EhException {
        if (null == mImagePath) {
            throw new EhException(getContext().getString(R.string.select_image_first));
        }

        builder.setImagePath(mImagePath);
        builder.setUseSimilarityScan(mSearchUSS.isChecked());
        builder.setOnlySearchCovers(mSearchOSC.isChecked());
        builder.setShowExpunged(mSearchSE.isChecked());
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.imagePath = mImagePath;
        return ss;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setImagePath(ss.imagePath);
    }

    private static class SavedState extends AbsSavedState {

        String imagePath;

        /**
         * Constructor called from {@link ImageSearchLayout#onSaveInstanceState()}
         */
        protected SavedState(Parcelable superState) {
            super(superState);
        }

        /**
         * Constructor called from {@link #CREATOR}
         */
        protected SavedState(Parcel source) {
            super(source);
            imagePath = source.readString();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeString(imagePath);
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    public interface Helper {
        void onSelectImage();
    }
}
