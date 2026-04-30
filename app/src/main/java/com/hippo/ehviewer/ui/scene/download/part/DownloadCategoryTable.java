package com.hippo.ehviewer.ui.scene.download.part;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhConfig;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.widget.CheckTextView;
import com.hippo.lib.yorozuya.NumberUtils;

import java.util.HashSet;
import java.util.Set;

public class DownloadCategoryTable extends TableLayout {

    private static final String STATE_KEY_SUPER = "super";
    private static final String STATE_KEY_CATEGORY = "category";

    private CheckTextView mDoujinshi;
    private CheckTextView mManga;
    private CheckTextView mArtistCG;
    private CheckTextView mGameCG;
    private CheckTextView mWestern;
    private CheckTextView mNonH;
    private CheckTextView mImageSets;
    private CheckTextView mCosplay;
    private CheckTextView mAsianPorn;
    private CheckTextView mMisc;

    private CheckTextView[] mOptions;

    public DownloadCategoryTable(Context context) {
        super(context);
        init();
    }

    public DownloadCategoryTable(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void init() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.widget_category_table, this);

        ViewGroup row0 = (ViewGroup) getChildAt(0);
        mDoujinshi = (CheckTextView) row0.getChildAt(0);
        mManga = (CheckTextView) row0.getChildAt(1);

        ViewGroup row1 = (ViewGroup) getChildAt(1);
        mArtistCG = (CheckTextView) row1.getChildAt(0);
        mGameCG = (CheckTextView) row1.getChildAt(1);

        ViewGroup row2 = (ViewGroup) getChildAt(2);
        mWestern = (CheckTextView) row2.getChildAt(0);
        mNonH = (CheckTextView) row2.getChildAt(1);

        ViewGroup row3 = (ViewGroup) getChildAt(3);
        mImageSets = (CheckTextView) row3.getChildAt(0);
        mCosplay = (CheckTextView) row3.getChildAt(1);

        ViewGroup row4 = (ViewGroup) getChildAt(4);
        mAsianPorn = (CheckTextView) row4.getChildAt(0);
        mMisc = (CheckTextView) row4.getChildAt(1);

