package com.hippo.ehviewer.client.data.userTag;

import android.os.Parcel;
import android.os.Parcelable;

import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhTagDatabase;
import com.hippo.ehviewer.util.TagTranslationUtil;

public class UserTag implements Parcelable {

    public String userTagId;
    public String tagName;
    public boolean watched;
    public boolean hidden;
    public String color;
    public int tagWeight;

    public UserTag(){

    }

    protected UserTag(Parcel in) {
        this.userTagId = in.readString();
        this.tagName = in.readString();
        this.watched = in.readByte() != 0;
        this.hidden = in.readByte() != 0;
        this.color = in.readString();
        this.tagWeight = in.readInt();
    }

    public static final Creator<UserTag> CREATOR = new Creator<UserTag>() {
        @Override
        public UserTag createFromParcel(Parcel in) {
            return new UserTag(in);
        }

        @Override
        public UserTag[] newArray(int size) {
            return new UserTag[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.userTagId);
        dest.writeString(this.tagName);
        dest.writeByte(watched ? (byte) 1 : (byte) 0);
        dest.writeByte(hidden ? (byte) 1 : (byte) 0);
        dest.writeString(this.color);
    }

    public String getName(EhTagDatabase ehTags) {
        //汉化标签
        final boolean judge = Settings.getShowTagTranslations();
        if (judge) {
            String name = tagName;
            //重设标签名称,并跳过已翻译的标签
            if (name != null && 2 == name.split(":").length) {
                return  TagTranslationUtil.getTagCN(name.split(":"), ehTags);
            }
        }
        return tagName;
    }


    public long getId(){
        return Long.parseLong(userTagId.substring(8));
    }


    public String deleteParam(){
        return "usertag_action=mass" +
                "&tagname_new=" +
                "&tagcolor_new=" +
                "&tagweight_new="+tagWeight
                +"&modify_usertags%5B%5D=" + getId()+
                "&usertag_target=0";
    }

}
