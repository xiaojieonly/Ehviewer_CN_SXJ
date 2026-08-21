package com.hippo.ehviewer.widget;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.android.resource.AttrResources;
import com.hippo.ehviewer.R;

/** Compact pager with drawer-safe gestures and a dismissible fast-page panel. */
public final class MinimalPaginationView extends LinearLayout {
    private static final int[] PAGE_SIZE_CHOICES = {50, 100, 200, 300, 500};

    public interface OnPageChangeListener {
        void onPageChanged(int page);
    }

    public interface OnPageSizeChangeListener {
        void onPageSizeChanged(int pageSize);
    }

    public interface OnInteractionStateChangeListener {
        void onInteractionStateChanged(boolean active);
    }

    private final LinearLayout controls;
    private final TextView previous;
    private final TextView status;
    private final TextView next;
    private final int touchSlop;
    private final Runnable longPressAction = this::requestFastPanel;
    private int totalItems;
    private int totalPages = 1;
    private int currentPage = 1;
    private int itemsPerPage = 50;
    private float downX;
    private float downY;
    private boolean trackingGesture;
    private boolean fastPanelRequested;
    private boolean interactionActive;
    @Nullable private Dialog fastDialog;
    @Nullable private FastPagePanel fastPanel;
    @Nullable private OnPageChangeListener pageChangeListener;
    @Nullable private OnPageSizeChangeListener pageSizeChangeListener;
    @Nullable private OnInteractionStateChangeListener interactionListener;

    public MinimalPaginationView(@NonNull Context context) {
        this(context, null);
    }

    public MinimalPaginationView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MinimalPaginationView(@NonNull Context context, @Nullable AttributeSet attrs,
                                 int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setGravity(Gravity.CENTER);
        setPadding(dp(8), 0, dp(8), 0);
        // Consume touches that begin on the non-clickable status label so the complete
        // gesture stream reaches dispatchTouchEvent. The arrow children still handle taps.
        setClickable(true);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        controls = new LinearLayout(context);
        controls.setOrientation(HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        previous = createChevron("‹", R.string.pagination_previous);
        status = new TextView(context);
        status.setGravity(Gravity.CENTER);
        status.setSingleLine(true);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        status.setTextColor(AttrResources.getAttrColor(context,
                android.R.attr.textColorSecondary));
        status.setClickable(true);
        status.setFocusable(true);
        applySelectableBackground(status);
        next = createChevron("›", R.string.pagination_next);

        controls.addView(previous, new LayoutParams(dp(56), ViewGroup.LayoutParams.MATCH_PARENT));
        controls.addView(status, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        controls.addView(next, new LayoutParams(dp(56), ViewGroup.LayoutParams.MATCH_PARENT));
        addView(controls, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        previous.setOnClickListener(view -> changePage(currentPage - 1));
        status.setOnClickListener(view -> showFastPanel());
        next.setOnClickListener(view -> changePage(currentPage + 1));
        updateView();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        ViewGroup.LayoutParams params = controls.getLayoutParams();
        params.width = Math.min(Math.max(0, width - dp(16)), dp(720));
        controls.setLayoutParams(params);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(longPressAction);
        dismissFastPanel();
        setInteractionActive(false);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) dismissFastPanel();
    }

    private TextView createChevron(String text, int description) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(getResources().getString(description));
        view.setClickable(true);
        view.setFocusable(true);
        applySelectableBackground(view);
        return view;
    }

    private TextView createPageSizeButton() {
        TextView view = new TextView(getContext());
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setTextColor(AttrResources.getAttrColor(getContext(),
                androidx.appcompat.R.attr.colorPrimary));
        view.setClickable(true);
        view.setFocusable(true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.TRANSPARENT);
        background.setCornerRadius(dp(17));
        background.setStroke(dp(1), AttrResources.getAttrColor(getContext(),
                androidx.appcompat.R.attr.colorPrimary));
        view.setBackground(background);
        view.setText(getResources().getString(R.string.pagination_per_page_short, itemsPerPage));
        view.setOnClickListener(this::showPageSizeMenu);
        return view;
    }

    private void applySelectableBackground(View view) {
        TypedValue selectable = new TypedValue();
        if (getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, selectable, true)
                && selectable.resourceId != 0) {
            view.setBackgroundResource(selectable.resourceId);
        }
    }

    public void setOnPageChangeListener(@Nullable OnPageChangeListener listener) {
        pageChangeListener = listener;
    }

    public void setOnPageSizeChangeListener(@Nullable OnPageSizeChangeListener listener) {
        pageSizeChangeListener = listener;
    }

    public void setOnInteractionStateChangeListener(
            @Nullable OnInteractionStateChangeListener listener) {
        interactionListener = listener;
    }

    public void setPagination(int totalItems, int itemsPerPage, int requestedPage) {
        this.totalItems = Math.max(0, totalItems);
        this.itemsPerPage = Math.max(1, itemsPerPage);
        totalPages = Math.max(1, (this.totalItems + this.itemsPerPage - 1)
                / this.itemsPerPage);
        currentPage = clamp(requestedPage, 1, totalPages);
        updateView();
        if (fastPanel != null) fastPanel.update(currentPage, totalPages, itemsPerPage);
    }

