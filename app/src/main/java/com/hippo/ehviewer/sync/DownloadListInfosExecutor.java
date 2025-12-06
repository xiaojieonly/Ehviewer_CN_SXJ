package com.hippo.ehviewer.sync;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.callBack.DownloadSearchCallback;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.GalleryTags;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.widget.AdvanceSearchTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;;

public class DownloadListInfosExecutor {
    private static final int sortByIdAsc = 1;
    private static final int sortByIdDesc = 2;
    private static final int sortByCreateTimeAsc = 3;
    private static final int sortByCreateTimeDesc = 4;
    private static final int sortByRatingAsc = 5;
    private static final int sortByRatingDesc = 6;


    private final String TAG = "DownloadSearchingExecutor";

    ExecutorService service = Executors.newSingleThreadExecutor();
    Handler handler = new Handler(Looper.getMainLooper());

    private DownloadSearchCallback mDownloadSearchCallback;

    @Nullable
    private List<DownloadInfo> mList;

    private List<DownloadInfo> resultList;

    private final String mSearchKey;

    private DownloadManager mDownloadManager;

    public DownloadListInfosExecutor(@Nullable List<DownloadInfo> mList, String searchKey) {
        this.mList = mList;
        this.mSearchKey = searchKey;
    }

    public DownloadListInfosExecutor(@Nullable List<DownloadInfo> mList, DownloadManager downloadManager) {
        this.mList = mList;
        this.mSearchKey = "";
        mDownloadManager = downloadManager;
    }

    public void setDownloadSearchingListener(DownloadSearchCallback downloadSearchCallback) {
        mDownloadSearchCallback = downloadSearchCallback;
    }

    public void executeSearching() {
        service.execute(() -> {
            resultList = searchingInBackground();

            handler.post(() -> {
                if (mDownloadSearchCallback == null) {
                    return;
                }
                mDownloadSearchCallback.onDownloadSearchSuccess(resultList);
            });
        });
    }

    @SuppressLint("NonConstantResourceId")
    public void executeFilterAndSort(int id) {
        service.execute(() -> {
            switch (id) {

                case R.id.download_done:
                    resultList = filterDownloadState(DownloadInfo.STATE_FINISH);
                    break;
                case R.id.not_started:
                    resultList = filterDownloadState(DownloadInfo.STATE_NONE);
                    break;
                case R.id.waiting:
                    resultList = filterDownloadState(DownloadInfo.STATE_WAIT);
                    break;
                case R.id.downloading:
                    resultList = filterDownloadState(DownloadInfo.STATE_DOWNLOAD);
                    break;
                case R.id.failed:
                    resultList = filterDownloadState(DownloadInfo.STATE_FAILED);
                    break;
                case R.id.sort_by_gallery_id_asc:
                case R.id.sort_by_gallery_id_desc:
                case R.id.sort_by_create_time_asc:
                case R.id.sort_by_create_time_desc:
                case R.id.sort_by_rating_asc:
                case R.id.sort_by_rating_desc:
                case R.id.sort_by_name_asc:
                case R.id.sort_by_name_desc:
                    resultList = sortByType(id);
                    break;
                case R.id.all:
                case R.id.sort_by_default:
                default:
                    resultList = mList;
                    break;
            }

            handler.post(() -> {
                if (mDownloadSearchCallback == null) {
                    return;
                }
                mDownloadSearchCallback.onDownloadSearchSuccess(resultList);
            });
        });
    }

