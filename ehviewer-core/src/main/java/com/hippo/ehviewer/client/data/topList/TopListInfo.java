package com.hippo.ehviewer.client.data.topList;

import com.hippo.ehviewer.client.data.EhTopListDetail;

public class TopListInfo {

    public TopListItemArray allTimeTopList;
    public TopListItemArray pastYearTopList;
    public TopListItemArray pastMonthTopList;
    public TopListItemArray yesterdayTopList;
    public String title;
    public EhTopListDetail.ListType type;

    private ClassLoader classLoader;

    public TopListInfo() {
        classLoader = TopListItemArray.class.getClassLoader();
    }

    public TopListItemArray get(int index) {
        switch (index) {
            case 0:
                return yesterdayTopList;
            case 1:
                return pastMonthTopList;
            case 2:
                return pastYearTopList;
            case 3:
                return allTimeTopList;
            default:
                return null;
        }
    }

    public int size() {
        return 4;
    }
}
