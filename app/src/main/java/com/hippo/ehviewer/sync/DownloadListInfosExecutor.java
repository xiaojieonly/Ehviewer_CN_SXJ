package com.hippo.ehviewer.sync;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.callBack.DownloadSearchCallback;
import com.hippo.ehviewer.client.EhConfig;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.GalleryTags;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.unifile.UniFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadListInfosExecutor {
    private static final int sortByIdAsc = 1;
    private static final int sortByIdDesc = 2;
    private static final int sortByCreateTimeAsc = 3;
    private static final int sortByCreateTimeDesc = 4;
    private static final int sortByRatingAsc = 5;
    private static final int sortByRatingDesc = 6;
    private static final int sortByFileSizeAsc = 7;
    private static final int sortByFileSizeDesc = 8;


    private final String TAG = "DownloadSearchingExecutor";

    ExecutorService service = Executors.newSingleThreadExecutor();
    Handler handler = new Handler(Looper.getMainLooper());

    private DownloadSearchCallback mDownloadSearchCallback;

    @Nullable
    private final List<DownloadInfo> mList;

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
                case R.id.sort_by_file_size_asc:
                case R.id.sort_by_file_size_desc:
                    resultList = sortByType(id);
                    break;
                case R.id.all_kind:
                case R.id.misc:
                case R.id.doujinshi:
                case R.id.manga:
                case R.id.artist_cg:
                case R.id.game_cg:
                case R.id.image_set:
                case R.id.cosplay:
                case R.id.asian_porn:
                case R.id.non_h:
                case R.id.western:
                case R.id.unknown:
                    resultList = filterDownloadKind(id);
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

    private List<DownloadInfo> sortByType(int type) {
        if (mList == null) {
            return new ArrayList<>();
        }
        DownloadInfo[] arr = new DownloadInfo[mList.size()];
        mList.toArray(arr);

        // 如果是按文件大小排序，先计算所有文件大小
        if (type == R.id.sort_by_file_size_asc || type == R.id.sort_by_file_size_desc) {
            for (DownloadInfo info : arr) {
                if (info.fileSize < 0) { // 未计算过
                    info.fileSize = calculateDownloadDirSize(info);
                }
            }
        }

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
                case R.id.sort_by_file_size_asc:
                    // 未计算的文件大小(-1)排在最后
                    if (arr[i].fileSize < 0 && arr[j].fileSize < 0) {
                        a[k++] = arr[i++];
                    } else if (arr[i].fileSize < 0) {
                        a[k++] = arr[j++];
                    } else if (arr[j].fileSize < 0) {
                        a[k++] = arr[i++];
                    } else if (arr[i].fileSize < arr[j].fileSize) {
                        a[k++] = arr[i++];
                    } else {
                        a[k++] = arr[j++];
                    }
                    break;
                case R.id.sort_by_file_size_desc:
                    // 未计算的文件大小(-1)排在最后
                    if (arr[i].fileSize < 0 && arr[j].fileSize < 0) {
                        a[k++] = arr[i++];
                    } else if (arr[i].fileSize < 0) {
                        a[k++] = arr[j++];
                    } else if (arr[j].fileSize < 0) {
                        a[k++] = arr[i++];
                    } else if (arr[i].fileSize > arr[j].fileSize) {
                        a[k++] = arr[i++];
                    } else {
                        a[k++] = arr[j++];
                    }
                    break;
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

    private List<DownloadInfo> filterDownloadKind(int state) {
        int kind = kindValue(state);
        List<DownloadInfo> list = new ArrayList<>();

        if (mList == null) {
            return null;
        }
        if (kind == EhUtils.ALL_CATEGORY) {
            return mList;
        }
        for(DownloadInfo info : mList){
            if (info.category == kind) {
                list.add(info);
            }
        }
        return list;
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

    /**
     * 计算下载目录的总大小
     */
    private long calculateDownloadDirSize(DownloadInfo info) {
        try {
            UniFile downloadDir = SpiderDen.getGalleryDownloadDir(info);
            if (downloadDir == null || !downloadDir.isDirectory()) {
                return -1;
            }
            return calculateFolderSize(downloadDir);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 递归计算文件夹大小
     */
    private long calculateFolderSize(UniFile folder) {
        long totalSize = 0;
        UniFile[] files = folder.listFiles();

        if (files == null) {
            return 0;
        }

        for (UniFile file : files) {
            if (file.isFile()) {
                long fileSize = file.length();
                if (fileSize > 0) {
                    totalSize += fileSize;
                }
            } else if (file.isDirectory()) {
                totalSize += calculateFolderSize(file); // 递归计算子文件夹
            }
        }

        return totalSize;
    }


    private int kindValue(int id) {
        return switch (id) {
            case R.id.doujinshi -> EhConfig.DOUJINSHI;
            case R.id.manga -> EhConfig.MANGA;
            case R.id.artist_cg -> EhConfig.ARTIST_CG;
            case R.id.game_cg -> EhConfig.GAME_CG;
            case R.id.western -> EhConfig.WESTERN;
            case R.id.non_h -> EhConfig.NON_H;
            case R.id.image_set -> EhConfig.IMAGE_SET;
            case R.id.cosplay -> EhConfig.COSPLAY;
            case R.id.asian_porn -> EhConfig.ASIAN_PORN;
            case R.id.misc -> EhConfig.MISC;
            default -> EhUtils.ALL_CATEGORY;
        };
    }

}
