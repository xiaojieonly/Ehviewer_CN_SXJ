package com.hippo.ehviewer.ui.scene.gallery.list;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.viewpager.widget.ViewPager;

import com.hippo.app.EditTextDialogBuilder;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.callBack.SubscriptionCallback;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.client.EhTagDatabase;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.data.userTag.TagPushParam;
import com.hippo.ehviewer.client.data.userTag.UserTag;
import com.hippo.ehviewer.client.data.userTag.UserTagList;
import com.hippo.ehviewer.client.exception.EhException;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.ehviewer.ui.scene.EhCallback;
import com.hippo.scene.Announcer;
import com.hippo.scene.SceneFragment;
import com.hippo.widget.ProgressView;
import com.hippo.yorozuya.AssertUtils;
import com.hippo.yorozuya.ViewUtils;

import java.util.ArrayList;
import java.util.List;

import static com.hippo.ehviewer.Settings.*;

public class SubscriptionDraw {
    private final Context context;
    private final LayoutInflater inflater;
    private ListView listView;
    private ProgressView progressView;
    private FrameLayout frameLayout;
    private TextView textView;
    private final EhClient ehClient;
    protected MainActivity activity;
    private SubscriptionCallback callback;

    private final String mTag;

    boolean needLoad = true;

    private UserTagList userTagList;

    private EhTagDatabase ehTags;

    private String tagName;

    public SubscriptionDraw(Context context, LayoutInflater inflater, EhClient ehClient, String mTag, EhTagDatabase ehTags) {
        this.context = context;
        this.inflater = inflater;
        this.ehClient = ehClient;
        this.mTag = mTag;
        if (ehTags == null){
            this.ehTags = EhTagDatabase.getInstance(context);
        }
        this.ehTags = ehTags;
    }

    @SuppressLint("NonConstantResourceId")
    public View onCreate(ViewPager drawPager, MainActivity activity, SubscriptionCallback callback) {
        this.activity = activity;
        this.callback = callback;
        @SuppressLint("InflateParams")
        View subscriptionView = inflater.inflate(R.layout.subscription_draw, null, false);

        progressView = (ProgressView) ViewUtils.$$(subscriptionView, R.id.tag_list_view_progress);
        frameLayout = (FrameLayout) ViewUtils.$$(subscriptionView, R.id.tag_list_parent);
        textView = (TextView)ViewUtils.$$(subscriptionView,R.id.not_login_text);
        frameLayout.setVisibility(View.GONE);

        Toolbar toolbar = (Toolbar) ViewUtils.$$(subscriptionView, R.id.toolbar);
        final TextView tip = (TextView) ViewUtils.$$(subscriptionView, R.id.tip);
        listView = (ListView) ViewUtils.$$(subscriptionView, R.id.list_view);
        AssertUtils.assertNotNull(context);

        tip.setText(R.string.subscription_tip);
        toolbar.setLogo(R.drawable.ic_baseline_subscriptions_24);
        toolbar.setTitle(R.string.subscription);
        toolbar.inflateMenu(R.menu.drawer_gallery_list);
        toolbar.setOnMenuItemClickListener(item -> {  //点击增加快速搜索按钮触发
            int id = item.getItemId();
            switch (id) {
                case R.id.action_add:
                    addNewTag();
                    break;
                case R.id.action_settings:
                    seeDetailPage();
                    break;
            }
            return true;
        });

        toolbar.setOnClickListener(l -> drawPager.setCurrentItem(0));

        if (needLoad) {
            try {
                loadData();
            } catch (EhException e) {
                e.printStackTrace();
            }
        }

        return subscriptionView;
    }

    private void seeDetailPage() {
        if(!isLogin()){
            Toast.makeText(context,R.string.settings_eh_identity_cookies_tourist,Toast.LENGTH_SHORT).show();
            return;
        }
        if (userTagList == null){
            Toast.makeText(context,R.string.empty_subscription,Toast.LENGTH_SHORT).show();
            return;
        }
        userTagList.stageId=activity.getStageId();
        EhApplication.saveUserTagList(context,userTagList);
        activity.startScene(new Announcer(SubscriptionsScene.class));
    }

