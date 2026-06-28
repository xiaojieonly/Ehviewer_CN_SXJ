package com.hippo.ehviewer.client.data;

import java.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class Tag {
    public String english;
    public String chinese;

    public Tag(String content){
        String[] cArray = content.split("\r");
        chinese = new String(Base64.decode(cArray[1], Base64.DEFAULT), StandardCharsets.UTF_8);
        english = content;
    }

    public Tag(String english,String chinese){
        this.chinese = chinese;
        this.english = english;
    }

    public boolean involve(String chars){
        if (english.contains(chars)){
            return true;
        }
        return chinese.contains(chars);
    }
}