        mOptions = new CheckTextView[] {
            mDoujinshi, mManga, mArtistCG, mGameCG, mWestern,
            mNonH, mImageSets, mCosplay, mAsianPorn, mMisc
        };
    }

    /**
     * 设置选中的分类。
     * 说明：为了与原版 CategoryTable 的视觉交互保持一致，"未选中"（彩色、无蒙层）表示包含该分类，
     * "选中"（带白色蒙层）表示排除该分类。
     *
     * @param selectedCategories 选中的分类集合
     */
    public void setSelectedCategories(Set<Integer> selectedCategories) {
        android.util.Log.d("DownloadCategoryTable", "setSelectedCategories: " + selectedCategories);
        
        if (selectedCategories == null || selectedCategories.isEmpty()) {
            // 如果为空，视为全选，全部保持“未选中”状态
            android.util.Log.d("DownloadCategoryTable", "设置为全选（空集合）");
            for (CheckTextView option : mOptions) {
                option.setChecked(false, false);
            }
        } else if (selectedCategories.contains(EhUtils.ALL_CATEGORY)) {
            // 全选
            android.util.Log.d("DownloadCategoryTable", "设置为全选（包含ALL_CATEGORY）");
            for (CheckTextView option : mOptions) {
                option.setChecked(false, false);
            }
        } else {
            // 设置具体分类
            android.util.Log.d("DownloadCategoryTable", "设置具体分类");
            // 未选中 = 包含该分类，选中 = 排除该分类
            mDoujinshi.setChecked(!selectedCategories.contains(EhConfig.DOUJINSHI), false);
            mManga.setChecked(!selectedCategories.contains(EhConfig.MANGA), false);
            mArtistCG.setChecked(!selectedCategories.contains(EhConfig.ARTIST_CG), false);
            mGameCG.setChecked(!selectedCategories.contains(EhConfig.GAME_CG), false);
            mWestern.setChecked(!selectedCategories.contains(EhConfig.WESTERN), false);
            mNonH.setChecked(!selectedCategories.contains(EhConfig.NON_H), false);
            mImageSets.setChecked(!selectedCategories.contains(EhConfig.IMAGE_SET), false);
            mCosplay.setChecked(!selectedCategories.contains(EhConfig.COSPLAY), false);
            mAsianPorn.setChecked(!selectedCategories.contains(EhConfig.ASIAN_PORN), false);
            mMisc.setChecked(!selectedCategories.contains(EhConfig.MISC), false);
        }
    }

    /**
     * 获取选中的分类。
     * 说明：未选中的按钮（彩色）视为“包含”，选中的按钮（蒙层）视为“排除”。
     *
     * @return 选中的分类集合
     */
    public Set<Integer> getSelectedCategories() {
        Set<Integer> selectedCategories = new HashSet<>();
        
        // 检查是否全选：所有按钮都处于“未选中”状态
        boolean allSelected = true;
        for (CheckTextView option : mOptions) {
            if (option.isChecked()) {
                allSelected = false;
                break;
            }
        }
        
        android.util.Log.d("DownloadCategoryTable", "getSelectedCategories: allSelected=" + allSelected);
        
        if (allSelected) {
            selectedCategories.add(EhUtils.ALL_CATEGORY);
            android.util.Log.d("DownloadCategoryTable", "返回全选");
        } else {
            // 只有部分排除时，才添加具体分类（未选中视为选中）
            if (!mDoujinshi.isChecked()) {
                selectedCategories.add(EhConfig.DOUJINSHI);
                android.util.Log.d("DownloadCategoryTable", "选中DOUJINSHI");
            }
            if (!mManga.isChecked()) {
                selectedCategories.add(EhConfig.MANGA);
                android.util.Log.d("DownloadCategoryTable", "选中MANGA");
            }
            if (!mArtistCG.isChecked()) {
                selectedCategories.add(EhConfig.ARTIST_CG);
                android.util.Log.d("DownloadCategoryTable", "选中ARTIST_CG");
            }
            if (!mGameCG.isChecked()) {
                selectedCategories.add(EhConfig.GAME_CG);
                android.util.Log.d("DownloadCategoryTable", "选中GAME_CG");
            }
            if (!mWestern.isChecked()) {
                selectedCategories.add(EhConfig.WESTERN);
                android.util.Log.d("DownloadCategoryTable", "选中WESTERN");
            }
            if (!mNonH.isChecked()) {
                selectedCategories.add(EhConfig.NON_H);
                android.util.Log.d("DownloadCategoryTable", "选中NON_H");
            }
            if (!mImageSets.isChecked()) {
                selectedCategories.add(EhConfig.IMAGE_SET);
                android.util.Log.d("DownloadCategoryTable", "选中IMAGE_SET");
            }
            if (!mCosplay.isChecked()) {
                selectedCategories.add(EhConfig.COSPLAY);
                android.util.Log.d("DownloadCategoryTable", "选中COSPLAY");
            }
            if (!mAsianPorn.isChecked()) {
                selectedCategories.add(EhConfig.ASIAN_PORN);
                android.util.Log.d("DownloadCategoryTable", "选中ASIAN_PORN");
            }
            if (!mMisc.isChecked()) {
                selectedCategories.add(EhConfig.MISC);
                android.util.Log.d("DownloadCategoryTable", "选中MISC");
            }
        }
        
        android.util.Log.d("DownloadCategoryTable", "返回的分类: " + selectedCategories);
        return selectedCategories;
    }

    /**
     * 设置为全选状态
     */
    public void setAllSelected() {
        for (CheckTextView option : mOptions) {
            // 全选 = 全部处于“未选中”状态
            option.setChecked(false, false);
        }
    }

    /**
     * 清除所有选择
     */
    public void clearAllSelections() {
        for (CheckTextView option : mOptions) {
            // 全部排除 = 全部处于“选中”状态
            option.setChecked(true, false);
        }
    }
}