    private void bindViewSecond() {
        progressView.setVisibility(View.GONE);
        frameLayout.setVisibility(View.VISIBLE);
        if (userTagList.userTags.isEmpty()){
            if (isLogin()){
                textView.setVisibility(View.VISIBLE);
            }
            return;
        }
        List<String> name = new ArrayList<>();

        for (UserTag userTag : userTagList.userTags) {
            name.add(userTag.getName(ehTags));
        }

        SubscriptionItemAdapter adapter = new SubscriptionItemAdapter(context,userTagList,ehTags);

        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view1, position, id) -> {
            UserTag tag = userTagList.userTags.get(position);
            callback.onSubscriptionItemClick(tag.tagName);
        });
    }

    private void addNewTag() {
        if(!isLogin()){
            Toast.makeText(context,R.string.settings_eh_identity_cookies_tourist,Toast.LENGTH_SHORT).show();
            return;
        }
        tagName = callback.getAddTagName(userTagList);
        if (tagName == null){
            Toast.makeText(context,R.string.can_not_use_this_tag,Toast.LENGTH_SHORT).show();
            return;
        }

        final EditTextDialogBuilder builder = new EditTextDialogBuilder(context,
                tagName, context.getString(R.string.tag_title));
        builder.setTitle(R.string.add_tag_dialog_title);
        builder.setPositiveButton(R.string.subscription_watched, this::onDialogPositiveButtonClick);
        builder.setNegativeButton(R.string.subscription_hidden, this::onDialogNegativeButtonClick);
//        final AlertDialog dialog = builder.show();
        builder.show();
//        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
//            dialog.dismiss();
//            requestTag(tagName,true);
//        });
    }

    private void onDialogNegativeButtonClick(DialogInterface dialog, int which){
        dialog.dismiss();
        requestTag(tagName,false);
    }

    private void onDialogPositiveButtonClick(DialogInterface dialog, int which){
        dialog.dismiss();
        requestTag(tagName,true);
    }

    private void loadData() throws EhException {
        boolean requested = request();
        if (!requested) {
            throw new EhException("请求数据失败请更换IP地址或检查网络设置是否正确~");
        }
    }

    private void  requestTag(String tagName,boolean tagState){
        String url = EhUrl.getMyTag();

        if (null == context || null == activity) {
            return;
        }

        progressView.setVisibility(View.VISIBLE);
        frameLayout.setVisibility(View.GONE);

        EhClient.Callback callback = new SubscriptionDetailListener(context, activity.getStageId(), mTag);

        TagPushParam param = new TagPushParam();

        param.tagNameNew = tagName;
        if (tagState){
            param.tagWatchNew = "on";
        }else {
            param.tagHiddenNew = "on";
        }


        EhRequest mRequest = new EhRequest()
                .setMethod(EhClient.METHOD_ADD_TAG)
                .setArgs(url,param).setCallback(callback);

        ehClient.execute(mRequest);
    }

    /**
     * 请求数据
     *
     * @return
     */
    private boolean request() {

//        String url = EhUrl.getTopListUrl();
        String url = EhUrl.getMyTag();

        if (null == context || null == activity) {
            return false;
        }

        EhClient.Callback callback = new SubscriptionDetailListener(context, activity.getStageId(), mTag);

        EhRequest mRequest = new EhRequest()
                .setMethod(EhClient.METHOD_GET_WATCHED)
                .setArgs(url).setCallback(callback);

        ehClient.execute(mRequest);

        return true;
    }


    private class SubscriptionDetailListener extends EhCallback<GalleryListScene, UserTagList> {

        public SubscriptionDetailListener(Context context, int stageId, String sceneTag) {
            super(context, stageId, sceneTag);
        }

        @Override
        public boolean isInstance(SceneFragment scene) {
            return false;
        }

        @Override
        public void onSuccess(UserTagList result) {

            if (result == null){
                userTagList = new UserTagList();
                userTagList.userTags = new ArrayList<>();
            }else {
                userTagList = result;
            }
            EhApplication.saveUserTagList(context,userTagList);
            bindViewSecond();
            needLoad = false;
        }

        @Override
        public void onFailure(Exception e) {

        }

        @Override
        public void onCancel() {

        }
    }


}
