package com.hippo.ehviewer.widget;

import android.animation.Animator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.ybq.android.spinkit.SpinKitView;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.client.data.HomeDetail;

import java.util.ArrayList;
import java.util.List;

public class LimitsCountView extends FrameLayout {

    private final Context context;
    private ImageView refreshIcon;
    private SpinKitView refreshing;
    private TextView limitsCount;

    private TextView fromGallery;
    private TextView fromTorrent;
    private TextView fromDownload;
    private TextView fromHentai;
    private TextView currentPower;
    private TextView resetLimits;

    private HomeDetail homeDetail;

    private OnViewNeedGone onViewNeedGone;
    private OnViewNeedVisible onViewNeedVisible;

    public boolean gone = true;

    private boolean animating = false;

    private int index = 5;

    private List<TextView> rows = new ArrayList<>();

    public LimitsCountView(@NonNull Context context) {
        super(context);
        this.context = context;
        init(context);
    }

    public LimitsCountView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init(context);
    }

    public LimitsCountView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        init(context);
    }

    public LimitsCountView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.context = context;
        init(context);
    }

    private void init(Context context) {
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.limits_count_main, this);
        boolean login = Settings.isLogin() && Settings.getShowEhLimits();
        if (!login) {
            this.setVisibility(GONE);
            return;
        }

        refreshIcon = findViewById(R.id.refresh_icon);
        refreshing = findViewById(R.id.refreshing);
        limitsCount = findViewById(R.id.limits_count);
        fromGallery = findViewById(R.id.from_gallery);
        fromTorrent = findViewById(R.id.from_torrent);
        fromDownload = findViewById(R.id.from_download);
        fromHentai = findViewById(R.id.from_hentai);
        currentPower = findViewById(R.id.current_power);
        resetLimits = findViewById(R.id.reset_limits);


        rows.add(fromGallery);
        rows.add(fromTorrent);
        rows.add(fromDownload);
        rows.add(fromHentai);
        rows.add(currentPower);
        rows.add(resetLimits);

        setOnClickListener(this::onClick);

        resetLimits.setOnClickListener(this::resetLimit);
        limitsCount.setOnClickListener(this::onClick);
        refreshIcon.setOnClickListener(this::onLoadData);

        if (onViewNeedGone == null) {
            onViewNeedGone = new OnViewNeedGone();
        }
        if (onViewNeedVisible == null) {
            onViewNeedVisible = new OnViewNeedVisible();
        }
    }

    private void resetLimit(View view) {
        if (homeDetail.resetCost() == 0L) {
            Toast.makeText(context, R.string.limit_unneed_reset, Toast.LENGTH_LONG).show();
            onLoadData(view);
            return;
        }
        refreshIcon.setVisibility(GONE);
        refreshing.setVisibility(VISIBLE);
        EhClient.Callback<HomeDetail> callback = new LimitsCountDataListener(context, view);

        EhRequest request = new EhRequest()
                .setMethod(EhClient.METHOD_RESET_LIMIT)
                .setCallback(callback);
        EhApplication.getEhClient(context).execute(request);
    }

    public void onLoadData(View view, boolean checkData) {
        if(!Settings.isLogin()){
            return;
        }
        if (!Settings.getShowEhLimits()){
            this.setVisibility(GONE);
            return;
        }
        if (checkData && homeDetail != null) {
            return;
        }
        if (refreshIcon==null||refreshing==null){
            return;
        }
        onLoadData(view);
    }

    private void onLoadData(View view) {
        refreshIcon.setVisibility(GONE);
        refreshing.setVisibility(VISIBLE);
        EhClient.Callback<HomeDetail> callback;
        if (view == this) {
            callback = new LimitsCountDataListener(context, view);
        } else {
            callback = new LimitsCountDataListener(context);
        }
        EhRequest request = new EhRequest()
                .setMethod(EhClient.METHOD_GET_HOME)
                .setCallback(callback);
        EhApplication.getEhClient(context).execute(request);
    }

    private void onClick(View view) {
        if (homeDetail == null) {
            onLoadData(this);
            return;
        }
        if (animating) {
            return;
        }
        animating = true;
        if (gone) {
            showNext();
        } else {
            removeNext();
        }
    }

    private void showNext() {
        rows.get(index).setVisibility(VISIBLE);
        rows.get(index).animate().translationZ(-50f).alpha(1f).setDuration(100).setListener(onViewNeedVisible);
    }

    private void removeNext() {
        rows.get(index).animate().translationZ(0f).alpha(0f).setDuration(100).setListener(onViewNeedGone);
    }

    private void bindingData() {
        fromGallery.setText(getResources().getString(R.string.from_gallery_visits, homeDetail.getFromGalleryVisits()));
        fromTorrent.setText(getResources().getString(R.string.from_torrent_completions, homeDetail.getFromTorrentCompletions()));
        fromDownload.setText(getResources().getString(R.string.from_archive_download, homeDetail.getFromArchiveDownloads()));
        fromHentai.setText(getResources().getString(R.string.from_hentai_home, homeDetail.getFromHentaiAtHome()));
        currentPower.setText(getResources().getString(R.string.current_moderation_power, homeDetail.getCurrentModerationPower()));
        resetLimits.setText(getResources().getString(R.string.reset_cost, homeDetail.getResetCost()));
    }

    public void hide() {
        if (animating || gone) {
            return;
        }
        animating = true;
        removeNext();
    }

    public void show() {
        if (animating || !gone) {
            return;
        }
        animating = true;
        showNext();
    }

    private class OnViewNeedVisible implements Animator.AnimatorListener {

        @Override
        public void onAnimationStart(Animator animation) {

        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (index == 0) {
                gone = false;
                animating = false;
                return;
            }
            index--;
            showNext();
        }

        @Override
        public void onAnimationCancel(Animator animation) {

        }

        @Override
        public void onAnimationRepeat(Animator animation) {

        }
    }

    private class OnViewNeedGone implements Animator.AnimatorListener {

        @Override
        public void onAnimationStart(Animator animation) {

        }

        @Override
        public void onAnimationEnd(Animator animation) {
//            fromGallery.setVisibility(GONE);
            rows.get(index).setVisibility(GONE);
            if (index == rows.size() - 1) {
                animating = false;
                gone = true;
                return;
            }
            index++;
            removeNext();
        }

        @Override
        public void onAnimationCancel(Animator animation) {

        }

        @Override
        public void onAnimationRepeat(Animator animation) {

        }
    }

    protected class LimitsCountDataListener implements EhClient.Callback<HomeDetail> {
        private final Context context;
        private View view = null;

        public LimitsCountDataListener(Context context) {
            this.context = context;
        }

        public LimitsCountDataListener(Context context, View view) {
            this.context = context;
            this.view = view;
        }

        @Override
        public void onSuccess(HomeDetail result) {
            homeDetail = result;
            limitsCount.setText(homeDetail.getImageLimits());
            refreshIcon.setVisibility(VISIBLE);
            refreshing.setVisibility(GONE);
            bindingData();
            if (view != null && gone) {
                onClick(view);
            }
        }

        @Override
        public void onFailure(Exception e) {

        }

        @Override
        public void onCancel() {

        }
    }
}