    // 新增方法：同时应用状态过滤和排序
    public void executeFilterAndSort(int statusId, int sortId) {
        Log.d("DownloadListInfos", "executeFilterAndSort: 开始, statusId=" + statusId + ", sortId=" + sortId);
        Log.d("DownloadListInfos", "executeFilterAndSort: 输入列表大小=" + (mList != null ? mList.size() : 0));
        
        service.execute(() -> {
            // 先应用状态过滤
            List<DownloadInfo> filteredList = mList;
            if (statusId != R.id.all) {
                Log.d("DownloadListInfos", "executeFilterAndSort: 应用状态过滤, statusId=" + statusId);
                switch (statusId) {
                    case R.id.download_done:
                        filteredList = filterDownloadState(DownloadInfo.STATE_FINISH);
                        break;
                    case R.id.not_started:
                        filteredList = filterDownloadState(DownloadInfo.STATE_NONE);
                        break;
                    case R.id.waiting:
                        filteredList = filterDownloadState(DownloadInfo.STATE_WAIT);
                        break;
                    case R.id.downloading:
                        filteredList = filterDownloadState(DownloadInfo.STATE_DOWNLOAD);
                        break;
                    case R.id.failed:
                        filteredList = filterDownloadState(DownloadInfo.STATE_FAILED);
                        break;
                    default:
                        filteredList = mList;
                        break;
                }
                Log.d("DownloadListInfos", "executeFilterAndSort: 状态过滤完成，列表大小=" + filteredList.size());
            }

            // 再应用排序
            if (sortId != R.id.sort_by_default) {
                Log.d("DownloadListInfos", "executeFilterAndSort: 应用排序, sortId=" + sortId);
                // 临时保存mList并设置为过滤后的列表
                List<DownloadInfo> originalList = this.mList;
                this.mList = filteredList;
                resultList = sortByType(sortId);
                // 恢复原始列表
                this.mList = originalList;
                Log.d("DownloadListInfos", "executeFilterAndSort: 排序完成，结果列表大小=" + resultList.size());
            } else {
                resultList = filteredList;
                Log.d("DownloadListInfos", "executeFilterAndSort: 使用默认排序，结果列表大小=" + resultList.size());
            }

            handler.post(() -> {
                if (mDownloadSearchCallback == null) {
                    Log.e("DownloadListInfos", "executeFilterAndSort: 回调为null");
                    return;
                }
                Log.d("DownloadListInfos", "executeFilterAndSort: 调用成功回调，结果列表大小=" + resultList.size());
                mDownloadSearchCallback.onDownloadSearchSuccess(resultList);
            });
        });
    }

    // 新增方法：同时应用分类过滤、状态过滤和排序（支持多选分类）
    public void executeFilterAndSort(Set<Integer> categoryIds, int statusId, int sortId) {
        Log.d("DownloadListInfos", "executeFilterAndSort(多选分类): 开始, categoryIds=" + categoryIds + ", statusId=" + statusId + ", sortId=" + sortId);
        Log.d("DownloadListInfos", "executeFilterAndSort(多选分类): 输入列表大小=" + (mList != null ? mList.size() : 0));
        
        service.execute(() -> {
            // 先应用分类过滤
            List<DownloadInfo> filteredList = mList;
            if (categoryIds != null && !categoryIds.contains(EhUtils.ALL_CATEGORY)) {
                Log.d("DownloadListInfos", "executeFilterAndSort(多选分类): 应用分类过滤, categoryIds=" + categoryIds);
                filteredList = filterByCategories(categoryIds);
                Log.d("DownloadListInfos", "executeFilterAndSort(多选分类): 分类过滤完成，列表大小=" + filteredList.size());
            }

            // 再应用状态过滤
            if (statusId != R.id.all) {
                Log.d("DownloadListInfos", "executeFilterAndSort(多选分类): 应用状态过滤, statusId=" + statusId);
                switch (statusId) {
                    case R.id.download_done:
                        filteredList = filterDownloadState(DownloadInfo.STATE_FINISH, filteredList);
                        break;
                    case R.id.not_started:
                        filteredList = filterDownloadState(DownloadInfo.STATE_NONE, filteredList);
                        break;
                    case R.id.waiting:
                        filteredList = filterDownloadState(DownloadInfo.STATE_WAIT, filteredList);
                        break;
                    case R.id.downloading:
                        filteredList = filterDownloadState(DownloadInfo.STATE_DOWNLOAD, filteredList);
                        break;
                    case R.id.failed:
                        filteredList = filterDownloadState(DownloadInfo.STATE_FAILED, filteredList);
                        break;
                    default:
                        break;
                }
                Log.d("DownloadListInfos", "executeFilterAndSort(多选分类): 状态过滤完成，列表大小=" + filteredList.size());
            }

            // 最后应用排序
            if (sortId != R.id.sort_by_default) {
                Log.d("DownloadListInfos", "executeFilterAndSort(多选分类): 应用排序, sortId=" + sortId);
                // 临时保存mList并设置为过滤后的列表
                List<DownloadInfo> originalList = this.mList;
                this.mList = filteredList;
                resultList = sortByType(sortId);
                // 恢复原始列表
                this.mList = originalList;
                Log.d("DownloadListInfos", "executeFilterAndSort(多选分类): 排序完成，结果列表大小=" + resultList.size());
            } else {
                resultList = filteredList;
                Log.d("DownloadListInfos", "executeFilterAndSort(多选分类): 使用默认排序，结果列表大小=" + resultList.size());
            }

            handler.post(() -> {
                if (mDownloadSearchCallback == null) {
                    Log.e("DownloadListInfos", "executeFilterAndSort(多选分类): 回调为null");
                    return;
                }
                Log.d("DownloadListInfos", "executeFilterAndSort(多选分类): 调用成功回调，结果列表大小=" + resultList.size());
                mDownloadSearchCallback.onDownloadSearchSuccess(resultList);
            });
        });
    }

