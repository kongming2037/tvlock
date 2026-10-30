package com.yongge.tvlock;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;
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

        findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        refresh();
    }

    private void refresh() {
        int lim = Prefs.getPeriodMin(this);
        boolean locked = Prefs.sp(this).getBoolean("locked", false);
        String st = locked ? "已锁定" : "计时中";
        status.setText("状态：" + st + "\n"
                + "倒计时周期：" + lim + " 分钟");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        registerReceiver(rx, new IntentFilter(TimerService.ACTION_UPDATE));
        if (!GuideActivity.criticalGranted(this)) {
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
        moveTaskToBack(true);
    }
}
