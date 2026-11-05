package com.hippo.ehviewer.ui.scene.download.part;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.lang.reflect.Method;
import org.junit.Test;

public class ArchiveReadingProgressTest {
    /** 未知总页数且未阅读时不显示误导性的页码。 */
    @Test public void hidesUnknownUnreadProgress() throws Exception {
        assertNull(format(0, 0));
        assertNull(format(-1, 0));
    }

    /** 总页数尚未读取时，保留已保存的阅读位置。 */
    @Test public void preservesProgressBeforePageCountLoads() throws Exception {
        assertEquals("6/?", format(5, 0));
        assertEquals("2147483648/?", format(Integer.MAX_VALUE, 0));
    }

    /** 页面展示使用从一开始的页码，并限制在归档总页数内。 */
    @Test public void clampsProgressToCurrentArchiveSize() throws Exception {
        assertEquals("1/10", format(-1, 10));
        assertEquals("1/10", format(0, 10));
        assertEquals("10/10", format(99, 10));
        assertEquals("1/1", format(1, 1));
    }

    /** 通过反射调用真实格式化方法，不为测试扩大生产代码可见性。 */
    private static String format(int page, int count) throws Exception {
        Method method = DownloadAdapter.class.getDeclaredMethod("formatArchiveReadingProgress", int.class, int.class);
        method.setAccessible(true);
        return (String) method.invoke(null, page, count);
    }
}