    // 新增方法：同时应用分类过滤、状态过滤和排序
    public void executeFilterAndSort(int categoryId, int statusId, int sortId) {
        Log.d("DownloadListInfos", "executeFilterAndSort(3参数): 开始, categoryId=" + categoryId + ", statusId=" + statusId + ", sortId=" + sortId);
        Log.d("DownloadListInfos", "executeFilterAndSort(3参数): 输入列表大小=" + (mList != null ? mList.size() : 0));
        
        service.execute(() -> {
            // 先应用分类过滤
            List<DownloadInfo> filteredList = mList;
            if (categoryId != EhUtils.ALL_CATEGORY) {
                Log.d("DownloadListInfos", "executeFilterAndSort(3参数): 应用分类过滤, categoryId=" + categoryId);
                filteredList = filterByCategory(categoryId);
                Log.d("DownloadListInfos", "executeFilterAndSort(3参数): 分类过滤完成，列表大小=" + filteredList.size());
            }

            // 再应用状态过滤
            if (statusId != R.id.all) {
                Log.d("DownloadListInfos", "executeFilterAndSort(3参数): 应用状态过滤, statusId=" + statusId);
                switch (statusId) {
                    case R.id.download_done:
                        filteredList = filterDownloadState(DownloadInfo.STATE_FINISH, filteredList);
                        break;
                    case R.id.not_started:
                        filteredList = filterDownloadState(DownloadInfo.STATE_NONE, filteredList);
                        break;
                    case R.id.waiting:
                        filteredList = filterDownloadState(DownloadInfo.STATE_WAIT, filteredList);
                        break;
                    case R.id.downloading:
                        filteredList = filterDownloadState(DownloadInfo.STATE_DOWNLOAD, filteredList);
                        break;
                    case R.id.failed:
                        filteredList = filterDownloadState(DownloadInfo.STATE_FAILED, filteredList);
                        break;
                    default:
                        break;
                }
                Log.d("DownloadListInfos", "executeFilterAndSort(3参数): 状态过滤完成，列表大小=" + filteredList.size());
            }

            // 最后应用排序
            if (sortId != R.id.sort_by_default) {
                Log.d("DownloadListInfos", "executeFilterAndSort(3参数): 应用排序, sortId=" + sortId);
                // 临时保存mList并设置为过滤后的列表
                List<DownloadInfo> originalList = this.mList;
                this.mList = filteredList;
                resultList = sortByType(sortId);
                // 恢复原始列表
                this.mList = originalList;
                Log.d("DownloadListInfos", "executeFilterAndSort(3参数): 排序完成，结果列表大小=" + resultList.size());
            } else {
                resultList = filteredList;
                Log.d("DownloadListInfos", "executeFilterAndSort(3参数): 使用默认排序，结果列表大小=" + resultList.size());
            }

            handler.post(() -> {
                if (mDownloadSearchCallback == null) {
                    Log.e("DownloadListInfos", "executeFilterAndSort(3参数): 回调为null");
                    return;
                }
                Log.d("DownloadListInfos", "executeFilterAndSort(3参数): 调用成功回调，结果列表大小=" + resultList.size());
                mDownloadSearchCallback.onDownloadSearchSuccess(resultList);
            });
        });
    }

