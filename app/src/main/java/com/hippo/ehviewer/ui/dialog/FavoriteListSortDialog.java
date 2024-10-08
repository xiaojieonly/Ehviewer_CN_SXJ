package com.hippo.ehviewer.ui.dialog;

import static com.hippo.ehviewer.client.EhConfig.ORDER_BY_FAV_TIME;
import static com.hippo.ehviewer.client.EhConfig.ORDER_BY_PUB_TIME;

import android.app.AlertDialog;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.parser.FavoritesParser;
import com.hippo.ehviewer.ui.scene.FavoritesScene;

public class FavoriteListSortDialog {
    private final FavoritesScene scene;

    public FavoriteListSortDialog(FavoritesScene scene) {
        this.scene = scene;
    }

    public void showCloudSort(FavoritesParser.Result mResult) {
        int checked;
        if (null == mResult || null == mResult.favOrder) {
            return;
        }
        if (mResult.favOrder.equals(ORDER_BY_FAV_TIME)) {
            checked = 0;
        } else if (mResult.favOrder.equals(ORDER_BY_PUB_TIME)) {
            checked = 1;
        } else {
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(scene.getContext())
                .setIcon(R.mipmap.ic_launcher)
                .setTitle(R.string.order)
                .setSingleChoiceItems(R.array.fav_sort, checked, (dialogInterface, i) -> {
                    if (i == 0) {
                        scene.updateSort("inline_set=fs_f");
                    } else {
                        scene.updateSort("inline_set=fs_p");
                    }
                    dialogInterface.dismiss();
                })
                .create();
        dialog.show();
    }

    public void showLocalSort(FavoritesParser.Result mResult) {

    }
}
