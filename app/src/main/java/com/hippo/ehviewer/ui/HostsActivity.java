/*
 * Copyright 2018 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.ui;

/*
 * Created by Hippo on 2018/3/23.
 */

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;
import com.hippo.android.resource.AttrResources;
import com.hippo.easyrecyclerview.EasyRecyclerView;
import com.hippo.easyrecyclerview.LinearDividerItemDecoration;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.Hosts;
import com.hippo.ehviewer.R;
import com.hippo.ripple.Ripple;
import com.hippo.yorozuya.LayoutUtils;
import java.util.List;
import java.util.Locale;

public class HostsActivity extends ToolbarActivity
    implements EasyRecyclerView.OnItemClickListener, View.OnClickListener {

  private static final String DIALOG_TAG_ADD_HOST = AddHostDialogFragment.class.getName();
  private static final String DIALOG_TAG_EDIT_HOST = EditHostDialogFragment.class.getName();

  private static final String KEY_HOST = "com.hippo.ehviewer.ui.HostsActivity.HOST";
  private static final String KEY_IP = "com.hippo.ehviewer.ui.HostsActivity.IP";

  private Hosts hosts;
  private List<Pair<String, String>> data;

  private EasyRecyclerView recyclerView;
  private View tip;
  private HostsAdapter adapter;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    hosts = EhApplication.getHosts(this);
    data = hosts.getAll();

    setContentView(R.layout.activity_hosts);
    setNavigationIcon(R.drawable.v_arrow_left_dark_x24);
    recyclerView = findViewById(R.id.recycler_view);
    tip = findViewById(R.id.tip);
    FloatingActionButton fab = findViewById(R.id.fab);

    adapter = new HostsAdapter();
    recyclerView.setAdapter(adapter);
    recyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
    LinearDividerItemDecoration decoration = new LinearDividerItemDecoration(
        LinearDividerItemDecoration.VERTICAL,
        AttrResources.getAttrColor(this, R.attr.dividerColor),
        LayoutUtils.dp2pix(this, 1));
    decoration.setShowLastDivider(true);
    recyclerView.addItemDecoration(decoration);
    recyclerView.setSelector(Ripple.generateRippleDrawable(this, !AttrResources.getAttrBoolean(this, R.attr.isLightTheme), new ColorDrawable(Color.TRANSPARENT)));
    recyclerView.setHasFixedSize(true);
    recyclerView.setOnItemClickListener(this);
    recyclerView.setPadding(
        recyclerView.getPaddingLeft(),
        recyclerView.getPaddingTop(),
        recyclerView.getPaddingRight(),
        recyclerView.getPaddingBottom() + getResources().getDimensionPixelOffset(R.dimen.gallery_padding_bottom_fab));

    fab.setOnClickListener(this);

    recyclerView.setVisibility(data.isEmpty() ? View.GONE : View.VISIBLE);
    tip.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public boolean onItemClick(EasyRecyclerView easyRecyclerView, View view, int position, long id) {
    Pair<String, String> pair = data.get(position);
    Bundle args = new Bundle();
    args.putString(KEY_HOST, pair.first);
    args.putString(KEY_IP, pair.second);

    DialogFragment fragment = new EditHostDialogFragment();
    fragment.setArguments(args);
    fragment.show(getSupportFragmentManager(), DIALOG_TAG_EDIT_HOST);

    return true;
  }

  @Override
  public void onClick(View v) {
    new AddHostDialogFragment().show(getSupportFragmentManager(), DIALOG_TAG_ADD_HOST);
  }

  private void notifyHostsChanges() {
    data = hosts.getAll();
    recyclerView.setVisibility(data.isEmpty() ? View.GONE : View.VISIBLE);
    tip.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
    adapter.notifyDataSetChanged();
  }

  private class HostsHolder extends RecyclerView.ViewHolder {

    public final TextView host;
    public final TextView ip;

    public HostsHolder(View itemView) {
      super(itemView);
      host = itemView.findViewById(R.id.host);
      ip = itemView.findViewById(R.id.ip);
    }
  }

  private class HostsAdapter extends RecyclerView.Adapter<HostsHolder> {

    private final LayoutInflater inflater = getLayoutInflater();

    @NonNull
    @Override
    public HostsHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      return new HostsHolder(inflater.inflate(R.layout.item_hosts, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull HostsHolder holder, int position) {
      Pair<String, String> pair = data.get(position);
      holder.host.setText(pair.first);
      holder.ip.setText(pair.second);
    }

    @Override
    public int getItemCount() {
      return data.size();
    }
  }

  public abstract static class HostDialogFragment extends DialogFragment {

    private TextView host;
    private TextView ip;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_hosts, null, false);
      host = view.findViewById(R.id.host);
      ip = view.findViewById(R.id.ip);

      Bundle arguments = getArguments();
      if (savedInstanceState == null && arguments != null) {
        host.setText(arguments.getString(KEY_HOST));
        ip.setText(arguments.getString(KEY_IP));
      }

      AlertDialog.Builder builder = new AlertDialog.Builder(getContext()).setView(view);
      onCreateDialogBuilder(builder);
      AlertDialog dialog = builder.create();
      dialog.setOnShowListener(d -> onCreateDialog((AlertDialog) d));

      return dialog;
    }

    protected abstract void onCreateDialogBuilder(AlertDialog.Builder builder);

    protected abstract void onCreateDialog(AlertDialog dialog);

    protected void put(AlertDialog dialog) {
      TextView host = dialog.findViewById(R.id.host);
      TextView ip = dialog.findViewById(R.id.ip);
      String hostString = host.getText().toString().trim().toLowerCase(Locale.US);
      String ipString = ip.getText().toString().trim();

      if (!Hosts.isValidHost(hostString)) {
        TextInputLayout hostInputLayout = dialog.findViewById(R.id.host_input_layout);
        hostInputLayout.setError(getContext().getString(R.string.invalid_host));
        return;
      }

      if (!Hosts.isValidIp(ipString)) {
        TextInputLayout ipInputLayout = dialog.findViewById(R.id.ip_input_layout);
        ipInputLayout.setError(getContext().getString(R.string.invalid_ip));
        return;
      }

      HostsActivity activity = (HostsActivity) dialog.getOwnerActivity();
      activity.hosts.put(hostString, ipString);
      activity.notifyHostsChanges();

      dialog.dismiss();
    }

    protected void delete(AlertDialog dialog) {
      TextView host = dialog.findViewById(R.id.host);
      String hostString = host.getText().toString().trim().toLowerCase(Locale.US);

      HostsActivity activity = (HostsActivity) dialog.getOwnerActivity();
      activity.hosts.delete(hostString);
      activity.notifyHostsChanges();

      dialog.dismiss();
    }
  }

  public static class AddHostDialogFragment extends HostDialogFragment {

    @Override
    protected void onCreateDialogBuilder(AlertDialog.Builder builder) {
      builder.setTitle(R.string.add_host);
      builder.setPositiveButton(R.string.add_host_add, null);
    }

    @Override
    protected void onCreateDialog(AlertDialog dialog) {
      dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> put(dialog));
    }
  }

  public static class EditHostDialogFragment extends HostDialogFragment {

    @Override
    protected void onCreateDialogBuilder(AlertDialog.Builder builder) {
      builder.setTitle(R.string.edit_host);
      builder.setPositiveButton(R.string.edit_host_confirm, null);
      builder.setNegativeButton(R.string.edit_host_delete, null);
    }

    @Override
    protected void onCreateDialog(AlertDialog dialog) {
      dialog.findViewById(R.id.host_input_layout).setEnabled(false);
      dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> put(dialog));
      dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(v -> delete(dialog));
    }
  }
}
