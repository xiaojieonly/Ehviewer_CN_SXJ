package com.hippo.anotherviewer.client.data.topList;

import java.util.ArrayList;

public class TopListItemArray {

    public TopListItem[] itemArray;
//    String name;

    public TopListItemArray(){}

    public int length(){
        return itemArray.length;
    }

    public TopListItem get(int index){
        return itemArray[index];
    }
}