    public List<DownloadInfo> sortByType(int type) {
        Log.d("DownloadListInfos", "sortByType: 开始排序, type=" + type);
        if (mList == null) {
            Log.w("DownloadListInfos", "sortByType: mList为null，返回空列表");
            return new ArrayList<>();
        }
        
        Log.d("DownloadListInfos", "sortByType: 排序前列表大小=" + mList.size());
        DownloadInfo[] arr = new DownloadInfo[mList.size()];
        mList.toArray(arr);

        int n = arr.length;
        // 子数组的大小分别为1，2，4，8...
        // 刚开始合并的数组大小是1，接着是2，接着4....
        for (int i = 1; i < n; i += i) {
            //进行数组进行划分
            int left = 0;
            int mid = left + i - 1;
            int right = mid + i;
            //进行合并，对数组大小为 i 的数组进行两两合并
            while (right < n) {
                // 合并函数和递归式的合并函数一样
                merge(arr, left, mid, right, type);
                left = right + 1;
                mid = left + i - 1;
                right = mid + i;
            }
            // 还有一些被遗漏的数组没合并，千万别忘了
            // 因为不可能每个字数组的大小都刚好为 i
            if (left < n && mid < n) {
                merge(arr, left, mid, n - 1, type);
            }
        }
        
        Log.d("DownloadListInfos", "sortByType: 排序完成");
        return Arrays.asList(arr);
    }

    // 合并函数，把两个有序的数组合并起来
    // arr[left..mif]表示一个数组，arr[mid+1 .. right]表示一个数组
    @SuppressLint("NonConstantResourceId")
    private static void merge(DownloadInfo[] arr, int left, int mid, int right, int sortType) {
        //先用一个临时数组把他们合并汇总起来
        DownloadInfo[] a = new DownloadInfo[right - left + 1];
        int i = left;
        int j = mid + 1;
        int k = 0;
        while (i <= mid && j <= right) {
            switch (sortType) {
                case R.id.sort_by_gallery_id_asc:
                    if (arr[i].gid < arr[j].gid) {
                        a[k++] = arr[i++];
                    } else {
                        a[k++] = arr[j++];
                    }
                    break;
                case R.id.sort_by_gallery_id_desc:
                    if (arr[i].gid > arr[j].gid) {
                        a[k++] = arr[i++];
                    } else {
                        a[k++] = arr[j++];
                    }
                    break;
                case R.id.sort_by_create_time_asc:
                    if (arr[i].time < arr[j].time) {
                        a[k++] = arr[i++];
                    } else {
                        a[k++] = arr[j++];
                    }
                    break;
                case R.id.sort_by_create_time_desc:
                    if (arr[i].time > arr[j].time) {
                        a[k++] = arr[i++];
                    } else {
                        a[k++] = arr[j++];
                    }
                    break;
                case R.id.sort_by_rating_asc:
                    if (arr[i].rating < arr[j].rating) {
                        a[k++] = arr[i++];
                    } else {
                        a[k++] = arr[j++];
                    }
                    break;
                case R.id.sort_by_rating_desc:
                    if (arr[i].rating > arr[j].rating) {
                        a[k++] = arr[i++];
                    } else {
                        a[k++] = arr[j++];
                    }
                    break;
                case R.id.sort_by_name_asc: {
                    String titleI = arr[i].title;
                    String titleJ = arr[j].title;
                    // null 值排在最后
                    if (titleI == null && titleJ == null) {
                        a[k++] = arr[i++];
                    } else if (titleI == null) {
                        a[k++] = arr[j++];
                    } else if (titleJ == null) {
                        a[k++] = arr[i++];
                    } else {
                        // 使用 compareToIgnoreCase 进行不区分大小写的比较
                        if (titleI.compareToIgnoreCase(titleJ) < 0) {
                            a[k++] = arr[i++];
                        } else {
                            a[k++] = arr[j++];
                        }
                    }
                    break;
                }
                case R.id.sort_by_name_desc: {
                    String titleI = arr[i].title;
                    String titleJ = arr[j].title;
                    // null 值排在最后
                    if (titleI == null && titleJ == null) {
                        a[k++] = arr[i++];
                    } else if (titleI == null) {
                        a[k++] = arr[j++];
                    } else if (titleJ == null) {
                        a[k++] = arr[i++];
                    } else {
                        // 使用 compareToIgnoreCase 进行不区分大小写的比较
                        if (titleI.compareToIgnoreCase(titleJ) > 0) {
                            a[k++] = arr[i++];
                        } else {
                            a[k++] = arr[j++];
                        }
                    }
                    break;
                }
            }

        }
        while (i <= mid) a[k++] = arr[i++];
        while (j <= right) a[k++] = arr[j++];
        // 把临时数组复制到原数组
        for (i = 0; i < k; i++) {
            arr[left++] = a[i];
        }
    }

