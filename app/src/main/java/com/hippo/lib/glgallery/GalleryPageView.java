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

package com.hippo.lib.glgallery;

import com.hippo.lib.glview.glrenderer.BasicTexture;
import com.hippo.lib.glview.glrenderer.Texture;
import com.hippo.lib.glview.image.GLImageMovableTextView;
import com.hippo.lib.glview.image.ImageMovableTextTexture;
import com.hippo.lib.glview.image.ImageTexture;
import com.hippo.lib.glview.view.Gravity;
import com.hippo.lib.glview.widget.GLFrameLayout;
import com.hippo.lib.glview.widget.GLLinearLayout;
import com.hippo.lib.glview.widget.GLProgressView;
import com.hippo.lib.glview.widget.GLTextureView;

public class GalleryPageView extends GLFrameLayout {

    public static final int INVALID_INDEX = -1;

    public static final float PROGRESS_GONE = -1.0f;
    public static final float PROGRESS_INDETERMINATE = -2.0f;

    private final ImageView mImage;
    private final GLLinearLayout mInfo;
    private final GLImageMovableTextView mPage;
    private final GLImageMovableTextView mProgressText;
    private final GLImageMovableTextView mSpeedText; // 新增：速度显示
    private final GLTextureView mError;
    private final GLProgressView mProgress;
    private int mProgressSizeNormal;
    private int mProgressSizeWithText;

    private final int mMinHeight;
    private final int mProgressTextSize;
    private final int mSpeedTextSize;

    private int mIndex = INVALID_INDEX;
    private boolean mShowDetailedProgress = false; // 是否显示详细进度（三行信息）

        public GalleryPageView(ImageMovableTextTexture pageTextTexture,
            ImageMovableTextTexture progressTextTexture,
            int progressColor, int progressBgColor, int progressSize, int progressTextSize,
            int minHeight, int infoInterval) {
        // Add image
        mImage = new ImageView();
        GravityLayoutParams glp = new GravityLayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        addComponent(mImage, glp);

        // Add other panel
        mInfo = new GLLinearLayout();
        mInfo.setOrientation(GLLinearLayout.VERTICAL);
        mInfo.setInterval(infoInterval);
        glp = new GravityLayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        glp.gravity = Gravity.CENTER;
        addComponent(mInfo, glp);

        // Add page (Line 1: 页码)
        mPage = new GLImageMovableTextView();
        mPage.setTextTexture(pageTextTexture);
        GLLinearLayout.LayoutParams lp = new GLLinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        mInfo.addComponent(mPage, lp);

        // Add progress circle (Line 2: 加载圆圈)
        mProgress = new GLProgressView();
        mProgress.setBgColor(progressBgColor);
        mProgress.setColor(progressColor);
        // Store both sizes for dynamic sizing based on text visibility
        mProgressSizeNormal = progressSize;
        mProgressSizeWithText = progressTextSize;
        mProgressTextSize = progressTextSize;
        mSpeedTextSize = progressTextSize / 2; // 速度文字大小为进度文字的一半
        mProgress.setMinimumWidth(progressSize);
        mProgress.setMinimumHeight(progressSize);
        lp = new GLLinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        mInfo.addComponent(mProgress, lp);

        // Add progress text (Line 3: 当前进度百分比)
        mProgressText = new GLImageMovableTextView();
        mProgressText.setTextTexture(progressTextTexture);
        mProgressText.setVisibility(GONE);
        lp = new GLLinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        mInfo.addComponent(mProgressText, lp);

        // Add speed text (Line 4: 当前速率)
        mSpeedText = new GLImageMovableTextView();
        mSpeedText.setTextTexture(progressTextTexture);
        mSpeedText.setVisibility(GONE);
        lp = new GLLinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        mInfo.addComponent(mSpeedText, lp);

        // Add error
        mError = new GLTextureView();
        lp = new GLLinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        mInfo.addComponent(mError, lp);

        mMinHeight = minHeight;
    }

    @Override
    protected int getSuggestedMinimumHeight() {
        // The height of the actual image may be smaller than mPageMinHeight.
        // Set min height as 0 when the image is visible.
        // For PageLayoutManager, min height is useless.
        if (mImage.getVisibility() == VISIBLE) {
            return 0;
        } else {
            return mMinHeight;
        }
    }

    int getIndex() {
        return mIndex;
    }

    void setIndex(int index) {
        mIndex = index;
    }

    public void showImage() {
        mImage.setVisibility(VISIBLE);
        mInfo.setVisibility(GONE);
    }

    public void showInfo() {
        // For image valid rect
        mImage.setVisibility(INVISIBLE);
        mInfo.setVisibility(VISIBLE);
    }

    private void unbindImage() {
        ImageTexture texture = mImage.getImageTexture();
        if (texture != null) {
            mImage.setImageTexture(null);
            texture.recycle();
        }
    }

    public void setImage(ImageTexture imageTexture) {
        unbindImage();
        if (imageTexture != null) {
            mImage.setImageTexture(imageTexture);
        }
    }

    public void setPage(int page) {
        mPage.setVisibility(VISIBLE);
        mPage.setText(Integer.toString(page));
    }

    public void hidePage() {
        mPage.setVisibility(GONE);
    }

