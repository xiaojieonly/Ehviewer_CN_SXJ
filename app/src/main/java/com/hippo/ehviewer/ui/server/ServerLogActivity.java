package com.hippo.ehviewer.ui.server;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.server.util.ServerLog;
import com.hippo.ehviewer.ui.ToolbarActivity;

public class ServerLogActivity extends ToolbarActivity {

    private TextView content;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_log);
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24);
        content = findViewById(R.id.server_log_content);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) {
            String dump = ServerLog.dumpText();
            content.setText(dump.isEmpty() ? getString(R.string.server_log_empty) : dump);
        }
    }
}