    private List<DownloadInfo> filterDownloadState(int state) {
        List<DownloadInfo> list = new ArrayList<>();
        if (mList == null) {
            return list;
        }
        for (int i = 0; i < mList.size(); i++) {
            DownloadInfo info = mList.get(i);
            if (info.state == state) {
                list.add(info);
            }
        }
        return list;
    }

    // 重载方法：基于指定列表进行状态过滤
    private List<DownloadInfo> filterDownloadState(int state, List<DownloadInfo> sourceList) {
        List<DownloadInfo> list = new ArrayList<>();
        if (sourceList == null) {
            return list;
        }
        for (int i = 0; i < sourceList.size(); i++) {
            DownloadInfo info = sourceList.get(i);
            if (info.state == state) {
                list.add(info);
            }
        }
        return list;
    }

    // 新增方法：按分类过滤
    private List<DownloadInfo> filterByCategory(int categoryId) {
        List<DownloadInfo> list = new ArrayList<>();
        if (mList == null) {
            return list;
        }
        for (int i = 0; i < mList.size(); i++) {
            DownloadInfo info = mList.get(i);
            if (info.category == categoryId) {
                list.add(info);
            }
        }
        return list;
    }

    // 新增方法：按多个分类过滤
    private List<DownloadInfo> filterByCategories(Set<Integer> categoryIds) {
        List<DownloadInfo> list = new ArrayList<>();
        if (mList == null || categoryIds == null) {
            return list;
        }
        
        Log.d("DownloadListInfos", "filterByCategories: 输入分类=" + categoryIds + ", 列表大小=" + mList.size());
        
        for (int i = 0; i < mList.size(); i++) {
            DownloadInfo info = mList.get(i);
            Log.d("DownloadListInfos", "filterByCategories: 检查项目，分类=" + info.category + ", 标题=" + info.title);
            if (categoryIds.contains(info.category)) {
                list.add(info);
                Log.d("DownloadListInfos", "filterByCategories: 匹配成功，添加到结果");
            }
        }
        
        Log.d("DownloadListInfos", "filterByCategories: 过滤后列表大小=" + list.size());
        return list;
    }

    // 新增方法：执行高级搜索
    public void executeAdvancedSearch(String keyword, int searchOption, Set<Integer> categories) {
        Log.d("DownloadListInfos", "executeAdvancedSearch: 开始, keyword=" + keyword + ", searchOption=" + searchOption + ", categories=" + categories);
        Log.d("DownloadListInfos", "executeAdvancedSearch: 输入列表大小=" + (mList != null ? mList.size() : 0));
        
        service.execute(() -> {
            // 先应用分类过滤
            List<DownloadInfo> filteredList = mList;
            if (categories != null && !categories.contains(EhUtils.ALL_CATEGORY)) {
                Log.d("DownloadListInfos", "executeAdvancedSearch: 应用分类过滤, categories=" + categories);
                filteredList = filterByCategories(categories);
                Log.d("DownloadListInfos", "executeAdvancedSearch: 分类过滤完成，列表大小=" + filteredList.size());
            }
            
            // 再应用关键词搜索
            if (keyword != null && !keyword.isEmpty()) {
                Log.d("DownloadListInfos", "executeAdvancedSearch: 应用关键词搜索, keyword=" + keyword);
                filteredList = searchByKeyword(keyword, searchOption, filteredList);
                Log.d("DownloadListInfos", "executeAdvancedSearch: 关键词搜索完成，列表大小=" + filteredList.size());
            }
            
            resultList = filteredList;

            handler.post(() -> {
                if (mDownloadSearchCallback == null) {
                    Log.e("DownloadListInfos", "executeAdvancedSearch: 回调为null");
                    return;
                }
                Log.d("DownloadListInfos", "executeAdvancedSearch: 调用成功回调，结果列表大小=" + resultList.size());
                mDownloadSearchCallback.onDownloadSearchSuccess(resultList);
            });
        });
    }

