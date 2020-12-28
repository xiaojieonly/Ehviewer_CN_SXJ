package com.hippo.util;

import com.hippo.ehviewer.client.EhTagDatabase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class TagTranslationutil {

    public static String getTagCN(String[] tags , EhTagDatabase ehTags ){
        if (ehTags != null && tags.length == 2) {
            String group = ehTags.getTranslation("n:" + tags[0]);
            //翻译标签名
            String prefix = EhTagDatabase.namespaceToPrefix(tags[0]);
            String tagstr = ehTags.getTranslation(prefix != null ? prefix + tags[1] : "" + tags[1]);


            if (group != null && tagstr != null){
                return group + "：" + tagstr;
            }else if (group != null && tagstr == null) {
                return group + "：" + tags[1];
            }else if (group == null && tagstr != null) {
                return tags[0] + "：" + tagstr;
            }else {
                return tags[0]+":"+tags[1];
            }
        }else {
            String s="";
            for (int i = 0; i < tags.length; i++) {
                if (i==0){
                    s=s+tags[i];
                }else {
                    s=s+":"+tags[i];
                }
            }

            return s;
        }
    }


    private List<Object> tagsList(String path,String transRow){
        List<String> readlist = fileReader(path);
        List<Object> daoList = new ArrayList<>();
        for (int i = 0; i < readlist.size(); i++) {
            String[] line = readlist.get(i).split("\\|");

        }

        return daoList;

    }

    private List<String> fileReader(String path){
        List<String> readlist = new ArrayList<>();
//        File file = new File("daogenerator/src/main/java/com/hippo/ehviewer/daogenerator/database/artist.txt");
        File file = new File(path);
        String encoding = System.getProperty("file.encoding");
        try {
            if (file.isFile() && file.exists()){
                InputStreamReader textReader = new InputStreamReader(new FileInputStream(file),encoding);
                BufferedReader bufferedReader = new BufferedReader(textReader);
                String line;
                while ((line = bufferedReader.readLine()) != null){
                    readlist.add(line);
                }
            }else {
                System.out.println("路径有误");
            }
        }catch (IOException e){
            System.err.println("????????");
        }
        return readlist;
    }
}
