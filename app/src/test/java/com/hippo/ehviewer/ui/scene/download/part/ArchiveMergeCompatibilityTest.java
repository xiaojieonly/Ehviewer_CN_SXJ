package com.hippo.ehviewer.ui.scene.download.part;

import static org.junit.Assert.*;
import android.content.SharedPreferences;
import androidx.recyclerview.widget.RecyclerView;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.widget.MyEasyRecyclerView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.Test;

public class ArchiveMergeCompatibilityTest {
    /** 新导入器仍接受大写扩展名，且不受土耳其语大小写规则影响。 */
    @Test public void acceptsArchivesAcrossLocales() throws Exception {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            for (String name : new String[]{"book.ZIP", "book.RAR", "book.CBZ", "book.CBR"}) {
                assertTrue(accepts(name));
            }
        } finally {
            Locale.setDefault(original);
        }
    }

    /** 缺少文件名或扩展名不受支持时仍拒绝导入。 */
    @Test public void rejectsUnsupportedArchives() throws Exception {
        assertFalse(accepts(null));
        assertFalse(accepts("book.zip.exe"));
        assertFalse(accepts("book"));
    }

    /** 调用迁入独立导入器的真实格式校验逻辑。 */
    private static boolean accepts(String name) throws Exception {
        Method method = DownloadArchiveImporter.class.getDeclaredMethod("isValidArchiveFormat", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, name);
    }

    /** 新分页控制器从归档专用存储刷新进度，同时淘汰离开当前页的缓存。 */
    @Test public void refreshesArchiveProgressAndDropsRemovedRows() throws Exception {
        Field progress = Settings.class.getDeclaredField("sArchiveReadingProgressPre");
        Field count = Settings.class.getDeclaredField("sArchivePageCountPre");
        progress.setAccessible(true);
        count.setAccessible(true);
        Object oldProgress = progress.get(null);
        Object oldCount = count.get(null);
        try {
            progress.set(null, preferences(5));
            count.set(null, preferences(20));
            List<DownloadInfo> rows = new ArrayList<>();
            DownloadInfo info = new DownloadInfo();
            info.gid = 123;
            info.archiveUri = "content://archives/book.zip";
            rows.add(info);
            DownloadPaginationController controller = new DownloadPaginationController(new DownloadPaginationController.Host() {
                /** 返回测试中的当前列表。 */
                @Override public List<DownloadInfo> getList() { return rows; }
                /** 此测试只核对数据，无需界面适配器。 */
                @Override public RecyclerView.Adapter getNotifyAdapter() { return null; }
                /** 此测试不执行滚动。 */
                @Override public MyEasyRecyclerView getRecyclerView() { return null; }
            });
            controller.queryUnreadSpiderInfo();
            assertEquals(5, controller.getSpiderInfoMap().get(123L).startPage);
            assertEquals(20, controller.getSpiderInfoMap().get(123L).pages);
            progress.set(null, preferences(8));
            controller.queryUnreadSpiderInfo();
            assertEquals(8, controller.getSpiderInfoMap().get(123L).startPage);
            controller.resetReadingProgressInUi();
            assertEquals(0, controller.getSpiderInfoMap().get(123L).startPage);
            rows.clear();
            controller.queryUnreadSpiderInfo();
            assertTrue(controller.getSpiderInfoMap().isEmpty());
        } finally {
            progress.set(null, oldProgress);
            count.set(null, oldCount);
        }
    }

    /** 替代 Android 持久层，只允许本回归测试需要的整数读取。 */
    private static SharedPreferences preferences(int value) {
        return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getInt")) { return value; }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