    protected List<DownloadInfo> searchingInBackground() {
        if (mDownloadSearchCallback == null) {
            return new ArrayList<>();
        }
        if (mSearchKey == null || mSearchKey.isEmpty()) {
            return mList;
        }
        if (mList == null) {
            return new ArrayList<>();
        }
        List<DownloadInfo> cache = new ArrayList<>();

        for (int i = 0; i < mList.size(); i++) {
            DownloadInfo info = mList.get(i);
            if (EhUtils.judgeSuitableTitle(info, mSearchKey)) {
                cache.add(info);
            } else if (matchTag(mSearchKey, info)) {
                cache.add(info);
            }
        }

        return cache;
    }

    private boolean matchTag(String mSearchKey, DownloadInfo info) {
        if (info.tgList == null || info.tgList.isEmpty()) {
            info.tgList = searchTagList(info.gid);
        }
        if (info.tgList == null) {
            return false;
        }

        String[] searchTags = mSearchKey.split("  ");

        boolean result = true;
        for (String searchTag : searchTags) {
            if (!info.tgList.contains(searchTag)) {
                result = false;
                break;
            }
        }


        return result;
    }

    private ArrayList<String> searchTagList(long gid) {
        GalleryTags tags = EhDB.queryGalleryTags(gid);

        if (tags == null) {
            return null;
        }

        ArrayList<String> tagList = new ArrayList<>();

        tagList.addAll(parserList("artist", tags.artist));
        tagList.addAll(parserList("rows", tags.rows));
        tagList.addAll(parserList("cosplayer", tags.cosplayer));
        tagList.addAll(parserList("character", tags.character));
        tagList.addAll(parserList("female", tags.female));
        tagList.addAll(parserList("group", tags.group));
        tagList.addAll(parserList("language", tags.language));
        tagList.addAll(parserList("male", tags.male));
        tagList.addAll(parserList("misc", tags.misc));
        tagList.addAll(parserList("mixed", tags.mixed));
        tagList.addAll(parserList("other", tags.other));
        tagList.addAll(parserList("parody", tags.parody));
        tagList.addAll(parserList("reclass", tags.reclass));

        return tagList;
    }

    private ArrayList<String> parserList(String name, String content) {
        if (name == null || content == null) {
            return new ArrayList<>();
        }
        ArrayList<String> list = new ArrayList<>();

        String[] tagNames = content.split(",");

        for (String s : tagNames) {
            list.add(name + ":" + s);
        }

        return list;
    }

    // 新增方法：根据关键词和搜索选项进行搜索
    private List<DownloadInfo> searchByKeyword(String keyword, int searchOption, List<DownloadInfo> sourceList) {
        List<DownloadInfo> list = new ArrayList<>();
        if (sourceList == null || keyword == null || keyword.isEmpty()) {
            return sourceList != null ? sourceList : new ArrayList<>();
        }

        String key = keyword.toLowerCase();
        for (DownloadInfo info : sourceList) {
            boolean match = false;
            
            // 根据搜索选项进行搜索
            if ((searchOption & AdvanceSearchTable.SNAME) != 0 && info.title != null) {
                if (info.title.toLowerCase().contains(key)) {
                    match = true;
                }
            }
            
            if (!match && (searchOption & AdvanceSearchTable.STAGS) != 0 && info.simpleTags != null) {
                String tags = String.join(",", info.simpleTags);
                if (tags.toLowerCase().contains(key)) {
                    match = true;
                }
            }
            
            if (!match && (searchOption & AdvanceSearchTable.SDESC) != 0 && info.title != null) {
                // 简化处理，使用标题代替描述
                if (info.title.toLowerCase().contains(key)) {
                    match = true;
                }
            }
            
            if (!match && (searchOption & AdvanceSearchTable.STORR) != 0 && info.uploader != null) {
                if (info.uploader.toLowerCase().contains(key)) {
                    match = true;
                }
            }
            
            if (match) {
                list.add(info);
            }
        }
        return list;
    }

}
