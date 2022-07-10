package com.hippo.ehviewer.ui.scene.topList;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.data.topList.TopListInfo;
import com.hippo.ehviewer.client.data.topList.TopListItem;
import com.hippo.ehviewer.client.data.topList.TopListItemArray;

abstract class EhTopListAdapter extends RecyclerView.Adapter<EhTopListAdapter.EhTopListViewHolder> {

    private Context context;
    private TopListInfo ehTopListInfo;
    private int searchType;

    public EhTopListAdapter(@NonNull Context context, @NonNull RecyclerView recyclerView, TopListInfo topListInfo, int searchType) {
        this.context = context;
        this.ehTopListInfo = topListInfo;
        this.searchType = searchType;
    }

    @NonNull
    @Override
    public EhTopListAdapter.EhTopListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = View.inflate(context, R.layout.gallery_top_list_table_item, null);

        return new EhTopListViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EhTopListAdapter.EhTopListViewHolder holder, int position) {
        holder.textView.setText(timeInfoId(position));
        TopListItemArray topListItemArray = ehTopListInfo.get(position);
        for (int i = 0; i < topListItemArray.length(); i++) {
            View view = View.inflate(context, R.layout.gallery_top_list_item, null);
            TextView textView = view.findViewById(R.id.list_item);

//            textView.setBackgroundColor(getRandomColor());
            GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setColor(getRandomColor(i));
            TopListItem topListItem = topListItemArray.get(i);
            gradientDrawable.setCornerRadius(8);
            textView.setBackground(gradientDrawable);

            textView.setText(topListItem.value);

            view.setOnClickListener(v -> {
                onItemClick(topListItem, searchType);
            });
            holder.tableLayout.addView(view);
        }
    }

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

    @Override
    public int getItemCount() {
        return ehTopListInfo.size();
    }


    public class EhTopListViewHolder extends RecyclerView.ViewHolder {

        private TextView textView;
        private TableLayout tableLayout;


        public EhTopListViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.list_of_time);
            tableLayout = itemView.findViewById(R.id.list_items_table_view);
        }
    }
}
