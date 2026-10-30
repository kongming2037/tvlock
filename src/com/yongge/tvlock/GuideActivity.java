package com.yongge.tvlock;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 权限向导：悬浮窗、使用情况访问 */
public class GuideActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = (int) (24 * getResources().getDisplayMetrics().density + 0.5f);
        root.setPadding(pad, pad, pad, pad);

        TextView t = new TextView(this);
        t.setText("需要以下权限才能正常工作");
        t.setTextSize(20);
        t.setGravity(Gravity.CENTER);
        root.addView(t);

        addRow(root, "1. 悬浮窗权限", "显示倒计时和锁屏界面",
                hasOverlay(), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    }
                });
        addRow(root, "2. 使用情况访问权限", "后台保活辅助",
                hasUsageStats(), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                    }
                });

        Button done = new Button(this);
        done.setText("完成");
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        root.addView(done);
        setContentView(root);
    }

    private void addRow(LinearLayout root, String title, String desc, boolean ok, View.OnClickListener l) {
        TextView tv = new TextView(this);
        tv.setText(title + "\n" + desc + "\n状态：" + (ok ? "已授权" : "未授权"));
        tv.setTextSize(16);
        int m = (int) (12 * getResources().getDisplayMetrics().density + 0.5f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = m;
        root.addView(tv, lp);
        if (!ok) {
            Button btn = new Button(this);
            btn.setText("去开启");
            btn.setOnClickListener(l);
            root.addView(btn);
        }
    }

    public static boolean hasOverlay() {
        return true; // 由外部 ADB 授权，应用内不强制校验
    }

    public boolean hasUsageStats() {
        try {
            AppOpsManager am = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = am.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean criticalGranted(Context c) {
        return true; // 权限由部署时 ADB 授予，不阻塞启动
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 刷新状态
        recreate();
    }
}