    public void skipToPage(int page) {
        currentPage = clamp(page, 1, totalPages);
        updateView();
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void goToNextPage() {
        changePage(currentPage + 1);
    }

    public void goToPreviousPage() {
        changePage(currentPage - 1);
    }

    private void changePage(int requestedPage) {
        int page = clamp(requestedPage, 1, totalPages);
        if (page == currentPage) return;
        currentPage = page;
        updateView();
        status.setAlpha(0.55f);
        status.animate().alpha(1f).setDuration(140L).start();
        if (pageChangeListener != null) pageChangeListener.onPageChanged(currentPage);
        announceForAccessibility(status.getText());
    }

    private void showPageSizeMenu(View anchor) {
        PopupMenu menu = new PopupMenu(getContext(), anchor);
        for (int choice : PAGE_SIZE_CHOICES) {
            menu.getMenu().add(getResources().getString(R.string.pagination_per_page, choice))
                    .setCheckable(true).setChecked(choice == itemsPerPage);
        }
        menu.setOnMenuItemClickListener(item -> {
            for (int choice : PAGE_SIZE_CHOICES) {
                if (item.getTitle().toString().equals(
                        getResources().getString(R.string.pagination_per_page, choice))) {
                    if (choice != itemsPerPage && pageSizeChangeListener != null) {
                        pageSizeChangeListener.onPageSizeChanged(choice);
                    }
                    return true;
                }
            }
            return false;
        });
        menu.show();
    }

    private void updateView() {
        status.setText(getResources().getString(R.string.pagination_minimal_status,
                currentPage, totalPages, totalItems));
        setChevronState(previous, currentPage > 1);
        setChevronState(next, currentPage < totalPages);
        setContentDescription(getResources().getString(R.string.pagination_swipe_hint,
                currentPage, totalPages));
    }

    private void setChevronState(TextView view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.22f);
        view.setTextColor(AttrResources.getAttrColor(getContext(), enabled
                ? androidx.appcompat.R.attr.colorPrimary : android.R.attr.textColorSecondary));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                setInteractionActive(true);
                downX = event.getX();
                downY = event.getY();
                trackingGesture = true;
                fastPanelRequested = false;
                if (isInStatusArea(downX)) postDelayed(longPressAction,
                        ViewConfiguration.getLongPressTimeout());
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                    removeCallbacks(longPressAction);
                }
                if (isInStatusArea(downX) && dy <= -dp(32)
                        && Math.abs(dy) > Math.abs(dx)) {
                    requestFastPanel();
                    cancelChildTouch(event);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPressAction);
                if (fastPanelRequested) {
                    trackingGesture = false;
                    post(this::showFastPanel);
                    return true;
                }
                if (trackingGesture) {
                    float swipeX = event.getX() - downX;
                    float swipeY = event.getY() - downY;
                    trackingGesture = false;
                    if (Math.abs(swipeX) >= dp(48)
                            && Math.abs(swipeX) > Math.abs(swipeY) * 1.25f) {
                        cancelChildTouch(event);
                        if (swipeX < 0) goToNextPage(); else goToPreviousPage();
                        setInteractionActive(false);
                        return true;
                    }
                }
                setInteractionActive(false);
                break;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPressAction);
                trackingGesture = false;
                if (fastPanelRequested) post(this::showFastPanel);
                else setInteractionActive(false);
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean isInStatusArea(float x) {
        return x >= controls.getLeft() + previous.getRight()
                && x <= controls.getLeft() + status.getRight();
    }

    private void requestFastPanel() {
        if (!trackingGesture || fastPanelRequested || totalPages <= 1) return;
        fastPanelRequested = true;
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
    }

    private void showFastPanel() {
        if (fastDialog != null && fastDialog.isShowing()) return;
        if (!isAttachedToWindow() || getWindowToken() == null || !isShown()) {
            setInteractionActive(false);
            return;
        }
        fastPanelRequested = false;
        setInteractionActive(true);
        int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(560));
        fastPanel = new FastPagePanel(getContext());
        fastPanel.update(currentPage, totalPages, itemsPerPage);
        fastDialog = new Dialog(getContext());
        fastDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        fastDialog.setContentView(fastPanel);
        fastDialog.setCanceledOnTouchOutside(true);
        fastDialog.setOnDismissListener(dialog -> {
            fastDialog = null;
            fastPanel = null;
            setInteractionActive(false);
        });
        fastDialog.show();
        Window window = fastDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            attributes.y = getHeight() + dp(12);
            window.setAttributes(attributes);
        }
    }

    public void dismissFastPanel() {
        fastPanelRequested = false;
        removeCallbacks(longPressAction);
        if (fastDialog != null) fastDialog.dismiss();
        fastDialog = null;
        fastPanel = null;
        setInteractionActive(false);
    }

    private void setInteractionActive(boolean active) {
        if (interactionActive == active) return;
        interactionActive = active;
        if (interactionListener != null) interactionListener.onInteractionStateChanged(active);
    }

    private void cancelChildTouch(MotionEvent source) {
        MotionEvent cancel = MotionEvent.obtain(source);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private final class FastPagePanel extends LinearLayout {
        private final TextView pageSize;
        private final TextView pageLabel;
        private final ScaleView scale;

        FastPagePanel(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(dp(18), dp(12), dp(18), dp(12));
            GradientDrawable background = new GradientDrawable();
            background.setColor(AttrResources.getAttrColor(context,
                    android.R.attr.colorBackground));
            background.setCornerRadius(dp(20));
            setBackground(background);

            LinearLayout header = new LinearLayout(context);
            header.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = new TextView(context);
            title.setText(R.string.pagination_quick_title);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setTextColor(AttrResources.getAttrColor(context,
                    androidx.appcompat.R.attr.colorPrimary));
            pageSize = createPageSizeButton();
            TextView close = new TextView(context);
            close.setText("×");
            close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            close.setGravity(Gravity.CENTER);
            close.setContentDescription(getResources().getString(R.string.pagination_close));
            applySelectableBackground(close);
            close.setOnClickListener(view -> dismissFastPanel());
            header.addView(title, new LayoutParams(0, dp(38), 1f));
            header.addView(pageSize, new LayoutParams(dp(82), dp(34)));
            LayoutParams closeParams = new LayoutParams(dp(42), dp(38));
            closeParams.leftMargin = dp(10);
            header.addView(close, closeParams);
            addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

            pageLabel = new TextView(context);
            pageLabel.setGravity(Gravity.CENTER);
            pageLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            pageLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            pageLabel.setTextColor(AttrResources.getAttrColor(context,
                    androidx.appcompat.R.attr.colorPrimary));
            LayoutParams labelParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(34));
            labelParams.topMargin = dp(12);
            addView(pageLabel, labelParams);

            scale = new ScaleView(context);
            scale.setOnPageSelectedListener((page, committed) -> {
                pageLabel.setText(getResources().getString(
                        R.string.pagination_quick_status, page, totalPages));
                if (committed) {
                    changePage(page);
                    dismissFastPanel();
                }
            });
            LayoutParams scaleParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(46));
            scaleParams.topMargin = dp(8);
            addView(scale, scaleParams);
        }

        void update(int page, int pages, int perPage) {
            pageSize.setText(getResources().getString(R.string.pagination_per_page_short,
                    perPage));
            pageLabel.setText(getResources().getString(R.string.pagination_quick_status,
                    page, pages));
            scale.update(page, pages);
        }
    }

    private static final class ScaleView extends View {
        interface OnPageSelectedListener {
            void onPageSelected(int page, boolean committed);
        }

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int accent;
        private final int secondary;
        private int page = 1;
        private int pages = 1;
        @Nullable private OnPageSelectedListener listener;

        ScaleView(Context context) {
            super(context);
            accent = AttrResources.getAttrColor(context,
                    androidx.appcompat.R.attr.colorPrimary);
            secondary = AttrResources.getAttrColor(context, android.R.attr.textColorSecondary);
            setClickable(true);
        }

        void setOnPageSelectedListener(@Nullable OnPageSelectedListener listener) {
            this.listener = listener;
        }

        void update(int page, int pages) {
            this.page = page;
            this.pages = Math.max(1, pages);
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    || event.getActionMasked() == MotionEvent.ACTION_MOVE
                    || event.getActionMasked() == MotionEvent.ACTION_UP) {
                float left = dp(8);
                float right = Math.max(left + 1f, getWidth() - dp(8));
                float fraction = Math.max(0f, Math.min(1f,
                        (event.getX() - left) / (right - left)));
                int selected = Math.round(fraction * (pages - 1)) + 1;
                if (selected != page) {
                    page = selected;
                    invalidate();
                }
                if (listener != null) listener.onPageSelected(page,
                        event.getActionMasked() == MotionEvent.ACTION_UP);
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) return true;
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = dp(8);
            float right = getWidth() - dp(8);
            float centerY = getHeight() * 0.55f;
            paint.setColor((secondary & 0x00ffffff) | 0x55000000);
            paint.setStrokeWidth(dp(1));
            canvas.drawLine(left, centerY, right, centerY, paint);
            int ticks = Math.min(41, pages);
            for (int i = 0; i < ticks; i++) {
                float x = ticks == 1 ? left : left + (right - left) * i / (ticks - 1f);
                float height = i % 5 == 0 ? dp(8) : dp(4);
                canvas.drawLine(x, centerY - height, x, centerY + height, paint);
            }
            float fraction = pages <= 1 ? 0f : (page - 1f) / (pages - 1f);
            float markerX = left + (right - left) * fraction;
            paint.setColor(accent);
            paint.setStrokeWidth(dp(3));
            canvas.drawLine(markerX, centerY - dp(12), markerX, centerY + dp(12), paint);
        }

        private float dp(int value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }
}
