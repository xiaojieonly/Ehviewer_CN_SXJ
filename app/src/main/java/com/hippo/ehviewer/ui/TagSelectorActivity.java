package com.hippo.ehviewer.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhTagDatabase;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tag selection screen with grouping support.
 * 支持分组的标签选择页面。
 *
 * Key logic / 核心逻辑:
 * 1. Pins common languages to the top. (常用语言置顶)
 * 2. Checks system locale to prevent Chinese tags on English devices. (根据系统语言强制切英文，防止乱码或违和感)
 * 3. Uses lazy loading for large tag lists. (数据量太大，采用了懒加载)
 */
public class TagSelectorActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TagAdapter adapter;
    private TextView tvSelectedCount;

    private final Set<String> selectedTags = new HashSet<>();
    private List<TagGroup> allGroups = new ArrayList<>();
    // Mixed list for Adapter (Headers + Items + Footers)
    // 混合列表，Adapter 会根据类型区分渲染
    private List<Object> displayList = new ArrayList<>();
    private EhTagDatabase ehTags;

    private boolean isDarkMode = false;
    private boolean isChineseSystem = true;

    // Search prefixes for incremental loading (0-9, a-z)
    // 数据库查询前缀，用于分批加载数据
    private static final String[] LOAD_PREFIXES = {
            "", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
            "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_selector);

        // Check locale first, this affects how we display tag names.
        // 先检查语言，这决定了后面是显示中文还是英文
        checkSystemEnvironment();

        ehTags = EhTagDatabase.getInstance(this);

        initView();
        initGroupsStructure();
        buildDisplayList();

        // Use GridLayout: Headers/Footers take 3 spans, Tags take 1 span.
        // 网格布局：标题和页脚占满一行（3格），标签占1格
        GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (position >= displayList.size()) return 1;
                int type = adapter.getItemViewType(position);
                return (type == TagAdapter.TYPE_HEADER || type == TagAdapter.TYPE_FOOTER) ? 3 : 1;
            }
        });
        recyclerView.setLayoutManager(layoutManager);

        adapter = new TagAdapter();
        recyclerView.setAdapter(adapter);
        updateInfo();
    }

    private void checkSystemEnvironment() {
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        isDarkMode = (nightModeFlags == Configuration.UI_MODE_NIGHT_YES);

        // Strict check: Only "zh" gets Chinese tags.
        // 严格检查：只有系统语言是中文(zh)才显示中文标签，其他一律英文
        Locale locale = Locale.getDefault();
        String lang = locale.getLanguage();
        isChineseSystem = "zh".equals(lang);
    }

    private void initView() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Tag Selector");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvSelectedCount = findViewById(R.id.tv_selected_count);
        recyclerView = findViewById(R.id.rv_tag_list);

        // Fix background color issues in some Dark Mode themes
        // 修复部分深色主题下背景太白的问题
        if (isDarkMode) {
            recyclerView.setBackgroundColor(Color.parseColor("#121212"));
            tvSelectedCount.setTextColor(Color.LTGRAY);
        }

        findViewById(R.id.btn_confirm_search).setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            for (String tag : selectedTags) {
                sb.append(tag).append(" ");
            }
            Intent resultIntent = new Intent();
            resultIntent.putExtra("selected_tags", sb.toString().trim());
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        });
    }

    private void updateInfo() {
        tvSelectedCount.setText("Selected: " + selectedTags.size());
    }

    private void initGroupsStructure() {
        allGroups.clear();

        // 1. Pinned Languages (Manually added)
        // 手动添加常用的12种语言，置顶显示
        TagGroup lang = new TagGroup("Language", "language");
        List<String> top12Langs = Arrays.asList(
                "chinese", "japanese", "korean", "english", "russian", "spanish",
                "portuguese", "french", "german", "polish", "thai", "vietnamese"
        );

        for (String key : top12Langs) {
            lang.addPinned(key, getDisplayLanguage(key));
        }

        lang.initialCount = 12;
        lang.visibleCount = 12;
        allGroups.add(lang);

        // 2. Standard Categories
        // 标准分类
        allGroups.add(new TagGroup("Female", "female", 12));
        allGroups.add(new TagGroup("Male", "male", 12));
        allGroups.add(new TagGroup("Parody", "parody", 12));
        allGroups.add(new TagGroup("Character", "character", 12));
        allGroups.add(new TagGroup("Group", "group", 12));
        allGroups.add(new TagGroup("Artist", "artist", 12));
        allGroups.add(new TagGroup("Cosplayer", "cosplayer", 12));

        // Preload standard groups
        // 预加载其他分组
        for (TagGroup group : allGroups) {
            if (!group.namespace.equals("language")) {
                loadNextBatch(group);
            }
        }
    }

    /**
     * Determines the display string for a tag.
     * 决定标签显示什么文字（中文还是英文）。
     */
    private String getDisplayLanguage(String key) {
        if (!isChineseSystem) {
            // Capitalize first letter for English
            // 英文环境：首字母大写
            return key.substring(0, 1).toUpperCase() + key.substring(1);
        }

        // Mapping for pinned languages
        // 常用语言的硬编码翻译
        switch (key) {
            case "chinese": return "中文";
            case "japanese": return "日语";
            case "korean": return "韩语";
            case "english": return "英语";
            case "russian": return "俄语";
            case "spanish": return "西班牙语";
            case "portuguese": return "葡萄牙语";
            case "french": return "法语";
            case "german": return "德语";
            case "polish": return "波兰语";
            case "thai": return "泰语";
            case "vietnamese": return "越南语";
            default: return key.substring(0, 1).toUpperCase() + key.substring(1);
        }
    }

    // Fetch next batch of tags
    // 加载下一批数据
    private void loadNextBatch(TagGroup group) {
        int targetCount = group.visibleCount + 12;
        if (group.loadedTags.size() >= targetCount) {
            group.visibleCount = targetCount;
            refreshList();
        } else {
            // Fetch from DB if local cache is empty
            // 缓存不够了，去数据库查
            group.visibleCount = targetCount;
            if (group.prefixIndex < LOAD_PREFIXES.length) {
                new LoadTagsTask(this, group).execute();
            } else {
                group.visibleCount = group.loadedTags.size();
                refreshList();
            }
        }
    }

    private void collapseGroup(TagGroup group) {
        group.visibleCount = group.initialCount;
        refreshList();
    }

    // ================== AsyncTask ==================
    // Query DB in background to avoid blocking main thread.
    // 后台查库，防止卡UI
    private static class LoadTagsTask extends AsyncTask<Void, Void, List<TagItem>> {
        private final WeakReference<TagSelectorActivity> activityRef;
        private final TagGroup group;
        private final String prefixToSearch;

        LoadTagsTask(TagSelectorActivity activity, TagGroup group) {
            activityRef = new WeakReference<>(activity);
            this.group = group;
            String p = LOAD_PREFIXES[group.prefixIndex];
            this.prefixToSearch = group.namespace + ":" + p;
        }

        @Override
        protected List<TagItem> doInBackground(Void... voids) {
            TagSelectorActivity activity = activityRef.get();
            if (activity == null || activity.ehTags == null) return new ArrayList<>();
            List<TagItem> newItems = new ArrayList<>();
            EhTagDatabase db = activity.ehTags;

            // DB returns raw results, we need to parse them
            List<Pair<String, String>> resultPairs = db.suggest(prefixToSearch);

            if (resultPairs != null) {
                for (Pair<String, String> pair : resultPairs) {
                    String val1 = pair.first; String val2 = pair.second;
                    String searchKey = null; String displayName = null;

                    // Normalize data (key vs translation position varies)
                    // 数据库返回的 key/value 位置可能不固定，这里做下标准化
                    if (val1 != null && val1.startsWith(group.namespace + ":")) { searchKey = val1; displayName = val2; }
                    else if (val2 != null && val2.startsWith(group.namespace + ":")) { searchKey = val2; displayName = val1; }
                    else { searchKey = val1; displayName = val2; }

                    if (searchKey == null || !searchKey.startsWith(group.namespace + ":")) continue;

                    String shortName = searchKey.contains(":") ? searchKey.substring(searchKey.indexOf(":") + 1) : searchKey;

                    if (!activity.isChineseSystem) {
                        // Force English on non-Chinese systems
                        // 非中文系统，强制显示英文
                        displayName = shortName.substring(0, 1).toUpperCase() + shortName.substring(1);
                    } else {
                        // Use translation if available
                        // 有翻译用翻译，没有就用英文
                        if (TextUtils.isEmpty(displayName) || displayName.equals(searchKey)) {
                            displayName = shortName.substring(0, 1).toUpperCase() + shortName.substring(1);
                        } else if (displayName.contains(":")) {
                            displayName = displayName.substring(displayName.indexOf(":") + 1);
                        }
                    }

                    // Skip CJK-only garbage tags
                    // 跳过一些纯中文的脏数据
                    if (isChinese(searchKey)) continue;
                    newItems.add(new TagItem(searchKey, displayName, false));
                }
            }
            return newItems;
        }

        private boolean isChinese(String str) {
            if (str == null) return false;
            for (char c : str.toCharArray()) {
                if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) return true;
            }
            return false;
        }

        @Override
        protected void onPostExecute(List<TagItem> newItems) {
            TagSelectorActivity activity = activityRef.get();
            if (activity == null) return;
            for (TagItem item : newItems) {
                if (!group.addedKeys.contains(item.key)) {
                    group.loadedTags.add(item);
                    group.addedKeys.add(item.key);
                }
            }
            // Continue loading if we haven't filled the visible count
            // 如果查回来的数据不够填满页面，就继续查下一个前缀
            group.prefixIndex++;
            if (group.loadedTags.size() < group.visibleCount && group.prefixIndex < LOAD_PREFIXES.length) {
                new LoadTagsTask(activity, group).execute();
            } else {
                if (group.visibleCount > group.loadedTags.size()) group.visibleCount = group.loadedTags.size();
                activity.refreshList();
            }
        }
    }

    private void buildDisplayList() {
        displayList = new ArrayList<>();
        for (TagGroup group : allGroups) {
            displayList.add(group);
            int limit = Math.min(group.visibleCount, group.loadedTags.size());
            for (int i = 0; i < limit; i++) displayList.add(group.loadedTags.get(i));
            displayList.add(new FooterItem(group));
        }
    }

    private void refreshList() {
        buildDisplayList();
        adapter.notifyDataSetChanged();
    }

    // ================== Data Classes ==================
    static class TagGroup {
        String title; String namespace;
        int initialCount; int visibleCount = 0; int prefixIndex = 0;
        List<TagItem> loadedTags = new ArrayList<>();
        HashSet<String> addedKeys = new HashSet<>();
        TagGroup(String t, String n) { this(t, n, 0); }
        TagGroup(String t, String n, int c) { title = t; namespace = n; initialCount = c; visibleCount = c; }

        void addPinned(String k, String d) {
            String full = namespace + ":" + k;
            if (!addedKeys.contains(full)) { loadedTags.add(new TagItem(full, d, true)); addedKeys.add(full); }
        }
    }
    static class TagItem { String key; String show; boolean isPinned; TagItem(String k, String s, boolean p) { key = k; show = s; isPinned = p;} }
    static class FooterItem { TagGroup group; FooterItem(TagGroup g) { group = g; } }

    // ================== RecyclerView Adapter ==================
    class TagAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        static final int TYPE_HEADER = 0;
        static final int TYPE_ITEM = 1;
        static final int TYPE_FOOTER = 2;

        int colorSelectedBg, colorUnselectedBg;
        int colorSelectedText, colorUnselectedText, colorUnselectedBorder;
        int colorHeader, colorFooterText;

        public TagAdapter() { initColors(); }

        void initColors() {
            if (isDarkMode) {
                colorSelectedBg = Color.parseColor("#AD1457");
                colorUnselectedBg = Color.parseColor("#424242");
                colorSelectedText = Color.WHITE;
                colorUnselectedText = Color.parseColor("#E0E0E0");
                colorUnselectedBorder = Color.parseColor("#616161");
                colorHeader = Color.parseColor("#F48FB1");
                colorFooterText = Color.GRAY;
            } else {
                colorSelectedBg = Color.parseColor("#F48FB1");
                colorUnselectedBg = Color.WHITE;
                colorSelectedText = Color.WHITE;
                colorUnselectedText = Color.parseColor("#757575");
                colorUnselectedBorder = Color.parseColor("#E0E0E0");
                colorHeader = Color.parseColor("#424242");
                colorFooterText = Color.parseColor("#757575");
            }
        }

        @Override public int getItemViewType(int position) {
            Object obj = displayList.get(position);
            if (obj instanceof TagGroup) return TYPE_HEADER;
            if (obj instanceof FooterItem) return TYPE_FOOTER;
            return TYPE_ITEM;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                TextView tv = new TextView(parent.getContext());
                tv.setPadding(32, 48, 16, 16);
                tv.setTextSize(18);
                tv.setTextColor(colorHeader);
                tv.getPaint().setFakeBoldText(true);
                return new RecyclerView.ViewHolder(tv) {};
            }
            else if (viewType == TYPE_FOOTER) {
                // Footer with Expand/Collapse buttons
                RelativeLayout layout = new RelativeLayout(parent.getContext());
                layout.setPadding(32, 24, 32, 24);

                TextView tvExpand = new TextView(parent.getContext());
                tvExpand.setTextSize(14);
                tvExpand.setTextColor(colorFooterText);
                tvExpand.setGravity(Gravity.CENTER);
                tvExpand.setPadding(24, 16, 24, 16);
                RelativeLayout.LayoutParams lpExpand = new RelativeLayout.LayoutParams(-2, -2);
                lpExpand.addRule(RelativeLayout.CENTER_HORIZONTAL);
                layout.addView(tvExpand, lpExpand);

                TextView tvCollapse = new TextView(parent.getContext());
                tvCollapse.setText("▲ Collapse");
                tvCollapse.setTextSize(12);
                tvCollapse.setTextColor(Color.GRAY);
                tvCollapse.setGravity(Gravity.CENTER);
                tvCollapse.setPadding(24, 16, 0, 16);
                RelativeLayout.LayoutParams lpCollapse = new RelativeLayout.LayoutParams(-2, -2);
                lpCollapse.addRule(RelativeLayout.ALIGN_PARENT_END);
                lpCollapse.addRule(RelativeLayout.CENTER_VERTICAL);
                layout.addView(tvCollapse, lpCollapse);

                return new FooterViewHolder(layout, tvExpand, tvCollapse);
            }
            else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tag_chip, parent, false);
                return new TagViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int type = getItemViewType(position);
            Object obj = displayList.get(position);

            if (type == TYPE_HEADER) { ((TextView) holder.itemView).setText(((TagGroup) obj).title); }
            else if (type == TYPE_FOOTER) {
                FooterItem footer = (FooterItem) obj; TagGroup group = footer.group; FooterViewHolder vh = (FooterViewHolder) holder;

                boolean hasMore = group.prefixIndex < LOAD_PREFIXES.length;
                boolean isExpanded = group.visibleCount > group.initialCount;

                // "More" button logic
                if (!hasMore && group.visibleCount >= group.loadedTags.size()) {
                    vh.tvExpand.setText("--- End ---");
                    vh.tvExpand.setEnabled(false);
                } else {
                    vh.tvExpand.setEnabled(true);
                    vh.tvExpand.setText("▼ More");
                    vh.tvExpand.setOnClickListener(v -> {
                        vh.tvExpand.setText("Loading...");
                        loadNextBatch(group);
                    });
                }

                vh.tvCollapse.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
                vh.tvCollapse.setOnClickListener(v -> collapseGroup(group));
            }
            else if (type == TYPE_ITEM) {
                final TagItem item = (TagItem) obj;
                final TagViewHolder vh = (TagViewHolder) holder;
                vh.tv.setText(item.show);

                final boolean isSelected = selectedTags.contains(item.key);

                // Mutate background to avoid sharing state between recycled views
                // 必须 mutate，否则复用 View 时会导致颜色错乱
                try {
                    android.graphics.drawable.GradientDrawable bg = (android.graphics.drawable.GradientDrawable) vh.tv.getBackground().mutate();
                    if (isSelected) {
                        bg.setColor(colorSelectedBg);
                        bg.setStroke(0, colorSelectedBg);
                        vh.tv.setTextColor(colorSelectedText);
                    } else {
                        bg.setColor(colorUnselectedBg);
                        bg.setStroke(2, colorUnselectedBorder);
                        vh.tv.setTextColor(colorUnselectedText);
                    }
                } catch (Exception e) { e.printStackTrace(); }

                vh.itemView.setOnClickListener(v -> {
                    if (selectedTags.contains(item.key)) {
                        selectedTags.remove(item.key);
                        animateTagSelection(vh.itemView, vh.tv, false);
                    } else {
                        selectedTags.add(item.key);
                        animateTagSelection(vh.itemView, vh.tv, true);
                    }
                    updateInfo();
                });
            }
        }

        // Basic scale and color animation for better touch feedback
        // 简单的缩放和颜色过渡动画，提升手感
        private void animateTagSelection(final View containerView, final View textView, final boolean isSelected) {
            containerView.animate()
                    .translationY(-12f)
                    .scaleX(1.1f).scaleY(1.1f)
                    .setDuration(120)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        containerView.animate()
                                .translationY(0f)
                                .scaleX(1.0f).scaleY(1.0f)
                                .setDuration(300)
                                .setInterpolator(new BounceInterpolator())
                                .start();
                    }).start();

            int startBg = isSelected ? colorUnselectedBg : colorSelectedBg;
            int endBg   = isSelected ? colorSelectedBg : colorUnselectedBg;
            int startText = isSelected ? colorUnselectedText : colorSelectedText;
            int endText   = isSelected ? colorSelectedText : colorUnselectedText;

            android.animation.ValueAnimator bgAnim = android.animation.ValueAnimator.ofObject(new android.animation.ArgbEvaluator(), startBg, endBg);
            bgAnim.setDuration(200);
            bgAnim.addUpdateListener(animator -> {
                int color = (int) animator.getAnimatedValue();
                android.graphics.drawable.GradientDrawable bg = (android.graphics.drawable.GradientDrawable) textView.getBackground();
                bg.setColor(color);
                if (isSelected) bg.setStroke(0, color);
                else bg.setStroke(2, colorUnselectedBorder);
            });
            bgAnim.start();

            android.animation.ValueAnimator textAnim = android.animation.ValueAnimator.ofObject(new android.animation.ArgbEvaluator(), startText, endText);
            textAnim.setDuration(200);
            textAnim.addUpdateListener(animator -> ((TextView)textView).setTextColor((int) animator.getAnimatedValue()));
            textAnim.start();
        }

        @Override public int getItemCount() { return displayList.size(); }
        class TagViewHolder extends RecyclerView.ViewHolder { TextView tv; TagViewHolder(View v) { super(v); tv = v.findViewById(R.id.tv_tag_name); } }
        class FooterViewHolder extends RecyclerView.ViewHolder { TextView tvExpand, tvCollapse; FooterViewHolder(View v, TextView e, TextView c) { super(v); tvExpand=e; tvCollapse=c; } }
    }
}