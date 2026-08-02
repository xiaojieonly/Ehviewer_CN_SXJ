package com.hippo.anotherviewer.ui.scene.gallery.list;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.hippo.anotherviewer.R;
import com.hippo.anotherviewer.Settings;
import com.hippo.anotherviewer.client.SiteTagDatabase;
import com.hippo.anotherviewer.client.data.userTag.UserTag;
import com.hippo.anotherviewer.client.data.userTag.UserTagList;

public class SubscriptionItemAdapter extends BaseAdapter {

    private final UserTagList userTagList;

    private final LayoutInflater inflater;

    private final SiteTagDatabase ehTags;

    public SubscriptionItemAdapter(Context context,UserTagList userTagList,SiteTagDatabase ehTags){
        this.userTagList = userTagList;
        if (ehTags==null){
            this.ehTags = SiteTagDatabase.getInstance(context);
        }else {
            this.ehTags = ehTags;
        }
        inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return userTagList.userTags.size();
    }

    @Override
    public UserTag getItem(int position) {
        return userTagList.userTags.get(position);
    }

    @Override
    public long getItemId(int position) {
        return Long.decode(getItem(position).userTagId.substring(8));
    }

    @SuppressLint({"ViewHolder","InflateParams"})
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        UserTag userTag = getItem(position);

        View view = inflater.inflate(R.layout.subscripition_list_item,null);
        ImageView imageView = view.findViewById(R.id.subscription_state);
        if (userTag.hidden){
            imageView.setImageResource(R.drawable.ic_baseline_visibility_off_24);
        }
        if (userTag.watched){
            imageView.setImageResource(R.drawable.ic_baseline_visibility_24);
        }

        TextView textView = view.findViewById(R.id.label);

        textView.setText(userTag.getName(ehTags));


        return view;
    }
}
