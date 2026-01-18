package com.hippo.ehviewer.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.ActionBar;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.util.NetworkDiagnosticTool;

/**
 * 网站连通性测试Activity
 */
public class NetworkDiagnosticActivity extends EhActivity {

    private LinearLayout container;
    private ProgressBar progressBar;
    private TextView infoText;
    private volatile boolean isRunning = false;

    @Override
    protected int getThemeResId(int theme) {
        // 使用父类的默认实现，支持自适应主题切换
        return super.getThemeResId(theme);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_diagnostic);

        // 设置ActionBar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.network_diagnostic_title);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        container = findViewById(R.id.diagnostic_container);
        progressBar = findViewById(R.id.diagnostic_progress);
        infoText = findViewById(R.id.network_info_text);

        // 显示当前网络信息
        NetworkDiagnosticTool.NetworkInfo networkInfo = 
            NetworkDiagnosticTool.getNetworkInfo(this);
        updateNetworkInfoDisplay(networkInfo);

        // 开始诊断
        startDiagnostic();
    }

    private void updateNetworkInfoDisplay(NetworkDiagnosticTool.NetworkInfo info) {
        String status = info.isConnected ? 
            getString(R.string.network_status_connected) : 
            getString(R.string.network_status_disconnected);
        String text = String.format(getString(R.string.network_info_format),
                status, info.networkType, info.currentIP);
        infoText.setText(text);
    }

    private void startDiagnostic() {
        if (isRunning) return;
        isRunning = true;

        progressBar.setVisibility(View.VISIBLE);
        container.removeAllViews();

        // 在后台线程执行诊断
        new Thread(() -> {
            String[] domains = {"e-hentai.org", "exhentai.org"};
            try {
                java.util.List<NetworkDiagnosticTool.SiteInfo> results = 
                    NetworkDiagnosticTool.checkMultipleSites(domains);

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    displayResults(results);
                    isRunning = false;
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(NetworkDiagnosticActivity.this, 
                        getString(R.string.diagnostic_error, e.getMessage()), 
                        Toast.LENGTH_LONG).show();
                    isRunning = false;
                });
            }
        }).start();
    }

    private void displayResults(java.util.List<NetworkDiagnosticTool.SiteInfo> results) {
        container.removeAllViews();

        for (NetworkDiagnosticTool.SiteInfo site : results) {
            View itemView = LayoutInflater.from(this).inflate(
                    R.layout.item_diagnostic_result, container, false);

            // 域名
            TextView domainText = itemView.findViewById(R.id.site_domain);
            domainText.setText(site.domain);

            // IP地址
            TextView ipText = itemView.findViewById(R.id.site_ip);
            if ("FAILED".equals(site.resolvedIP)) {
                ipText.setText(R.string.dns_resolution_failed);
                ipText.setTextColor(getErrorColor());
            } else {
                ipText.setText(getString(R.string.ip_address_format, site.resolvedIP));
                ipText.setTextColor(getTextSecondaryColor());
            }

            // 可访问性状态
            TextView statusText = itemView.findViewById(R.id.site_status);
            if (site.isAccessible) {
                statusText.setText(getString(R.string.site_accessible, site.responseTime));
                statusText.setTextColor(getSuccessColor());
            } else {
                statusText.setText(R.string.site_inaccessible);
                statusText.setTextColor(getErrorColor());
                if (site.error != null) {
                    statusText.setText(statusText.getText() + " (" + site.error + ")");
                }
            }

            container.addView(itemView);
        }

        // 添加刷新按钮
        addRefreshButton();
    }

    private void addRefreshButton() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 32, 0, 0);

        android.widget.Button refreshBtn = new android.widget.Button(this);
        refreshBtn.setText(R.string.refresh_diagnostic);
        refreshBtn.setLayoutParams(params);
        refreshBtn.setOnClickListener(v -> startDiagnostic());

        container.addView(refreshBtn);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private int getErrorColor() {
        // 使用红色作为错误颜色，兼容所有Android版本
        return getResources().getColor(android.R.color.holo_red_light);
    }

    private int getSuccessColor() {
        // 使用绿色作为成功颜色
        return getResources().getColor(android.R.color.holo_green_light);
    }

    private int getTextSecondaryColor() {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.textColorSecondary, typedValue, true);
        return typedValue.data;
    }
}
