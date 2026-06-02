/*
 * Copyright 2026 Copilot
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

package com.hippo.widget;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.hippo.android.resource.AttrResources;
import com.hippo.ehviewer.R;

public final class FabLabelHelper {

    private FabLabelHelper() {
        // No instances
    }

    public static void updateFabLabel(@NonNull FloatingActionButton fab, boolean show) {
        TextView label = getLabel(fab);
        if (!show || TextUtils.isEmpty(fab.getContentDescription())) {
            if (label != null) {
                label.setVisibility(View.GONE);
            }
            return;
        }

        ViewGroup root = getContentRoot(fab);
        if (root == null) {
            return;
        }

        if (label == null) {
            label = createLabel(fab.getContext(), fab);
            label.setTag(R.id.fab_function_name_label);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            root.addView(label, lp);
            final TextView labelView = label;
            View.OnLayoutChangeListener listener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                    updateFabLabelPosition(fab, labelView, root);
            fab.addOnLayoutChangeListener(listener);
            fab.setTag(R.id.fab_function_name_layout_listener, listener);
        }

        label.setText(fab.getContentDescription());
        updateLabelAppearance(label, fab.getContext());
        label.setVisibility(fab.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
        updateFabLabelPosition(fab, label, root);
    }

    public static void removeFabLabel(@NonNull FloatingActionButton fab) {
        TextView label = getLabel(fab);
        if (label != null) {
            ViewGroup parent = (ViewGroup) label.getParent();
            if (parent != null) {
                parent.removeView(label);
            }
            fab.setTag(R.id.fab_function_name_label, null);
        }
        Object listenerObject = fab.getTag(R.id.fab_function_name_layout_listener);
        if (listenerObject instanceof View.OnLayoutChangeListener) {
            fab.removeOnLayoutChangeListener((View.OnLayoutChangeListener) listenerObject);
            fab.setTag(R.id.fab_function_name_layout_listener, null);
        }
    }

    private static TextView getLabel(@NonNull FloatingActionButton fab) {
        Object tag = fab.getTag(R.id.fab_function_name_label);
        return tag instanceof TextView ? (TextView) tag : null;
    }

    @Nullable
    private static ViewGroup getContentRoot(@NonNull FloatingActionButton fab) {
        View root = fab.getRootView().findViewById(android.R.id.content);
        return root instanceof ViewGroup ? (ViewGroup) root : null;
    }

    private static TextView createLabel(Context context, FloatingActionButton fab) {
        TextView label = new TextView(context);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setIncludeFontPadding(false);
        label.setPadding(
                context.getResources().getDimensionPixelOffset(R.dimen.fab_function_name_padding_horizontal),
                context.getResources().getDimensionPixelOffset(R.dimen.fab_function_name_padding_vertical),
                context.getResources().getDimensionPixelOffset(R.dimen.fab_function_name_padding_horizontal),
                context.getResources().getDimensionPixelOffset(R.dimen.fab_function_name_padding_vertical));
        label.setClickable(false);
        label.setFocusable(false);
        label.setVisibility(View.GONE);
        return label;
    }

    private static void updateLabelAppearance(@NonNull TextView label, Context context) {
        boolean isLightTheme = AttrResources.getAttrBoolean(context, androidx.appcompat.R.attr.isLightTheme);
        int backgroundColor = isLightTheme ? 0xCCFFFFFF : 0xCC000000;
        int textColor = isLightTheme ? 0xFF000000 : 0xFFFFFFFF;
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(context.getResources().getDimension(R.dimen.fab_function_name_corner_radius));
        background.setColor(backgroundColor);
        label.setBackground(background);
        label.setTextColor(textColor);
    }

    private static void updateFabLabelPosition(@NonNull FloatingActionButton fab, @NonNull TextView label, @NonNull ViewGroup root) {
        int[] fabLocation = new int[2];
        int[] rootLocation = new int[2];
        fab.getLocationOnScreen(fabLocation);
        root.getLocationOnScreen(rootLocation);
        int margin = root.getResources().getDimensionPixelOffset(R.dimen.fab_function_name_margin);
        label.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.AT_MOST));
        int left = fabLocation[0] - rootLocation[0] - label.getMeasuredWidth() - margin;
        int top = fabLocation[1] - rootLocation[1] + (fab.getHeight() - label.getMeasuredHeight()) / 2;
        if (left < 0) {
            left = margin;
        }
        if (top < 0) {
            top = 0;
        }
        label.setX(left);
        label.setY(top);
    }
}
