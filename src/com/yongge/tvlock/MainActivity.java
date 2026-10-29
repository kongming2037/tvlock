package com.yongge.tvlock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView status;
    private boolean guideShown = false;
    private final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);

        Intent s = new Intent(this, TimerService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(s);
        else startService(s);

        Button settings = findViewById(R.id.btn_settings);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askPassword(new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                    }
                });
            }
        });
        Button locknow = findViewById(R.id.btn_locknow);
        locknow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askPassword(new Runnable() {
                    @Override
                    public void run() {
                        startService(new Intent(MainActivity.this, TimerService.class).setAction("RELOCK"));
                        Toast.makeText(MainActivity.this, "已锁定", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        Button guide = findViewById(R.id.btn_guide);
        guide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askPassword(new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(MainActivity.this, GuideActivity.class));
                    }
                });
            }
        });
        refresh();
    }

    private void refresh() {
        int lim = Prefs.getDailyLimitMin(this);
        int rem = Prefs.sp(this).getInt("remaining", lim * 60);
        boolean locked = Prefs.sp(this).getBoolean("locked", false);
        boolean override = Prefs.sp(this).getBoolean("override", false);
        String st = locked ? "已锁定" : (override ? "今日已解锁" : "计时中");
        status.setText("状态：" + st + "\n"
                + "每日时长：" + lim + " 分钟\n今日剩余：" + Prefs.fmt(rem));
    }

    private void askPassword(final Runnable ok) {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("管理员密码")
                .setView(et)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (Prefs.checkPassword(MainActivity.this, et.getText().toString())) {
                            ok.run();
                        } else {
                            Toast.makeText(MainActivity.this, "密码错误", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        registerReceiver(rx, new IntentFilter(TimerService.ACTION_UPDATE));
        // 关键权限缺失时自动弹出设置向导（每个实例只弹一次，避免返回死循环）
        if (!guideShown && !GuideActivity.criticalGranted(this)) {
            guideShown = true;
            startActivity(new Intent(this, GuideActivity.class));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(rx);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onBackPressed() {
        // 按返回键时像 Home 键一样退到后台，不销毁 Activity。
        // 某些系统会把“返回键退出应用”视为用户关闭应用而杀掉整个进程（含前台服务），
        // 导致倒计时消失；退到后台则不会触发该行为。
        moveTaskToBack(true);
    }
}
