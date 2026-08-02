package com.hippo.anotherviewer.ui.scene.topList;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hippo.anotherviewer.R;
import com.hippo.anotherviewer.client.data.SiteTopListDetail;
import com.hippo.anotherviewer.client.data.topList.TopListInfo;
import com.hippo.anotherviewer.client.data.topList.TopListItem;
import com.hippo.anotherviewer.client.data.topList.TopListItemArray;

abstract class SiteTopListAdapter extends RecyclerView.Adapter<SiteTopListAdapter.SiteTopListViewHolder> {

    private final Context context;
    private final TopListInfo ehTopListInfo;
    private final int searchType;

    public SiteTopListAdapter(@NonNull Context context, TopListInfo topListInfo, int searchType) {
        this.context = context;
        this.ehTopListInfo = topListInfo;
        this.searchType = searchType;
    }

    @NonNull
    @Override
    public SiteTopListAdapter.SiteTopListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = View.inflate(context, R.layout.gallery_top_list_table_item, null);

        return new SiteTopListViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SiteTopListAdapter.SiteTopListViewHolder holder, int position) {
        holder.textView.setText(timeInfoId(position));
        if (ehTopListInfo.type== SiteTopListDetail.ListType.GALLERY){
            holder.textView.setOnClickListener(v->clickTitle(urlFollow(position)));
            holder.textView.getPaint().setFlags(Paint.UNDERLINE_TEXT_FLAG);
        }

        TopListItemArray topListItemArray = ehTopListInfo.get(position);
        for (int i = 0; i < topListItemArray.length(); i++) {
            View view = View.inflate(context, R.layout.gallery_top_list_item, null);
            TextView textView = view.findViewById(R.id.list_item);
            GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setColor(getRandomColor(i));
            TopListItem topListItem = topListItemArray.get(i);
            gradientDrawable.setCornerRadius(8);
            textView.setBackground(gradientDrawable);

            textView.setText(topListItem.value);

            view.setOnClickListener(v -> onItemClick(topListItem, searchType));
            holder.tableLayout.addView(view);
        }
    }

    abstract void clickTitle(String urlFollow);

    abstract int getRandomColor(int position);

    abstract void onItemClick(TopListItem topListItem, int searchType);

    private int timeInfoId(int index) {
        switch (index) {
            default:
            case 3:
                return R.string.all_time_top_list;
            case 2:
                return R.string.past_year_top_list;
            case 1:
                return R.string.past_month_top_list;
            case 0:
                return R.string.yesterday_top_list;
        }
    }

    private String urlFollow(int index) {
        switch (index) {
            default:
            case 3:
                return "tl=11";
            case 2:
                return "tl=12";
            case 1:
                return "tl=13";
            case 0:
                return "tl=15";
        }
    }

    @Override
    public int getItemCount() {
        return ehTopListInfo.size();
    }


    public static class SiteTopListViewHolder extends RecyclerView.ViewHolder {

        private final TextView textView;
        private final TableLayout tableLayout;


        public SiteTopListViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.list_of_time);
            tableLayout = itemView.findViewById(R.id.list_items_table_view);

        }
    }
}
