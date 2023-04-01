package com.hippo.ehviewer.ui.scene.gallery.list;

import static com.hippo.ehviewer.ui.scene.BaseScene.LENGTH_SHORT;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.UrlOpener;
import com.hippo.ehviewer.client.EhFilter;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.dao.Filter;
import com.hippo.ehviewer.ui.MainActivity;

public class GalleryListSecenDialog {
    final GalleryListScene scene;
    final Context context;
    private String tagName;
    GalleryListSecenDialog(GalleryListScene scene){
        this.scene = scene;
        this.context = scene.getContext();
    }

    public void setTagName(String tagName){
        this.tagName = tagName;
    }

    public void showTagLongPressDialog(){
        String temp;
        int index = tagName.indexOf(':');
        if (index >= 0) {
            temp = tagName.substring(index + 1);
        } else {
            temp = tagName;
        }
        final String tag2 = temp;

        new AlertDialog.Builder(context)
                .setTitle(tagName)
                .setItems(R.array.tag_menu_entries, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            UrlOpener.openUrl(context, EhUrl.getTagDefinitionUrl(tag2), false);
                            break;
                        case 1:
                            showFilterTagDialog();
                            break;
                    }
                })
                .setNegativeButton(R.string.copy_tag, (dialog, which) -> copyTag(tagName))
                .show();
    }

    public void showFilterTagDialog(){
        if (context == null) {
            return;
        }

        new AlertDialog.Builder(context)
                .setMessage(context.getString(R.string.filter_the_tag, tagName))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (which != DialogInterface.BUTTON_POSITIVE) {
                        return;
                    }

                    Filter filter = new Filter();
                    filter.mode = EhFilter.MODE_TAG;
                    filter.text = tagName;
                    EhFilter.getInstance().addFilter(filter);

                    showTip(R.string.filter_added, LENGTH_SHORT);
                }).show();
    }

    private void showTip(@StringRes int id, int length) {
        FragmentActivity activity = scene.getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).showTip(id, length);
        }
    }

    private void copyTag(String tag) {
        ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText(null, tag));
        Toast.makeText(context, R.string.gallery_tag_copy, Toast.LENGTH_LONG).show();
    }
}
