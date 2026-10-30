package com.yongge.tvlock;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 权限设置向导：逐项检查计时锁必需的权限/状态，
 * 未开启的项提供一键跳转到系统设置的按钮。
 * 电视遥控器方向键可操作。
 */
public class GuideActivity extends Activity {

    private LinearLayout rows;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(28);
        root.setPadding(pad, pad, pad, pad);
        sv.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("权限设置向导");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("以下权限是计时锁正常工作所必需的。\n" +
                "状态为“未开启”的项，请按“去开启”到系统设置中打开。\n" +
                "从系统设置返回后，本页会自动刷新状态。");
        sub.setTextSize(17);
        sub.setPadding(0, dp(12), 0, dp(8));
        root.addView(sub);

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        root.addView(rows);

        Button back = new Button(this);
        back.setText("返回");
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.gravity = Gravity.CENTER;
        bp.topMargin = dp(18);
        root.addView(back, bp);

        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void refresh() {
        rows.removeAllViews();
        final String pkg = getPackageName();
        addRow("1. 使用情况访问权限",
                "计时结束时锁定其它应用",
                hasUsageStats(),
                () -> startActivity(new Intent(
                        Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        // 电池优化：仅在系统提供对应设置页时显示，否则隐藏（部分电视无此功能）
        Intent battIntent = new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + pkg));
        if (canHandle(battIntent)) {
            addRow("2. 忽略电池优化",
                    "防止系统杀掉计时服务",
                    hasBattOpt(),
                    () -> startActivity(battIntent));
        }
        boolean svc = isServiceRunning();
        addRow(canHandle(battIntent) ? "3. 计时服务" : "2. 计时服务",
                svc ? "运行中" : "未运行",
                svc,
                () -> startForegroundService(new Intent(
                        GuideActivity.this, TimerService.class)));
    }

    /** 系统是否有 Activity 能处理该 Intent */
    private boolean canHandle(Intent i) {
        return getPackageManager().queryIntentActivities(i,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY).size() > 0;
    }

    private void addRow(String title, String desc, boolean ok, Runnable onGo) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, dp(10));

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(20);
        txt.addView(t);

        TextView d = new TextView(this);
        d.setText(desc + "  [" + (ok ? "已开启" : "未开启") + "]");
        d.setTextSize(16);
        txt.addView(d);
        row.addView(txt);

        if (!ok) {
            Button b = new Button(this);
            b.setText("去开启");
            b.setFocusable(true);
            b.setOnClickListener(v -> {
                try {
                    onGo.run();
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception ignored) {
                    }
                }
            });
            row.addView(b);
        }
        rows.addView(row);
    }

    private boolean hasUsageStats() {
        AppOpsManager am = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = am.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean hasBattOpt() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean isServiceRunning() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo s : am.getRunningServices(100)) {
            if (TimerService.class.getName().equals(s.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /** 关键权限（使用情况访问）是否齐全，供 MainActivity 自动弹出向导用 */
    public static boolean criticalGranted(Context c) {
        AppOpsManager am = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        int mode = am.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), c.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    @Override
    public void onBackPressed() {
        // 同 MainActivity：退到后台而非退出，避免系统杀进程
        moveTaskToBack(true);
    }
}
