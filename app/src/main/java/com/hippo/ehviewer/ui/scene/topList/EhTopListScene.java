package com.hippo.ehviewer.ui.scene.topList;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.Spinner;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.data.EhTopListDetail;
import com.hippo.ehviewer.client.data.ListUrlBuilder;
import com.hippo.ehviewer.client.data.topList.TopListInfo;
import com.hippo.ehviewer.client.data.topList.TopListItem;
import com.hippo.ehviewer.client.exception.EhException;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.ehviewer.ui.scene.BaseScene;
import com.hippo.ehviewer.ui.scene.EhCallback;
import com.hippo.ehviewer.ui.scene.gallery.list.GalleryListScene;
import com.hippo.scene.SceneFragment;
import com.hippo.view.ViewTransition;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Random;

public class EhTopListScene extends BaseScene {

    @IntDef({STATE_INIT, STATE_NORMAL, STATE_REFRESH, STATE_REFRESH_HEADER, STATE_FAILED})
    @Retention(RetentionPolicy.SOURCE)
    private @interface State {
    }

    private static final int STATE_INIT = -1;
    private static final int STATE_NORMAL = 0;
    private static final int STATE_REFRESH = 1;
    private static final int STATE_REFRESH_HEADER = 2;
    private static final int STATE_FAILED = 3;

    private static int mPosition = 0;

    private long mPressBackTime = 0;
    private static final int BACK_PRESSED_INTERVAL = 2000;

    @State
    private int mState = STATE_INIT;

    public final static String KEY_ACTION = "action";
    //    public final static String ACTION_HOMEPAGE = "action_homepage";
//    public final static String ACTION_SUBSCRIPTION = "action_subscription";
//    public final static String ACTION_WHATS_HOT = "action_whats_hot";
    public final static String ACTION_TOP_LIST = "action_top_list";
//    public final static String ACTION_LIST_URL_BUILDER = "action_list_url_builder";

//    public static final String ACTION_TOP_LIST_INFO = "action_gallery_info";
//    public static final String ACTION_GID_TOKEN = "action_gid_token";


    private EhTopListDetail ehTopListDetail;

    @Nullable
    private ViewTransition mViewTransition;
    @Nullable
    private RecyclerView mRecyclerView;
    @NonNull
    private ViewPager drawPager;

//    private ShowcaseView mShowcaseView;


    @Nullable
    private EhClient mClient;

    EhRequest mRequest;

    @Nullable
    private ListUrlBuilder mUrlBuilder = new ListUrlBuilder();

    private boolean mHasFirstRefresh = false;

    private static final boolean TRANSITION_ANIMATION_DISABLED = true;



    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getEHContext();