    public void setProgress(float progress) {
        if (progress == PROGRESS_GONE) {
            mProgress.setVisibility(GONE);
            mProgressText.setVisibility(GONE);
            if (mShowDetailedProgress) {
                mProgress.hideDetailedProgress();
            }
        } else if (progress == PROGRESS_INDETERMINATE) {
            mProgress.setVisibility(VISIBLE);
            mProgress.setIndeterminate(true);
            mProgressText.setVisibility(GONE);
            // Use smaller size for indeterminate progress without text
            mProgress.setMinimumWidth(mProgressSizeNormal);
            mProgress.setMinimumHeight(mProgressSizeNormal);
            if (mShowDetailedProgress) {
                mProgress.hideDetailedProgress();
            }
        } else {
            mProgress.setVisibility(VISIBLE);
            mProgress.setIndeterminate(false);
            mProgress.setProgress(progress);
            // Use larger size when showing percentage text
            mProgress.setMinimumWidth(mProgressSizeWithText);
            mProgress.setMinimumHeight(mProgressSizeWithText);
            // Don't hide detailed progress text when updating numeric progress
            // Text is managed by setDetailedProgress() caller
        }
    }

    public void setProgressText(String text) {
        if (text == null || text.isEmpty()) {
            mProgressText.setVisibility(GONE);
            mProgressText.setText("");
            // Revert to smaller size when no text
            mProgress.setMinimumWidth(mProgressSizeNormal);
            mProgress.setMinimumHeight(mProgressSizeNormal);
        } else {
            mProgressText.setVisibility(VISIBLE);
            mProgressText.setText(text);
            // Use larger size when showing text
            mProgress.setMinimumWidth(mProgressSizeWithText);
            mProgress.setMinimumHeight(mProgressSizeWithText);
        }
    }

    /**
     * Set speed text (Line 3 in detailed progress mode)
     */
    public void setSpeedText(String text) {
        if (text == null || text.isEmpty()) {
            mSpeedText.setVisibility(GONE);
            mSpeedText.setText("");
        } else {
            mSpeedText.setVisibility(VISIBLE);
            mSpeedText.setText(text);
        }
    }

    /**
     * Enable or disable detailed progress mode
     * When enabled, text is rendered inside the circle by GLProgressView
     * When disabled, text views are shown separately below the circle
     */
    public void setShowDetailedProgress(boolean show) {
        mShowDetailedProgress = show;
        if (show) {
            // Hide separate text views, let GLProgressView handle inner text
            mPage.setVisibility(GONE);
            mProgressText.setVisibility(GONE);
            mSpeedText.setVisibility(GONE);
        } else {
            // Clear inner text from GLProgressView
            mProgress.hideDetailedProgress();
        }
    }

    /**
     * Set detailed loading info (page index, progress, speed)
     * When detailed progress is enabled, text is rendered inside the circle
     * by GLProgressView itself (outer ring wrapping inner content style)
     * @param pageIndex The current page index (1-based for display)
     * @param progressText Progress text like "50%"
     * @param speedText Speed text like "1.5MB/s"
     */
    public void setDetailedProgress(int pageIndex, String progressText, String speedText) {
        if (mShowDetailedProgress) {
            // Use GLProgressView's inner text rendering
            // Hide separate text views since text is inside the circle now
            mPage.setVisibility(GONE);
            mProgressText.setVisibility(GONE);
            mSpeedText.setVisibility(GONE);
            
            mProgress.setVisibility(VISIBLE);
            mProgress.setIndeterminate(false);
            mProgress.setDetailedProgress(pageIndex, progressText, speedText);
        } else {
            // Legacy mode: show page -> progress circle -> progress percent -> speed
            mPage.setVisibility(VISIBLE);
            mPage.setText("第" + pageIndex + "页");
            
            // Keep progress circle visible
            mProgress.setVisibility(VISIBLE);
            mProgress.setIndeterminate(false);
            mProgress.setMinimumWidth(mProgressSizeWithText);
            mProgress.setMinimumHeight(mProgressSizeWithText);
            
            if (progressText != null && !progressText.isEmpty()) {
                mProgressText.setVisibility(VISIBLE);
                mProgressText.setText(progressText);
            } else {
                mProgressText.setVisibility(GONE);
            }
            
            if (speedText != null && !speedText.isEmpty()) {
                mSpeedText.setVisibility(VISIBLE);
                mSpeedText.setText(speedText);
            } else {
                mSpeedText.setVisibility(GONE);
            }
        }
    }

    private void unbindError() {
        Texture texture = mError.getTexture();
        if (texture != null) {
            mError.setTexture(null);
            if (texture instanceof BasicTexture) {
                ((BasicTexture) texture).recycle();
            }
        }
    }

    public void setError(String error, GalleryView galleryView) {
        unbindError();
        if (error == null) {
            mError.setVisibility(GONE);
        } else {
            mError.setVisibility(VISIBLE);
            galleryView.bindErrorView(mError, error);
        }
    }

    ImageView getImageView() {
        return mImage;
    }

    boolean isLoaded() {
        return mImage.getVisibility() == VISIBLE;
    }

    boolean isError() {
        return mError.getVisibility() == VISIBLE;
    }

    boolean isUnderInfo(float x, float y) {
        return mInfo.bounds().contains((int) x, (int) y);
    }

    boolean isCurrentPageAnimating() {
        return mImage.isImageAnimating();
    }
}
