package com.hippo.ehviewer.ui.scene.download;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.dao.DownloadLabel;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.scene.Announcer;
import com.hippo.yorozuya.AssertUtils;
import com.hippo.yorozuya.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DownloadLabelDraw {
    private final LayoutInflater inflater;
    private final DownloadsScene scene;
    private final ViewGroup container;
    private final Context context;

    private View view;
    private Toolbar toolbar;
    private ListView listView;

    public DownloadLabelDraw(LayoutInflater inflater, @Nullable ViewGroup container,DownloadsScene scene){
        this.inflater = inflater;
        this.container = container;
        this.scene = scene;
        this.context = scene.getEHContext();
    }

    public View createView(){
        view = inflater.inflate(R.layout.bookmarks_draw, container, false);
        assert context != null;
        AssertUtils.assertNotNull(context);

        toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.download_labels);
        toolbar.inflateMenu(R.menu.drawer_download);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            switch (id) {
                case R.id.action_settings:
                    scene.startScene(new Announcer(DownloadLabelsScene.class));
                    return true;
                case R.id.action_default_download_label:
                    DownloadManager dm = scene.getMDownloadManager();
                    if (null == dm) {
                        return true;
                    }

                    List<DownloadLabel> list = dm.getLabelList();
                    final String[] items = new String[list.size() + 2];
                    items[0] = scene.getString(R.string.let_me_select);
                    items[1] = scene.getString(R.string.default_download_label_name);
                    for (int i = 0, n = list.size(); i < n; i++) {
                        items[i + 2] = list.get(i).getLabel();
                    }
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.default_download_label)
                            .setItems(items, (dialog, which) -> {
                                if (which == 0) {
                                    Settings.putHasDefaultDownloadLabel(false);
                                } else {
                                    Settings.putHasDefaultDownloadLabel(true);
                                    String label;
                                    if (which == 1) {
                                        label = null;
                                    } else {
                                        label = items[which];
                                    }
                                    Settings.putDefaultDownloadLabel(label);
                                }
                            }).show();
                    return true;
            }
            return false;
        });

        final DownloadManager downloadManager = EhApplication.getDownloadManager(context);


        List<DownloadLabel> list = downloadManager.getLabelList();
        final List<String> labels = new ArrayList<>(list.size() + 1);
        // Add default label name
        labels.add(scene.getString(R.string.default_download_label_name));
        for (DownloadLabel raw : list) {
            labels.add(raw.getLabel());
        }

        // TODO handle download label items update
        final List<DownloadLabelItem> downloadLabelList = new ArrayList<>();

        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            if (i == 0) {
                downloadLabelList.add(new DownloadLabelItem(label, downloadManager.getDefaultDownloadInfoList().size()));
                continue;
            }
            downloadLabelList.add(new DownloadLabelItem(label, downloadManager.getLabelCount(label)));
        }

        listView = (ListView) view.findViewById(R.id.list_view);
        DownloadLabelAdapter adapter = new DownloadLabelAdapter(Objects.requireNonNull(scene.getEHContext()), R.layout.item_download_label_list, downloadLabelList);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view1, position, id) -> {
            if (scene.searching) {
                Toast.makeText(context, R.string.download_searching, Toast.LENGTH_LONG).show();
                return;
            }
            String label;
            if (position == 0) {
                label = null;
            } else {
                label = labels.get(position);
            }
            if (!ObjectUtils.equal(label, scene.mLabel)) {
                scene.mLabel = label;
                scene.updateForLabel();
                if (scene.searchKey != null && !scene.searchKey.isEmpty()) {
                    scene.startSearching();
                } else {
                    scene.updateView();
                }
                scene.closeDrawer(Gravity.RIGHT);
            }

        });
        return view;
    }

    public void updateDownloadLabels(){
        final DownloadManager downloadManager = EhApplication.getDownloadManager(context);
        List<DownloadLabel> list = downloadManager.getLabelList();
        final List<String> labels = new ArrayList<>(list.size() + 1);
        // Add default label name
        labels.add(scene.getString(R.string.default_download_label_name));
        for (DownloadLabel raw : list) {
            labels.add(raw.getLabel());
        }

        // TODO handle download label items update
        final List<DownloadLabelItem> downloadLabelList = new ArrayList<>();

        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            if (i == 0) {
                downloadLabelList.add(new DownloadLabelItem(label, downloadManager.getDefaultDownloadInfoList().size()));
                continue;
            }
            downloadLabelList.add(new DownloadLabelItem(label, downloadManager.getLabelCount(label)));
        }

        DownloadLabelAdapter adapter = new DownloadLabelAdapter(Objects.requireNonNull(scene.getEHContext()), R.layout.item_download_label_list, downloadLabelList);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view1, position, id) -> {
            if (scene.searching) {
                Toast.makeText(context, R.string.download_searching, Toast.LENGTH_LONG).show();
                return;
            }
            String label;
            if (position == 0) {
                label = null;
            } else {
                label = labels.get(position);
            }
            if (!ObjectUtils.equal(label, scene.mLabel)) {
                scene.mLabel = label;
                scene.updateForLabel();
                if (scene.searchKey != null && !scene.searchKey.isEmpty()) {
                    scene.startSearching();
                } else {
                    scene.updateView();
                }
                scene.closeDrawer(Gravity.RIGHT);
            }

        });
    }
}