        mClient = EhApplication.getEhClient(context);
    }

    @Nullable
    @Override
    public View onCreateView2(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.scene_gallery_top_list, container, false);

        Spinner spinner = view.findViewById(R.id.top_list_spinner);
        spinner.setSelection(0);
        spinner.setOnItemSelectedListener(new TopListKindSelectedListener());
        FrameLayout mFrameLayout = view.findViewById(R.id.page_detail_view);
        View transitionView = view.findViewById(R.id.data_loading_view);
        mViewTransition = new ViewTransition(transitionView, mFrameLayout);

        mRecyclerView = view.findViewById(R.id.top_list_recycler_view);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getEHContext());
        mRecyclerView.setLayoutManager(linearLayoutManager);
        if (!mHasFirstRefresh) {
            mHasFirstRefresh = true;
            try {
                loadData();
            } catch (EhException e) {
                e.printStackTrace();
            }
        } else {
            bindViewSecond(mPosition);
            adjustViewVisibility(STATE_NORMAL, true);
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mViewTransition = null;
    }

    @Override
    public void onBackPressed() {

        boolean handle = checkDoubleClickExit();


        if (!handle) {
            if (mState == STATE_INIT) {
                mRequest.cancel();
            }
            finish();
        }
    }

    private boolean checkDoubleClickExit() {
        if (getStackIndex() != 0) {
            return false;
        }

        long time = System.currentTimeMillis();
        if (time - mPressBackTime > BACK_PRESSED_INTERVAL) {
            // It is the last scene
            mPressBackTime = time;
            showTip(R.string.press_twice_exit, LENGTH_SHORT);
            return true;
        } else {
            return false;
        }
    }


    /**
     * 数据请求入口
     */
    private void loadData() throws EhException {
        boolean requested = request();
        if (!requested) {
            throw new EhException("请求数据失败请更换IP地址或检查网络设置是否正确~");
        }
    }


    /**
     * 请求数据
     *
     * @return
     */
    private boolean request() {

        Context context = getEHContext();

        MainActivity activity = getActivity2();

        String url = EhUrl.getTopListUrl();
//        String url = EhUrl.getMyTag();

        if (null == context || null == activity) {
            return false;
        }

        EhClient.Callback callback = new GetTopListDetailListener(context, activity.getStageId(), getTag());

        mRequest = new EhRequest()
                .setMethod(EhClient.METHOD_GET_TOP_LIST)
//                .setMethod(EhClient.METHOD_ADD_WATCHED)
                .setArgs(url).setCallback(callback);

        mClient.execute(mRequest);


        return true;
    }

    /**
     * 通过返回监听赋值数据
     *
     * @param ehTopListDetail
     */
    private void onGetEhTopListDetailSuccess(EhTopListDetail ehTopListDetail, int index) {
        this.ehTopListDetail = ehTopListDetail;
        bindViewSecond(index);
        adjustViewVisibility(STATE_NORMAL, true);
    }

    /**
     * 绑定接口返回数据
     */
    private void bindViewSecond(int index) {
        if (null == ehTopListDetail) {
            return;
        }
        EhTopListAdapterView mAdapter = new EhTopListAdapterView(getEHContext(), mRecyclerView, ehTopListDetail.get(index), this, index);
        mRecyclerView.setAdapter(mAdapter);

    }

    /**
     * 调整页面组件可见性
     *
     * @param state
     */
    private void adjustViewVisibility(int state, boolean animation) {
//        if (state == mState) {
//            return;
//        }
        if (null == mViewTransition) {
            return;
        }


        mState = state;

        animation = !TRANSITION_ANIMATION_DISABLED && animation;

        switch (state) {
            case STATE_INIT:
            case STATE_REFRESH:
                mViewTransition.showView(0, animation);
                break;
            default:
            case STATE_NORMAL:
            case STATE_REFRESH_HEADER:
            case STATE_FAILED:
                mViewTransition.showView(1, animation);
                break;
        }


    }

    /**
     * 请求返回监听
     */
    private class GetTopListDetailListener extends EhCallback<EhTopListScene, EhTopListDetail> {

        public GetTopListDetailListener(Context context, int stageId, String sceneTag) {
            super(context, stageId, sceneTag);
        }

        @Override
        public boolean isInstance(SceneFragment scene) {
            return scene instanceof EhTopListScene;
        }

        @Override
        public void onSuccess(EhTopListDetail result) {
            onGetEhTopListDetailSuccess(result, 0);
        }

        @Override
        public void onFailure(Exception e) {

        }

        @Override
        public void onCancel() {

        }
    }

    private class EhTopListAdapterView extends EhTopListAdapter {
        private SceneFragment sceneFragment;
        private HashMap<Integer, Integer> hashMap = new HashMap();

        public EhTopListAdapterView(@NonNull Context context, @NonNull RecyclerView recyclerView, TopListInfo topListInfo, SceneFragment scene, int searchType) {
            super(context, recyclerView, topListInfo, searchType);
            sceneFragment = scene;
        }

        @Override
        int getRandomColor(int position) {
            Random random = new Random();

            if (hashMap.containsKey(position)) {
                return hashMap.get(position);
            } else {
                int color = Color.argb(160, random.nextInt(256), random.nextInt(256), random.nextInt(256));
                hashMap.put(position, color);
                return color;
            }
        }

        @Override
        public void onItemClick(TopListItem topListItem, int searchType) {
            ListUrlBuilder urlBuilder = new ListUrlBuilder();
            if (searchType == 0) {
                urlBuilder.setMode(ListUrlBuilder.MODE_NORMAL);
            } else {
                urlBuilder.setMode(ListUrlBuilder.MODE_UPLOADER);
            }
            urlBuilder.setKeyword(topListItem.value);
            GalleryListScene.startScene(sceneFragment, urlBuilder);
            return;
        }
    }

    /**
     * 下拉框监听
     */
    private class TopListKindSelectedListener implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            mPosition = position;
            bindViewSecond(mPosition);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
    }
}
