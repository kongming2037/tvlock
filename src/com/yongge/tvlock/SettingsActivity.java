package com.yongge.tvlock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);

        final EditText minutes = findViewById(R.id.minutes);
        minutes.setText(String.valueOf(Prefs.getDailyLimitMin(this)));

        findViewById(R.id.save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    int m = Integer.parseInt(minutes.getText().toString().trim());
                    if (m < 1) m = 1;
                    if (m > 1440) m = 1440;
                    Prefs.setDailyLimitMin(SettingsActivity.this, m);
                    Toast.makeText(SettingsActivity.this, "已保存，明天 0 点生效", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(SettingsActivity.this, "请输入数字", Toast.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.chpw).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changePassword();
            }
        });

        findViewById(R.id.add30).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(SettingsActivity.this, TimerService.class);
                i.setAction("ADD_TIME");
                i.putExtra("min", 30);
                startService(i);
                Toast.makeText(SettingsActivity.this, "已加时 30 分钟", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.resetday).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(SettingsActivity.this, TimerService.class).setAction("RESET_DAY"));
                Toast.makeText(SettingsActivity.this, "今日计时已重置", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void changePassword() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density + 0.5f);
        ll.setPadding(pad, pad / 2, pad, pad / 2);
        final EditText oldPw = new EditText(this);
        oldPw.setHint("原密码");
        final EditText newPw = new EditText(this);
        newPw.setHint("新密码");
        final EditText newPw2 = new EditText(this);
        newPw2.setHint("确认新密码");
        int t = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        oldPw.setInputType(t);
        newPw.setInputType(t);
        newPw2.setInputType(t);
        ll.addView(oldPw);
        ll.addView(newPw);
        ll.addView(newPw2);
        new AlertDialog.Builder(this)
                .setTitle("修改管理员密码")
                .setView(ll)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String o = oldPw.getText().toString();
                        String n1 = newPw.getText().toString();
                        String n2 = newPw2.getText().toString();
                        if (!Prefs.checkPassword(SettingsActivity.this, o)) {
                            Toast.makeText(SettingsActivity.this, "原密码错误", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (n1.length() < 4) {
                            Toast.makeText(SettingsActivity.this, "新密码至少 4 位", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (!n1.equals(n2)) {
                            Toast.makeText(SettingsActivity.this, "两次输入不一致", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Prefs.setPassword(SettingsActivity.this, n1);
                        Toast.makeText(SettingsActivity.this, "密码已修改", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        // 同 MainActivity：退到后台而非退出，避免系统杀进程
        moveTaskToBack(true);
    }
}
