package com.yongge.tvlock;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class LockActivity extends Activity {
    private boolean lockTaskStarted = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_lock);
        hideNav();

        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null && dpm.isLockTaskPermitted(getPackageName())) {
                startLockTask();
                lockTaskStarted = true;
            }
        } catch (Exception ignored) {
        }

        final EditText pw = findViewById(R.id.pw);
        final TextView err = findViewById(R.id.err);
        Button ok = findViewById(R.id.ok);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Prefs.checkPassword(LockActivity.this, pw.getText().toString())) {
                    if (lockTaskStarted) {
                        try {
                            stopLockTask();
                        } catch (Exception ignored) {
                        }
                    }
                    startService(new Intent(LockActivity.this, TimerService.class).setAction("UNLOCK"));
                    finish();
                } else {
                    err.setText("密码错误");
                    pw.setText("");
                }
            }
        });
    }

    private void hideNav() {
        View d = getWindow().getDecorView();
        d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) hideNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideNav();
    }

    @Override
    public void onBackPressed() {
    }
}
